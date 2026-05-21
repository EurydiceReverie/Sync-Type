use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use tracing::{error, info};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    pub device_id: String,
    pub device_name: String,
    pub ws_port: u16,
    pub discovery_port: u16,
    pub auto_start: bool,
    pub max_queue_size: usize,
    pub max_text_length: usize,
    pub default_typing_mode: String,
    pub default_speed: u8,
    pub rate_limit_messages_per_minute: u32,
    pub max_connections: usize,
    pub pin_expiry_seconds: u64,
    pub max_pin_attempts: u32,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            device_id: format!("desktop_{}", uuid::Uuid::new_v4().simple()),
            device_name: get_device_name(),
            ws_port: 9876,
            discovery_port: 9877,
            auto_start: true,
            max_queue_size: 50,
            max_text_length: 10000,
            default_typing_mode: "human".to_string(),
            default_speed: 50,
            rate_limit_messages_per_minute: 100,
            max_connections: 5,
            pin_expiry_seconds: 180,
            max_pin_attempts: 5,
        }
    }
}

impl AppConfig {
    pub fn load() -> Self {
        let config_path = get_config_path();

        if config_path.exists() {
            match std::fs::read_to_string(&config_path) {
                Ok(content) => match serde_json::from_str(&content) {
                    Ok(config) => {
                        info!("Config loaded from {:?}", config_path);
                        return config;
                    }
                    Err(e) => {
                        error!("Failed to parse config: {}", e);
                    }
                },
                Err(e) => {
                    error!("Failed to read config: {}", e);
                }
            }
        }

        let config = AppConfig::default();
        if let Err(e) = config.save() {
            error!("Failed to save default config: {}", e);
        }

        config
    }

    pub fn save(&self) -> anyhow::Result<()> {
        let config_path = get_config_path();
        if let Some(parent) = config_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(self)?;
        std::fs::write(&config_path, content)?;
        info!("Config saved to {:?}", config_path);
        Ok(())
    }
}

fn get_config_path() -> PathBuf {
    let mut path = dirs::config_dir().unwrap_or_else(|| PathBuf::from("."));
    path.push("lan-remote-type");
    path.push("config.json");
    path
}

fn get_device_name() -> String {
    hostname::get()
        .ok()
        .and_then(|h| h.to_str().map(String::from))
        .unwrap_or_else(|| "DESKTOP".to_string())
}
