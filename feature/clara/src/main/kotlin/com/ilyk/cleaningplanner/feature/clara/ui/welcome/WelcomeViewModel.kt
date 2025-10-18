package com.ilyk.cleaningplanner.feature.clara.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.AvatarPrefs
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraRepository
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraResult
import com.ilyk.cleaningplanner.feature.clara.service.TTSService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    val avatarPrefs: AvatarPrefs = AvatarPrefs(),
    val currentSubtitle: String = "",
    val isWelcomeSpeaking: Boolean = false,
    val followUpMessage: String = "",
    val isLoadingFollowUp: Boolean = false
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val claraRepository: ClaraRepository,
    private val ttsService: TTSService
) : ViewModel() {

    companion object {
        const val WELCOME_TEXT = """Hi! I'm Clara — your cleaning planning assistant.
Welcome to the world of Cleaning Planning, where we make looking after your home simple, shared, and even a bit enjoyable.

To get started, just tell me about your household — whatever comes to mind.
You can chat with me, type it out, or, if you'd rather go step by step, we can use a few quick forms and questions instead.

As we talk, I'll quietly organize what you tell me into your plan — things like rooms, people, and routines — so you don't have to think about the details.
You can keep going for as long as you like, add things later, or stop whenever you feel it's enough.

You'll see me down here as a friendly avatar with subtitles so you can read along.
Tap the AI avatar button in the bottom-right corner to change how I look or sound, or to turn me off if you prefer just text.
I'll always be there when you want me back."""
    }

    val avatarPrefs: StateFlow<AvatarPrefs> = claraRepository.avatarPrefs
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AvatarPrefs()
        )

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun startWelcome() {
        viewModelScope.launch {
            val prefs = avatarPrefs.value
            _uiState.value = _uiState.value.copy(
                avatarPrefs = prefs,
                currentSubtitle = WELCOME_TEXT,
                isWelcomeSpeaking = true
            )

            if (!prefs.muteVoice && prefs.showAvatar) {
                val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                ttsService.speak(WELCOME_TEXT, voiceStyle)
            }
        }
    }

    fun onOptionSelected(option: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFollowUp = true)
            
            when (val result = claraRepository.getClaraResponse(option)) {
                is ClaraResult.Success -> {
                    val message = result.message
                    _uiState.value = _uiState.value.copy(
                        followUpMessage = message,
                        isLoadingFollowUp = false,
                        currentSubtitle = message
                    )
                    
                    val prefs = avatarPrefs.value
                    if (!prefs.muteVoice && prefs.showAvatar) {
                        val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                        ttsService.speak(message, voiceStyle)
                    }
                }
                is ClaraResult.Error -> {
                    val message = result.fallback
                    _uiState.value = _uiState.value.copy(
                        followUpMessage = message,
                        isLoadingFollowUp = false,
                        currentSubtitle = message
                    )
                    
                    val prefs = avatarPrefs.value
                    if (!prefs.muteVoice && prefs.showAvatar) {
                        val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                        ttsService.speak(message, voiceStyle)
                    }
                }
            }
        }
    }

    fun clearSubtitle() {
        _uiState.value = _uiState.value.copy(currentSubtitle = "")
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.stop()
    }
}

