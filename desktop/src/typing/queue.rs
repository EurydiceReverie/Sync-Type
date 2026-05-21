use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::sync::Arc;
use tokio::sync::{mpsc, Mutex};
use tracing::{error, info, warn};
use enigo::Keyboard;

use crate::state::AppState;
use crate::typing::engine::TypingMode;
use crate::websocket::protocol::WsMessage;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TypingJob {
    pub job_id: String,
    pub text: String,
    pub mode: String,
    pub speed: u8,
    pub priority: String,
    pub device_id: String,
    pub status: JobStatus,
    pub created_at: u64,
    pub chars_typed: usize,
    pub total_chars: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum JobStatus {
    Queued,
    Typing,
    Complete,
    Aborted,
    Error,
}

pub struct TypingQueue {
    jobs: Mutex<VecDeque<TypingJob>>,
    active_job: Mutex<Option<TypingJob>>,
    sender: mpsc::Sender<TypingJob>,
    receiver: Mutex<Option<mpsc::Receiver<TypingJob>>>,
}

impl TypingQueue {
    pub fn new_with_internal_receiver() -> Self {
        let (sender, receiver) = mpsc::channel(100);
        Self {
            jobs: Mutex::new(VecDeque::new()),
            active_job: Mutex::new(None),
            sender,
            receiver: Mutex::new(Some(receiver)),
        }
    }

    pub async fn take_receiver(&self) -> Option<mpsc::Receiver<TypingJob>> {
        self.receiver.lock().await.take()
    }

    pub async fn enqueue(&self, job: TypingJob) -> Result<(), &'static str> {
        let mut jobs = self.jobs.lock().await;
        if jobs.len() >= 50 {
            return Err("Queue full");
        }
        let priority = job.priority.clone();
        match priority.as_str() {
            "urgent" => jobs.push_front(job),
            "high" => {
                let pos = jobs.iter().position(|j| j.priority != "urgent").unwrap_or(jobs.len());
                jobs.insert(pos, job);
            }
            "low" => jobs.push_back(job),
            _ => {
                let pos = jobs.iter().rposition(|j| j.priority == "low").unwrap_or(jobs.len());
                jobs.insert(pos, job);
            }
        }
        info!("Enqueued: priority={}, queue={}", priority, jobs.len());
        Ok(())
    }

    pub async fn set_active(&self, job: TypingJob) { *self.active_job.lock().await = Some(job); }
    pub async fn clear_active(&self) { *self.active_job.lock().await = None; }
    pub async fn get_active(&self) -> Option<TypingJob> { self.active_job.lock().await.clone() }
    pub async fn queue_length(&self) -> usize { self.jobs.lock().await.len() }
    pub async fn get_queue(&self) -> Vec<TypingJob> { self.jobs.lock().await.iter().cloned().collect() }
    pub async fn cancel_job(&self, job_id: &str) -> bool {
        let mut jobs = self.jobs.lock().await;
        if let Some(pos) = jobs.iter().position(|j| j.job_id == job_id) {
            jobs.remove(pos);
            return true;
        }
        false
    }
    pub async fn clear_all(&self) { self.jobs.lock().await.clear(); }
    pub async fn get_sender(&self) -> mpsc::Sender<TypingJob> { self.sender.clone() }
}

async fn broadcast(state: &Arc<AppState>, msg_type: &str, job_id: &str, payload: serde_json::Value) {
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let msg = serde_json::to_string(&WsMessage::new(msg_type, &format!("{}-{}", msg_type, job_id), payload)).unwrap_or_default();
        ws.broadcast_to_authenticated(&msg).await;
    }
}

pub async fn start_queue_processor(state: Arc<AppState>) {
    info!("Queue processor starting");
    let mut receiver = match state.typing_queue.take_receiver().await {
        Some(r) => r,
        None => { error!("No queue receiver"); return; }
    };
    info!("Queue processor ready");

    loop {
        match receiver.recv().await {
            Some(mut job) => {
                let job_id = job.job_id.clone();
                let total_chars = job.total_chars;
                let mode_str = job.mode.clone();
                let speed = job.speed;

                info!("Processing: {} ({} chars, mode={}, speed={})", job_id, total_chars, mode_str, speed);

                // CRITICAL: Reset abort/pause flags before each job
                *state.typing_engine.should_abort.lock().await = false;
                *state.typing_engine.is_paused.lock().await = false;

                job.status = JobStatus::Typing;
                state.typing_queue.set_active(job.clone()).await;

                broadcast(&state, "type_progress", &job_id, serde_json::json!({
                    "job_id": job_id, "chars_typed": 0, "total_chars": total_chars, "progress": 0.0
                })).await;

                let mode = TypingMode::from_str(&mode_str);
                let mut chars_typed = 0usize;

                match mode {
                    TypingMode::Instant => {
                        // Instant: type all at once using enigo.text()
                        let mut enigo = state.typing_engine.enigo.lock().await;
                        if let Err(e) = enigo.text(&job.text) {
                            error!("Instant type failed: {}", e);
                        }
                        drop(enigo);
                        chars_typed = total_chars;
                        // Small delay for UI to update
                        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
                    }
                    _ => {
                        let base_delay = mode.base_delay_ms(speed);
                        for ch in job.text.chars() {
                            // Check abort
                            if *state.typing_engine.should_abort.lock().await {
                                info!("Aborted: {} at {}/{}", job_id, chars_typed, total_chars);
                                break;
                            }

                            // Wait while paused
                            while *state.typing_engine.is_paused.lock().await {
                                tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
                            }

                            // Type character
                            let mut enigo = state.typing_engine.enigo.lock().await;
                            let _ = enigo.text(&ch.to_string());
                            drop(enigo);

                            chars_typed += 1;

                            // Broadcast progress every 5 chars
                            if chars_typed % 5 == 0 || chars_typed == total_chars {
                                broadcast(&state, "type_progress", &job_id, serde_json::json!({
                                    "job_id": job_id,
                                    "chars_typed": chars_typed,
                                    "total_chars": total_chars,
                                    "progress": chars_typed as f64 / total_chars as f64
                                })).await;
                            }

                            if base_delay > 0 {
                                tokio::time::sleep(tokio::time::Duration::from_millis(base_delay)).await;
                            }
                        }
                    }
                }

                // Final progress
                broadcast(&state, "type_progress", &job_id, serde_json::json!({
                    "job_id": job_id, "chars_typed": chars_typed, "total_chars": total_chars,
                    "progress": if total_chars > 0 { chars_typed as f64 / total_chars as f64 } else { 0.0 }
                })).await;

                // Complete
                broadcast(&state, "type_complete", &job_id, serde_json::json!({
                    "job_id": job_id, "chars_typed": chars_typed, "total_chars": total_chars
                })).await;

                info!("Done: {} ({} chars)", job_id, chars_typed);
                state.typing_queue.clear_active().await;
            }
            None => { warn!("Queue closed"); break; }
        }
    }
}
