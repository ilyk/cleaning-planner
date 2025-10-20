package com.ilyk.cleaningplanner.feature.clara.ui.welcome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.feature.clara.R
import com.ilyk.cleaningplanner.feature.clara.util.LocaleManager
import com.ilyk.cleaningplanner.feature.clara.service.TTSState
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
    val legacyAvatarPrefs by claraViewModel.avatarPrefs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    val currentLanguage by viewModel.languageCode.collectAsState(initial = "en")
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Start welcome on first load (language comes from datastore)
        viewModel.startWelcome(currentLanguage)
    }

    Scaffold(
        floatingActionButton = {
            ClaraFAB(
                avatarPrefs = legacyAvatarPrefs,
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
                Spacer(modifier = Modifier.height(16.dp))
                
                // Language switcher
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    LanguageFlag("🇺🇸", "en", currentLanguage) { 
                        viewModel.startWelcome(it)
                    }
                    LanguageFlag("🇪🇸", "es", currentLanguage) { 
                        viewModel.startWelcome(it)
                    }
                    LanguageFlag("🇫🇷", "fr", currentLanguage) { 
                        viewModel.startWelcome(it)
                    }
                    LanguageFlag("🇩🇪", "de", currentLanguage) { 
                        viewModel.startWelcome(it)
                    }
                    LanguageFlag("🇺🇦", "uk", currentLanguage) { 
                        viewModel.startWelcome(it)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Show loading spinner or avatar
                when (ttsState) {
                    is TTSState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        }
                    }
                    else -> {
                        if (uiState.avatarPrefs.showAvatar) {
                            val avatar = uiState.currentAvatar
                            if (avatar != null) {
                                // 3D Avatar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                ) {
                                    Avatar3DView(
                                        glbPath = avatar.glbPath,
                                        avatarProvider = viewModel.avatarProvider,
                                        onLoaded = {
                                            // Avatar loaded successfully
                                        },
                                        onError = { error ->
                                            // Silently fallback (icon avatar)
                                        }
                                    )
                                }
                            } else {
                                // Icon fallback while DB loads
                                ClaraAvatar(
                                    appearance = AvatarAppearance.fromId(legacyAvatarPrefs.appearanceId),
                                    size = 120.dp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show spinner, streaming caption, or static text
                when {
                    uiState.isGeneratingWelcome || ttsState is TTSState.Loading -> {
                        // Show spinner while generating
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    uiState.currentSubtitle.isNotEmpty() -> {
                        // Show text with auto-scroll
                        val scrollState = rememberScrollState()
                        
                        // Auto-scroll to bottom as text accumulates
                        LaunchedEffect(uiState.currentSubtitle) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 300.dp)
                                .padding(horizontal = 16.dp)
                        ) {
                            TextBubble(
                                text = uiState.currentSubtitle,
                                modifier = Modifier.verticalScroll(scrollState)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Only show buttons when not loading/generating AND not currently speaking
                val showButtons = !uiState.isGeneratingWelcome && 
                                  ttsState !is TTSState.Loading && 
                                  ttsState !is TTSState.Speaking
                
                if (showButtons) {
                    Text(
                        text = stringResource(R.string.how_would_you_like_to_begin),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (showButtons) {
                    // Get strings outside semantics blocks
                    val voiceChatTitle = stringResource(R.string.voice_chat)
                    val voiceChatDesc = stringResource(R.string.voice_chat_desc)
                    val typeMyInfoTitle = stringResource(R.string.type_my_info)
                    val typeMyInfoDesc = stringResource(R.string.type_my_info_desc)
                    val useWizardTitle = stringResource(R.string.use_wizard)
                    val useWizardDesc = stringResource(R.string.use_wizard_desc)
                    
                    // Voice Chat
                    Button(
                        onClick = {
                            viewModel.stopSpeaking()
                            viewModel.onOptionSelected("voice_chat")
                            onNavigateToChat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .semantics {
                                contentDescription = voiceChatDesc
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎤 $voiceChatTitle",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = voiceChatDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Text Chat
                    Button(
                        onClick = {
                            viewModel.stopSpeaking()
                            viewModel.onOptionSelected("text_chat")
                            onNavigateToTypeInfo()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .semantics {
                                contentDescription = typeMyInfoDesc
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⌨️ $typeMyInfoTitle",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = typeMyInfoDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Wizard
                    Button(
                        onClick = {
                            viewModel.stopSpeaking()
                            onNavigateToWizard()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .semantics {
                                contentDescription = useWizardDesc
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📋 $useWizardTitle",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = useWizardDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
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
                currentPrefs = legacyAvatarPrefs,
                onDismiss = { showSettings = false },
                onSave = { newPrefs ->
                    claraViewModel.updateAvatarPrefs(newPrefs)
                    showSettings = false
                }
            )
        }
    }
}

@Composable
private fun LanguageFlag(
    flag: String,
    langCode: String,
    selectedLanguage: String,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable { onSelect(langCode) }
            .alpha(if (selectedLanguage == langCode) 1f else 0.5f),
        color = if (selectedLanguage == langCode) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = flag,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
