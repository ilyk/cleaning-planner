package com.ilyk.cleaningplanner.feature.clara.ui.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.feature.clara.R
import com.ilyk.cleaningplanner.core.model.AvatarAppearance
import com.ilyk.cleaningplanner.feature.clara.ui.components.Avatar3DView
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraAvatar
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraFAB
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraViewModel
import com.ilyk.cleaningplanner.feature.clara.ui.components.TextBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: (String) -> Unit, // Navigate to dashboard with conversation data
    modifier: Modifier = Modifier,
    viewModel: VoiceChatViewModel = hiltViewModel(),
    claraViewModel: ClaraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val legacyAvatarPrefs by claraViewModel.avatarPrefs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showPermissionDenied by remember { mutableStateOf(false) }
    
    // Handle session completion - navigate to dashboard
    LaunchedEffect(uiState.isSessionComplete) {
        if (uiState.isSessionComplete) {
            // Navigate to dashboard with conversation transcript
            onNavigateToDashboard(uiState.conversationTranscript)
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleListening()
        } else {
            showPermissionDenied = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ClaraFAB(
                avatarPrefs = legacyAvatarPrefs,
                onClick = { /* Show settings */ }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Avatar at top
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // 3D Avatar
                val avatar = uiState.currentAvatar
                if (avatar != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        Avatar3DView(
                            glbPath = avatar.glbPath,
                            avatarProvider = viewModel.avatarProvider,
                            onLoaded = {},
                            onError = {}
                        )
                    }
                } else {
                    ClaraAvatar(
                        appearance = AvatarAppearance.fromId(legacyAvatarPrefs.appearanceId),
                        size = 120.dp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Streaming transcript
                if (uiState.claraTranscript.isNotEmpty()) {
                    TextBubble(
                        text = uiState.claraTranscript,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                if (uiState.userTranscript.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = uiState.userTranscript,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            // Large continuous listening button at bottom
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status text
                val statusText = when {
                    uiState.isUserSpeaking -> stringResource(R.string.you_speaking)
                    uiState.isClaraSpeaking -> stringResource(R.string.clara_responding)
                    uiState.isListening -> stringResource(R.string.waiting_to_speak)
                    else -> stringResource(R.string.tap_to_start_conversation)
                }
                
                val statusColor = when {
                    uiState.isUserSpeaking -> MaterialTheme.colorScheme.tertiary
                    uiState.isClaraSpeaking -> MaterialTheme.colorScheme.primary
                    uiState.isListening -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Pulsing indicator when user speaks
                if (uiState.isUserSpeaking) {
                    Text(
                        text = "🎤 ●●●",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                FloatingActionButton(
                    onClick = {
                        // Check permission first
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        
                        if (hasPermission) {
                            viewModel.toggleListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(80.dp),
                    containerColor = when {
                        uiState.isUserSpeaking -> MaterialTheme.colorScheme.tertiary
                        uiState.isListening -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                ) {
                    Icon(
                        if (uiState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (uiState.isListening) "Stop conversation" else "Start conversation",
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (uiState.isListening) 
                        stringResource(R.string.tap_to_end) 
                    else 
                        stringResource(R.string.tap_to_start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        
        // Permission denied dialog
        if (showPermissionDenied) {
            AlertDialog(
                onDismissRequest = { showPermissionDenied = false },
                title = { Text(stringResource(R.string.mic_permission_required)) },
                text = { Text(stringResource(R.string.mic_permission_message)) },
                confirmButton = {
                    TextButton(onClick = { showPermissionDenied = false }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}

