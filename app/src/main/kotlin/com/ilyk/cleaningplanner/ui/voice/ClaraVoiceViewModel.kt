package com.ilyk.cleaningplanner.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClaraVoiceViewModel @Inject constructor(
    // TODO: Inject ClaraRepository when it's available
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ClaraVoiceUiState())
    val uiState: StateFlow<ClaraVoiceUiState> = _uiState.asStateFlow()
    
    fun startListening() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                state = ClaraVoiceState.LISTENING,
                error = null
            )
            
            try {
                // TODO: Start audio recording and WebSocket connection
                // For now, simulate listening for 3 seconds
                kotlinx.coroutines.delay(3000)
                
                _uiState.value = _uiState.value.copy(
                    state = ClaraVoiceState.THINKING,
                    transcript = "Hello, I need help with my cleaning plan"
                )
                
                // Simulate thinking
                kotlinx.coroutines.delay(2000)
                
                _uiState.value = _uiState.value.copy(
                    state = ClaraVoiceState.SPEAKING
                )
                
                // Simulate speaking
                kotlinx.coroutines.delay(3000)
                
                _uiState.value = _uiState.value.copy(
                    state = ClaraVoiceState.READY
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    state = ClaraVoiceState.IDLE,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    fun stopListening() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                state = ClaraVoiceState.THINKING
            )
            
            try {
                // TODO: Stop audio recording and process the audio
                // For now, simulate processing
                kotlinx.coroutines.delay(2000)
                
                _uiState.value = _uiState.value.copy(
                    state = ClaraVoiceState.SPEAKING
                )
                
                // Simulate speaking response
                kotlinx.coroutines.delay(3000)
                
                _uiState.value = _uiState.value.copy(
                    state = ClaraVoiceState.READY
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    state = ClaraVoiceState.IDLE,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
}
