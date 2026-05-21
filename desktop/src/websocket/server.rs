use futures_util::{SinkExt, StreamExt};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::sync::{mpsc, RwLock};
use tracing::{error, info, warn};
use tungstenite::Message;
use enigo::Keyboard;

use crate::state::AppState;
use crate::websocket::protocol::WsMessage;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BlacklistedDevice {
    pub device_id: String,
    pub device_name: String,
    pub blocked_at: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct BlacklistStorage {
    devices: Vec<BlacklistedDevice>,
}

pub struct WebSocketState {
    clients: RwLock<HashMap<String, ConnectedClient>>,
    pending_approval: RwLock<Option<PendingConnection>>,
    blacklist: RwLock<Vec<BlacklistedDevice>>,
}

pub struct ConnectedClient {
    pub ip: String,
    pub device_id: String,
    pub device_name: String,
    pub authenticated: bool,
    pub tx: mpsc::UnboundedSender<Message>,
}

pub struct PendingConnection {
    pub device_id: String,
    pub device_name: String,
    pub client_id: String,
    pub tx: mpsc::UnboundedSender<Message>,
}

impl WebSocketState {
    pub fn new() -> Self {
        let blacklist = Self::load_blacklist();
        Self {
            clients: RwLock::new(HashMap::new()),
            pending_approval: RwLock::new(None),
            blacklist: RwLock::new(blacklist),
        }
    }

    fn get_blacklist_path() -> PathBuf {
        let mut path = dirs::config_dir().unwrap_or_else(|| PathBuf::from("."));
        path.push("sync-type");
        path.push("blacklist.json");
        path
    }

    fn load_blacklist() -> Vec<BlacklistedDevice> {
        let path = Self::get_blacklist_path();
        if path.exists() {
            if let Ok(content) = std::fs::read_to_string(&path) {
                if let Ok(storage) = serde_json::from_str::<BlacklistStorage>(&content) {
                    return storage.devices;
                }
            }
        }
        Vec::new()
    }

    async fn save_blacklist(&self) {
        let blacklist = self.blacklist.read().await;
        let storage = BlacklistStorage { devices: blacklist.clone() };
        let path = Self::get_blacklist_path();
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        if let Ok(content) = serde_json::to_string_pretty(&storage) {
            let _ = std::fs::write(&path, content);
        }
    }

    pub async fn is_blacklisted(&self, device_id: &str) -> bool {
        self.blacklist.read().await.iter().any(|d| d.device_id == device_id)
    }

    pub async fn add_to_blacklist(&self, device_id: String, device_name: String) {
        let mut blacklist = self.blacklist.write().await;
        if !blacklist.iter().any(|d| d.device_id == device_id) {
            blacklist.push(BlacklistedDevice {
                device_id,
                device_name,
                blocked_at: current_timestamp_ms(),
            });
            drop(blacklist);
            self.save_blacklist().await;
        }
    }

    pub async fn remove_from_blacklist(&self, device_id: &str) {
        let mut blacklist = self.blacklist.write().await;
        blacklist.retain(|d| d.device_id != device_id);
        drop(blacklist);
        self.save_blacklist().await;
    }

    pub async fn get_blacklisted_devices(&self) -> Vec<BlacklistedDevice> {
        self.blacklist.read().await.clone()
    }

    pub async fn add_client(&self, id: String, ip: String, tx: mpsc::UnboundedSender<Message>) {
        self.clients.write().await.insert(id, ConnectedClient {
            ip, device_id: String::new(), device_name: String::new(), authenticated: false, tx,
        });
    }

    pub async fn set_client_device(&self, id: &str, device_id: String, device_name: String) {
        if let Some(c) = self.clients.write().await.get_mut(id) {
            c.device_id = device_id;
            c.device_name = device_name;
        }
    }

    pub async fn authenticate_client(&self, id: &str) {
        if let Some(c) = self.clients.write().await.get_mut(id) {
            c.authenticated = true;
            info!("Client {} authenticated: {}", id, c.device_name);
        }
    }

    pub async fn remove_client(&self, id: &str) {
        self.clients.write().await.remove(id);
        let mut pending = self.pending_approval.write().await;
        if pending.as_ref().map(|p| p.client_id == id).unwrap_or(false) {
            *pending = None;
        }
    }

    pub async fn is_authenticated(&self, id: &str) -> bool {
        self.clients.read().await.get(id).map(|c| c.authenticated).unwrap_or(false)
    }

    pub async fn get_authenticated_devices(&self) -> Vec<(String, String)> {
        self.clients.read().await.values()
            .filter(|c| c.authenticated)
            .map(|c| (c.device_id.clone(), c.device_name.clone()))
            .collect()
    }

    pub async fn has_pending_approval(&self) -> bool {
        self.pending_approval.read().await.is_some()
    }

    pub async fn set_pending_approval(&self, pending: PendingConnection) {
        info!("Pending approval: {} ({})", pending.device_name, pending.device_id);
        *self.pending_approval.write().await = Some(pending);
    }

    pub async fn get_pending_device_name(&self) -> Option<String> {
        self.pending_approval.read().await.as_ref().map(|p| p.device_name.clone())
    }

    pub async fn remove_trusted_and_disconnect(&self, security: &crate::security::auth::SecurityManager, device_id: &str) -> bool {
        info!("Removing trusted device: {}", device_id);
        
        // Remove from trusted devices
        security.revoke_device(device_id).await;

        // Find and disconnect the client
        let clients = self.clients.read().await;
        for (cid, client) in clients.iter() {
            if client.device_id == device_id {
                let resp = serde_json::to_string(&WsMessage::new("device_removed", "remove",
                    serde_json::json!({"message": "Removed from trusted devices by desktop"})
                )).unwrap_or_default();
                let _ = client.tx.send(Message::Text(resp));
                // Also close the connection
                let _ = client.tx.send(Message::Close(None));
                info!("Sent device_removed to {} ({})", client.device_name, cid);
                return true;
            }
        }
        info!("Device {} not connected, but removed from trusted", device_id);
        true
    }

    pub async fn blacklist_and_disconnect(&self, device_id: &str) -> bool {
        info!("Blacklisting device: {}", device_id);
        
        // Find device name before blacklisting
        let device_name = {
            let clients = self.clients.read().await;
            clients.iter()
                .find(|(_, c)| c.device_id == device_id)
                .map(|(_, c)| c.device_name.clone())
                .unwrap_or_else(|| "Unknown".to_string())
        };
        
        // Add to blacklist with device name
        self.add_to_blacklist(device_id.to_string(), device_name.clone()).await;

        // Find and disconnect the client
        let clients = self.clients.read().await;
        for (cid, client) in clients.iter() {
            if client.device_id == device_id {
                let resp = serde_json::to_string(&WsMessage::new("device_blacklisted", "blacklist",
                    serde_json::json!({"message": "Device has been blacklisted by desktop"})
                )).unwrap_or_default();
                let _ = client.tx.send(Message::Text(resp));
                // Also close the connection
                let _ = client.tx.send(Message::Close(None));
                info!("Sent device_blacklisted to {} ({})", client.device_name, cid);
                return true;
            }
        }
        info!("Device {} not connected, but blacklisted", device_id);
        true
    }

    pub async fn disconnect_device(&self, device_id: &str) -> bool {
        info!("Disconnecting device: {}", device_id);
        
        let clients = self.clients.read().await;
        for (cid, client) in clients.iter() {
            if client.device_id == device_id {
                let resp = serde_json::to_string(&WsMessage::new("device_disconnected", "disconnect",
                    serde_json::json!({"message": "Disconnected by desktop"})
                )).unwrap_or_default();
                let _ = client.tx.send(Message::Text(resp));
                // Also close the connection
                let _ = client.tx.send(Message::Close(None));
                info!("Sent disconnect to {} ({})", client.device_name, cid);
                return true;
            }
        }
        info!("Device {} not connected", device_id);
        false
    }

    pub async fn approve_connection(&self, security: &crate::security::auth::SecurityManager) -> bool {
        let pending = self.pending_approval.write().await.take();
        if let Some(p) = pending {
            let token = generate_token();
            let device = crate::security::pairing::TrustedDevice {
                device_id: p.device_id.clone(),
                device_name: p.device_name.clone(),
                token_hash: String::new(),
                paired_at: current_timestamp_ms(),
            };
            security.add_trusted_device(device, &token).await;

            {
                let mut clients = self.clients.write().await;
                if let Some(client) = clients.get_mut(&p.client_id) {
                    client.authenticated = true;
                }
            }

            let resp = serde_json::to_string(&WsMessage::new("connection_approved", "approve",
                serde_json::json!({"token": token, "message": "Connection approved"})
            )).unwrap_or_default();
            let _ = p.tx.send(Message::Text(resp));
            info!("Connection approved: {}", p.device_name);
            return true;
        }
        false
    }

    pub async fn reject_connection(&self) -> bool {
        let pending = self.pending_approval.write().await.take();
        if let Some(p) = pending {
            // Add to blacklist
            self.add_to_blacklist(p.device_id.clone(), p.device_name.clone()).await;

            let resp = serde_json::to_string(&WsMessage::new("connection_rejected", "reject",
                serde_json::json!({"message": "Connection rejected and blacklisted"})
            )).unwrap_or_default();
            let _ = p.tx.send(Message::Text(resp));
            info!("Connection rejected and blacklisted: {}", p.device_name);
            return true;
        }
        false
    }

    pub async fn broadcast_to_authenticated(&self, msg: &str) {
        for (_, client) in self.clients.read().await.iter() {
            if client.authenticated {
                let _ = client.tx.send(Message::Text(msg.to_string()));
            }
        }
    }

    pub async fn broadcast_job_complete(&self, job_id: &str, chars_typed: usize) {
        let msg = serde_json::to_string(&WsMessage::new("type_complete", &format!("bc_{}", job_id),
            serde_json::json!({"job_id": job_id, "status": "complete", "chars_typed": chars_typed})
        )).unwrap_or_default();
        self.broadcast_to_authenticated(&msg).await;
    }

    pub async fn broadcast_job_error(&self, job_id: &str, error: &str) {
        let msg = serde_json::to_string(&WsMessage::new("type_error", &format!("bc_{}", job_id),
            serde_json::json!({"job_id": job_id, "error": error})
        )).unwrap_or_default();
        self.broadcast_to_authenticated(&msg).await;
    }
}

fn generate_token() -> String {
    use rand::Rng;
    let mut rng = rand::thread_rng();
    let bytes: Vec<u8> = (0..32).map(|_| rng.gen()).collect();
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

fn current_timestamp_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

pub async fn start(state: Arc<AppState>) -> anyhow::Result<()> {
    let port = state.config.read().await.ws_port;
    let addr = format!("0.0.0.0:{}", port);
    let listener = TcpListener::bind(&addr).await?;
    info!("WebSocket server listening on {}", addr);

    let ws_state = Arc::new(WebSocketState::new());
    *state.websocket_state.write().await = Some(ws_state.clone());

    loop {
        match listener.accept().await {
            Ok((stream, addr)) => {
                let s = state.clone();
                let ws = ws_state.clone();
                tokio::spawn(async move { handle_connection(s, ws, stream, addr).await; });
            }
            Err(e) => error!("Accept error: {}", e),
        }
    }
}

async fn handle_connection(
    state: Arc<AppState>,
    ws_state: Arc<WebSocketState>,
    stream: tokio::net::TcpStream,
    addr: SocketAddr,
) {
    info!("New connection: {}", addr);

    let ws_stream = match tokio_tungstenite::accept_async(stream).await {
        Ok(ws) => ws,
        Err(e) => { error!("Handshake failed: {}", e); return; }
    };

    let (mut ws_writer, mut ws_reader) = ws_stream.split();
    let (tx, mut rx) = mpsc::unbounded_channel::<Message>();
    let client_id = format!("{}", addr);

    ws_state.add_client(client_id.clone(), addr.ip().to_string(), tx.clone()).await;

    let write_task = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if ws_writer.send(msg).await.is_err() { break; }
        }
    });

    let mut device_id = String::new();
    let mut device_name = String::new();

    while let Some(result) = ws_reader.next().await {
        let msg = match result {
            Ok(m) => m,
            Err(e) => { warn!("Read error: {}", e); break; }
        };

        let text = match msg {
            Message::Text(t) => t,
            Message::Close(_) => { info!("Client disconnected: {}", addr); break; }
            _ => continue,
        };

        let ws_msg: WsMessage = match serde_json::from_str(&text) {
            Ok(m) => m,
            Err(e) => { warn!("Bad JSON: {}", e); continue; }
        };

        info!("Msg: type={}, id={}", ws_msg.msg_type, ws_msg.id);

        match ws_msg.msg_type.as_str() {
            "connect_request" => {
                let did = ws_msg.payload["device_id"].as_str().unwrap_or("").to_string();
                let dname = ws_msg.payload["device_name"].as_str().unwrap_or("Unknown").to_string();
                let token = ws_msg.payload["token"].as_str().unwrap_or("").to_string();

                info!("Connect request: {} ({}), token_len={}", dname, did, token.len());

                // Check if device already connected - remove old connection
                {
                    let mut clients = ws_state.clients.write().await;
                    let old_ids: Vec<String> = clients.iter()
                        .filter(|(_, c)| c.device_id == did)
                        .map(|(id, _)| id.clone())
                        .collect();
                    for old_id in old_ids {
                        if old_id != client_id {
                            clients.remove(&old_id);
                            info!("Removed old connection for device: {} ({})", did, old_id);
                        }
                    }
                }

                device_id = did.clone();
                device_name = dname.clone();
                ws_state.set_client_device(&client_id, did.clone(), dname.clone()).await;

                // Check blacklist
                if ws_state.is_blacklisted(&did).await {
                    warn!("Device blacklisted: {}", dname);
                    send_json(&tx, "auth", "connection_rejected", serde_json::json!({
                        "message": "Device is blacklisted by desktop"
                    }));
                    continue;
                }

                // Check if token is valid
                if !token.is_empty() {
                    info!("Checking token for device: {}", did);
                    let auth_result = state.security.authenticate(&did, &token).await;
                    info!("Token auth result: {}", auth_result);
                    
                    if auth_result {
                        ws_state.authenticate_client(&client_id).await;
                        send_json(&tx, "auth", "connection_confirmed", serde_json::json!({
                            "success": true, "message": "Welcome back!"
                        }));
                        info!("Auto-authenticated: {} ({})", dname, did);
                        continue;
                    } else {
                        warn!("Token invalid for device: {}", did);
                    }
                } else {
                    info!("No token provided for device: {}", did);
                }

                // Need approval
                ws_state.set_pending_approval(PendingConnection {
                    device_id: did, device_name: dname.clone(),
                    client_id: client_id.clone(), tx: tx.clone(),
                }).await;
                send_json(&tx, "auth", "approval_required", serde_json::json!({
                    "message": "Waiting for desktop approval..."
                }));
                info!("Waiting for approval: {}", dname);
            }
            "approve_connection" => {
                if ws_state.approve_connection(&state.security).await {
                    info!("Connection approved");
                }
            }
            "reject_connection" => {
                ws_state.reject_connection().await;
            }
            _ => {
                let authed = ws_state.is_authenticated(&client_id).await;
                if !authed {
                    send_json(&tx, "err", "error", serde_json::json!({"code":"AUTH_REQUIRED","message":"Not authenticated"}));
                    continue;
                }

                info!("Processing: {}", ws_msg.msg_type);

                match ws_msg.msg_type.as_str() {
                    "ping" => { send_json(&tx, &ws_msg.id, "pong", serde_json::json!({})); }
                    "type" => {
                        let text = ws_msg.payload["text"].as_str().unwrap_or("").to_string();
                        let mode = ws_msg.payload["mode"].as_str().unwrap_or("normal").to_string();
                        let speed = ws_msg.payload["speed"].as_u64().unwrap_or(50) as u8;
                        let priority = ws_msg.payload["priority"].as_str().unwrap_or("normal").to_string();

                        if text.is_empty() {
                            send_json(&tx, &ws_msg.id, "error", serde_json::json!({"code":"INVALID","message":"Empty text"}));
                            continue;
                        }

                        let total_chars = text.chars().count();

                        // Instant mode - use clipboard paste for reliability
                        if mode == "instant" {
                            info!("Instant typing: {} chars", total_chars);
                            match state.typing_engine.paste_clipboard(&text).await {
                                Ok(_) => {
                                    send_json(&tx, &ws_msg.id, "success", serde_json::json!({"job_id":"instant","status":"complete","total_chars":total_chars}));
                                    if let Some(ws) = state.websocket_state.read().await.as_ref() {
                                        ws.broadcast_job_complete("instant", total_chars).await;
                                    }
                                }
                                Err(e) => {
                                    error!("Instant type failed: {}", e);
                                    send_json(&tx, &ws_msg.id, "error", serde_json::json!({"code":"INPUT_ERROR","message":e.to_string()}));
                                }
                            }
                            continue;
                        }

                        // Other modes - queue
                        let job_id = format!("job_{}", uuid::Uuid::new_v4());
                        let job = crate::typing::queue::TypingJob {
                            job_id: job_id.clone(), text, mode, speed, priority,
                            device_id: device_id.clone(),
                            status: crate::typing::queue::JobStatus::Queued,
                            created_at: current_timestamp_ms(),
                            chars_typed: 0, total_chars,
                        };

                        match state.typing_queue.enqueue(job.clone()).await {
                            Ok(_) => {
                                let sender = state.typing_queue.get_sender().await;
                                if sender.send(job).await.is_ok() {
                                    send_json(&tx, &ws_msg.id, "success", serde_json::json!({"job_id":job_id,"status":"queued","total_chars":total_chars}));
                                    info!("Job queued: {} ({} chars)", job_id, total_chars);
                                }
                            }
                            Err(e) => { send_json(&tx, &ws_msg.id, "error", serde_json::json!({"code":"QUEUE_FULL","message":e})); }
                        }
                    }
                    "key_press" => {
                        let key = ws_msg.payload["key"].as_str().unwrap_or("").to_string();
                        let modifiers: Vec<String> = ws_msg.payload["modifiers"].as_array()
                            .map(|a| a.iter().filter_map(|v| v.as_str().map(String::from)).collect())
                            .unwrap_or_default();

                        if key.is_empty() { continue; }

                        if let Ok(engine) = crate::typing::engine::TypingEngine::new() {
                            let _ = engine.send_key(&key, &modifiers).await;
                            send_json(&tx, &ws_msg.id, "success", serde_json::json!({"key":key}));
                        }
                    }
                    "clipboard" => {
                        let text = ws_msg.payload["text"].as_str().unwrap_or("").to_string();
                        if text.is_empty() {
                            send_json(&tx, &ws_msg.id, "error", serde_json::json!({"code":"INVALID","message":"Empty text"}));
                            continue;
                        }

                        info!("Clipboard: {} chars", text.len());
                        match state.typing_engine.paste_clipboard(&text).await {
                            Ok(_) => {
                                send_json(&tx, &ws_msg.id, "success", serde_json::json!({"pasted":true}));
                            }
                            Err(e) => {
                                error!("Clipboard failed: {}", e);
                                send_json(&tx, &ws_msg.id, "error", serde_json::json!({"code":"INPUT_ERROR","message":e.to_string()}));
                            }
                        }
                    }
                    "queue_status" => {
                        let len = state.typing_queue.queue_length().await;
                        let active = state.typing_queue.get_active().await;
                        let jobs = state.typing_queue.get_queue().await;
                        let jobs_json: Vec<serde_json::Value> = jobs.iter().map(|j| {
                            serde_json::json!({
                                "job_id": j.job_id,
                                "chars": j.total_chars,
                                "priority": j.priority,
                                "mode": j.mode,
                                "status": format!("{:?}", j.status)
                            })
                        }).collect();
                        send_json(&tx, &ws_msg.id, "queue_status", serde_json::json!({
                            "queue_length": len,
                            "active_job": active.map(|j| serde_json::json!({
                                "job_id": j.job_id,
                                "chars_typed": j.chars_typed,
                                "total_chars": j.total_chars,
                                "mode": j.mode,
                                "priority": j.priority
                            })),
                            "jobs": jobs_json
                        }));
                    }
                    "queue_cancel" => {
                        let job_id = ws_msg.payload["job_id"].as_str().unwrap_or("").to_string();
                        if state.typing_queue.cancel_job(&job_id).await {
                            send_json(&tx, &ws_msg.id, "success", serde_json::json!({"cancelled":true}));
                        }
                    }
                    "queue_clear" => {
                        state.typing_queue.clear_all().await;
                        send_json(&tx, &ws_msg.id, "success", serde_json::json!({"cleared":true}));
                    }
                    "typing_control" => {
                        let action = ws_msg.payload["action"].as_str().unwrap_or("").to_string();
                        match action.as_str() {
                            "pause" => state.typing_engine.pause().await,
                            "resume" => state.typing_engine.resume().await,
                            "abort" => state.typing_engine.abort().await,
                            _ => {}
                        }
                        send_json(&tx, &ws_msg.id, "success", serde_json::json!({"action":action}));
                    }
                    _ => {
                        send_json(&tx, &ws_msg.id, "error", serde_json::json!({"code":"UNKNOWN","message":"Unknown type"}));
                    }
                }
            }
        }
    }

    ws_state.remove_client(&client_id).await;
    write_task.abort();
    info!("Connection closed: {} ({})", addr, device_name);
}

fn send_json(tx: &mpsc::UnboundedSender<Message>, msg_id: &str, msg_type: &str, payload: serde_json::Value) {
    let resp = serde_json::to_string(&WsMessage::new(msg_type, msg_id, payload)).unwrap_or_default();
    let _ = tx.send(Message::Text(resp));
}
