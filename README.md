# SyncType

**LAN-based remote typing system that turns your phone into a wireless keyboard for your desktop.**

Send text, execute keyboard shortcuts, paste clipboard content, and manage typing queues - all over your local network with zero internet dependency.

---

## How It Works

```
┌─────────────────┐       WebSocket      ┌──────────────────┐     SendInput      ┌─────────────┐
│  Android App    │ ◄──────────────────► │  Desktop Agent   │ ─────────────────► │  Windows    │
│  (Kotlin)       │     LAN (Port 9876)  │  (Rust + Tauri)  │    Keyboard API    │  Desktop    │
└─────────────────┘                      └──────────────────┘                    └─────────────┘
        │                                         │
        │ UDP Broadcast                           │ UDP Listener
        │ (Port 9877)                             │ (Port 9877)
        └─────────────────────────────────────────┘
                    Auto-Discovery
```

---

## Desktop Features

### Core
- **WebSocket Server** - Accepts connections on port 9876
- **UDP Discovery** - Responds to device probes on port 9877
- **System Tray** - Runs minimized in system tray
- **Stealth Mode** - Hides from taskbar and tray completely
- **Auto-Start** - Launch with Windows (configurable)

### Queue System
- **Job Queue** - Process multiple typing jobs sequentially
- **Priority Levels** - Urgent, High, Normal, Low
- **Real-time Progress** - Broadcast progress every 5 characters
- **Pause/Resume/Abort** - Full control over active typing

### Security
- **Device Approval** - Desktop must approve each new connection
- **Token Persistence** - Trusted devices reconnect automatically
- **Blacklist** - Block unwanted devices permanently
- **Connection Rejection** - Deny access with one click
---

## Android Features

### Connection
- **Auto-Discovery** - Find desktop on local network via UDP
- **Manual Connection** - Enter IP address directly
- **Auto-Reconnect** - Reconnects on WiFi disconnect (2s-30s backoff)
- **Network Monitor** - Detects WiFi changes instantly
- **Token Persistence** - Remembers approved devices

### Queue Management
- **Queue View** - See all pending and active jobs
- **Cancel Jobs** - Remove specific jobs from queue
- **Clear Queue** - Remove all pending jobs
- **Active Job** - See current typing progress

---

## Connection Flow

### First Time
1. Android discovers desktop (or enter IP manually)
2. Android sends connection request
3. Desktop shows approval popup
4. User clicks **Approve**
5. Token saved on both devices
6. Connected!

### Subsequent Connections
1. Android connects with saved token
2. Desktop auto-authenticates
3. Connected immediately (no approval needed)

### After Disconnect
1. Android detects WiFi change
2. Auto-reconnects with exponential backoff
3. Sends saved token
4. Auto-authenticated
5. Connection restored

---

## Typing Modes

| Mode | Speed | Use Case |
|------|-------|----------|
| **Instant** | N/A | Paste long text quickly |
| **Fast** | 80-120 WPM | Quick typing, minimal delay |
| **Normal** | 30-70 WPM | Standard typing speed |
| **Human** | 20-55 WPM | Realistic with natural pauses |

---

## Priority Levels

| Level | Behavior |
|-------|----------|
| **Urgent** | Processed immediately, before all others |
| **High** | Processed after urgent, before normal |
| **Normal** | Standard FIFO order |
| **Low** | Processed after all normal jobs |

---

## Setup

### Desktop

#### Install Rust

```bash
powershell -c "irm https://win.rustup.rs -OutFile rustup-init.exe; .\rustup-init.exe -y"
```
(OR)
Official Rust installer:  
https://www.rust-lang.org/tools/install

#### Install Tauri CLI

```bash
cargo install tauri-cli
```

#### Run in development

```bash
cd desktop
```

```bash
cargo tauri dev
```

#### Build for release

```bash
cargo tauri build
```

---

### Android

#### Install Android Studio

https://developer.android.com/studio

#### Set JAVA_HOME

```bash
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
```

#### Add Java to PATH

```bash
set PATH=%JAVA_HOME%\bin;%PATH%
```

#### Build debug APK

```bash
cd android
```

```bash
./gradlew assembleDebug
```

#### Build release APK

```bash
./gradlew assembleRelease
```

---

## Requirements

### Desktop
- Windows 10 or later
- Rust 1.70+
- 50MB disk space

### Android
- Android 8.0 (API 26) or later
- WiFi connection
- 20MB disk space

### Network
- Both devices on same WiFi network
- No firewall blocking ports 9876/9877

---

## License

MIT License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
