package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.model.domain.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    val userProfile: Flow<UserProfile>
    val isWelcomeCompleted: Flow<Boolean>
    suspend fun saveUserProfile(profile: UserProfile)
    suspend fun clearUserProfile()
}

