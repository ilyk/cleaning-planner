package com.ilyk.cleaningplanner.feature.clara.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.core.model.AvatarAppearance
import com.ilyk.cleaningplanner.feature.clara.ui.components.Avatar3DView
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraAvatar
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraFAB
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraViewModel
import com.ilyk.cleaningplanner.feature.clara.ui.components.SubtitleDisplay
import com.ilyk.cleaningplanner.feature.clara.ui.components.TextBubble
import com.ilyk.cleaningplanner.feature.clara.ui.settings.Avatar3DSettingsScreen
import com.ilyk.cleaningplanner.feature.clara.ui.settings.AvatarSettingsSheet

@Composable
fun WelcomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToTypeInfo: () -> Unit,
    onNavigateToWizard: () -> Unit,
    onNavigateToAISettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
    claraViewModel: ClaraViewModel = hiltViewModel()
) {
    val avatarPrefs by claraViewModel.avatarPrefs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startWelcome()
    }

    Scaffold(
        floatingActionButton = {
            ClaraFAB(
                avatarPrefs = avatarPrefs,
                onClick = { showSettings = true }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                if (avatarPrefs.showAvatar && uiState.currentAvatar != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        Avatar3DView(
                            glbPath = uiState.currentAvatar.glbPath,
                            avatarProvider = viewModel.avatarProvider,
                            onError = { error ->
                                // Fallback to icon avatar
                            }
                        )
                    }
                } else if (avatarPrefs.showAvatar) {
                    // Fallback to icon avatar while 3D loads
                    ClaraAvatar(
                        appearance = AvatarAppearance.fromId(avatarPrefs.appearanceId),
                        size = 120.dp
                    )
                }

                if (avatarPrefs.showAvatar && !avatarPrefs.muteVoice) {
                    SubtitleDisplay(
                        text = uiState.currentSubtitle,
                        visible = avatarPrefs.alwaysShowSubtitles || uiState.isWelcomeSpeaking,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    TextBubble(
                        text = WelcomeViewModel.WELCOME_TEXT,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "How would you like to begin?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = {
                        viewModel.onOptionSelected("lets_chat")
                        onNavigateToChat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics {
                            contentDescription = "Let's Chat - Start conversational intake"
                        }
                ) {
                    Text(
                        text = "Let's Chat",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Button(
                    onClick = {
                        viewModel.onOptionSelected("type_info")
                        onNavigateToTypeInfo()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics {
                            contentDescription = "Type My Info - Text input"
                        }
                ) {
                    Text(
                        text = "Type My Info",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Button(
                    onClick = onNavigateToWizard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .semantics {
                            contentDescription = "Use Wizard - Step-by-step forms"
                        }
                ) {
                    Text(
                        text = "Use Wizard",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (uiState.isLoadingFollowUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        if (showSettings) {
            AvatarSettingsSheet(
                currentPrefs = avatarPrefs,
                onDismiss = { showSettings = false },
                onSave = { newPrefs ->
                    claraViewModel.updateAvatarPrefs(newPrefs)
                    showSettings = false
                }
            )
        }
    }
}


