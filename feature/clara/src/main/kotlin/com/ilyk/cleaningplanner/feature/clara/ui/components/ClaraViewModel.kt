package com.ilyk.cleaningplanner.feature.clara.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.core.model.AvatarPrefs
import com.ilyk.cleaningplanner.feature.clara.repository.ClaraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClaraViewModel @Inject constructor(
    private val claraRepository: ClaraRepository
) : ViewModel() {

    val avatarPrefs: StateFlow<AvatarPrefs> = claraRepository.avatarPrefs
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AvatarPrefs()
        )

    fun updateAvatarPrefs(prefs: AvatarPrefs) {
        viewModelScope.launch {
            claraRepository.updateAvatarPrefs(prefs)
        }
    }
}

