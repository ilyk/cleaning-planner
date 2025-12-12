package com.ilyk.cleaningplanner.ui.home.modes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyk.cleaningplanner.core.model.domain.Task
import com.ilyk.cleaningplanner.core.model.TaskStatus

/**
 * Low Energy Mode - "Gentle Guidance & Kind Minimalism"
 * A compassionate view for tired days
 */
@Composable
fun LowEnergyModeScreen(
    tasks: List<Task>,
    onCompleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Show only 3 easiest tasks
    val easyTasks = tasks
        .filter { it.status == TaskStatus.Pending }
        .sortedBy { it.estimatedMin }
        .take(3)
    
    val completedCount = easyTasks.count { it.status == TaskStatus.Done }
    val progress = if (easyTasks.isNotEmpty()) completedCount.toFloat() / easyTasks.size else 0f
    
    // Rotating motivational quotes
    val quotes = listOf(
        "Progress, not perfection.",
        "Even tidying one corner matters.",
        "Small steps still count.",
        "You're doing great — go at your own pace."
    )
    val currentQuote by remember { mutableStateOf(quotes.random()) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF5F7FA), Color(0xFFEBEFF3))
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        LowEnergyModeHeader()
        
        // Mood Message Card
        MoodMessageCard(quote = currentQuote)
        
        // Energy Bar
        EnergyLevelBar()
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Today's Focus - Just 3 tasks
        Text(
            text = "Today's focus",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D3748)
        )
        
        if (easyTasks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🌙",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You're all set for today",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2D3748),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Rest well — you've earned it",
                        fontSize = 14.sp,
                        color = Color(0xFF718096),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            easyTasks.forEach { task ->
                LowEnergyTaskCard(
                    task = task,
                    onComplete = { onCompleteTask(task.id) }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Progress Widget with ambient pulsing glow
        LowEnergyProgressWidget(progress = progress)
    }
}

@Composable
fun LowEnergyModeHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🌙 ",
                fontSize = 32.sp
            )
            Text(
                text = "Take it easy",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2D3748)
            )
        }
        Text(
            text = "Small steps still count",
            fontSize = 14.sp,
            color = Color(0xFF718096)
        )
    }
}

@Composable
fun MoodMessageCard(quote: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8E3FF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9C88FF).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✨",
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = quote,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2D3748),
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun EnergyLevelBar() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Effort level today",
                fontSize = 13.sp,
                color = Color(0xFF718096)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Low",
                    fontSize = 12.sp,
                    color = Color(0xFF9C88FF),
                    fontWeight = FontWeight.Medium
                )
                
                LinearProgressIndicator(
                    progress = { 0.3f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF9C88FF),
                    trackColor = Color(0xFFE2E8F0)
                )
                
                Text(
                    text = "Medium",
                    fontSize = 12.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

@Composable
fun LowEnergyTaskCard(
    task: Task,
    onComplete: () -> Unit
) {
    val isDone = task.status == TaskStatus.Done
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) Color(0xFFF7FAFC) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDone) Color(0xFF718096) else Color(0xFF2D3748)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${task.room} · ${task.estimatedMin} min",
                        fontSize = 14.sp,
                        color = Color(0xFF718096)
                    )
                }
                
                if (!isDone) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFE8E3FF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Easy",
                            fontSize = 12.sp,
                            color = Color(0xFF9C88FF),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !isDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C88FF),
                    disabledContainerColor = Color(0xFFE2E8F0)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.Check else Icons.Default.Done,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDone) "Completed" else "Mark as done",
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun LowEnergyProgressWidget(progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Ambient pulsing glow
        Box(
            modifier = Modifier
                .size(120.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(Color(0xFF9C88FF).copy(alpha = 0.2f))
        )
        
        // Progress ring
        Box(
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(100.dp),
                color = Color(0xFF9C88FF),
                strokeWidth = 6.dp,
                trackColor = Color(0xFFE2E8F0)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2D3748)
                )
                Text(
                    text = "done",
                    fontSize = 12.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

