package com.ilyk.cleaningplanner.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.core.model.domain.Mode

/**
 * Haptic feedback style per mode
 */
enum class HapticStyle {
    NONE,
    LIGHT,
    MEDIUM
}

/**
 * Theme tokens for each cleaning mode
 * Contains all visual and behavioral tokens
 */
data class ModeThemeTokens(
    val primary: Color,
    val secondary: Color,
    val bgGradient: List<Color>,
    val surface: Color,
    val onSurface: Color,
    val accent: Color,
    val cardElevation: Dp,
    val motionScale: Float,      // animation speed multiplier
    val hapticStyle: HapticStyle,
    val emoji: String,
    val bannerSubtext: String
)

/**
 * Predefined theme tokens for each mode
 */
object ModeThemes {
    val Focus = ModeThemeTokens(
        primary = Color(0xFF3BC9A9),
        secondary = Color(0xFF2E6F67),
        bgGradient = listOf(Color(0xFFF1FFFB), Color(0xFFE8FAF5)),
        surface = Color.White,
        onSurface = Color(0xFF1C1C1C),
        accent = Color(0xFF55DEC4),
        cardElevation = 4.dp,
        motionScale = 1.1f,
        hapticStyle = HapticStyle.LIGHT,
        emoji = "⚡",
        bannerSubtext = "All tasks under 15 minutes"
    )
    
    val LowEnergy = ModeThemeTokens(
        primary = Color(0xFF7A8F9A),
        secondary = Color(0xFF4A5B63),
        bgGradient = listOf(Color(0xFFF7FAFB), Color(0xFFF1F5F7)),
        surface = Color.White,
        onSurface = Color(0xFF1E2528),
        accent = Color(0xFF9FB3BD),
        cardElevation = 2.dp,
        motionScale = 0.85f,
        hapticStyle = HapticStyle.NONE,
        emoji = "🌙",
        bannerSubtext = "Easiest tasks first"
    )
    
    val FullReset = ModeThemeTokens(
        primary = Color(0xFF3A7AFE),
        secondary = Color(0xFF1F3E91),
        bgGradient = listOf(Color(0xFFEFF4FF), Color(0xFFE5EDFF)),
        surface = Color.White,
        onSurface = Color(0xFF141A2A),
        accent = Color(0xFF6CA2FF),
        cardElevation = 6.dp,
        motionScale = 1.0f,
        hapticStyle = HapticStyle.MEDIUM,
        emoji = "🧼",
        bannerSubtext = "Deep clean every room"
    )
    
    val PetMode = ModeThemeTokens(
        primary = Color(0xFF49B4F2),
        secondary = Color(0xFF2A6A93),
        bgGradient = listOf(Color(0xFFEFF9FF), Color(0xFFE6F4FD)),
        surface = Color.White,
        onSurface = Color(0xFF153041),
        accent = Color(0xFF86D3FF),
        cardElevation = 4.dp,
        motionScale = 1.05f,
        hapticStyle = HapticStyle.LIGHT,
        emoji = "🐾",
        bannerSubtext = "Pet-friendly cleaning"
    )
}

/**
 * Get tokens for a specific mode
 */
fun tokensFor(mode: Mode) = when (mode) {
    Mode.Focus -> ModeThemes.Focus
    Mode.LowEnergy -> ModeThemes.LowEnergy
    Mode.FullReset -> ModeThemes.FullReset
    Mode.PetMode -> ModeThemes.PetMode
}

/**
 * CompositionLocal for accessing mode tokens throughout the tree
 */
val LocalModeTokens = staticCompositionLocalOf { ModeThemes.Focus }

/**
 * Convert mode tokens to Material3 ColorScheme
 */
fun colorSchemeFrom(tokens: ModeThemeTokens) = lightColorScheme(
    primary = tokens.primary,
    secondary = tokens.accent,
    background = tokens.bgGradient.first(),
    surface = tokens.surface,
    onSurface = tokens.onSurface,
    tertiary = tokens.secondary,
    onPrimary = Color.White,
    onSecondary = Color.White
)

/**
 * Mode-specific behaviors for task filtering and UI
 */
data class ModeBehaviors(
    val maxTaskDurationMin: Int?,   // Max task duration (e.g., Focus=15)
    val limitTaskCount: Int?,       // Max number of tasks to show
    val extraTags: Set<String>,     // Additional tags (e.g., "pet")
    val showDeepClean: Boolean,     // Show deep clean tasks
    val bannerMessage: String,      // Message to display
    val bannerSubtext: String       // Subtext for the banner
)

/**
 * Get behaviors for a specific mode
 */
fun behaviorsFor(mode: Mode) = when (mode) {
    Mode.Focus -> ModeBehaviors(
        maxTaskDurationMin = 15,
        limitTaskCount = null,
        extraTags = emptySet(),
        showDeepClean = false,
        bannerMessage = "Quick Wins Mode",
        bannerSubtext = "All tasks under 15 minutes"
    )
    Mode.LowEnergy -> ModeBehaviors(
        maxTaskDurationMin = null,
        limitTaskCount = 5,
        extraTags = setOf("easy", "light"),
        showDeepClean = false,
        bannerMessage = "Low Energy Mode",
        bannerSubtext = "Taking it easy with light tasks"
    )
    Mode.FullReset -> ModeBehaviors(
        maxTaskDurationMin = null,
        limitTaskCount = null,
        extraTags = setOf("deep"),
        showDeepClean = true,
        bannerMessage = "Deep Clean Mode",
        bannerSubtext = "Full reset with all tasks"
    )
    Mode.PetMode -> ModeBehaviors(
        maxTaskDurationMin = null,
        limitTaskCount = null,
        extraTags = setOf("pet", "pet-safe"),
        showDeepClean = true,
        bannerMessage = "Pet-Friendly Mode",
        bannerSubtext = "Safe cleaning with pets around"
    )
}

