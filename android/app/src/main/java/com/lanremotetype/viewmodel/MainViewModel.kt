package com.lanremotetype.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanremotetype.network.ConnectionState
import com.lanremotetype.network.LanDiscovery
import com.lanremotetype.network.TokenManager
import com.lanremotetype.network.WebSocketClientManager
import com.lanremotetype.network.WebSocketEvent
import com.lanremotetype.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueJob(
    val jobId: String,
    val chars: Int,
    val priority: String,
    val status: String = "queued"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainVM"
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    val tokenManager = TokenManager(application)
    val wsClient = WebSocketClientManager(viewModelScope, tokenManager, application)
    val discovery = LanDiscovery(application)
    private var pingJob: Job? = null

    // Track jobs locally
    private val _localJobs = mutableListOf<QueueJob>()

    init {
        observeConnectionState()
        observeEvents()
        observeDiscoveredDevices()
        tokenManager.getLastConnection()?.let {
            _uiState.update { s -> s.copy(lastConnectIp = it.ip, lastConnectPort = it.port) }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            wsClient.connectionState.collectLatest { state ->
                Log.d(TAG, "State: $state")
                _uiState.update { it.copy(connectionState = state) }
                when (state) {
                    is ConnectionState.Connected -> {
                        startPingLoop()
                        _uiState.update { it.copy(isAuthenticated = true, waitingForApproval = false) }
                    }
                    is ConnectionState.Disconnected -> {
                        pingJob?.cancel()
                        if (!wsClient.hasStoredToken()) {
                            _uiState.update { it.copy(isAuthenticated = false, waitingForApproval = false) }
                        }
                    }
                    is ConnectionState.Error -> {
                        pingJob?.cancel()
                        _uiState.update { it.copy(waitingForApproval = false) }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            wsClient.events.collectLatest { event ->
                when (event) {
                    is WebSocketEvent.AuthResponse -> {
                        if (event.success) {
                            _uiState.update { it.copy(isAuthenticated = true, waitingForApproval = false, errorMessage = null) }
                            showToast("Connected!")
                        } else {
                            if (event.message.contains("Invalid token", ignoreCase = true)) {
                                wsClient.clearToken()
                            }
                            _uiState.update { it.copy(isAuthenticated = false, waitingForApproval = false, errorMessage = event.message) }
                            showToast(event.message)
                        }
                    }
                    is WebSocketEvent.TypingProgress -> {
                        _uiState.update { it.copy(isTyping = true, typingProgress = event.progress, charsTyped = event.charsTyped, totalChars = event.totalChars) }
                        // Update active job
                        _uiState.update { it.copy(activeJob = Triple(event.jobId, event.charsTyped, event.totalChars)) }
                    }
                    is WebSocketEvent.TypingComplete -> {
                        _uiState.update { it.copy(typingProgress = 1f, charsTyped = event.charsTyped, isTyping = false, activeJob = null) }
                        // Move job to completed
                        _localJobs.find { it.jobId == event.jobId }?.let { job ->
                            _localJobs.remove(job)
                            _localJobs.add(job.copy(status = "complete"))
                        }
                        updateQueueState()
                        showToast("Done! ${event.charsTyped} chars")
                    }
                    is WebSocketEvent.TypingError -> {
                        _uiState.update { it.copy(isTyping = false, activeJob = null) }
                        _localJobs.find { it.jobId == event.jobId }?.let { job ->
                            _localJobs.remove(job)
                            _localJobs.add(job.copy(status = "error"))
                        }
                        updateQueueState()
                    }
                    is WebSocketEvent.ConnectionChanged -> {
                        when (event.state) {
                            is ConnectionState.Disconnected -> {
                                if (!wsClient.hasStoredToken()) {
                                    _uiState.update { it.copy(isAuthenticated = false) }
                                }
                            }
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun observeDiscoveredDevices() {
        viewModelScope.launch {
            discovery.discoveredDevices.collectLatest { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }
            }
        }
    }

    fun connectToDevice(ip: String, port: Int = Constants.DEFAULT_WS_PORT) {
        tokenManager.saveLastConnection(ip, port, "Desktop", "")
        _uiState.update { it.copy(lastConnectIp = ip, lastConnectPort = port, waitingForApproval = !wsClient.hasStoredToken()) }
        wsClient.connect(ip, port)
    }

    fun disconnect() {
        wsClient.disconnect()
        _uiState.update { it.copy(isAuthenticated = false, waitingForApproval = false) }
    }

    fun startDiscovery() {
        _uiState.update { it.copy(discoveredDevices = emptyList()) }
        discovery.clearDiscovered()
        discovery.startDiscovery()
    }

    fun sendText(text: String, mode: String, speed: Int, priority: String = "normal") {
        if (text.isBlank()) return
        val jobId = "job_${System.currentTimeMillis()}"
        _localJobs.add(QueueJob(jobId, text.length, priority, "queued"))
        updateQueueState()
        wsClient.sendText(text, mode, speed, priority)
        _uiState.update { it.copy(isTyping = true, typingProgress = 0f) }
    }

    fun sendKeyPress(key: String, modifiers: List<String> = emptyList()) = wsClient.sendKeyPress(key, modifiers)
    fun sendClipboard(text: String) = wsClient.sendClipboard(text)
    fun controlTyping(action: String) {
        wsClient.sendTypingControl(action)
        if (action == "abort") _uiState.update { it.copy(isTyping = false) }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun clearToast() { _uiState.update { it.copy(toastMessage = null) } }
    fun clearQueue() {
        _localJobs.clear()
        updateQueueState()
        wsClient.clearQueue()
    }
    fun cancelJob(jobId: String) {
        _localJobs.removeAll { it.jobId == jobId }
        updateQueueState()
        wsClient.cancelJob(jobId)
    }
    fun requestQueueStatus() {
        // Use local state
        updateQueueState()
    }
    fun retryConnection() {
        val ip = _uiState.value.lastConnectIp
        if (ip != null) connectToDevice(ip, _uiState.value.lastConnectPort)
    }

    private fun updateQueueState() {
        val active = _localJobs.find { it.status == "typing" || it.status == "queued" }
        val pending = _localJobs.filter { it.status == "queued" }.map {
            Triple(it.jobId, it.chars, it.priority)
        }
        _uiState.update { it.copy(
            activeJob = active?.let { Triple(it.jobId, 0, it.chars) },
            queueJobs = pending
        ) }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (true) { wsClient.sendPing(); delay(Constants.PING_INTERVAL_MS) }
        }
    }

    private fun showToast(msg: String) { _uiState.update { it.copy(toastMessage = msg) } }

    override fun onCleared() {
        super.onCleared()
        wsClient.destroy()
        pingJob?.cancel()
    }
}

data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isAuthenticated: Boolean = false,
    val discoveredDevices: List<com.lanremotetype.model.Device> = emptyList(),
    val isTyping: Boolean = false,
    val typingProgress: Float = 0f,
    val charsTyped: Int = 0,
    val totalChars: Int = 0,
    val waitingForApproval: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val lastConnectIp: String? = null,
    val lastConnectPort: Int = Constants.DEFAULT_WS_PORT,
    val activeJob: Triple<String, Int, Int>? = null,
    val queueJobs: List<Triple<String, Int, String>> = emptyList()
)
