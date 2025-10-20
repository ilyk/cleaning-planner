package com.ilyk.cleaningplanner.feature.clara.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LocaleManager {
    
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "es" -> Locale("es")
            "fr" -> Locale("fr")
            "de" -> Locale("de")
            "uk" -> Locale("uk")
            else -> Locale("en")
        }
        
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
    
    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
}

