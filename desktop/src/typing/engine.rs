use enigo::{Direction, Enigo, Key, Keyboard};
use std::sync::Arc;
use tokio::sync::Mutex;
use tracing::{info, warn};

#[derive(Debug, Clone, PartialEq)]
pub enum TypingMode {
    Instant,
    Fast,
    Normal,
    Human,
}

impl TypingMode {
    pub fn from_str(s: &str) -> Self {
        match s {
            "instant" => TypingMode::Instant,
            "fast" => TypingMode::Fast,
            "normal" => TypingMode::Normal,
            "human" => TypingMode::Human,
            _ => TypingMode::Normal,
        }
    }

    pub fn base_delay_ms(&self, speed: u8) -> u64 {
        let speed = speed.max(1).min(100);
        match self {
            TypingMode::Instant => 0,
            TypingMode::Fast => {
                let wpm = 120 + (speed as u64 * 80 / 100);
                60_000 / (wpm * 5)
            }
            TypingMode::Normal => {
                let wpm = 60 + (speed as u64 * 90 / 100);
                60_000 / (wpm * 5)
            }
            TypingMode::Human => {
                let wpm = 40 + (speed as u64 * 60 / 100);
                60_000 / (wpm * 5)
            }
        }
    }
}

pub struct TypingEngine {
    pub enigo: Arc<Mutex<Enigo>>,
    pub is_paused: Arc<Mutex<bool>>,
    pub should_abort: Arc<Mutex<bool>>,
}

impl TypingEngine {
    pub fn new() -> anyhow::Result<Self> {
        let enigo = Enigo::new(&enigo::Settings::default())?;
        Ok(Self {
            enigo: Arc::new(Mutex::new(enigo)),
            is_paused: Arc::new(Mutex::new(false)),
            should_abort: Arc::new(Mutex::new(false)),
        })
    }

    pub async fn type_text(&self, text: &str, mode: TypingMode, speed: u8) -> anyhow::Result<usize> {
        self.reset_controls().await;
        match mode {
            TypingMode::Instant => self.type_instant(text).await,
            _ => self.type_with_delay(text, mode, speed).await,
        }
    }

    async fn type_instant(&self, text: &str) -> anyhow::Result<usize> {
        info!("Instant typing: {} chars", text.len());
        let mut enigo = self.enigo.lock().await;
        enigo.text(text).map_err(|e| anyhow::anyhow!("Type failed: {}", e))?;
        Ok(text.chars().count())
    }

    async fn type_with_delay(&self, text: &str, mode: TypingMode, speed: u8) -> anyhow::Result<usize> {
        let mut chars_typed = 0;
        let base_delay = mode.base_delay_ms(speed);

        for ch in text.chars() {
            // Check abort
            if *self.should_abort.lock().await {
                info!("Typing aborted at {}", chars_typed);
                return Ok(chars_typed);
            }

            // Check pause
            while *self.is_paused.lock().await {
                tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
            }

            // Type character
            let char_str = ch.to_string();
            let mut enigo = self.enigo.lock().await;
            if let Err(e) = enigo.text(&char_str) {
                warn!("Failed to type '{}': {}", ch, e);
            }
            drop(enigo);

            chars_typed += 1;

            // Apply delay
            if base_delay > 0 {
                let delay = if mode == TypingMode::Human {
                    get_human_delay(base_delay, chars_typed)
                } else {
                    base_delay
                };
                tokio::time::sleep(tokio::time::Duration::from_millis(delay)).await;
            }
        }

        Ok(chars_typed)
    }

    pub async fn send_key(&self, key: &str, modifiers: &[String]) -> anyhow::Result<()> {
        let mut enigo = self.enigo.lock().await;

        // Press modifiers
        for modifier in modifiers {
            if let Some(k) = parse_modifier(modifier) {
                let _ = enigo.key(k, Direction::Press);
                tokio::time::sleep(tokio::time::Duration::from_millis(20)).await;
            }
        }

        // Press the key
        if let Some(k) = parse_key(key) {
            let _ = enigo.key(k, Direction::Click);
        } else {
            let _ = enigo.text(key);
        }

        tokio::time::sleep(tokio::time::Duration::from_millis(20)).await;

        // Release modifiers in reverse
        for modifier in modifiers.iter().rev() {
            if let Some(k) = parse_modifier(modifier) {
                let _ = enigo.key(k, Direction::Release);
            }
        }

        Ok(())
    }

    pub async fn paste_clipboard(&self, text: &str) -> anyhow::Result<()> {
        info!("Paste clipboard: {} chars", text.len());
        
        // Write text to temp file, then use PowerShell to set clipboard from file
        let temp_dir = std::env::temp_dir();
        let temp_file = temp_dir.join("synctype_clipboard.txt");
        std::fs::write(&temp_file, text)?;
        
        let ps_script = format!(
            "Set-Clipboard -Value (Get-Content -Raw '{}')",
            temp_file.to_string_lossy().replace("\\", "\\\\")
        );
        
        let output = std::process::Command::new("powershell.exe")
            .args(&["-NoProfile", "-NonInteractive", "-Command", &ps_script])
            .output()?;

        // Clean up temp file
        let _ = std::fs::remove_file(&temp_file);

        if !output.status.success() {
            return Err(anyhow::anyhow!("Clipboard failed"));
        }

        tokio::time::sleep(tokio::time::Duration::from_millis(300)).await;

        // Ctrl+V
        let mut enigo = self.enigo.lock().await;
        let _ = enigo.key(Key::Control, Direction::Press);
        tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        let _ = enigo.key(Key::Unicode('v'), Direction::Click);
        tokio::time::sleep(tokio::time::Duration::from_millis(50)).await;
        let _ = enigo.key(Key::Control, Direction::Release);

        info!("Paste complete");
        Ok(())
    }

    async fn reset_controls(&self) {
        *self.is_paused.lock().await = false;
        *self.should_abort.lock().await = false;
    }

    pub async fn pause(&self) {
        *self.is_paused.lock().await = true;
    }

    pub async fn resume(&self) {
        *self.is_paused.lock().await = false;
    }

    pub async fn abort(&self) {
        *self.should_abort.lock().await = true;
    }
}

fn get_human_delay(base_delay: u64, position: usize) -> u64 {
    use rand::Rng;
    let mut rng = rand::thread_rng();
    let variation = (base_delay as f64 * 0.3) as i64;
    let mut delay = base_delay as i64 + rng.gen_range(-variation..=variation);
    if position % 5 == 0 { delay += rng.gen_range(50..150); }
    if position % 15 == 0 { delay += rng.gen_range(100..300); }
    delay.max(10) as u64
}

fn parse_key(key: &str) -> Option<Key> {
    match key.to_lowercase().as_str() {
        "enter" | "return" => Some(Key::Return),
        "tab" => Some(Key::Tab),
        "space" => Some(Key::Space),
        "backspace" => Some(Key::Backspace),
        "delete" | "del" => Some(Key::Delete),
        "escape" | "esc" => Some(Key::Escape),
        "up" => Some(Key::UpArrow),
        "down" => Some(Key::DownArrow),
        "left" => Some(Key::LeftArrow),
        "right" => Some(Key::RightArrow),
        "home" => Some(Key::Home),
        "end" => Some(Key::End),
        "pageup" | "pgup" => Some(Key::PageUp),
        "pagedown" | "pgdn" => Some(Key::PageDown),
        "insert" | "ins" => Some(Key::Insert),
        "f1" => Some(Key::F1), "f2" => Some(Key::F2), "f3" => Some(Key::F3), "f4" => Some(Key::F4),
        "f5" => Some(Key::F5), "f6" => Some(Key::F6), "f7" => Some(Key::F7), "f8" => Some(Key::F8),
        "f9" => Some(Key::F9), "f10" => Some(Key::F10), "f11" => Some(Key::F11), "f12" => Some(Key::F12),
        // Single characters - use Unicode key for proper modifier support
        s if s.len() == 1 => Some(Key::Unicode(s.chars().next().unwrap())),
        _ => None,
    }
}

fn parse_modifier(modifier: &str) -> Option<Key> {
    match modifier.to_lowercase().as_str() {
        "ctrl" | "control" => Some(Key::Control),
        "shift" => Some(Key::Shift),
        "alt" => Some(Key::Alt),
        "meta" | "win" | "windows" | "super" => Some(Key::Meta),
        _ => None,
    }
}
