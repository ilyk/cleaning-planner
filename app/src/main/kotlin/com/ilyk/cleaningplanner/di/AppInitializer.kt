package com.ilyk.cleaningplanner.di

import com.ilyk.cleaningplanner.feature.clara.initialization.AvatarInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles app initialization tasks.
 */
@Singleton
class AppInitializer @Inject constructor(
    private val avatarInitializer: AvatarInitializer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize() {
        scope.launch {
            // Initialize bundled avatars
            avatarInitializer.initializeBundledAvatars()
        }
    }
}

