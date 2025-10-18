package com.ilyk.cleaningplanner.feature.clara.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.core.model.Avatar3DPrefs
import com.ilyk.cleaningplanner.core.model.AvatarPrefs
import com.ilyk.cleaningplanner.core.model.VoiceStyle
import com.ilyk.cleaningplanner.feature.clara.avatar.SceneViewAvatarProvider
import com.ilyk.cleaningplanner.feature.clara.data.Avatar3DPrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.lipsync.VisemeEngine
import com.ilyk.cleaningplanner.feature.clara.repository.AvatarRepository
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraRepository
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraResult
import com.ilyk.cleaningplanner.feature.clara.service.TTSService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(
    val avatarPrefs: Avatar3DPrefs = Avatar3DPrefs(),
    val currentAvatar: Avatar3DAsset? = null,
    val currentSubtitle: String = "",
    val isWelcomeSpeaking: Boolean = false,
    val followUpMessage: String = "",
    val isLoadingFollowUp: Boolean = false
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val claraRepository: ClaraRepository,
    private val ttsService: TTSService,
    private val avatar3DPrefsDataStore: Avatar3DPrefsDataStore,
    private val avatarRepository: AvatarRepository,
    val avatarProvider: SceneViewAvatarProvider,
    private val visemeEngine: VisemeEngine
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

    private val _followUpState = MutableStateFlow<Pair<String, Boolean>>("" to false)

    val uiState: StateFlow<WelcomeUiState> = combine(
        avatar3DPrefsDataStore.avatar3DPrefs,
        avatarRepository.allAvatars,
        _followUpState
    ) { prefs, avatars, (followUp, isLoading) ->
        WelcomeUiState(
            avatarPrefs = prefs,
            currentAvatar = avatars.find { it.id == prefs.appearanceId },
            currentSubtitle = if (followUp.isNotEmpty()) followUp else WELCOME_TEXT,
            followUpMessage = followUp,
            isLoadingFollowUp = isLoading
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WelcomeUiState()
    )

    fun startWelcome() {
        viewModelScope.launch {
            val prefs = uiState.value.avatarPrefs

            if (!prefs.muteVoice && prefs.showAvatar) {
                val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                ttsService.speak(WELCOME_TEXT, voiceStyle)
                
                // Generate visemes for lip-sync
                val visemes = visemeEngine.phonemesToVisemes(WELCOME_TEXT, estimateDuration(WELCOME_TEXT))
                visemeEngine.playVisemes(visemes, avatarProvider)
            }
        }
    }
    
    private fun estimateDuration(text: String): Long {
        // Rough estimate: ~150 words per minute = ~2.5 words per second
        val words = text.split(" ").size
        return (words / 2.5 * 1000).toLong()
    }

    fun onOptionSelected(option: String) {
        viewModelScope.launch {
            _followUpState.value = "" to true
            
            when (val result = claraRepository.getClaraResponse(option)) {
                is ClaraResult.Success -> {
                    val message = result.message
                    _followUpState.value = message to false
                    
                    val prefs = uiState.value.avatarPrefs
                    if (!prefs.muteVoice && prefs.showAvatar) {
                        val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                        ttsService.speak(message, voiceStyle)
                        
                        // Generate and play visemes
                        val visemes = visemeEngine.phonemesToVisemes(message, estimateDuration(message))
                        visemeEngine.playVisemes(visemes, avatarProvider)
                    }
                }
                is ClaraResult.Error -> {
                    val message = result.fallback
                    _followUpState.value = message to false
                    
                    val prefs = uiState.value.avatarPrefs
                    if (!prefs.muteVoice && prefs.showAvatar) {
                        val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
                        ttsService.speak(message, voiceStyle)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.stop()
        visemeEngine.stop()
    }
}

