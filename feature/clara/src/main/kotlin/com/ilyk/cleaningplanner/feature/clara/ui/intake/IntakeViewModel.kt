package com.ilyk.cleaningplanner.feature.clara.ui.intake

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

@HiltViewModel
class IntakeViewModel @Inject constructor(
    private val claraRepository: ClaraRepository,
    private val ttsService: TTSService
) : ViewModel() {

    val avatarPrefs: StateFlow<AvatarPrefs> = claraRepository.avatarPrefs
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AvatarPrefs()
        )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun sendMessage(text: String) {
        viewModelScope.launch {
            _messages.value = _messages.value + ChatMessage(text, isFromUser = true)

            when (val result = claraRepository.getClaraResponse("conversation", text)) {
                is ClaraResult.Success -> {
                    addClaraMessage(result.message)
                }
                is ClaraResult.Error -> {
                    addClaraMessage(result.fallback)
                }
            }
        }
    }

    private fun addClaraMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(text, isFromUser = false)

        val prefs = avatarPrefs.value
        if (!prefs.muteVoice && prefs.showAvatar) {
            val voiceStyle = VoiceStyle.fromId(prefs.voiceId)
            ttsService.speak(text, voiceStyle)
        }
    }

    fun updateAvatarPrefs(prefs: AvatarPrefs) {
        viewModelScope.launch {
            claraRepository.updateAvatarPrefs(prefs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.stop()
    }
}

