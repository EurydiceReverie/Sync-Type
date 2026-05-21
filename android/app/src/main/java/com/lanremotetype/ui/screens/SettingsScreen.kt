package com.lanremotetype.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import android.content.Intent
import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanremotetype.ui.components.*
import com.lanremotetype.ui.theme.*
import com.lanremotetype.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToConnect: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidBackground)
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(LiquidPrimary.copy(alpha = 0.1f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Settings", color = LiquidOnSurface, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, "Back", tint = LiquidOnSurfaceSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SettingsSection("Device") {
                    InfoRow("Device Name", android.os.Build.MODEL)
                    InfoRow("Device ID", viewModel.wsClient.getDeviceId())
                    InfoRow("Manufacturer", android.os.Build.MANUFACTURER)

                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.isAuthenticated) {
                        LiquidButton(
                            onClick = { viewModel.disconnect() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = LiquidButtonDefaults.dangerColors()
                        ) {
                            Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Disconnect")
                        }
                    } else {
                        LiquidButton(
                            onClick = onNavigateToConnect,
                            modifier = Modifier.fillMaxWidth(),
                            colors = LiquidButtonDefaults.primaryColors()
                        ) {
                            Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect to Desktop")
                        }
                    }
                }

                SettingsSection("About") {
                    InfoRow("App", "SyncType")
                    InfoRow("Version", "1.0.0")
                    InfoRow("Protocol", "WebSocket")
                    InfoRow("Platform", getAndroidVersion())
                    InfoRow("Device", android.os.Build.MODEL)
                }

                SettingsSection("Help") {
                    Text(
                        "Make sure both devices are on the same Wi-Fi network. " +
                        "Use the Connect screen to discover or manually connect to the desktop app. " +
                        "Approve the connection on your desktop when prompted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiquidOnSurfaceSecondary
                    )
                }

                SettingsSection("Data") {
                    var showClearDialog by remember { mutableStateOf(false) }

                    LiquidButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = LiquidButtonDefaults.dangerColors()
                    ) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Data")
                    }
                    Text(
                        "Removes all saved tokens, connection history, and device data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiquidOnSurfaceSecondary
                    )

                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text("Clear All Data?") },
                            text = { Text("This will remove all trusted devices, blocked devices, and settings. App will restart.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showClearDialog = false
                                        viewModel.tokenManager.clearAll()
                                        viewModel.disconnect()
                                        Toast.makeText(context, "All data cleared. Restarting...", Toast.LENGTH_SHORT).show()
                                        // Restart app
                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        (context as? Activity)?.finish()
                                    }
                                ) {
                                    Text("Clear & Restart", color = LiquidRed)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { 
                                    showClearDialog = false
                                    Toast.makeText(context, "Cancelled", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LiquidOnSurfaceSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = LiquidOnSurface)
    }
}

fun getAndroidVersion(): String {
    return when (android.os.Build.VERSION.SDK_INT) {
        21, 22 -> "Android 5"
        23 -> "Android 6"
        24, 25 -> "Android 7"
        26, 27 -> "Android 8"
        28 -> "Android 9"
        29 -> "Android 10"
        30 -> "Android 11"
        31 -> "Android 12"
        32 -> "Android 12L"
        33 -> "Android 13"
        34 -> "Android 14"
        35 -> "Android 15"
        36 -> "Android 16"
        37 -> "Android 17"
        38 -> "Android 18"
        else -> "Android ${android.os.Build.VERSION.SDK_INT}"
    }
}
