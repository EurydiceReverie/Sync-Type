use enigo::{Direction, Enigo, Key, Keyboard};
use std::sync::Arc;
use tokio::sync::Mutex;
use tracing::error;

pub struct WindowsInput {
    enigo: Arc<Mutex<Enigo>>,
}

impl WindowsInput {
    pub fn new() -> anyhow::Result<Self> {
        let enigo = Enigo::new(&enigo::Settings::default())?;
        Ok(Self {
            enigo: Arc::new(Mutex::new(enigo)),
        })
    }

    pub async fn key_click(&self, key: Key) {
        let mut enigo = self.enigo.lock().await;
        if let Err(e) = enigo.key(key, Direction::Click) {
            error!("key_click failed: {}", e);
        }
    }

    pub async fn text(&self, text: &str) {
        let mut enigo = self.enigo.lock().await;
        if let Err(e) = enigo.text(text) {
            error!("text input failed: {}", e);
        }
    }

    pub async fn modifier_combo(&self, modifiers: &[Key], key: Key) {
        let mut enigo = self.enigo.lock().await;
        for modifier in modifiers {
            let _ = enigo.key(*modifier, Direction::Press);
        }
        let _ = enigo.key(key, Direction::Click);
        for modifier in modifiers.iter().rev() {
            let _ = enigo.key(*modifier, Direction::Release);
        }
    }
}

impl Default for WindowsInput {
    fn default() -> Self {
        Self::new().expect("Failed to create WindowsInput")
    }
}
