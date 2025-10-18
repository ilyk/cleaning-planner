package com.ilyk.cleaningplanner.feature.clara.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import com.ilyk.cleaningplanner.feature.clara.ui.components.Avatar3DView
import com.ilyk.cleaningplanner.feature.clara.ui.import.AvatarImportDialog
import com.ilyk.cleaningplanner.feature.clara.ui.import.ImportSource
import com.ilyk.cleaningplanner.feature.clara.ui.pronunciation.PronunciationEditorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Avatar3DSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: Avatar3DSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showImportDialog by remember { mutableStateOf<ImportSource?>(null) }
    var showPronunciationEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Avatar Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (viewModel.isDeveloperMode()) {
                        TextButton(onClick = onNavigateToDiagnostics) {
                            Text("Diagnostics")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportDialog = ImportSource.File }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import avatar")
            }
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
            // 3D Preview
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.currentAvatar != null && uiState.prefs.showAvatar) {
                        Avatar3DView(
                            glbPath = uiState.currentAvatar.glbPath,
                            avatarProvider = viewModel.avatarProvider,
                            onError = { error ->
                                // Show error snackbar
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Avatar hidden")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Avatar Selection
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(uiState.availableAvatars) { avatar ->
                    AvatarCard(
                        avatar = avatar,
                        isSelected = avatar.id == uiState.prefs.appearanceId,
                        onClick = { viewModel.selectAvatar(avatar.id) }
                    )
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Name & Pronunciation
            Text(
                text = "Name & Pronunciation",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPronunciationEditor = true }
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
                            text = uiState.prefs.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (uiState.prefs.pronunciationMode != com.ilyk.cleaningplanner.core.model.PronunciationMode.NONE) {
                            Text(
                                text = "Pronunciation: ${uiState.prefs.pronunciationMode.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Voice Style
            Text(
                text = "Voice Style",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                VoiceStyle.entries.forEach { voice ->
                    FilterChip(
                        selected = uiState.prefs.voiceId == voice.id,
                        onClick = { viewModel.updateVoiceStyle(voice.id) },
                        label = { Text(voice.displayName) },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.previewVoice(voice.id) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Visibility & Audio
            Text(
                text = "Visibility & Audio",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SettingRow(
                label = "Show avatar",
                checked = uiState.prefs.showAvatar,
                onCheckedChange = { viewModel.toggleShowAvatar() }
            )

            SettingRow(
                label = "Mute voice",
                checked = uiState.prefs.muteVoice,
                onCheckedChange = { viewModel.toggleMuteVoice() }
            )

            SettingRow(
                label = "Always show subtitles",
                checked = uiState.prefs.alwaysShowSubtitles,
                onCheckedChange = { viewModel.toggleSubtitles() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.turnOffAssistant() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Turn Off Assistant")
            }
        }

        // Dialogs
        showImportDialog?.let { source ->
            AvatarImportDialog(
                source = source,
                onDismiss = { showImportDialog = null },
                onImport = { path, name, license ->
                    viewModel.importAvatar(source, path, name, license)
                    showImportDialog = null
                },
                isLoading = uiState.isImporting
            )
        }

        if (showPronunciationEditor) {
            PronunciationEditorDialog(
                currentName = uiState.prefs.displayName,
                currentMode = uiState.prefs.pronunciationMode,
                currentValue = uiState.prefs.pronunciationValue,
                supportsIPA = uiState.providerCapabilities.supportsIPA,
                supportsSSML = uiState.providerCapabilities.supportsSSML,
                onDismiss = { showPronunciationEditor = false },
                onSave = { name, mode, value ->
                    viewModel.updatePronunciation(name, mode, value)
                    showPronunciationEditor = false
                },
                onPreview = { name, mode, value ->
                    viewModel.previewPronunciation(name, mode, value)
                }
            )
        }
    }
}

@Composable
private fun AvatarCard(
    avatar: Avatar3DAsset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(120.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Thumbnail would go here
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", style = MaterialTheme.typography.displaySmall)
            }
            
            Text(
                text = avatar.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckedChange)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            modifier = Modifier.semantics {
                contentDescription = "$label: ${if (checked) "on" else "off"}"
            }
        )
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier
    ) {
        content()
    }
}

