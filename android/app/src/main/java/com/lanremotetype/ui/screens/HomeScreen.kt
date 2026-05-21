package com.lanremotetype.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.graphicsLayer // <--- ADD THIS LINE
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lanremotetype.network.ConnectionState
import com.lanremotetype.ui.components.*
import com.lanremotetype.ui.theme.*
import com.lanremotetype.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToConnect: () -> Unit,
    onNavigateToType: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Show toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Show error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidBackground)
    ) {
        // Background glow effects
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LiquidPrimary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 100.dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            LiquidSecondary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidTopBar(
                    isConnected = uiState.isAuthenticated,
                    onSettings = onNavigateToSettings
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Connection Status Card - PROMINENT
                item {
                    ConnectionStatusCard(
                        state = uiState.connectionState,
                        isAuthenticated = uiState.isAuthenticated,
                        onConnect = onNavigateToConnect,
                        onDisconnect = { viewModel.disconnect() }
                    )
                }

                // Typing Progress (if active)
                if (uiState.isTyping) {
                    item {
                        LiquidTypingProgress(
                            progress = uiState.typingProgress,
                            charsTyped = uiState.charsTyped,
                            totalChars = uiState.totalChars,
                            onPause = { viewModel.controlTyping("pause") },
                            onResume = { viewModel.controlTyping("resume") },
                            onAbort = { viewModel.controlTyping("abort") }
                        )
                    }
                }

                item {
                    LiquidSectionTitle("Quick Actions")
                }

                item {
                    QuickActionsGrid(
                        viewModel = viewModel,
                        isAuthenticated = uiState.isAuthenticated,
                        onNavigateToType = onNavigateToType,
                        onNavigateToQueue = onNavigateToQueue
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidTopBar(isConnected: Boolean, onSettings: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SyncType",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LiquidOnSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Connection indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) LiquidGreen else LiquidRed)
                )
            }
        },
        actions = {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    "Settings",
                    tint = LiquidOnSurfaceSecondary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = LiquidOnSurface
        )
    )
}

@Composable
fun ConnectionStatusCard(
    state: ConnectionState,
    isAuthenticated: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = state is ConnectionState.Connected && isAuthenticated
    val isConnecting = state is ConnectionState.Connecting || state is ConnectionState.Reconnecting
    val isError = state is ConnectionState.Error

    val statusColor = when {
        isConnected -> LiquidGreen
        isConnecting -> LiquidYellow
        isError -> LiquidRed
        else -> LiquidOnSurfaceSecondary.copy(alpha = 0.4f)
    }

    val statusTitle = when {
        isConnected -> "System Linked"
        isConnecting -> "Establishing Link..."
        isError -> "Link Interrupted"
        else -> "System Offline"
    }

    LiquidCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (!isConnected && !isConnecting) onConnect else null
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: Animated Visual Indicator
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnected) {
                        // Pulsing circles for active connection
                        StatusPulse(color = LiquidGreen)
                    } else if (isConnecting) {
                        // Rotating border for connecting
                        LoadingOrbit(color = LiquidYellow)
                    }

                    // Central Icon
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f))
                            .border(1.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isConnected -> Icons.Filled.Wifi
                                isConnecting -> Icons.Filled.Sync
                                isError -> Icons.Filled.PriorityHigh
                                else -> Icons.Filled.CloudOff
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right Side: Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LiquidOnSurface
                    )
                    Text(
                        text = if (isConnected) "Secure tunnel active" else "Ready for input",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiquidOnSurfaceSecondary
                    )
                }
                
                // Active/Inactive Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = if (isConnected) "LIVE" else "IDLE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Action Button
            LiquidButton(
                onClick = if (isConnected) onDisconnect else onConnect,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isConnected) LiquidButtonDefaults.dangerColors() else LiquidButtonDefaults.primaryColors(),
                enabled = !isConnecting
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Outlined.LinkOff else Icons.Outlined.CastConnected,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "Terminate Link" else "Initialize Connection",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun StatusPulse(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .background(color, CircleShape)
    )
}

@Composable
fun LoadingOrbit(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(54.dp)
            .graphicsLayer(rotationZ = rotation)
            .border(2.dp, Brush.sweepGradient(listOf(Color.Transparent, color)), CircleShape)
    )
}

// Extension to help with alpha in older Compose versions if needed
fun Color.withAlpha(alpha: Float): Color = this.copy(alpha = alpha)

@Composable
fun LiquidTypingProgress(
    progress: Float,
    charsTyped: Int,
    totalChars: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAbort: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300)
    )

    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Typing...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LiquidOnSurface
                )
                Text(
                    "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LiquidPrimary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LiquidSurfaceHighlight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(LiquidPrimaryDark, LiquidPrimary)
                            )
                        )
                )
            }

            Text(
                "$charsTyped / $totalChars chars",
                style = MaterialTheme.typography.bodySmall,
                color = LiquidOnSurfaceSecondary
            )

            // Compact buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiquidYellow),
                    border = BorderStroke(1.dp, LiquidYellow.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.Pause, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pause", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiquidGreen),
                    border = BorderStroke(1.dp, LiquidGreen.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Resume", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onAbort,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiquidRed),
                    border = BorderStroke(1.dp, LiquidRed.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Abort", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun QuickActionsGrid(
    viewModel: MainViewModel,
    isAuthenticated: Boolean,
    onNavigateToType: () -> Unit,
    onNavigateToQueue: () -> Unit
) {
    val context = LocalContext.current
    val actions = listOf(
        Triple(Icons.Outlined.Keyboard, "Type Text", onNavigateToType),
        Triple(Icons.Outlined.Queue, "Queue", onNavigateToQueue),
        Triple(Icons.Outlined.ContentPaste, "Paste", { 
            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clipText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clipText.isNotEmpty()) {
                viewModel.sendClipboard(clipText)
            } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }),
        Triple(Icons.Outlined.KeyboardReturn, "Enter", { viewModel.sendKeyPress("enter") }),
        Triple(Icons.Outlined.KeyboardTab, "Tab", { viewModel.sendKeyPress("tab") }),
        Triple(Icons.Outlined.Close, "Esc", { viewModel.sendKeyPress("escape") }),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (icon, label, action) ->
                    ActionCard(
                        icon = icon,
                        label = label,
                        enabled = isAuthenticated,
                        onClick = action,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidCard(
        modifier = modifier,
        onClick = if (enabled) onClick else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                label,
                modifier = Modifier.size(28.dp),
                tint = if (enabled) LiquidPrimary else LiquidOnSurfaceSecondary.copy(alpha = 0.4f)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) LiquidOnSurface else LiquidOnSurfaceSecondary.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}
