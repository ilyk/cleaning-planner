package com.ilyk.cleaningplanner.feature.clara.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ilyk.cleaningplanner.core.model.Avatar3DPrefs
import com.ilyk.cleaningplanner.core.model.PronunciationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.avatar3DPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "avatar_3d_prefs")

@Singleton
class Avatar3DPrefsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val APPEARANCE_ID = stringPreferencesKey("appearance_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PRONUNCIATION_MODE = stringPreferencesKey("pronunciation_mode")
        val PRONUNCIATION_VALUE = stringPreferencesKey("pronunciation_value")
        val VOICE_ID = stringPreferencesKey("voice_id")
        val SHOW_AVATAR = booleanPreferencesKey("show_avatar")
        val MUTE_VOICE = booleanPreferencesKey("mute_voice")
        val ALWAYS_SHOW_SUBTITLES = booleanPreferencesKey("always_show_subtitles")
    }

    val avatar3DPrefs: Flow<Avatar3DPrefs> = context.avatar3DPrefsDataStore.data.map { prefs ->
        Avatar3DPrefs(
            appearanceId = prefs[Keys.APPEARANCE_ID] ?: "clara_default",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "Clara",
            pronunciationMode = PronunciationMode.valueOf(
                prefs[Keys.PRONUNCIATION_MODE] ?: PronunciationMode.NONE.name
            ),
            pronunciationValue = prefs[Keys.PRONUNCIATION_VALUE] ?: "",
            voiceId = prefs[Keys.VOICE_ID] ?: "warm",
            showAvatar = prefs[Keys.SHOW_AVATAR] ?: true,
            muteVoice = prefs[Keys.MUTE_VOICE] ?: false,
            alwaysShowSubtitles = prefs[Keys.ALWAYS_SHOW_SUBTITLES] ?: true
        )
    }

    suspend fun updatePrefs(prefs: Avatar3DPrefs) {
        context.avatar3DPrefsDataStore.edit { preferences ->
            preferences[Keys.APPEARANCE_ID] = prefs.appearanceId
            preferences[Keys.DISPLAY_NAME] = prefs.displayName
            preferences[Keys.PRONUNCIATION_MODE] = prefs.pronunciationMode.name
            preferences[Keys.PRONUNCIATION_VALUE] = prefs.pronunciationValue
            preferences[Keys.VOICE_ID] = prefs.voiceId
            preferences[Keys.SHOW_AVATAR] = prefs.showAvatar
            preferences[Keys.MUTE_VOICE] = prefs.muteVoice
            preferences[Keys.ALWAYS_SHOW_SUBTITLES] = prefs.alwaysShowSubtitles
        }
    }
}

