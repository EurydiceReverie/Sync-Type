use serde::{Deserialize, Serialize};
use std::net::{Ipv4Addr, SocketAddr, UdpSocket};
use std::sync::Arc;
use std::time::Duration;
use tokio::net::UdpSocket as TokioUdpSocket;
use tracing::{error, info, warn};

use crate::state::AppState;

const DISCOVERY_PORT: u16 = 9877;
const BUFFER_SIZE: usize = 1024;

#[derive(Debug, Serialize, Deserialize)]
struct DiscoveryProbe {
    #[serde(rename = "type")]
    msg_type: String,
    client_id: String,
    timestamp: u64,
}

#[derive(Debug, Serialize, Deserialize)]
struct DiscoveryResponse {
    #[serde(rename = "type")]
    msg_type: String,
    device_name: String,
    device_id: String,
    ws_port: u16,
    paired: bool,
    version: String,
    timestamp: u64,
}

pub async fn start_listener(state: Arc<AppState>) -> anyhow::Result<()> {
    info!(
        "Starting UDP discovery listener on port {}",
        DISCOVERY_PORT
    );

    let socket = match TokioUdpSocket::bind(format!("0.0.0.0:{}", DISCOVERY_PORT)).await {
        Ok(s) => s,
        Err(e) => {
            error!("Failed to bind UDP socket: {}", e);
            return Err(e.into());
        }
    };

    let device_id = state.config.read().await.device_id.clone();
    let device_name = get_device_name();
    let ws_port = state.config.read().await.ws_port;

    let mut buf = [0u8; BUFFER_SIZE];

    loop {
        match socket.recv_from(&mut buf).await {
            Ok((len, addr)) => {
                let data = &buf[..len];
                info!("Received {} bytes from {}", len, addr);
                
                if let Ok(probe) = serde_json::from_slice::<DiscoveryProbe>(data) {
                    info!("Parsed probe: type={}, client={}", probe.msg_type, probe.client_id);
                    
                    if probe.msg_type == "discovery_probe" {
                        info!("Discovery probe from {}", addr);

                        let response = DiscoveryResponse {
                            msg_type: "discovery_response".to_string(),
                            device_name: device_name.clone(),
                            device_id: device_id.clone(),
                            ws_port,
                            paired: state.security.has_devices().await,
                            version: env!("CARGO_PKG_VERSION").to_string(),
                            timestamp: current_timestamp_ms(),
                        };

                        let response_data = serde_json::to_vec(&response).unwrap_or_default();
                        info!("Sending response to {}: {} bytes", addr, response_data.len());

                        if let Err(e) = socket.send_to(&response_data, addr).await {
                            warn!("Failed to send discovery response: {}", e);
                        } else {
                            info!("Response sent to {}", addr);
                        }
                    }
                } else {
                    warn!("Failed to parse probe from {}", addr);
                }
            }
            Err(e) => {
                error!("UDP recv error: {}", e);
                tokio::time::sleep(Duration::from_millis(100)).await;
            }
        }
    }
}

pub fn broadcast_discovery() -> anyhow::Result<()> {
    let socket = UdpSocket::bind("0.0.0.0:0")?;
    socket.set_broadcast(true)?;
    socket.set_read_timeout(Some(Duration::from_secs(1)))?;

    let addr = SocketAddr::new(
        Ipv4Addr::new(255, 255, 255, 255).into(),
        DISCOVERY_PORT,
    );

    let probe = DiscoveryProbe {
        msg_type: "discovery_probe".to_string(),
        client_id: "desktop_self".to_string(),
        timestamp: current_timestamp_ms(),
    };

    let data = serde_json::to_vec(&probe)?;
    socket.send_to(&data, addr)?;

    info!("Broadcast discovery probe sent");
    Ok(())
}

fn get_device_name() -> String {
    hostname::get()
        .ok()
        .and_then(|h| h.to_str().map(String::from))
        .unwrap_or_else(|| "DESKTOP".to_string())
}

fn current_timestamp_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
