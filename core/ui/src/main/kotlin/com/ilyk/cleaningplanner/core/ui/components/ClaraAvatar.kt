package com.ilyk.cleaningplanner.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyk.cleaningplanner.core.ui.theme.*

/**
 * CleanFlow Clara Avatar System - Emotionally Intelligent UI
 * Floating AI assistant with contextual tips, emotional expressions, and empathetic interactions
 */

@Composable
fun ClaraAvatar(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    isExpanded: Boolean = false,
    currentTip: String? = null,
    emotion: ClaraEmotion = ClaraEmotion.NEUTRAL,
    onTipDismissed: () -> Unit = {},
    onAvatarClicked: () -> Unit = {}
) {
    if (!isVisible) return
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Floating avatar in bottom-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            // Tip bubble (if expanded)
            if (isExpanded && currentTip != null) {
                TipBubble(
                    tip = currentTip,
                    emotion = emotion,
                    onDismissed = onTipDismissed,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp)
                )
            }
            
            // Avatar circle with emotional expressions
            EmotionalAvatarCircle(
                onClick = onAvatarClicked,
                isExpanded = isExpanded,
                emotion = emotion
            )
        }
    }
}

@Composable
fun EmotionalAvatarCircle(
    onClick: () -> Unit,
    isExpanded: Boolean = false,
    emotion: ClaraEmotion = ClaraEmotion.NEUTRAL
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isExpanded) 1.1f else 1f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
        label = "avatar_scale"
    )
    
    // Breathing animation for neutral state
    val breathingAnimation by animateFloatAsState(
        targetValue = if (emotion == ClaraEmotion.NEUTRAL && !isExpanded) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    
    // Emotional color based on emotion
    val avatarColor = when (emotion) {
        ClaraEmotion.HAPPY -> EncouragementColor.copy(alpha = 0.15f)
        ClaraEmotion.CELEBRATING -> SuccessColor.copy(alpha = 0.15f)
        ClaraEmotion.ENCOURAGING -> ClaraAccent.copy(alpha = 0.15f)
        ClaraEmotion.SUPPORTIVE -> MintAccent.copy(alpha = 0.15f)
        ClaraEmotion.NEUTRAL -> ClaraAccent.copy(alpha = 0.1f)
        ClaraEmotion.THINKING -> InfoColor.copy(alpha = 0.15f)
    }
    
    Card(
        modifier = Modifier
            .size(64.dp)
            .scale(animatedScale * breathingAnimation)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = SoftShadow,
                spotColor = SoftShadow
            )
            .clickable { onClick() },
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = avatarColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Clara's emotional face
            ClaraFace(emotion = emotion)
        }
    }
}

@Composable
fun ClaraFace(emotion: ClaraEmotion) {
    val faceExpression = when (emotion) {
        ClaraEmotion.HAPPY -> "😊"
        ClaraEmotion.CELEBRATING -> "🎉"
        ClaraEmotion.ENCOURAGING -> "💪"
        ClaraEmotion.SUPPORTIVE -> "🤗"
        ClaraEmotion.NEUTRAL -> "🤖"
        ClaraEmotion.THINKING -> "🤔"
    }
    
    // Subtle animation for the face
    val faceScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "face_scale"
    )
    
    Text(
        text = faceExpression,
        fontSize = 28.sp,
        modifier = Modifier.scale(faceScale)
    )
}

@Composable
fun TipBubble(
    tip: String,
    emotion: ClaraEmotion = ClaraEmotion.NEUTRAL,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 400, easing = EaseOutBack),
        label = "tip_scale"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 300),
        label = "tip_alpha"
    )
    
    Card(
        modifier = modifier
            .scale(animatedScale)
            .graphicsLayer { alpha = animatedAlpha }
            .widthIn(max = 300.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with Clara's name and emotion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClaraFace(emotion = emotion)
                    Text(
                        text = "Clara",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ClaraAccent
                    )
                }
                
                IconButton(
                    onClick = onDismissed,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tip content with better typography
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
                lineHeight = 22.sp,
                textAlign = TextAlign.Start
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons with emotional context
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = getEmotionalResponse(emotion),
                    onClick = onDismissed,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.PRIMARY
                )
                
                ActionButton(
                    text = "More tips",
                    onClick = { /* Show more tips */ },
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.OUTLINE
                )
            }
        }
    }
}

@Composable
fun ClaraContextualTips(
    context: ClaraContext,
    onTipShown: (String) -> Unit = {},
    onTipDismissed: () -> Unit = {}
) {
    val contextualTips = remember(context) {
        getContextualTips(context)
    }
    
    LaunchedEffect(context) {
        contextualTips.firstOrNull()?.let { tip ->
            onTipShown(tip)
        }
    }
}

private fun getContextualTips(context: ClaraContext): List<String> {
    return when (context) {
        ClaraContext.HOME_SCREEN -> listOf(
            "Good morning! Ready to tackle today's cleaning plan? 🌅",
            "Swipe right on tasks to mark them as done! ✨",
            "Try Focus Mode for shorter, more manageable sessions.",
            "Your progress looks great! Keep up the momentum! 💪"
        )
        ClaraContext.TASK_DETAIL -> listOf(
            "Take your time with this task - quality over speed! 🎯",
            "Remember to take breaks between tasks.",
            "Need supplies? I can add them to your shopping list! 🛒"
        )
        ClaraContext.PLANNER -> listOf(
            "Planning ahead helps reduce stress! 📅",
            "You can create custom tasks for your specific needs.",
            "Try scheduling similar tasks together to save time."
        )
        ClaraContext.FAMILY_MODE -> listOf(
            "Great teamwork! Family cleaning is more fun together! 👨‍👩‍👧‍👦",
            "Fairness matters - everyone should contribute their share.",
            "Print the family plan so everyone can see their tasks! 🖨️"
        )
        ClaraContext.KIDS_MODE -> listOf(
            "You're doing amazing! Every little step counts! 🌟",
            "Take your time and do your best work!",
            "Ask for help if you need it - that's totally okay! 💕"
        )
        ClaraContext.AI_INSIGHTS -> listOf(
            "Your cleaning patterns are looking great! 📊",
            "I've noticed you're most productive on Tuesdays!",
            "Would you like me to adjust your schedule based on your habits?"
        )
        ClaraContext.PAPER_BRIDGE -> listOf(
            "Paper plans are great for offline cleaning! 📄",
            "QR codes make it easy to sync your progress back to the app.",
            "Don't forget to scan your completed tasks! 📱"
        )
        ClaraContext.TASK_COMPLETED -> listOf(
            "Excellent work! You're on fire today! 🔥",
            "One task down, feeling accomplished! 🎉",
            "Ready for the next challenge? You've got this! 💪"
        )
        ClaraContext.STREAK_MILESTONE -> listOf(
            "Incredible streak! You're building amazing habits! 🏆",
            "Consistency is key - you're doing it perfectly! ⭐",
            "This dedication is inspiring! Keep it up! 🌟"
        )
    }
}

private fun getEmotionalResponse(emotion: ClaraEmotion): String {
    return when (emotion) {
        ClaraEmotion.HAPPY -> "Thanks! 😊"
        ClaraEmotion.CELEBRATING -> "Awesome! 🎉"
        ClaraEmotion.ENCOURAGING -> "Got it! 💪"
        ClaraEmotion.SUPPORTIVE -> "Thanks! 🤗"
        ClaraEmotion.NEUTRAL -> "Got it!"
        ClaraEmotion.THINKING -> "Interesting! 🤔"
    }
}

enum class ClaraContext {
    HOME_SCREEN,
    TASK_DETAIL,
    PLANNER,
    FAMILY_MODE,
    KIDS_MODE,
    AI_INSIGHTS,
    PAPER_BRIDGE,
    TASK_COMPLETED,
    STREAK_MILESTONE
}

enum class ClaraEmotion {
    NEUTRAL,    // Default state
    HAPPY,      // Positive feedback
    CELEBRATING, // Task completion
    ENCOURAGING, // Motivation
    SUPPORTIVE,  // Help and guidance
    THINKING    // Processing or considering
}

@Composable
fun ClaraReaction(
    reaction: ClaraReactionType,
    modifier: Modifier = Modifier
) {
    val reactionText = when (reaction) {
        ClaraReactionType.HAPPY -> "😊"
        ClaraReactionType.CELEBRATE -> "🎉"
        ClaraReactionType.ENCOURAGE -> "💪"
        ClaraReactionType.SUPPORT -> "🤗"
        ClaraReactionType.BLINK -> "😉"
        ClaraReactionType.NOD -> "👍"
    }
    
    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "reaction_scale"
    )
    
    Text(
        text = reactionText,
        fontSize = 24.sp,
        modifier = modifier.scale(animatedScale)
    )
}

enum class ClaraReactionType {
    HAPPY,
    CELEBRATE,
    ENCOURAGE,
    SUPPORT,
    BLINK,
    NOD
}
