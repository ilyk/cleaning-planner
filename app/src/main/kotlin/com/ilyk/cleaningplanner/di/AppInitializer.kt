package com.ilyk.cleaningplanner.di

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
class AppInitializer @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize() {
        scope.launch {
            // TODO: Initialize app components when clara feature is re-enabled
            // Initialize bundled avatars
            // avatarInitializer.initializeBundledAvatars()
        }
    }
}

