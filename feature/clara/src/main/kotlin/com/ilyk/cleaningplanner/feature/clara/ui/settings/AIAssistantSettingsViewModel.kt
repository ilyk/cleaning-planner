package com.ilyk.cleaningplanner.feature.clara.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.OpenAIConfig
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AISettingsUiState(
    val config: OpenAIConfig = OpenAIConfig(),
    val isValidating: Boolean = false,
    val validationSuccess: Boolean? = null,
    val validationError: String? = null
)

@HiltViewModel
class AIAssistantSettingsViewModel @Inject constructor(
    private val claraRepository: ClaraRepository
) : ViewModel() {

    val openAIConfig: StateFlow<OpenAIConfig> = claraRepository.openAIConfig
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            OpenAIConfig()
        )

    private val _uiState = MutableStateFlow(AISettingsUiState())
    val uiState: StateFlow<AISettingsUiState> = _uiState.asStateFlow()

    fun updateConfig(config: OpenAIConfig) {
        viewModelScope.launch {
            claraRepository.updateOpenAIConfig(config)
            _uiState.value = _uiState.value.copy(config = config)
        }
    }

    fun validateConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isValidating = true,
                validationSuccess = null,
                validationError = null
            )

            val result = claraRepository.validateOpenAIConfig()
            
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(
                    isValidating = false,
                    validationSuccess = true,
                    validationError = null
                )
            } else {
                _uiState.value.copy(
                    isValidating = false,
                    validationSuccess = false,
                    validationError = result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun clearValidationState() {
        _uiState.value = _uiState.value.copy(
            validationSuccess = null,
            validationError = null
        )
    }
}

