package com.lanremotetype.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Reconnecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

sealed class WebSocketEvent {
    data class AuthResponse(val success: Boolean, val message: String) : WebSocketEvent()
    data class TypingProgress(val jobId: String, val progress: Float, val charsTyped: Int, val totalChars: Int) : WebSocketEvent()
    data class TypingComplete(val jobId: String, val charsTyped: Int) : WebSocketEvent()
    data class TypingError(val jobId: String, val error: String) : WebSocketEvent()
    data class ConnectionChanged(val state: ConnectionState) : WebSocketEvent()
}

class WebSocketClientManager(
    private val scope: CoroutineScope,
    private val tokenManager: TokenManager,
    private val context: Context? = null
) {
    private val TAG = "WSClient"
    private val deviceId: String = getOrCreateDeviceId()
    private val deviceName: String = android.os.Build.MODEL
    private var storedToken: String? = null
    private var client: WebSocketClient? = null
    private var isConnected = AtomicBoolean(false)
    private var manualDisconnect = AtomicBoolean(false)
    private var lastIp: String? = null
    private var lastPort: Int = 0
    private var retryCount = 0
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableStateFlow<WebSocketEvent?>(null)
    val events: StateFlow<WebSocketEvent?> = _events.asStateFlow()

    init {
        storedToken = tokenManager.getToken(deviceId)
        Log.d(TAG, "Init: device=$deviceId, has_token=${storedToken != null}")
        setupNetworkMonitor()
    }

    private fun setupNetworkMonitor() {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available")
                    if (!isConnected.get() && !manualDisconnect.get() && lastIp != null) {
                        scope.launch { delay(1000); if (!isConnected.get() && !manualDisconnect.get()) doConnect(lastIp!!, lastPort) }
                    }
                }
                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost")
                    if (isConnected.get()) {
                        isConnected.set(false)
                        scope.launch(Dispatchers.Main) {
                            _connectionState.value = ConnectionState.Reconnecting
                            _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Reconnecting)
                        }
                    }
                }
            })
        } catch (e: Exception) { Log.e(TAG, "Network monitor failed: ${e.message}") }
    }

    private fun getOrCreateDeviceId(): String {
        tokenManager.getDeviceId()?.let { return it }
        val id = "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.DEVICE}".hashCode().toUInt().toString(16).take(8)
        tokenManager.saveDeviceId(id)
        return id
    }

    fun hasStoredToken(): Boolean = getToken() != null
    fun getDeviceId(): String = deviceId

    fun setToken(token: String) {
        storedToken = token
        tokenManager.saveToken(deviceId, token)
        Log.d(TAG, "Token saved: ${token.take(8)}...")
    }

    fun clearToken() {
        storedToken = null
        tokenManager.clearToken(deviceId)
        Log.d(TAG, "Token cleared")
    }

    fun getToken(): String? {
        if (storedToken == null) storedToken = tokenManager.getToken(deviceId)
        return storedToken
    }

    fun connect(ip: String, port: Int) {
        lastIp = ip; lastPort = port
        manualDisconnect.set(false); retryCount = 0
        reconnectJob?.cancel()
        doConnect(ip, port)
    }

    private fun doConnect(ip: String, port: Int) {
        if (isConnected.get()) return
        try { client?.close() } catch (_: Exception) {}
        client = null
        scope.launch(Dispatchers.Main) { _connectionState.value = ConnectionState.Connecting }
        val uri = URI("ws://$ip:$port")
        Log.d(TAG, "Connecting to $uri, has_token=${getToken() != null}")
        try {
            client = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    Log.d(TAG, "WebSocket opened")
                    isConnected.set(true); retryCount = 0
                    sendConnectRequest()
                    startPingLoop()
                }
                override fun onMessage(message: String?) {
                    if (message == null) return
                    scope.launch(Dispatchers.Main) {
                        try { handleMessage(Json.decodeFromString(WsMessage.serializer(), message)) }
                        catch (e: Exception) { Log.e(TAG, "Parse error: ${e.message}") }
                    }
                }
                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d(TAG, "Closed: code=$code, reason=$reason")
                    isConnected.set(false); pingJob?.cancel()
                    scope.launch(Dispatchers.Main) {
                        _connectionState.value = ConnectionState.Disconnected
                        _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Disconnected)
                    }
                    if (!manualDisconnect.get()) scheduleReconnect()
                }
                override fun onError(ex: Exception?) {
                    Log.e(TAG, "Error: ${ex?.message}")
                    isConnected.set(false)
                    scope.launch(Dispatchers.Main) {
                        _connectionState.value = ConnectionState.Error(ex?.message ?: "Error")
                    }
                }
            }
            client?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            _connectionState.value = ConnectionState.Error(e.message ?: "Failed")
        }
    }

    private fun scheduleReconnect() {
        if (manualDisconnect.get() || lastIp == null) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            retryCount++
            val delayMs = (2000L * (1L shl minOf(retryCount, 4))).coerceAtMost(30000L)
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $retryCount)")
            delay(delayMs)
            if (!manualDisconnect.get() && !isConnected.get()) {
                scope.launch(Dispatchers.Main) { _connectionState.value = ConnectionState.Reconnecting }
                doConnect(lastIp!!, lastPort)
            }
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isConnected.get()) {
                try { sendPing(); delay(15000) } catch (e: Exception) { break }
            }
        }
    }

    private fun handleMessage(msg: WsMessage) {
        fun extract(key: String) = msg.payload[key]?.toString()?.removeSurrounding("\"") ?: ""
        
        when (msg.type) {
            "connection_approved" -> {
                val token = extract("token")
                Log.d(TAG, "Approved! token=${token.take(8)}")
                if (token.isNotEmpty() && token != "null") setToken(token)
                isConnected.set(true)
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Connected
                    _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Connected)
                }
                _events.value = WebSocketEvent.AuthResponse(true, extract("message"))
            }
            "connection_confirmed" -> {
                Log.d(TAG, "Confirmed - authenticated")
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Connected
                    _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Connected)
                }
                _events.value = WebSocketEvent.AuthResponse(true, extract("message"))
            }
            "connection_rejected" -> {
                val message = extract("message")
                Log.d(TAG, "Rejected: $message")
                manualDisconnect.set(true)
                cleanup()
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Error(message)
                    _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Error(message))
                }
                _events.value = WebSocketEvent.AuthResponse(false, message)
            }
            "device_removed" -> {
                val message = extract("message")
                Log.d(TAG, "Removed: $message")
                storedToken = null; tokenManager.clearToken(deviceId)
                manualDisconnect.set(false)
                cleanup()
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Disconnected
                    _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Disconnected)
                }
                _events.value = WebSocketEvent.AuthResponse(false, message)
            }
            "device_blacklisted" -> {
                val message = extract("message")
                Log.d(TAG, "Blacklisted: $message")
                storedToken = null; tokenManager.clearToken(deviceId)
                manualDisconnect.set(true)
                cleanup()
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Error(message)
                    _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Error(message))
                }
                _events.value = WebSocketEvent.AuthResponse(false, message)
            }
            "device_disconnected" -> {
                val message = extract("message")
                Log.d(TAG, "Disconnected by desktop: $message")
                manualDisconnect.set(false)
                cleanup()
                scope.launch(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Disconnected
                    _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Disconnected)
                }
                _events.value = WebSocketEvent.AuthResponse(false, message)
            }
            "approval_required" -> {
                Log.d(TAG, "Waiting for approval")
                scope.launch(Dispatchers.Main) { _connectionState.value = ConnectionState.Connecting }
            }
            "auth_response" -> {
                val success = extract("success").toBoolean()
                val message = extract("message")
                Log.d(TAG, "Auth: success=$success")
                _events.value = WebSocketEvent.AuthResponse(success, message)
                if (success) extract("token").takeIf { it.isNotEmpty() && it != "null" }?.let { setToken(it) }
            }
            "type_progress" -> {
                val charsTyped = extract("chars_typed").toIntOrNull() ?: 0
                val totalChars = extract("total_chars").toIntOrNull() ?: 1
                _events.value = WebSocketEvent.TypingProgress(
                    extract("job_id"),
                    if (totalChars > 0) charsTyped.toFloat() / totalChars else 0f,
                    charsTyped, totalChars
                )
            }
            "type_complete" -> { _events.value = WebSocketEvent.TypingComplete(extract("job_id"), extract("chars_typed").toIntOrNull() ?: 0) }
            "type_error" -> { _events.value = WebSocketEvent.TypingError(extract("job_id"), extract("error")) }
            "pong" -> {}
            "error" -> {
                val code = extract("code")
                val errorMsg = extract("message")
                Log.e(TAG, "Server error: $code - $errorMsg")
                if (code == "AUTH_FAILED" || code == "AUTH_REQUIRED") {
                    _events.value = WebSocketEvent.AuthResponse(false, errorMsg)
                }
            }
        }
    }

    private fun cleanup() {
        reconnectJob?.cancel(); pingJob?.cancel()
        try { client?.close() } catch (_: Exception) {}
        client = null; isConnected.set(false)
    }

    private fun sendConnectRequest() {
        val token = getToken() ?: ""
        Log.d(TAG, "Connect request: device=$deviceId, token_len=${token.length}")
        send(WsMessage.create("connect_request", mapOf(
            "device_name" to deviceName, "device_id" to deviceId, "token" to token
        )))
    }

    fun disconnect() {
        Log.d(TAG, "Manual disconnect")
        manualDisconnect.set(true)
        cleanup()
        scope.launch(Dispatchers.Main) {
            _connectionState.value = ConnectionState.Disconnected
            _events.value = WebSocketEvent.ConnectionChanged(ConnectionState.Disconnected)
        }
    }

    fun retryConnection() {
        Log.d(TAG, "Retry: lastIp=$lastIp")
        manualDisconnect.set(false); retryCount = 0
        reconnectJob?.cancel()
        if (lastIp != null) doConnect(lastIp!!, lastPort)
    }

    fun sendText(text: String, mode: String, speed: Int, priority: String = "normal") {
        send(WsMessage("type", generateId(), System.currentTimeMillis(), buildJsonObject {
            put("text", text); put("mode", mode); put("speed", speed); put("priority", priority)
        }))
    }

    fun sendKeyPress(key: String, modifiers: List<String> = emptyList()) {
        send(WsMessage("key_press", generateId(), System.currentTimeMillis(), buildJsonObject {
            put("key", key)
            put("modifiers", kotlinx.serialization.json.JsonArray(modifiers.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }))
    }

    fun sendClipboard(text: String) { send(WsMessage("clipboard", generateId(), System.currentTimeMillis(), buildJsonObject { put("text", text) })) }
    fun sendTypingControl(action: String) { send(WsMessage("typing_control", generateId(), System.currentTimeMillis(), buildJsonObject { put("action", action) })) }
    fun clearQueue() { send(WsMessage.create("queue_clear", emptyMap())) }
    fun cancelJob(jobId: String) { send(WsMessage.create("queue_cancel", mapOf("job_id" to jobId))) }
    fun requestQueueStatus() { send(WsMessage.create("queue_status", emptyMap())) }
    fun sendPing() { send(WsMessage.create("ping", emptyMap())) }

    private fun send(msg: WsMessage) {
        try { if (isConnected.get()) client?.send(Json.encodeToString(WsMessage.serializer(), msg)) }
        catch (e: Exception) { Log.e(TAG, "Send failed: ${e.message}") }
    }

    private fun generateId() = "msg_${System.currentTimeMillis()}_${(0..999).random()}"
    fun destroy() { disconnect() }
}
