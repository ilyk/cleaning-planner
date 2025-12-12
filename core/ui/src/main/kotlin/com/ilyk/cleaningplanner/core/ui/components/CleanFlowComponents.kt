package com.ilyk.cleaningplanner.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyk.cleaningplanner.core.ui.theme.*
import com.ilyk.cleaningplanner.core.model.TaskStatus

/**
 * CleanFlow Design System Components
 * Following the calm, minimalist design with soft teal/mint accents
 */

@Composable
fun CleanFlowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Int = 2,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(24.dp), // Increased corner radius for v3
        colors = CardDefaults.cardColors(
            containerColor = CardBackground,
            contentColor = OnSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp), // Reduced padding for edge-to-edge feel
            content = content
        )
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Int = 80,
    strokeWidth: Int = 6,
    color: Color = TealAccent,
    backgroundColor: Color = LightGray,
    showPercentage: Boolean = true,
    showEmoji: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic),
        label = "progress"
    )
    
    // Pulsing animation when progress is high
    val pulseScale by animateFloatAsState(
        targetValue = if (progress >= 0.8f) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(
                color = backgroundColor,
                radius = (size - strokeWidth).toFloat() / 2,
                style = Stroke(width = strokeWidth.toFloat(), cap = StrokeCap.Round)
            )
        }
        
        // Progress circle
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toFloat(), cap = StrokeCap.Round)
            )
        }
        
        // Content in center
        Box(
            modifier = Modifier.scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            if (showEmoji) {
                val emoji = when {
                    progress >= 1f -> "🎉"
                    progress >= 0.8f -> "💪"
                    progress >= 0.5f -> "😊"
                    else -> "🌱"
                }
                Text(
                    text = emoji,
                    fontSize = (size * 0.3).sp
                )
            } else if (showPercentage) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
            }
        }
    }
}

@Composable
fun TaskChip(
    emoji: String,
    title: String,
    estimatedTime: String,
    status: TaskStatus = TaskStatus.Pending,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onSwipeLeft: () -> Unit = {}
) {
    val backgroundColor = when (status) {
        TaskStatus.Pending -> CardBackground
        TaskStatus.Done -> GentleHighlight
        TaskStatus.Skipped -> WarningColor.copy(alpha = 0.1f)
    }
    
    val borderColor = when (status) {
        TaskStatus.Pending -> InactiveBorder
        TaskStatus.Done -> SuccessColor
        TaskStatus.Skipped -> WarningColor
    }
    
    // Smooth scale animation for completed tasks
    val completedScale by animateFloatAsState(
        targetValue = if (status == TaskStatus.Done) 1.02f else 1f,
        animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.EaseOutCubic),
        label = "completed_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(completedScale)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (status == TaskStatus.Done) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji with subtle animation
            Text(
                text = emoji,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Text
                )
                Text(
                    text = estimatedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            // Status indicator with better visual feedback
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(borderColor)
            )
        }
    }
}

@Composable
fun ModeSelector(
    selectedMode: CleaningMode,
    onModeSelected: (CleaningMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CleaningMode.values().forEach { mode ->
            ModeChip(
                mode = mode,
                isSelected = selectedMode == mode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
fun ModeChip(
    mode: CleaningMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        ActiveFill // Soft mint fill for active state
    } else {
        PillBackground // White background for inactive
    }
    
    val borderColor = if (isSelected) {
        Color.Transparent // No border for active state
    } else {
        InactiveBorder // Subtle border for inactive state
    }
    
    val textColor = if (isSelected) {
        PillText // Dark text on light background
    } else {
        PillText // Same text color
    }

    // Pill button with rounded corners (border-radius: 9999px)
    Card(
        modifier = modifier
            .clickable { onClick() }
            .height(48.dp), // Fixed height as specified
        shape = RoundedCornerShape(9999.dp), // Pill shape
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // No shadows
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 20.dp), // Horizontal padding for pill
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = mode.emoji,
                fontSize = 18.sp
            )
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.titleMedium, // Inter Medium 16px
                fontWeight = FontWeight.Medium,
                color = textColor,
                letterSpacing = 0.16.sp // +1% letter spacing
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY
) {
    val backgroundColor = when (variant) {
        ButtonVariant.PRIMARY -> TealAccent
        ButtonVariant.SECONDARY -> MintAccent
        ButtonVariant.OUTLINE -> PillBackground
    }
    
    val contentColor = when (variant) {
        ButtonVariant.PRIMARY -> OnPrimary
        ButtonVariant.SECONDARY -> OnSecondary
        ButtonVariant.OUTLINE -> TealAccent
    }
    
    val borderColor = when (variant) {
        ButtonVariant.PRIMARY -> Color.Transparent
        ButtonVariant.SECONDARY -> Color.Transparent
        ButtonVariant.OUTLINE -> InactiveBorder
    }

    // Pill button design - no thick borders, no shadows
    Card(
        modifier = modifier
            .clickable { onClick() }
            .height(48.dp), // Fixed height
        shape = RoundedCornerShape(9999.dp), // Pill shape
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // No shadows
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium, // Inter Medium 16px
                fontWeight = FontWeight.Medium,
                color = contentColor,
                letterSpacing = 0.16.sp // +1% letter spacing
            )
        }
    }
}

@Composable
fun ConfettiAnimation(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    emotion: ClaraEmotion = ClaraEmotion.CELEBRATING
) {
    if (isVisible) {
        // Enhanced confetti effect with emotional context
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            repeat(8) { index ->
                val animatedScale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 1200, delayMillis = index * 100),
                    label = "confetti_$index"
                )
                
                val animatedRotation by animateFloatAsState(
                    targetValue = if (isVisible) 360f else 0f,
                    animationSpec = tween(durationMillis = 1000),
                    label = "rotation_$index"
                )
                
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(animatedScale)
                        .graphicsLayer { rotationZ = animatedRotation }
                        .background(
                            color = when (index % 4) {
                                0 -> TealAccent
                                1 -> MintAccent
                                2 -> EncouragementColor
                                else -> SuccessColor
                            },
                            shape = CircleShape
                        )
                )
            }
            
            // Central celebration emoji
            val celebrationEmoji by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 800, delayMillis = 200),
                label = "celebration"
            )
            
            Text(
                text = when (emotion) {
                    ClaraEmotion.CELEBRATING -> "🎉"
                    ClaraEmotion.HAPPY -> "😊"
                    ClaraEmotion.ENCOURAGING -> "💪"
                    else -> "✨"
                },
                fontSize = 32.sp,
                modifier = Modifier.scale(celebrationEmoji)
            )
        }
    }
}

@Composable
fun ClaraAvatarBubble(
    isVisible: Boolean,
    isExpanded: Boolean,
    currentTip: String?,
    emotion: ClaraEmotion = ClaraEmotion.NEUTRAL,
    onTipDismissed: () -> Unit,
    onAvatarClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Breathing glow animation
    val breathingScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Clara Avatar - Bottom right floating orb
        Box(
            modifier = Modifier
                .padding(24.dp) // Margin 24px from edges
                .size(64.dp)
                .scale(breathingScale)
                .clickable { onAvatarClicked() }
                .background(
                    color = ClaraGlow.copy(alpha = glowAlpha),
                    shape = CircleShape
                )
                .background(
                    color = ClaraAccent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Clara's face/emoji based on emotion
            val claraEmoji = when (emotion) {
                ClaraEmotion.CELEBRATING -> "🎉"
                ClaraEmotion.HAPPY -> "😊"
                ClaraEmotion.ENCOURAGING -> "💪"
                ClaraEmotion.SUPPORTIVE -> "🤗"
                ClaraEmotion.THINKING -> "🤔"
                ClaraEmotion.NEUTRAL -> "✨"
            }
            
            Text(
                text = claraEmoji,
                fontSize = 28.sp
            )
        }
        
        // Floating speech bubble when expanded
        if (isExpanded && currentTip != null) {
            val bubbleScale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(300, easing = EaseOutCubic),
                label = "bubble_scale"
            )
            
            Card(
                modifier = Modifier
                    .padding(bottom = 100.dp, end = 16.dp)
                    .scale(bubbleScale)
                    .widthIn(max = 280.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = currentTip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onTipDismissed,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = TealAccent
                            )
                        ) {
                            Text("Got it!")
                        }
                    }
                }
            }
        }
    }
}

// Enums for type safety

enum class CleaningMode(val emoji: String, val displayName: String) {
    FOCUS("⚡", "Focus"),
    LOW_ENERGY("🌙", "Low Energy"),
    FULL_RESET("🧼", "Full Reset"),
    PET_MODE("🐾", "Pet Mode")
}

enum class ButtonVariant {
    PRIMARY, SECONDARY, OUTLINE
}

