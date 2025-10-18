package com.ilyk.cleaningplanner.feature.clara.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ilyk.cleaningplanner.core.model.AvatarPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.avatarPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "avatar_prefs")

@Singleton
class AvatarPrefsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val APPEARANCE_ID = stringPreferencesKey("appearance_id")
        val VOICE_ID = stringPreferencesKey("voice_id")
        val SHOW_AVATAR = booleanPreferencesKey("show_avatar")
        val MUTE_VOICE = booleanPreferencesKey("mute_voice")
        val ALWAYS_SHOW_SUBTITLES = booleanPreferencesKey("always_show_subtitles")
    }

    val avatarPrefs: Flow<AvatarPrefs> = context.avatarPrefsDataStore.data.map { prefs ->
        AvatarPrefs(
            appearanceId = prefs[Keys.APPEARANCE_ID] ?: "clara",
            voiceId = prefs[Keys.VOICE_ID] ?: "warm",
            showAvatar = prefs[Keys.SHOW_AVATAR] ?: true,
            muteVoice = prefs[Keys.MUTE_VOICE] ?: false,
            alwaysShowSubtitles = prefs[Keys.ALWAYS_SHOW_SUBTITLES] ?: true
        )
    }

    suspend fun updateAvatarPrefs(prefs: AvatarPrefs) {
        context.avatarPrefsDataStore.edit { preferences ->
            preferences[Keys.APPEARANCE_ID] = prefs.appearanceId
            preferences[Keys.VOICE_ID] = prefs.voiceId
            preferences[Keys.SHOW_AVATAR] = prefs.showAvatar
            preferences[Keys.MUTE_VOICE] = prefs.muteVoice
            preferences[Keys.ALWAYS_SHOW_SUBTITLES] = prefs.alwaysShowSubtitles
        }
    }

    suspend fun updateAppearance(appearanceId: String) {
        context.avatarPrefsDataStore.edit { preferences ->
            preferences[Keys.APPEARANCE_ID] = appearanceId
        }
    }

    suspend fun updateVoice(voiceId: String) {
        context.avatarPrefsDataStore.edit { preferences ->
            preferences[Keys.VOICE_ID] = voiceId
        }
    }

    suspend fun setShowAvatar(show: Boolean) {
        context.avatarPrefsDataStore.edit { preferences ->
            preferences[Keys.SHOW_AVATAR] = show
        }
    }

    suspend fun setMuteVoice(mute: Boolean) {
        context.avatarPrefsDataStore.edit { preferences ->
            preferences[Keys.MUTE_VOICE] = mute
        }
    }

    suspend fun setAlwaysShowSubtitles(always: Boolean) {
        context.avatarPrefsDataStore.edit { preferences ->
            preferences[Keys.ALWAYS_SHOW_SUBTITLES] = always
        }
    }
}

