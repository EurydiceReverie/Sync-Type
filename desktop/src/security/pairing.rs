use rand::Rng;
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;
use tracing::info;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrustedDevice {
    pub device_id: String,
    pub device_name: String,
    pub token_hash: String,
    pub paired_at: u64,
}

#[derive(Debug, Clone)]
pub struct PendingPairing {
    pub pin: String,
    pub device_id: String,
    pub device_name: String,
    pub expires_at: u64,
}

pub struct PairingManager {
    pending: RwLock<Option<PendingPairing>>,
}

const PIN_EXPIRY_MS: u64 = 180_000;

impl PairingManager {
    pub fn new() -> Self {
        Self {
            pending: RwLock::new(None),
        }
    }

    pub async fn start_pairing(&self, device_id: String, device_name: String) -> String {
        let pin = generate_pin();
        let expires_at = current_timestamp_ms() + PIN_EXPIRY_MS;

        let pending = PendingPairing {
            pin: pin.clone(),
            device_id,
            device_name,
            expires_at,
        };

        *self.pending.write().await = Some(pending);
        info!("Pairing started with PIN: {}", pin);

        pin
    }

    pub async fn regenerate_pin(&self) -> Option<String> {
        let mut pending_lock = self.pending.write().await;
        if let Some(ref pending) = *pending_lock {
            let new_pin = generate_pin();
            let new_expires_at = current_timestamp_ms() + PIN_EXPIRY_MS;

            let updated = PendingPairing {
                pin: new_pin.clone(),
                device_id: pending.device_id.clone(),
                device_name: pending.device_name.clone(),
                expires_at: new_expires_at,
            };

            *pending_lock = Some(updated);
            info!("PIN regenerated: {}", new_pin);
            Some(new_pin)
        } else {
            None
        }
    }

    pub async fn get_pending_pairing(&self) -> Option<(String, String, u64)> {
        self.pending.read().await.as_ref().map(|p| {
            (
                p.pin.clone(),
                p.device_id.clone(),
                p.expires_at,
            )
        })
    }

    pub async fn verify_pin(&self, device_id: &str, pin: &str) -> PairingResult {
        let pending_lock = self.pending.read().await;
        let pending = match pending_lock.as_ref() {
            Some(p) => p,
            None => return PairingResult::NoActivePairing,
        };

        if pending.device_id != device_id {
            return PairingResult::WrongDevice;
        }

        if current_timestamp_ms() > pending.expires_at {
            drop(pending_lock);
            *self.pending.write().await = None;
            return PairingResult::Expired;
        }

        if pending.pin != pin {
            return PairingResult::InvalidPin;
        }

        let device = TrustedDevice {
            device_id: pending.device_id.clone(),
            device_name: pending.device_name.clone(),
            token_hash: String::new(),
            paired_at: current_timestamp_ms(),
        };

        let token = generate_token();
        drop(pending_lock);
        *self.pending.write().await = None;

        info!(
            "Device paired successfully: {} ({})",
            device.device_name, device.device_id
        );

        PairingResult::Success { device, token }
    }

    pub async fn cancel_pairing(&self) {
        *self.pending.write().await = None;
    }
}

#[derive(Debug)]
pub enum PairingResult {
    Success {
        device: TrustedDevice,
        token: String,
    },
    InvalidPin,
    Expired,
    NoActivePairing,
    WrongDevice,
}

pub fn generate_pin() -> String {
    let mut rng = rand::thread_rng();
    format!("{:06}", rng.gen_range(0..1_000_000))
}

pub fn generate_pairing_qr_data(ws_ip: &str, ws_port: u16, pin: &str, device_id: &str) -> String {
    format!("lanremotetype://pair?ip={}&port={}&pin={}&device_id={}",
        ws_ip, ws_port, pin, device_id)
}

fn generate_token() -> String {
    let mut rng = rand::thread_rng();
    let bytes: Vec<u8> = (0..32).map(|_| rng.gen()).collect();
    bytes
        .iter()
        .map(|b| format!("{:02x}", b))
        .collect::<String>()
}

fn current_timestamp_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
