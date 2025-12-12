package com.ilyk.cleaningplanner.ui.voice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Clara voice assistant screen
 * Implements states: idle → listening → thinking → speaking → ready
 */
@Composable
fun ClaraVoiceScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: ClaraVoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Clara Voice Assistant",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Main voice interface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Voice status
                Text(
                    text = when (uiState.state) {
                        ClaraVoiceState.IDLE -> "Tap to start talking"
                        ClaraVoiceState.LISTENING -> "Listening..."
                        ClaraVoiceState.THINKING -> "Thinking..."
                        ClaraVoiceState.SPEAKING -> "Speaking..."
                        ClaraVoiceState.READY -> "Ready"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                // Voice button
                Button(
                    onClick = {
                        when (uiState.state) {
                            ClaraVoiceState.IDLE, ClaraVoiceState.READY -> viewModel.startListening()
                            ClaraVoiceState.LISTENING -> viewModel.stopListening()
                            ClaraVoiceState.THINKING, ClaraVoiceState.SPEAKING -> {
                                // Do nothing during processing
                            }
                        }
                    },
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    enabled = uiState.state == ClaraVoiceState.IDLE || 
                             uiState.state == ClaraVoiceState.LISTENING || 
                             uiState.state == ClaraVoiceState.READY,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (uiState.state) {
                            ClaraVoiceState.LISTENING -> MaterialTheme.colorScheme.primary
                            ClaraVoiceState.THINKING -> MaterialTheme.colorScheme.secondary
                            ClaraVoiceState.SPEAKING -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.state == ClaraVoiceState.LISTENING) {
                            Icons.Default.MicOff
                        } else {
                            Icons.Default.Mic
                        },
                        contentDescription = "Voice",
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                // Live captions
                if (uiState.transcript.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = uiState.transcript,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                // Error message
                if (uiState.error != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

enum class ClaraVoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    READY
}

data class ClaraVoiceUiState(
    val state: ClaraVoiceState = ClaraVoiceState.IDLE,
    val transcript: String = "",
    val error: String? = null
)
