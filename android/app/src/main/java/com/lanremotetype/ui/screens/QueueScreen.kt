package com.lanremotetype.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lanremotetype.ui.components.*
import com.lanremotetype.ui.theme.*
import com.lanremotetype.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Request queue status when screen opens
    LaunchedEffect(Unit) {
        viewModel.requestQueueStatus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidBackground)
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-40).dp)
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(LiquidTeal.copy(alpha = 0.1f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Queue", color = LiquidOnSurface, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, "Back", tint = LiquidOnSurfaceSecondary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearQueue() }) {
                            Icon(Icons.Outlined.DeleteSweep, "Clear All", tint = LiquidOnSurfaceSecondary)
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
            ) {
                // Active job
                uiState.activeJob?.let { job ->
                    Text("Active Job", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    ActiveJobCard(
                        jobId = job.first,
                        charsTyped = job.second,
                        totalChars = job.third
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Queue
                Text("Queue (${uiState.queueJobs.size})", fontWeight = FontWeight.SemiBold, color = LiquidOnSurface)
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.queueJobs.isEmpty() && uiState.activeJob == null) {
                    EmptyQueueMessage()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.queueJobs) { job ->
                            QueueItemCard(
                                jobId = job.first,
                                chars = job.second,
                                priority = job.third,
                                onCancel = { viewModel.cancelJob(job.first) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveJobCard(jobId: String, charsTyped: Int, totalChars: Int) {
    val progress = if (totalChars > 0) charsTyped.toFloat() / totalChars else 0f

    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Typing...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = LiquidOnSurface)
                Text("$charsTyped / $totalChars", style = MaterialTheme.typography.bodySmall, color = LiquidPrimary)
            }
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(LiquidSurfaceHighlight)) {
                Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(LiquidPrimaryDark, LiquidPrimary))))
            }
        }
    }
}

@Composable
fun QueueItemCard(jobId: String, chars: Int, priority: String, onCancel: () -> Unit) {
    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$chars chars", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = LiquidOnSurface)
                Text(priority, style = MaterialTheme.typography.bodySmall, color = when(priority) {
                    "urgent" -> LiquidRed
                    "high" -> LiquidOrange
                    else -> LiquidOnSurfaceSecondary
                })
            }
            LiquidButton(onClick = onCancel, colors = LiquidButtonDefaults.dangerColors()) {
                Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun EmptyQueueMessage() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.background(brush = Brush.horizontalGradient(
            colors = listOf(LiquidSurfaceHighlight.copy(alpha = 0.4f), LiquidSurfaceLight.copy(alpha = 0.3f))
        ), shape = MaterialTheme.shapes.medium).padding(20.dp)) {
            Icon(Icons.Outlined.Queue, null, modifier = Modifier.size(48.dp), tint = LiquidOnSurfaceSecondary.copy(alpha = 0.5f))
        }
        Text("Queue is empty", style = MaterialTheme.typography.titleMedium, color = LiquidOnSurfaceSecondary)
        Text("Send text from the Type screen to add jobs", style = MaterialTheme.typography.bodyMedium, color = LiquidOnSurfaceSecondary.copy(alpha = 0.6f), textAlign = TextAlign.Center)
    }
}
