#![windows_subsystem = "windows"]

use std::sync::Arc;
use tauri::Manager;
use tracing_subscriber::EnvFilter;

fn main() {
    // Only enable logging in debug mode
    #[cfg(debug_assertions)]
    {
        tracing_subscriber::fmt()
            .with_env_filter(EnvFilter::from_default_env().add_directive("lan_remote_type=info".parse().unwrap()))
            .with_target(true)
            .with_thread_ids(true)
            .init();
        tracing::info!("SyncType starting...");
    }

    tauri::Builder::default()
        .system_tray(lan_remote_type::tray::create_tray())
        .setup(|app| {
            lan_remote_type::setup(app);
            
            // Get main window
            let window = app.get_window("main").unwrap();
            
            // Handle close to tray instead of quitting
            let window_clone = window.clone();
            window.on_window_event(move |event| {
                if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                    // Prevent default close
                    api.prevent_close();
                    // Hide window instead
                    let _ = window_clone.hide();
                }
            });
            
            Ok(())
        })
        .on_system_tray_event(|app, event| lan_remote_type::handle_tray_event(app, event))
        .invoke_handler(tauri::generate_handler![
            get_status,
            quit_app,
            approve_connection,
            reject_connection,
            get_pending_connection,
            get_typing_status,
            hide_window,
            show_window,
            enable_stealth_mode,
            disable_stealth_mode,
            get_stealth_status,
            get_trusted_devices,
            remove_trusted_device,
            get_blacklisted_devices,
            unblock_device,
            blacklist_device,
            disconnect_device,
            clear_all_data
        ])
        .run(tauri::generate_context!())
        .expect("error while running SyncType");
}

#[tauri::command]
async fn get_status(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<serde_json::Value, String> {
    let config = state.config.read().await;
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64;
    let uptime_seconds = (now - state.start_time) / 1000;

    let (conn_count, connected_devices) = if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let devices = ws.get_authenticated_devices().await;
        let count = devices.len();
        let device_list: Vec<serde_json::Value> = devices.into_iter().map(|(id, name)| {
            serde_json::json!({"device_id": id, "device_name": name})
        }).collect();
        (count, device_list)
    } else {
        (0, Vec::new())
    };

    let pending = if let Some(ws) = state.websocket_state.read().await.as_ref() {
        ws.get_pending_device_name().await.map(|name| serde_json::json!({"device_name": name}))
    } else {
        None
    };

    Ok(serde_json::json!({
        "ws_port": config.ws_port,
        "connected_devices": conn_count,
        "connected_device_list": connected_devices,
        "uptime_seconds": uptime_seconds,
        "device_name": config.device_name,
        "pending_connection": pending,
        "stealth_mode": state.stealth_mode.read().await.clone(),
        "local_ip": get_local_ip()
    }))
}

#[tauri::command]
fn quit_app(app: tauri::AppHandle) {
    app.exit(0);
}

#[tauri::command]
fn hide_window(app: tauri::AppHandle) {
    if let Some(window) = app.get_window("main") {
        let _ = window.hide();
    }
}

#[tauri::command]
fn show_window(app: tauri::AppHandle) {
    if let Some(window) = app.get_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
    }
}

#[tauri::command]
async fn enable_stealth_mode(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
    app: tauri::AppHandle,
) -> Result<(), String> {
    *state.stealth_mode.write().await = true;
    
    // Hide window completely
    if let Some(window) = app.get_window("main") {
        let _ = window.hide();
        let _ = window.set_skip_taskbar(true);
    }
    
    // Hide tray icon
    app.tray_handle().destroy().ok();
    
    info!("Stealth mode enabled - hidden from taskbar and tray");
    Ok(())
}

#[tauri::command]
async fn disable_stealth_mode(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
    app: tauri::AppHandle,
) -> Result<(), String> {
    *state.stealth_mode.write().await = false;
    
    // Show window
    if let Some(window) = app.get_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
        let _ = window.set_skip_taskbar(false);
    }
    
    // Recreate tray
    let tray = lan_remote_type::tray::create_tray();
    let _ = tray.build(&app);
    
    info!("Stealth mode disabled");
    Ok(())
}

#[tauri::command]
async fn get_stealth_status(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<bool, String> {
    Ok(*state.stealth_mode.read().await)
}

#[tauri::command]
async fn approve_connection(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<bool, String> {
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        Ok(ws.approve_connection(&state.security).await)
    } else {
        Ok(false)
    }
}

#[tauri::command]
async fn reject_connection(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<bool, String> {
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        Ok(ws.reject_connection().await)
    } else {
        Ok(false)
    }
}

#[tauri::command]
async fn get_pending_connection(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<serde_json::Value, String> {
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        if ws.has_pending_approval().await {
            let name = ws.get_pending_device_name().await.unwrap_or_else(|| "Unknown".to_string());
            Ok(serde_json::json!({"device_name": name, "pending": true}))
        } else {
            Ok(serde_json::json!({"pending": false}))
        }
    } else {
        Ok(serde_json::json!({"pending": false}))
    }
}

#[tauri::command]
async fn get_typing_status(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<serde_json::Value, String> {
    let active = state.typing_queue.get_active().await;
    let queue_len = state.typing_queue.queue_length().await;

    Ok(serde_json::json!({
        "active_job": active.map(|j| serde_json::json!({
            "job_id": j.job_id,
            "chars_typed": j.chars_typed,
            "total_chars": j.total_chars,
        })),
        "queue_length": queue_len
    }))
}

fn get_local_ip() -> String {
    let socket = match std::net::UdpSocket::bind("0.0.0.0:0") {
        Ok(s) => s,
        Err(_) => return "Unknown".to_string(),
    };
    if socket.connect("8.8.8.8:80").is_err() {
        return "Unknown".to_string();
    }
    match socket.local_addr() {
        Ok(addr) => addr.ip().to_string(),
        Err(_) => "Unknown".to_string(),
    }
}

#[tauri::command]
async fn get_trusted_devices(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<serde_json::Value, String> {
    let devices = state.security.get_trusted_devices().await;
    let devices_json: Vec<serde_json::Value> = devices.iter().map(|d| {
        serde_json::json!({
            "device_id": d.device_id,
            "device_name": d.device_name,
            "paired_at": d.paired_at
        })
    }).collect();

    Ok(serde_json::json!({
        "devices": devices_json,
        "count": devices_json.len()
    }))
}

#[tauri::command]
async fn remove_trusted_device(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
    device_id: String,
) -> Result<bool, String> {
    tracing::info!("Tauri command: remove_trusted_device, device_id={}", device_id);
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let result = ws.remove_trusted_and_disconnect(&state.security, &device_id).await;
        tracing::info!("remove_trusted_and_disconnect result: {}", result);
        Ok(result)
    } else {
        state.security.revoke_device(&device_id).await;
        Ok(true)
    }
}

#[tauri::command]
async fn get_blacklisted_devices(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<serde_json::Value, String> {
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let devices = ws.get_blacklisted_devices().await;
        let devices_json: Vec<serde_json::Value> = devices.iter().map(|d| {
            serde_json::json!({
                "device_id": d.device_id,
                "device_name": d.device_name,
                "blocked_at": d.blocked_at
            })
        }).collect();
        Ok(serde_json::json!({"devices": devices_json, "count": devices_json.len()}))
    } else {
        Ok(serde_json::json!({"devices": [], "count": 0}))
    }
}

#[tauri::command]
async fn unblock_device(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
    device_id: String,
) -> Result<bool, String> {
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        ws.remove_from_blacklist(&device_id).await;
        Ok(true)
    } else {
        Ok(false)
    }
}

#[tauri::command]
async fn blacklist_device(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
    device_id: String,
) -> Result<bool, String> {
    tracing::info!("Tauri command: blacklist_device, device_id={}", device_id);
    // Remove from trusted devices first
    state.security.revoke_device(&device_id).await;
    // Then blacklist and disconnect
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let result = ws.blacklist_and_disconnect(&device_id).await;
        tracing::info!("blacklist_and_disconnect result: {}", result);
        Ok(result)
    } else {
        Ok(false)
    }
}

#[tauri::command]
async fn disconnect_device(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
    device_id: String,
) -> Result<bool, String> {
    tracing::info!("Tauri command: disconnect_device, device_id={}", device_id);
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let result = ws.disconnect_device(&device_id).await;
        tracing::info!("disconnect_device result: {}", result);
        Ok(result)
    } else {
        Ok(false)
    }
}

#[tauri::command]
async fn clear_all_data(
    state: tauri::State<'_, Arc<lan_remote_type::state::AppState>>,
) -> Result<bool, String> {
    // Disconnect all clients first
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let devices = ws.get_authenticated_devices().await;
        for (device_id, _) in devices {
            ws.disconnect_device(&device_id).await;
        }
    }

    // Clear trusted devices
    let devices = state.security.get_trusted_devices().await;
    for device in devices {
        state.security.revoke_device(&device.device_id).await;
    }

    // Clear blacklist
    if let Some(ws) = state.websocket_state.read().await.as_ref() {
        let blacklisted = ws.get_blacklisted_devices().await;
        for device in blacklisted {
            ws.remove_from_blacklist(&device.device_id).await;
        }
    }

    // Clear config file
    let mut config_path = dirs::config_dir().unwrap_or_else(|| std::path::PathBuf::from("."));
    config_path.push("sync-type");
    if config_path.exists() {
        let _ = std::fs::remove_dir_all(&config_path);
    }

    tracing::info!("All data cleared and clients disconnected");
    Ok(true)
}

use tracing::info;
