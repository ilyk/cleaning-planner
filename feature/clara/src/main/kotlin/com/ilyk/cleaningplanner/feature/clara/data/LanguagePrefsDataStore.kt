package com.ilyk.cleaningplanner.feature.clara.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.languagePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_prefs")

@Singleton
class LanguagePrefsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
    }

    val languageCode: Flow<String> = context.languagePrefsDataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE_CODE] ?: "en"
    }

    suspend fun setLanguage(langCode: String) {
        context.languagePrefsDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_CODE] = langCode
        }
    }
}

