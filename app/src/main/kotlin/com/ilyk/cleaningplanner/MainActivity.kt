package com.ilyk.cleaningplanner

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ilyk.cleaningplanner.core.ui.theme.CleaningPlannerTheme
import com.ilyk.cleaningplanner.feature.clara.data.LanguagePrefsDataStore
import com.ilyk.cleaningplanner.feature.clara.util.LocaleManager
import com.ilyk.cleaningplanner.navigation.CleaningPlannerNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var languagePrefsDataStore: LanguagePrefsDataStore
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            CleaningPlannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CleaningPlannerNavHost(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}



