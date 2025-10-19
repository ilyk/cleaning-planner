package com.ilyk.cleaningplanner.feature.clara.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ilyk.cleaningplanner.core.model.OpenAIConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.openAIConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "openai_config")

@Singleton
class OpenAIConfigDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val MODEL = stringPreferencesKey("model")
        val API_KEY = stringPreferencesKey("api_key")
    }

    val openAIConfig: Flow<OpenAIConfig> = context.openAIConfigDataStore.data.map { prefs ->
        OpenAIConfig(
            provider = prefs[Keys.PROVIDER] ?: "OpenAI",
            model = prefs[Keys.MODEL] ?: "gpt-5",
            apiKey = prefs[Keys.API_KEY] ?: ""
        )
    }

    suspend fun updateConfig(config: OpenAIConfig) {
        context.openAIConfigDataStore.edit { preferences ->
            preferences[Keys.PROVIDER] = config.provider
            preferences[Keys.MODEL] = config.model
            preferences[Keys.API_KEY] = config.apiKey
        }
    }

    suspend fun clearApiKey() {
        context.openAIConfigDataStore.edit { preferences ->
            preferences[Keys.API_KEY] = ""
        }
    }
}

