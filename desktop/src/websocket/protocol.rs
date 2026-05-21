use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WsMessage {
    #[serde(rename = "type")]
    pub msg_type: String,
    pub id: String,
    pub timestamp: u64,
    pub payload: serde_json::Value,
}

impl WsMessage {
    pub fn new(msg_type: &str, id: &str, payload: serde_json::Value) -> Self {
        Self {
            msg_type: msg_type.to_string(),
            id: id.to_string(),
            timestamp: current_timestamp_ms(),
            payload,
        }
    }

    pub fn response_to(&self, msg_type: &str, payload: serde_json::Value) -> Self {
        Self::new(msg_type, &self.id, payload)
    }

    pub fn error_response(&self, code: &str, message: &str) -> Self {
        self.response_to(
            "error",
            serde_json::json!({
                "code": code,
                "message": message,
            }),
        )
    }

    pub fn success_response(&self, payload: serde_json::Value) -> Self {
        self.response_to("success", payload)
    }
}

pub fn current_timestamp_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
