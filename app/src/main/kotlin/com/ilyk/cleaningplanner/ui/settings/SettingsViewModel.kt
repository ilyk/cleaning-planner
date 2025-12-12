package com.ilyk.cleaningplanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.data.repository.UserProfileRepository
import com.ilyk.cleaningplanner.state.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val cloudOptIn: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsStore: PrefsStore,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            combine(
                prefsStore.cloudOptInFlow,
                userProfileRepository.userProfile
            ) { cloudOptIn, profile ->
                // Prefer UserProfile.cloudOptIn if available, otherwise use PrefsStore
                profile.cloudOptIn || cloudOptIn
            }.collect { enabled ->
                _uiState.value = _uiState.value.copy(cloudOptIn = enabled)
            }
        }
    }
    
    fun setCloudOptIn(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setCloudOptIn(enabled)
            
            // Also update UserProfile if it exists
            try {
                val profile = userProfileRepository.userProfile.first()
                val updatedProfile = profile.copy(cloudOptIn = enabled)
                userProfileRepository.saveUserProfile(updatedProfile)
            } catch (e: Exception) {
                // If profile doesn't exist yet, that's fine - PrefsStore is the source of truth
            }
        }
    }
}
