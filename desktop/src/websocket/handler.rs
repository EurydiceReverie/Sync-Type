use serde_json::json;
use std::sync::Arc;
use tokio::sync::mpsc;
use tracing::{error, info};
use tungstenite::Message;

use crate::state::AppState;
use crate::typing::queue::{JobStatus, TypingJob};
use crate::websocket::protocol::{current_timestamp_ms, WsMessage};

pub async fn handle_message(
    state: Arc<AppState>,
    msg: WsMessage,
    device_id: String,
    tx: mpsc::UnboundedSender<Message>,
) {
    match msg.msg_type.as_str() {
        "type" => handle_type(state, msg, device_id, tx).await,
        "key_press" => handle_key_press(state, msg, tx).await,
        "clipboard" => handle_clipboard(state, msg, tx).await,
        "queue_status" => handle_queue_status(state, msg, tx).await,
        "queue_cancel" => handle_queue_cancel(state, msg, tx).await,
        "queue_clear" => handle_queue_clear(state, msg, tx).await,
        "typing_control" => handle_typing_control(state, msg, tx).await,
        "ping" => handle_ping(msg, tx).await,
        _ => {
            let _ = tx.send(Message::Text(
                serde_json::to_string(&msg.error_response("INVALID_MESSAGE", "Unknown message type"))
                    .unwrap_or_default(),
            ));
        }
    }
}

async fn handle_type(
    state: Arc<AppState>,
    msg: WsMessage,
    device_id: String,
    tx: mpsc::UnboundedSender<Message>,
) {
    let text = msg.payload["text"].as_str().unwrap_or("").to_string();
    let mode = msg.payload["mode"].as_str().unwrap_or("normal").to_string();
    let speed = msg.payload["speed"].as_u64().unwrap_or(50) as u8;
    let priority = msg.payload["priority"].as_str().unwrap_or("normal").to_string();

    if text.is_empty() {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INVALID_MESSAGE", "Empty text"))
                .unwrap_or_default(),
        ));
        return;
    }

    if text.len() > 10000 {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INVALID_MESSAGE", "Text too long"))
                .unwrap_or_default(),
        ));
        return;
    }

    let job_id = format!("job_{}", uuid::Uuid::new_v4());
    let total_chars = text.chars().count();

    let job = TypingJob {
        job_id: job_id.clone(),
        text,
        mode,
        speed,
        priority,
        device_id,
        status: JobStatus::Queued,
        created_at: current_timestamp_ms(),
        chars_typed: 0,
        total_chars,
    };

    if let Err(e) = state.typing_queue.enqueue(job.clone()).await {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("QUEUE_FULL", e))
                .unwrap_or_default(),
        ));
        return;
    }

    let sender = state.typing_queue.get_sender().await;
    if let Err(e) = sender.send(job.clone()).await {
        error!("Failed to send job to processor: {}", e);
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INTERNAL_ERROR", "Queue processor error"))
                .unwrap_or_default(),
        ));
        return;
    }

    let _ = tx.send(Message::Text(
        serde_json::to_string(&msg.success_response(json!({
            "job_id": job_id,
            "status": "queued",
            "total_chars": total_chars,
        })))
        .unwrap_or_default(),
    ));

    info!("Typing job queued: {} ({} chars)", job_id, total_chars);
}

async fn handle_key_press(
    _state: Arc<AppState>,
    msg: WsMessage,
    tx: mpsc::UnboundedSender<Message>,
) {
    let key = msg.payload["key"].as_str().unwrap_or("").to_string();
    let modifiers = msg.payload["modifiers"]
        .as_array()
        .map(|arr| {
            arr.iter()
                .filter_map(|v| v.as_str().map(String::from))
                .collect::<Vec<_>>()
        })
        .unwrap_or_default();

    if key.is_empty() {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INVALID_MESSAGE", "Missing key"))
                .unwrap_or_default(),
        ));
        return;
    }

    let engine = match crate::typing::engine::TypingEngine::new() {
        Ok(e) => e,
        Err(e) => {
            let _ = tx.send(Message::Text(
                serde_json::to_string(&msg.error_response("INPUT_ERROR", &e.to_string()))
                    .unwrap_or_default(),
            ));
            return;
        }
    };

    if let Err(e) = engine.send_key(&key, &modifiers).await {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INPUT_ERROR", &e.to_string()))
                .unwrap_or_default(),
        ));
        return;
    }

    let _ = tx.send(Message::Text(
        serde_json::to_string(&msg.success_response(json!({
            "key": key,
            "modifiers": modifiers,
        })))
        .unwrap_or_default(),
    ));
}

async fn handle_clipboard(
    _state: Arc<AppState>,
    msg: WsMessage,
    tx: mpsc::UnboundedSender<Message>,
) {
    let text = msg.payload["text"].as_str().unwrap_or("").to_string();

    if text.is_empty() {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INVALID_MESSAGE", "Empty text"))
                .unwrap_or_default(),
        ));
        return;
    }

    let engine = match crate::typing::engine::TypingEngine::new() {
        Ok(e) => e,
        Err(e) => {
            let _ = tx.send(Message::Text(
                serde_json::to_string(&msg.error_response("INPUT_ERROR", &e.to_string()))
                    .unwrap_or_default(),
            ));
            return;
        }
    };

    if let Err(e) = engine.paste_clipboard(&text).await {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INPUT_ERROR", &e.to_string()))
                .unwrap_or_default(),
        ));
        return;
    }

    let _ = tx.send(Message::Text(
        serde_json::to_string(&msg.success_response(json!({
            "chars_pasted": text.chars().count(),
        })))
        .unwrap_or_default(),
    ));
}

async fn handle_queue_status(
    state: Arc<AppState>,
    msg: WsMessage,
    tx: mpsc::UnboundedSender<Message>,
) {
    let queue_len = state.typing_queue.queue_length().await;
    let active = state.typing_queue.get_active().await;

    let _ = tx.send(Message::Text(
        serde_json::to_string(&msg.success_response(json!({
            "queue_length": queue_len,
            "active_job": active.map(|j| json!({
                "job_id": j.job_id,
                "status": "typing",
                "chars_typed": j.chars_typed,
                "total_chars": j.total_chars,
            })),
        })))
        .unwrap_or_default(),
    ));
}

async fn handle_queue_cancel(
    state: Arc<AppState>,
    msg: WsMessage,
    tx: mpsc::UnboundedSender<Message>,
) {
    let job_id = msg.payload["job_id"].as_str().unwrap_or("").to_string();

    if job_id.is_empty() {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("INVALID_MESSAGE", "Missing job_id"))
                .unwrap_or_default(),
        ));
        return;
    }

    let cancelled = state.typing_queue.cancel_job(&job_id).await;

    if cancelled {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.success_response(json!({
                "job_id": job_id,
                "status": "cancelled",
            })))
            .unwrap_or_default(),
        ));
    } else {
        let _ = tx.send(Message::Text(
            serde_json::to_string(&msg.error_response("JOB_NOT_FOUND", "Job not found or active"))
                .unwrap_or_default(),
        ));
    }
}

async fn handle_queue_clear(
    state: Arc<AppState>,
    msg: WsMessage,
    tx: mpsc::UnboundedSender<Message>,
) {
    state.typing_queue.clear_all().await;

    let _ = tx.send(Message::Text(
        serde_json::to_string(&msg.success_response(json!({
            "status": "cleared",
        })))
        .unwrap_or_default(),
    ));
}

async fn handle_typing_control(
    state: Arc<AppState>,
    msg: WsMessage,
    tx: mpsc::UnboundedSender<Message>,
) {
    let action = msg.payload["action"].as_str().unwrap_or("").to_string();

    match action.as_str() {
        "pause" => {
            state.typing_engine.pause().await;
        }
        "resume" => {
            state.typing_engine.resume().await;
        }
        "abort" => {
            state.typing_engine.abort().await;
        }
        _ => {
            let _ = tx.send(Message::Text(
                serde_json::to_string(
                    &msg.error_response("INVALID_MESSAGE", "Unknown action"),
                )
                .unwrap_or_default(),
            ));
            return;
        }
    }

    let _ = tx.send(Message::Text(
        serde_json::to_string(&msg.success_response(json!({
            "action": action,
        })))
        .unwrap_or_default(),
    ));
}

async fn handle_ping(
    msg: WsMessage,
    tx: mpsc::UnboundedSender<Message>,
) {
    let _ = tx.send(Message::Text(
        serde_json::to_string(&msg.response_to("pong", json!({})))
            .unwrap_or_default(),
    ));
}
