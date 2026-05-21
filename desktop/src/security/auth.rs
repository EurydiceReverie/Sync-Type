use sha2::{Digest, Sha256};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::RwLock;
use tracing::{error, info, warn};

use crate::security::pairing::TrustedDevice;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuthToken {
    pub token_hash: String,
    pub device_name: String,
    pub device_id: String,
    pub created_at: u64,
    pub last_used: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StoredDevices {
    pub devices: HashMap<String, AuthToken>,
}

pub struct SecurityManager {
    trusted_devices: Arc<RwLock<HashMap<String, AuthToken>>>,
    active_sessions: Arc<RwLock<HashMap<String, SessionInfo>>>,
}

#[derive(Debug, Clone)]
pub struct SessionInfo {
    pub device_id: String,
    pub connected_at: u64,
    pub last_activity: u64,
}

impl SecurityManager {
    pub fn new() -> Self {
        let devices = Self::load_from_disk();
        Self {
            trusted_devices: Arc::new(RwLock::new(devices)),
            active_sessions: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    fn get_storage_path() -> PathBuf {
        let mut path = dirs::config_dir().unwrap_or_else(|| PathBuf::from("."));
        path.push("sync-type");
        path.push("trusted_devices.json");
        path
    }

    fn load_from_disk() -> HashMap<String, AuthToken> {
        let path = Self::get_storage_path();
        if path.exists() {
            match std::fs::read_to_string(&path) {
                Ok(content) => match serde_json::from_str::<StoredDevices>(&content) {
                    Ok(stored) => {
                        info!("Loaded {} trusted devices", stored.devices.len());
                        return stored.devices;
                    }
                    Err(e) => error!("Failed to parse trusted devices: {}", e),
                },
                Err(e) => error!("Failed to read trusted devices: {}", e),
            }
        }
        HashMap::new()
    }

    async fn save_to_disk(&self) {
        let devices = self.trusted_devices.read().await;
        let stored = StoredDevices { devices: devices.clone() };
        let path = Self::get_storage_path();

        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }

        match serde_json::to_string_pretty(&stored) {
            Ok(content) => {
                if let Err(e) = std::fs::write(&path, content) {
                    error!("Failed to save trusted devices: {}", e);
                } else {
                    info!("Saved {} trusted devices", devices.len());
                }
            }
            Err(e) => error!("Failed to serialize trusted devices: {}", e),
        }
    }

    pub fn hash_token(token: &str) -> String {
        let mut hasher = Sha256::new();
        hasher.update(token.as_bytes());
        format!("{:x}", hasher.finalize())
    }

    pub async fn add_trusted_device(&self, device: TrustedDevice, token: &str) {
        let token_hash = Self::hash_token(token);
        let now = current_timestamp_ms();

        info!("Adding trusted device: {}, token_hash={}", device.device_id, &token_hash[..16]);

        let mut devices = self.trusted_devices.write().await;
        
        // Check if device already exists - update instead of duplicate
        if let Some(existing) = devices.get_mut(&device.device_id) {
            existing.token_hash = token_hash;
            existing.last_used = now;
            info!("Updated trusted device: {}", device.device_name);
        } else {
            let auth_token = AuthToken {
                token_hash,
                device_name: device.device_name.clone(),
                device_id: device.device_id.clone(),
                created_at: now,
                last_used: now,
            };
            devices.insert(device.device_id.clone(), auth_token);
            info!("Added trusted device: {}", device.device_name);
        }
        
        drop(devices);
        self.save_to_disk().await;
    }

    pub async fn authenticate(&self, device_id: &str, token: &str) -> bool {
        let token_hash = Self::hash_token(token);
        info!("Auth check: device={}, token_hash={}", device_id, &token_hash[..16]);

        let mut devices = self.trusted_devices.write().await;
        if let Some(auth_token) = devices.get_mut(device_id) {
            info!("Found device: {}, stored_hash={}", device_id, &auth_token.token_hash[..16]);
            if auth_token.token_hash == token_hash {
                auth_token.last_used = current_timestamp_ms();
                info!("Token match! Authentication successful");
                return true;
            } else {
                warn!("Token hash mismatch for device: {}", device_id);
            }
        } else {
            warn!("Device not found in trusted devices: {}", device_id);
            info!("Trusted devices: {:?}", devices.keys().collect::<Vec<_>>());
        }
        false
    }

    pub async fn has_devices(&self) -> bool {
        !self.trusted_devices.read().await.is_empty()
    }

    pub async fn get_trusted_devices(&self) -> Vec<TrustedDevice> {
        self.trusted_devices
            .read()
            .await
            .values()
            .map(|t| TrustedDevice {
                device_id: t.device_id.clone(),
                device_name: t.device_name.clone(),
                token_hash: t.token_hash.clone(),
                paired_at: t.created_at,
            })
            .collect()
    }

    pub async fn revoke_device(&self, device_id: &str) {
        let mut devices = self.trusted_devices.write().await;
        devices.remove(device_id);
        drop(devices);

        let mut sessions = self.active_sessions.write().await;
        sessions.remove(device_id);

        info!("Device revoked: {}", device_id);
        self.save_to_disk().await;
    }

    pub async fn cleanup_duplicates(&self) {
        let mut devices = self.trusted_devices.write().await;
        let mut seen_names: HashMap<String, String> = HashMap::new();
        let mut to_remove: Vec<String> = Vec::new();

        for (id, device) in devices.iter() {
            if let Some(existing_id) = seen_names.get(&device.device_name) {
                // Keep the more recent one
                if let Some(existing) = devices.get(existing_id) {
                    if device.last_used > existing.last_used {
                        to_remove.push(existing_id.clone());
                        seen_names.insert(device.device_name.clone(), id.clone());
                    } else {
                        to_remove.push(id.clone());
                    }
                }
            } else {
                seen_names.insert(device.device_name.clone(), id.clone());
            }
        }

        for id in to_remove {
            devices.remove(&id);
            info!("Removed duplicate device: {}", id);
        }
        
        drop(devices);
        self.save_to_disk().await;
    }
}

fn current_timestamp_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
