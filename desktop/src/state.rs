use std::sync::Arc;
use tokio::sync::RwLock;

use crate::config::AppConfig;
use crate::security::auth::SecurityManager;
use crate::security::pairing::PairingManager;
use crate::typing::engine::TypingEngine;
use crate::typing::queue::TypingQueue;
use crate::websocket::server::WebSocketState;

pub struct AppState {
    pub config: RwLock<AppConfig>,
    pub security: SecurityManager,
    pub pairing: PairingManager,
    pub typing_engine: TypingEngine,
    pub typing_queue: TypingQueue,
    pub websocket_state: RwLock<Option<Arc<WebSocketState>>>,
    pub start_time: u64,
    pub stealth_mode: RwLock<bool>,
}

impl AppState {
    pub fn new() -> Self {
        let typing_queue = TypingQueue::new_with_internal_receiver();
        let typing_engine = TypingEngine::new().expect("Failed to create typing engine");

        Self {
            config: RwLock::new(AppConfig::load()),
            security: SecurityManager::new(),
            pairing: PairingManager::new(),
            typing_engine,
            typing_queue,
            websocket_state: RwLock::new(None),
            start_time: current_timestamp_ms(),
            stealth_mode: RwLock::new(false),
        }
    }
}

fn current_timestamp_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
