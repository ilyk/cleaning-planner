package com.ilyk.cleaningplanner.state

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    private val homeId = stringPreferencesKey("home_id")
    private val claraEnabled = booleanPreferencesKey("clara_enabled")
    private val currentMode = stringPreferencesKey("current_mode")
    private val locale = stringPreferencesKey("locale")
    private val deviceId = stringPreferencesKey("device_id")
    private val cloudOptIn = booleanPreferencesKey("cloud_opt_in")
    
    suspend fun setClaraEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[claraEnabled] = enabled
        }
    }
    
    val isClaraEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[claraEnabled] ?: true
    }
    
    suspend fun setCurrentMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[currentMode] = mode
        }
    }
    
    val currentModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[currentMode] ?: "FOCUS"
    }
    
    suspend fun setLocale(locale: String) {
        dataStore.edit { prefs ->
            prefs[this.locale] = locale
        }
    }
    
    val localeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[locale] ?: "en"
    }
    
    suspend fun getOrCreateDeviceId(): String {
        val current = dataStore.data.first()[deviceId]
        return current ?: run {
            val newId = "android_${java.util.UUID.randomUUID().toString().take(8)}"
            dataStore.edit { prefs ->
                prefs[deviceId] = newId
            }
            newId
        }
    }
    
    suspend fun setCloudOptIn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[cloudOptIn] = enabled
        }
    }
    
    val cloudOptInFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[cloudOptIn] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean, homeId: String? = null) {
        dataStore.edit { prefs ->
            prefs[onboardingCompleted] = completed
            homeId?.let { prefs[this@PrefsStore.homeId] = it }
        }
    }

    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[onboardingCompleted] ?: false
    }

    val homeIdFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[homeId]
    }

    suspend fun getHomeId(): String? = dataStore.data.first()[homeId]
}
