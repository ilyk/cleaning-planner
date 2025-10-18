package com.ilyk.cleaningplanner.feature.clara.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

data class DiagnosticsUiState(
    val fps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val jankPercentage: Float = 0f,
    val textureMemoryMb: Float = 0f,
    val glbSizeMb: Float = 0f,
    val triangleCount: Int = 0,
    val isRunningProbe: Boolean = false,
    val probeProgress: Float = 0f,
    val probeResult: ProbeResult? = null
)

sealed class ProbeResult {
    data class Pass(val avgFps: Float) : ProbeResult()
    data class Fail(val avgFps: Float, val reason: String) : ProbeResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Real-time Metrics",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            MetricCard(
                label = "FPS",
                value = "%.1f".format(uiState.fps),
                target = "≥ 60",
                isPassing = uiState.fps >= 60f
            )

            Spacer(modifier = Modifier.height(12.dp))

            MetricCard(
                label = "Frame Time",
                value = "%.2f ms".format(uiState.frameTimeMs),
                target = "≤ 16.67 ms",
                isPassing = uiState.frameTimeMs <= 16.67f
            )

            Spacer(modifier = Modifier.height(12.dp))

            MetricCard(
                label = "Jank",
                value = "%.1f%%".format(uiState.jankPercentage),
                target = "< 5%",
                isPassing = uiState.jankPercentage < 5f
            )

            Spacer(modifier = Modifier.height(12.dp))

            MetricCard(
                label = "Texture Memory",
                value = "%.1f MB".format(uiState.textureMemoryMb),
                target = "< 50 MB",
                isPassing = uiState.textureMemoryMb < 50f
            )

            Spacer(modifier = Modifier.height(12.dp))

            MetricCard(
                label = "GLB File Size",
                value = "%.2f MB".format(uiState.glbSizeMb),
                target = "≤ 10 MB",
                isPassing = uiState.glbSizeMb <= 10f
            )

            Spacer(modifier = Modifier.height(12.dp))

            MetricCard(
                label = "Triangle Count",
                value = uiState.triangleCount.toString(),
                target = "≤ 80,000",
                isPassing = uiState.triangleCount <= 80000
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "10-Second Performance Probe",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.isRunningProbe) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Running probe...")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.probeProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(uiState.probeProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.runProbe() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run Probe")
                }

                uiState.probeResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (result) {
                                is ProbeResult.Pass -> MaterialTheme.colorScheme.primaryContainer
                                is ProbeResult.Fail -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = when (result) {
                                    is ProbeResult.Pass -> "✓ PASS"
                                    is ProbeResult.Fail -> "✗ FAIL"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = when (result) {
                                    is ProbeResult.Pass -> MaterialTheme.colorScheme.onPrimaryContainer
                                    is ProbeResult.Fail -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            when (result) {
                                is ProbeResult.Pass -> {
                                    Text(
                                        text = "Average FPS: %.1f".format(result.avgFps),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                is ProbeResult.Fail -> {
                                    Text(
                                        text = "Average FPS: %.1f".format(result.avgFps),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Reason: ${result.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    target: String,
    isPassing: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPassing) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "Target: $target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (isPassing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

