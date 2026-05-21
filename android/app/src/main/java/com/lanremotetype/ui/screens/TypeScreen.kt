package com.lanremotetype.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.lanremotetype.model.TypingMode
import com.lanremotetype.model.Priority
import com.lanremotetype.ui.components.*
import com.lanremotetype.ui.theme.*
import com.lanremotetype.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(TypingMode.HUMAN) }
    var selectedPriority by remember { mutableStateOf(Priority.NORMAL) }
    var speed by remember { mutableIntStateOf(getDefaultSpeed(TypingMode.HUMAN)) }
    val charCount = text.length

    // Show toast
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidBackground)
    ) {
        // Background glow
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(LiquidSecondary.copy(alpha = 0.1f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Type Text", color = LiquidOnSurface, fontWeight = FontWeight.Bold)
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
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Text input card
                LiquidCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Content", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                            Text("$charCount chars", style = MaterialTheme.typography.bodySmall, color = LiquidOnSurfaceSecondary)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Text field - no limit, scrollable
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 400.dp),
                            placeholder = {
                                Text("Type or paste your text here...\nNo limit!", color = LiquidOnSurfaceSecondary.copy(alpha = 0.5f))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LiquidOnSurface,
                                unfocusedTextColor = LiquidOnSurface,
                                focusedBorderColor = LiquidPrimary,
                                unfocusedBorderColor = LiquidBorder,
                                cursorColor = LiquidPrimary,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }

                // Typing Mode
                Text("Typing Mode", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TypingMode.entries.forEach { mode ->
                        LiquidChip(
                            selected = mode == selectedMode,
                            onClick = { 
                                selectedMode = mode
                                speed = getDefaultSpeed(mode)
                            },
                            label = mode.label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Mode description
                Text(
                    selectedMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LiquidOnSurfaceSecondary
                )

                // Speed slider
                LiquidCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speed", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                            Text(
                                "$speed • ${getWpmLabel(selectedMode, speed)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LiquidPrimary
                            )
                        }
                        Slider(
                            value = speed.toFloat(),
                            onValueChange = { speed = it.toInt() },
                            valueRange = 1f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = LiquidPrimary,
                                activeTrackColor = LiquidPrimary,
                                inactiveTrackColor = LiquidSurfaceHighlight
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Slow", style = MaterialTheme.typography.bodySmall, color = LiquidOnSurfaceSecondary)
                            Text("Fast", style = MaterialTheme.typography.bodySmall, color = LiquidOnSurfaceSecondary)
                        }
                    }
                }

                // Priority
                Text("Priority", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Priority.entries.forEach { priority ->
                        LiquidChip(
                            selected = priority == selectedPriority,
                            onClick = { selectedPriority = priority },
                            label = priority.label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Send button
                LiquidButton(
                    onClick = {
                        if (text.isNotEmpty()) {
                            viewModel.sendText(text, selectedMode.value, speed, selectedPriority.value)
                            Toast.makeText(context, "Sending ${charCount} chars...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = text.isNotEmpty()
                ) {
                    Icon(Icons.Outlined.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send to Type ($charCount chars)")
                }

                // Quick Keys section
                Text("Quick Keys", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)

                // Key buttons grid
                val keys = listOf(
                    Triple("Enter", "enter", emptyList<String>()),
                    Triple("Tab", "tab", emptyList()),
                    Triple("Space", "space", emptyList()),
                    Triple("Backspace", "backspace", emptyList()),
                    Triple("Delete", "delete", emptyList()),
                    Triple("Escape", "escape", emptyList()),
                )
                
                keys.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { (label, key, mods) ->
                            LiquidButton(
                                onClick = { viewModel.sendKeyPress(key, mods) },
                                modifier = Modifier.weight(1f),
                                colors = LiquidButtonDefaults.neutralColors()
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Modifier combos
                Text("Modifiers", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)

                val combos = listOf(
                    Triple("Ctrl+C", "c", listOf("ctrl")),
                    Triple("Ctrl+V", "v", listOf("ctrl")),
                    Triple("Ctrl+Z", "z", listOf("ctrl")),
                    Triple("Ctrl+A", "a", listOf("ctrl")),
                    Triple("Ctrl+X", "x", listOf("ctrl")),
                    Triple("Ctrl+S", "s", listOf("ctrl")),
                    Triple("Alt+Tab", "tab", listOf("alt")),
                    Triple("Alt+F4", "f4", listOf("alt")),
                    Triple("Win+D", "d", listOf("meta")),
                    Triple("Win+L", "l", listOf("meta")),
                    Triple("Ctrl+Shift", "shift", listOf("ctrl", "shift")),
                    Triple("Ctrl+Alt+Del", "delete", listOf("ctrl", "alt")),
                )

                combos.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { (label, key, mods) ->
                            LiquidButton(
                                onClick = { viewModel.sendKeyPress(key, mods) },
                                modifier = Modifier.weight(1f),
                                colors = LiquidButtonDefaults.secondaryColors()
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Arrow keys
                Text("Arrow Keys", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LiquidButton(
                        onClick = { viewModel.sendKeyPress("up") },
                        modifier = Modifier.width(80.dp),
                        colors = LiquidButtonDefaults.neutralColors()
                    ) { Text("↑") }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LiquidButton(
                            onClick = { viewModel.sendKeyPress("left") },
                            modifier = Modifier.width(80.dp),
                            colors = LiquidButtonDefaults.neutralColors()
                        ) { Text("←") }
                        LiquidButton(
                            onClick = { viewModel.sendKeyPress("down") },
                            modifier = Modifier.width(80.dp),
                            colors = LiquidButtonDefaults.neutralColors()
                        ) { Text("↓") }
                        LiquidButton(
                            onClick = { viewModel.sendKeyPress("right") },
                            modifier = Modifier.width(80.dp),
                            colors = LiquidButtonDefaults.neutralColors()
                        ) { Text("→") }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

fun getDefaultSpeed(mode: TypingMode): Int {
    return when (mode) {
        TypingMode.INSTANT -> 100
        TypingMode.FAST -> 70
        TypingMode.NORMAL -> 50
        TypingMode.HUMAN -> 40
    }
}

fun getWpmLabel(mode: TypingMode, speed: Int): String {
    val s = speed.coerceIn(1, 100)
    return when (mode) {
        TypingMode.INSTANT -> "Instant"
        TypingMode.FAST -> "~${120 + (s * 80 / 100)} WPM"
        TypingMode.NORMAL -> "~${60 + (s * 90 / 100)} WPM"
        TypingMode.HUMAN -> "~${40 + (s * 60 / 100)} WPM"
    }
}
