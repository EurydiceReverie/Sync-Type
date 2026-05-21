use std::sync::Arc;
use tauri::{AppHandle, CustomMenuItem, Manager, SystemTray, SystemTrayMenu, SystemTrayMenuItem};
use tracing::info;

pub fn create_tray() -> SystemTray {
    let show_window = CustomMenuItem::new("show_window".to_string(), "Show Window");
    let hide_window = CustomMenuItem::new("hide_window".to_string(), "Hide Window");
    let stealth = CustomMenuItem::new("stealth".to_string(), "Stealth Mode");
    let status = CustomMenuItem::new("status".to_string(), "Status");
    let quit = CustomMenuItem::new("quit".to_string(), "Quit");

    let tray_menu = SystemTrayMenu::new()
        .add_item(show_window)
        .add_item(hide_window)
        .add_native_item(SystemTrayMenuItem::Separator)
        .add_item(stealth)
        .add_native_item(SystemTrayMenuItem::Separator)
        .add_item(status)
        .add_native_item(SystemTrayMenuItem::Separator)
        .add_item(quit);

    SystemTray::new().with_menu(tray_menu)
}

pub fn show_window(app: &AppHandle) {
    if let Some(window) = app.get_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
        let _ = window.unminimize();
    }
}

pub fn hide_window(app: &AppHandle) {
    if let Some(window) = app.get_window("main") {
        let _ = window.hide();
    }
}

pub fn handle_menu_item(app: &AppHandle, id: &str) {
    match id {
        "show_window" => show_window(app),
        "hide_window" => hide_window(app),
        "stealth" => {
            let state = app.try_state::<Arc<crate::state::AppState>>();
            if let Some(state) = state {
                let is_stealth = futures::executor::block_on(async {
                    *state.stealth_mode.read().await
                });

                if is_stealth {
                    futures::executor::block_on(async {
                        *state.stealth_mode.write().await = false;
                    });
                    if let Some(window) = app.get_window("main") {
                        let _ = window.show();
                        let _ = window.set_focus();
                        let _ = window.set_skip_taskbar(false);
                    }
                    info!("Stealth mode disabled");
                } else {
                    futures::executor::block_on(async {
                        *state.stealth_mode.write().await = true;
                    });
                    if let Some(window) = app.get_window("main") {
                        let _ = window.hide();
                        let _ = window.set_skip_taskbar(true);
                    }
                    info!("Stealth mode enabled");
                }
            }
        }
        "status" => {
            show_window(app);
        }
        "quit" => {
            info!("Quitting application");
            app.exit(0);
        }
        _ => {}
    }
}
