package com.ilyk.cleaningplanner.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import com.ilyk.cleaningplanner.core.model.domain.Mode

/**
 * Animate mode theme tokens with smooth transitions
 */
@Composable
fun animateModeTokens(targetMode: Mode): ModeThemeTokens {
    val baseTokens = tokensFor(targetMode)
    
    // Animate primary color
    val primary by animateColorAsState(
        targetValue = baseTokens.primary,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "primary"
    )
    
    // Animate secondary color
    val secondary by animateColorAsState(
        targetValue = baseTokens.secondary,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "secondary"
    )
    
    // Animate accent color
    val accent by animateColorAsState(
        targetValue = baseTokens.accent,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "accent"
    )
    
    // Animate surface color
    val surface by animateColorAsState(
        targetValue = baseTokens.surface,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "surface"
    )
    
    // Animate onSurface color
    val onSurface by animateColorAsState(
        targetValue = baseTokens.onSurface,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "onSurface"
    )
    
    // Animate gradient start
    val gradientStart by animateColorAsState(
        targetValue = baseTokens.bgGradient[0],
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "gradientStart"
    )
    
    // Animate gradient end
    val gradientEnd by animateColorAsState(
        targetValue = baseTokens.bgGradient[1],
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "gradientEnd"
    )
    
    // Animate card elevation
    val cardElevation by animateDpAsState(
        targetValue = baseTokens.cardElevation,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "cardElevation"
    )
    
    // Animate motion scale
    val motionScale by animateFloatAsState(
        targetValue = baseTokens.motionScale,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "motionScale"
    )
    
    return ModeThemeTokens(
        primary = primary,
        secondary = secondary,
        bgGradient = listOf(gradientStart, gradientEnd),
        surface = surface,
        onSurface = onSurface,
        accent = accent,
        cardElevation = cardElevation,
        motionScale = motionScale,
        hapticStyle = baseTokens.hapticStyle,
        emoji = baseTokens.emoji,
        bannerSubtext = baseTokens.bannerSubtext // No animation for text content
    )
}

/**
 * Get spring animation spec scaled by motion scale
 */
fun <T> springWithMotionScale(motionScale: Float): SpringSpec<T> = spring(
    dampingRatio = 0.8f,
    stiffness = 300f * motionScale
)

/**
 * Get tween animation spec scaled by motion scale
 */
fun <T> tweenWithMotionScale(motionScale: Float, durationMillis: Int = 300): TweenSpec<T> = tween(
    durationMillis = (durationMillis / motionScale).toInt(),
    easing = FastOutSlowInEasing
)

