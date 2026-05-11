package com.ilyk.cleaningplanner.ui.avatar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

sealed interface ClaraEvent {
    object TaskCompleted : ClaraEvent
    data class ModeChanged(val mode: com.ilyk.cleaningplanner.domain.model.CleaningMode) : ClaraEvent
    object SuggestionAccepted : ClaraEvent
    object StreakMilestone : ClaraEvent
    object WelcomeComplete : ClaraEvent
}

data class ClaraState(
    val mood: ClaraMood,
    val message: String?,
    val isVisible: Boolean = true
)

enum class ClaraMood {
    Idle,
    Happy,
    Encouraging,
    Suggesting,
    Celebrating
}

@Composable
fun ClaraBubbleOverlay(
    state: ClaraState,
    onAvatarClicked: () -> Unit = {},
    onTipDismissed: () -> Unit = {}
) {
    if (!state.isVisible) return
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Speech bubble
        if (state.message != null) {
            SpeechBubble(
                message = state.message,
                onDismissed = onTipDismissed,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
        
        // Clara avatar
        ClaraAvatar(
            mood = state.mood,
            onClick = onAvatarClicked,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
fun ClaraAvatar(
    mood: ClaraMood,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "clara_breathing"
    )
    
    Box(
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(getMoodColor(mood))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = getMoodEmoji(mood),
            fontSize = 32.sp
        )
    }
}

@Composable
fun SpeechBubble(
    message: String,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        delay(5000) // Auto-dismiss after 5 seconds
        isVisible = false
        onDismissed()
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .padding(bottom = 80.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            isVisible = false
                            onDismissed()
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClaraController(
    initialState: ClaraState = ClaraState(ClaraMood.Idle, null)
): ClaraController {
    val _state = remember { mutableStateOf(initialState) }
    val state by _state
    
    val controller = remember {
        object : ClaraController {
            override val state: State<ClaraState> = _state
            
            override fun react(event: ClaraEvent) {
                when (event) {
                    is ClaraEvent.TaskCompleted -> {
                        _state.value = ClaraState(
                            mood = ClaraMood.Happy,
                            message = "Great job! Keep up the momentum! 💪"
                        )
                    }
                    is ClaraEvent.ModeChanged -> {
                        _state.value = ClaraState(
                            mood = ClaraMood.Suggesting,
                            message = "Switched to ${event.mode.name} mode. Perfect choice! ✨"
                        )
                    }
                    is ClaraEvent.SuggestionAccepted -> {
                        _state.value = ClaraState(
                            mood = ClaraMood.Celebrating,
                            message = "Excellent! That's a smart optimization! 🎉"
                        )
                    }
                    is ClaraEvent.StreakMilestone -> {
                        _state.value = ClaraState(
                            mood = ClaraMood.Celebrating,
                            message = "Amazing streak! You're on fire! 🔥"
                        )
                    }
                    is ClaraEvent.WelcomeComplete -> {
                        _state.value = ClaraState(
                            mood = ClaraMood.Happy,
                            message = "Welcome to CleanFlow! I'm here to help! 🌟"
                        )
                    }
                }
            }
                    }
                }
                
                // Clear message after delay
    LaunchedEffect(state.message) {
        if (state.message != null) {
                    kotlinx.coroutines.delay(3000)
                    _state.value = _state.value.copy(message = null)
        }
    }
    
    return controller
}

interface ClaraController {
    val state: State<ClaraState>
    fun react(event: ClaraEvent)
}

private fun getMoodColor(mood: ClaraMood): Color {
    return when (mood) {
        ClaraMood.Idle -> Color(0xFFE3F2FD)
        ClaraMood.Happy -> Color(0xFFE8F5E8)
        ClaraMood.Encouraging -> Color(0xFFFFF3E0)
        ClaraMood.Suggesting -> Color(0xFFF3E5F5)
        ClaraMood.Celebrating -> Color(0xFFFFEBEE)
    }
}

private fun getMoodEmoji(mood: ClaraMood): String {
    return when (mood) {
        ClaraMood.Idle -> "🤖"
        ClaraMood.Happy -> "😊"
        ClaraMood.Encouraging -> "💪"
        ClaraMood.Suggesting -> "💡"
        ClaraMood.Celebrating -> "🎉"
    }
}
