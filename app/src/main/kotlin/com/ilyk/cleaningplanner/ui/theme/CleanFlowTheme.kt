package com.ilyk.cleaningplanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Main theme wrapper for CleanFlow app
 * Uses a simple light theme for now
 */
@Composable
fun CleanFlowTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme()
    
    MaterialTheme(colorScheme = colorScheme) {
        content()
    }
}