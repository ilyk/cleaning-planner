package com.ilyk.cleaningplanner.feature.clara.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.core.model.Avatar3DPrefs
import com.ilyk.cleaningplanner.core.model.PronunciationMode
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import com.ilyk.cleaningplanner.feature.clara.avatar.SceneViewAvatarProvider
import com.ilyk.cleaningplanner.feature.clara.data.Avatar3DPrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.generation.ProviderCapabilities
import com.ilyk.cleaningplanner.feature.clara.repository.AvatarRepository
import com.ilyk.cleaningplanner.feature.clara.service.TTSService
import com.ilyk.cleaningplanner.feature.clara.ui.import.ImportSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Avatar3DSettingsUiState(
    val prefs: Avatar3DPrefs = Avatar3DPrefs(),
    val currentAvatar: Avatar3DAsset? = null,
    val availableAvatars: List<Avatar3DAsset> = emptyList(),
    val providerCapabilities: ProviderCapabilities = ProviderCapabilities(),
    val isImporting: Boolean = false
)

@HiltViewModel
class Avatar3DSettingsViewModel @Inject constructor(
    private val avatar3DPrefsDataStore: Avatar3DPrefsDataStore,
    private val avatarRepository: AvatarRepository,
    private val ttsService: TTSService,
    val avatarProvider: SceneViewAvatarProvider
) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)

    val uiState: StateFlow<Avatar3DSettingsUiState> = combine(
        avatar3DPrefsDataStore.avatar3DPrefs,
        avatarRepository.allAvatars,
        _isImporting
    ) { prefs, avatars, isImporting ->
        Avatar3DSettingsUiState(
            prefs = prefs,
            currentAvatar = avatars.find { it.id == prefs.appearanceId },
            availableAvatars = avatars,
            providerCapabilities = ProviderCapabilities(
                supportsIPA = false,
                supportsSSML = true,
                supportsVisemes = avatarProvider.hasVisemeSupport()
            ),
            isImporting = isImporting
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Avatar3DSettingsUiState()
    )

    fun selectAvatar(id: String) {
        viewModelScope.launch {
            val current = uiState.value.prefs
            avatar3DPrefsDataStore.updatePrefs(current.copy(appearanceId = id))
        }
    }

    fun updateVoiceStyle(voiceId: String) {
        viewModelScope.launch {
            val current = uiState.value.prefs
            avatar3DPrefsDataStore.updatePrefs(current.copy(voiceId = voiceId))
        }
    }

    fun toggleShowAvatar() {
        viewModelScope.launch {
            val current = uiState.value.prefs
            avatar3DPrefsDataStore.updatePrefs(current.copy(showAvatar = !current.showAvatar))
        }
    }

    fun toggleMuteVoice() {
        viewModelScope.launch {
            val current = uiState.value.prefs
            avatar3DPrefsDataStore.updatePrefs(current.copy(muteVoice = !current.muteVoice))
        }
    }

    fun toggleSubtitles() {
        viewModelScope.launch {
            val current = uiState.value.prefs
            avatar3DPrefsDataStore.updatePrefs(current.copy(alwaysShowSubtitles = !current.alwaysShowSubtitles))
        }
    }

    fun turnOffAssistant() {
        viewModelScope.launch {
            val current = uiState.value.prefs
            avatar3DPrefsDataStore.updatePrefs(
                current.copy(
                    showAvatar = false,
                    muteVoice = true
                )
            )
        }
    }

    fun updatePronunciation(name: String, mode: PronunciationMode, value: String) {
        viewModelScope.launch {
            val current = uiState.value.prefs
            avatar3DPrefsDataStore.updatePrefs(
                current.copy(
                    displayName = name,
                    pronunciationMode = mode,
                    pronunciationValue = value
                )
            )
        }
    }

    fun previewVoice(voiceId: String) {
        viewModelScope.launch {
            val voiceStyle = VoiceStyle.fromId(voiceId)
            val name = uiState.value.prefs.displayName
            ttsService.speak("Hi, I'm $name.", voiceStyle)
        }
    }

    fun previewPronunciation(name: String, mode: PronunciationMode, value: String) {
        viewModelScope.launch {
            val voiceStyle = VoiceStyle.fromId(uiState.value.prefs.voiceId)
            
            val text = when (mode) {
                PronunciationMode.PHONETIC -> "Hi, I'm $value."
                PronunciationMode.IPA -> "Hi, I'm <phoneme alphabet='ipa' ph='$value'>$name</phoneme>."
                PronunciationMode.SSML -> "Hi, I'm $value."
                PronunciationMode.NONE -> "Hi, I'm $name."
            }
            
            ttsService.speak(text, voiceStyle)
        }
    }

    fun importAvatar(source: ImportSource, path: String, displayName: String, licenseNote: String) {
        viewModelScope.launch {
            _isImporting.value = true
            
            val result = when (source) {
                ImportSource.File -> avatarRepository.importFromFile(path, displayName, licenseNote)
                ImportSource.Url -> avatarRepository.importFromUrl(path, displayName, licenseNote)
            }
            
            _isImporting.value = false
            
            result.onSuccess { asset ->
                // Auto-select the imported avatar
                selectAvatar(asset.id)
            }
        }
    }

    fun isDeveloperMode(): Boolean {
        // Check if developer mode is enabled (e.g., via BuildConfig or settings)
        return true // For now, always show for testing
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.stop()
    }
}

