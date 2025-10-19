package com.ilyk.cleaningplanner.feature.clara.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.OpenAIConfig
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class APIKeySetupViewModel @Inject constructor(
    private val claraRepository: ClaraRepository
) : ViewModel() {

    val openAIConfig: StateFlow<OpenAIConfig> = claraRepository.openAIConfig
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            OpenAIConfig()
        )
}

