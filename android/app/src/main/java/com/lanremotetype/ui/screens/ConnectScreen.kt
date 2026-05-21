package com.lanremotetype.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lanremotetype.model.Device
import com.lanremotetype.network.ConnectionState
import com.lanremotetype.ui.components.*
import com.lanremotetype.ui.theme.*
import com.lanremotetype.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("9876") }
    var isDiscovering by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    // Show toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Auto-navigate back when connected
    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            Toast.makeText(context, "Connected!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    // Stop discovering animation when devices found
    LaunchedEffect(uiState.discoveredDevices) {
        if (uiState.discoveredDevices.isNotEmpty()) {
            isDiscovering = false
            hasSearched = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidBackground)
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopStart)
                .offset(x = (-100).dp, y = (-50).dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(LiquidPrimary.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Connect", color = LiquidOnSurface, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, "Back", tint = LiquidOnSurfaceSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Connection Status Banner
                item {
                    ConnectionStatusBanner(
                        state = uiState.connectionState,
                        isAuthenticated = uiState.isAuthenticated,
                        isWaiting = uiState.waitingForApproval,
                        errorMessage = uiState.errorMessage
                    )
                }

                // Discover Devices
                item {
                    LiquidCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!isDiscovering) {
                                isDiscovering = true
                                hasSearched = false
                                viewModel.startDiscovery()
                                // Auto-stop after 5 seconds
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(LiquidPrimaryDark, LiquidPrimary)
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    if (isDiscovering) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "rotate")
                                        val rotation by infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ), label = "rotate"
                                        )
                                        Icon(
                                            Icons.Outlined.Sync,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .rotate(rotation)
                                        )
                                    } else {
                                        Icon(Icons.Outlined.Search, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column {
                                    Text(
                                        if (isDiscovering) "Searching..." else "Discover Devices",
                                        fontWeight = FontWeight.SemiBold,
                                        color = LiquidOnSurface
                                    )
                                    Text(
                                        if (isDiscovering) "Scanning local network..." else "Scan local network",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiquidOnSurfaceSecondary
                                    )
                                }
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = LiquidOnSurfaceSecondary)
                        }
                    }
                }

                // Show discovered devices or "not found" message
                if (isDiscovering && uiState.discoveredDevices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = LiquidPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Looking for devices...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LiquidOnSurfaceSecondary
                                )
                            }
                        }
                    }
                }

                if (hasSearched && uiState.discoveredDevices.isEmpty() && !isDiscovering) {
                    item {
                        LiquidCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = LiquidYellow.copy(alpha = 0.1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Outlined.Warning, null, tint = LiquidYellow, modifier = Modifier.size(24.dp))
                                Column {
                                    Text("No devices found", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                                    Text(
                                        "Make sure desktop app is running and both devices are on same WiFi",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LiquidOnSurfaceSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.discoveredDevices.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Available Devices", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                            Text(
                                "${uiState.discoveredDevices.size} found",
                                style = MaterialTheme.typography.bodySmall,
                                color = LiquidPrimary
                            )
                        }
                    }
                    items(uiState.discoveredDevices) { device ->
                        DeviceCard(
                            device = device,
                            onConnect = {
                                manualIp = device.ipAddress
                                manualPort = device.wsPort.toString()
                                viewModel.connectToDevice(device.ipAddress, device.wsPort)
                            }
                        )
                    }
                }

                // Divider
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("OR", style = MaterialTheme.typography.bodySmall, color = LiquidOnSurfaceSecondary)
                    }
                }

                // Manual Connection
                item {
                    Text("Manual Connection", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                }

                item {
                    LiquidTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = "IP Address",
                        placeholder = "192.168.1.100 or 192.168.1.100:9876"
                    )
                }

                item {
                    LiquidTextField(
                        value = manualPort,
                        onValueChange = { manualPort = it },
                        label = "Port",
                        placeholder = "9876"
                    )
                }

                item {
                    LiquidButton(
                        onClick = {
                            if (manualIp.isNotEmpty()) {
                                // Parse IP:Port if user enters it together
                                val ip: String
                                val port: Int
                                if (manualIp.contains(":")) {
                                    val parts = manualIp.split(":")
                                    ip = parts[0]
                                    port = parts.getOrNull(1)?.toIntOrNull() ?: 9876
                                } else {
                                    ip = manualIp
                                    port = manualPort.toIntOrNull() ?: 9876
                                }
                                viewModel.connectToDevice(ip, port)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualIp.isNotEmpty() && uiState.connectionState !is ConnectionState.Connecting
                    ) {
                        if (uiState.connectionState is ConnectionState.Connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting...")
                        } else {
                            Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect")
                        }
                    }
                }

                // Retry button
                if (uiState.lastConnectIp != null && !uiState.isAuthenticated && uiState.connectionState !is ConnectionState.Connecting) {
                    item {
                        LiquidButton(
                            onClick = {
                                viewModel.connectToDevice(
                                    uiState.lastConnectIp!!,
                                    uiState.lastConnectPort
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = LiquidButtonDefaults.secondaryColors()
                        ) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retry: ${uiState.lastConnectIp}")
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun ConnectionStatusBanner(
    state: ConnectionState,
    isAuthenticated: Boolean,
    isWaiting: Boolean,
    errorMessage: String? = null
) {
    val isConnected = state is ConnectionState.Connected && isAuthenticated
    val isConnecting = state is ConnectionState.Connecting || isWaiting
    val isRejected = errorMessage?.contains("reject", ignoreCase = true) == true

    val (bgColor, text, icon) = when {
        isConnected -> Triple(LiquidGreen.copy(alpha = 0.15f), "Connected to Desktop", Icons.Filled.CheckCircle)
        isRejected -> Triple(LiquidRed.copy(alpha = 0.15f), "Connection Rejected", Icons.Filled.Close)
        state is ConnectionState.Reconnecting -> Triple(LiquidYellow.copy(alpha = 0.15f), "Reconnecting...", Icons.Filled.Sync)
        isConnecting -> Triple(LiquidYellow.copy(alpha = 0.15f), if (isWaiting) "Waiting for Approval..." else "Connecting...", Icons.Filled.Sync)
        state is ConnectionState.Error -> Triple(LiquidRed.copy(alpha = 0.15f), "Connection Error", Icons.Filled.Error)
        else -> Triple(LiquidSurfaceHighlight, "Not Connected", Icons.Filled.Close)
    }

    LiquidCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = bgColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = LiquidYellow,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    icon,
                    null,
                    tint = if (isConnected) LiquidGreen else if (isConnecting) LiquidYellow else LiquidRed,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = LiquidOnSurface
                )
                if (isWaiting) {
                    Text(
                        "Approve the connection on your desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiquidOnSurfaceSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: Device, onConnect: () -> Unit) {
    LiquidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onConnect
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(LiquidSecondary.copy(alpha = 0.6f), LiquidSecondary)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Icon(Icons.Outlined.Computer, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(device.deviceName, fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                    Text(
                        "${device.ipAddress}:${device.wsPort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiquidOnSurfaceSecondary
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = LiquidOnSurfaceSecondary)
        }
    }
}
