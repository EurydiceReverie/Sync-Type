pub mod config;
pub mod discovery;
pub mod security;
pub mod state;
pub mod tray;
pub mod typing;
pub mod websocket;

use std::sync::Arc;
use tauri::{App, AppHandle, Manager, SystemTrayEvent};
use tracing::info;

use crate::state::AppState;

pub fn setup(app: &mut App) {
    info!("SyncType starting up");

    let state = Arc::new(AppState::new());
    app.manage(state.clone());

    let handle = app.handle();
    tauri::async_runtime::spawn(async move {
        // Cleanup duplicate devices on startup
        state.security.cleanup_duplicates().await;
        
        if let Err(e) = start_services(state, handle).await {
            tracing::error!("Service error: {}", e);
        }
    });

    info!("SyncType initialized");
}

async fn start_services(
    state: Arc<AppState>,
    _handle: AppHandle,
) -> anyhow::Result<()> {
    let ws_state = state.clone();
    let ws_handle = tauri::async_runtime::spawn(async move {
        match websocket::server::start(ws_state).await {
            Ok(_) => info!("WebSocket server stopped"),
            Err(e) => tracing::error!("WebSocket server error: {}", e),
        }
    });

    let disc_state = state.clone();
    let disc_handle = tauri::async_runtime::spawn(async move {
        match discovery::udp::start_listener(disc_state).await {
            Ok(_) => info!("Discovery listener stopped"),
            Err(e) => tracing::error!("Discovery listener error: {}", e),
        }
    });

    let type_state = state.clone();
    let type_handle = tauri::async_runtime::spawn(async move {
        typing::queue::start_queue_processor(type_state).await;
    });

    tokio::select! {
        _ = ws_handle => {},
        _ = disc_handle => {},
        _ = type_handle => {},
    }

    Ok(())
}

pub fn handle_tray_event(app: &AppHandle, event: SystemTrayEvent) {
    match event {
        SystemTrayEvent::LeftClick { .. } => {
            tray::show_window(app);
        }
        SystemTrayEvent::RightClick { .. } => {}
        SystemTrayEvent::MenuItemClick { id, .. } => {
            tray::handle_menu_item(app, &id);
        }
        _ => {}
    }
}
