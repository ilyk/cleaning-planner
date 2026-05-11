package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.domain.model.UserProfile
import com.ilyk.cleaningplanner.domain.model.Preference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    // PrefsStore is in app module, so we can't inject it here
    // Cloud opt-in sync will be handled at the app layer
) : UserProfileRepository {
    
    private val _userProfile = MutableStateFlow<UserProfile?>(
        // Default profile - in a real app, this would be loaded from storage
        UserProfile(
            name = "",
            rooms = emptyList(),
            floors = 1,
            hasPets = false,
            devices = emptyList(),
            preference = Preference.Minimalist,
            cloudOptIn = false
        )
    )
    
    override val userProfile: Flow<UserProfile> = _userProfile.asStateFlow().map { it ?: getDefaultProfile() }
    
    override val isWelcomeCompleted: Flow<Boolean> = _userProfile.asStateFlow().map { profile ->
        profile?.isComplete ?: false
    }
    
    override suspend fun saveUserProfile(profile: UserProfile) {
        _userProfile.value = profile
        // Note: Cloud opt-in sync to PrefsStore is handled at the app layer (SettingsViewModel)
    }
    
    override suspend fun clearUserProfile() {
        _userProfile.value = null
    }
    
    private fun getDefaultProfile(): UserProfile {
        return UserProfile(
            name = "",
            rooms = emptyList(),
            floors = 1,
            hasPets = false,
            devices = emptyList(),
            preference = Preference.Minimalist,
            cloudOptIn = false
        )
    }
}
