use rand::Rng;
use std::sync::atomic::{AtomicU64, Ordering};

pub struct Humanizer {
    keystroke_count: AtomicU64,
}

impl Humanizer {
    pub fn new() -> Self {
        Self {
            keystroke_count: AtomicU64::new(0),
        }
    }

    pub fn get_delay(&self, base_delay: u64, position: usize) -> u64 {
        let count = self.keystroke_count.fetch_add(1, Ordering::SeqCst);
        let mut rng = rand::thread_rng();

        // Base variation: ±30% of base delay
        let variation = (base_delay as f64 * 0.3) as i64;
        let mut delay = base_delay as i64 + rng.gen_range(-variation..=variation);

        // Occasional thinking pause (every 8-15 characters)
        if count > 0 && count % rng.gen_range(8..15) == 0 {
            delay += rng.gen_range(100..300);
        }

        // Longer pause at word boundaries (roughly every 5 chars = 1 word)
        if count > 0 && count % 5 == 0 {
            delay += rng.gen_range(50..150);
        }

        // Very occasional long pause (like thinking) - every 30-50 chars
        if count > 0 && count % rng.gen_range(30..50) == 0 {
            delay += rng.gen_range(300..800);
        }

        // Ensure minimum delay
        delay.max(10) as u64
    }

    pub fn reset(&self) {
        self.keystroke_count.store(0, Ordering::SeqCst);
    }
}
