package com.ilyk.cleaningplanner.feature.clara.ui.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.Avatar3DAsset
import com.ilyk.cleaningplanner.core.model.Avatar3DPrefs
import com.ilyk.cleaningplanner.feature.clara.avatar.SceneViewAvatarProvider
import com.ilyk.cleaningplanner.feature.clara.data.Avatar3DPrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.repository.AvatarRepository
import com.ilyk.cleaningplanner.feature.clara.service.OpenAIRealtimeService
import com.ilyk.cleaningplanner.feature.clara.service.RealtimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceChatUiState(
    val avatarPrefs: Avatar3DPrefs = Avatar3DPrefs(),
    val currentAvatar: Avatar3DAsset? = null,
    val claraTranscript: String = "",
    val userTranscript: String = "",
    val isListening: Boolean = false,
    val isClaraSpeaking: Boolean = false,
    val isUserSpeaking: Boolean = false,
    val isConnecting: Boolean = false,
    val isSwitchingLanguage: Boolean = false,
    val error: String? = null,
    val isSessionComplete: Boolean = false,
    val conversationTranscript: String = "",
    val debugInfo: String = ""
)

/**
 * ViewModel for Voice Chat using OpenAI Realtime API
 * Real-time bidirectional audio streaming
 */
@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    private val avatar3DPrefsDataStore: Avatar3DPrefsDataStore,
    private val avatarRepository: AvatarRepository,
    val avatarProvider: SceneViewAvatarProvider,
    private val realtimeService: OpenAIRealtimeService
) : ViewModel() {

    val uiState: StateFlow<VoiceChatUiState> = combine(
        avatar3DPrefsDataStore.avatar3DPrefs,
        avatarRepository.allAvatars,
        realtimeService.state,
        realtimeService.userTranscript,
        realtimeService.claraTranscript,
        realtimeService.isSwitchingLanguage
    ) { flows: Array<Any?> ->
        val prefs = flows[0] as Avatar3DPrefs
        val avatars = flows[1] as List<Avatar3DAsset>
        val state = flows[2] as RealtimeState
        val userText = flows[3] as String
        val claraText = flows[4] as String
        val switching = flows[5] as Boolean

        VoiceChatUiState(
            avatarPrefs = prefs,
            currentAvatar = avatars.find { it.id == prefs.appearanceId },
            userTranscript = userText,
            claraTranscript = claraText,
            isConnecting = state is RealtimeState.Connecting,
            isListening = state is RealtimeState.Connected || state is RealtimeState.UserSpeaking || state is RealtimeState.ClaraSpeaking,
            isUserSpeaking = state is RealtimeState.UserSpeaking,
            isClaraSpeaking = state is RealtimeState.ClaraSpeaking,
            isSwitchingLanguage = switching,
            isSessionComplete = state is RealtimeState.SessionComplete,
            conversationTranscript = (state as? RealtimeState.SessionComplete)?.conversationTranscript ?: "",
            debugInfo = realtimeService.getDebugState(),
            error = (state as? RealtimeState.Error)?.message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        VoiceChatUiState()
    )

    init {
        // Start realtime session immediately
        viewModelScope.launch {
            Log.d(TAG, "Starting OpenAI Realtime session")
            val result = realtimeService.startRealtimeSession()
            
            if (result.isFailure) {
                Log.e(TAG, "Failed to start realtime session: ${result.exceptionOrNull()?.message}")
            } else {
                Log.d(TAG, "Realtime session started successfully")
            }
        }
    }

    fun toggleListening() {
        val currentState = realtimeService.state.value
        
        if (currentState is RealtimeState.Idle || currentState is RealtimeState.Error) {
            // Start session
            viewModelScope.launch {
                realtimeService.startRealtimeSession()
            }
        } else {
            // Stop session
            realtimeService.stopRealtimeSession()
        }
    }
    
    /**
     * Switch language mid-conversation with proper cancellation
     * This prevents the "two languages loading" bug by:
     * 1. Cancelling the active response
     * 2. Stopping local playback
     * 3. Updating session configuration
     * 4. Starting fresh in new language
     */
    fun switchLanguage(newLanguage: String) {
        viewModelScope.launch {
            Log.d(TAG, "Switching to language: $newLanguage")
            realtimeService.switchLanguage(newLanguage)
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeService.stopRealtimeSession()
    }

    companion object {
        private const val TAG = "VoiceChatViewModel"
    }
}
