package com.ilyk.cleaningplanner.ui.home.modes
import com.ilyk.cleaningplanner.domain.model.status

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilyk.cleaningplanner.domain.model.Task
import com.ilyk.cleaningplanner.core.model.TaskStatus

/**
 * Focus Mode - "Quick Wins & Momentum"
 * Productivity-driven view for short bursts and dopamine hits
 */
@Composable
fun FocusModeScreen(
    tasks: List<Task>,
    onCompleteTask: (String) -> Unit,
    onStartTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val completedCount = tasks.count { it.status == TaskStatus.Done }
    val totalCount = tasks.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val totalTime = tasks.sumOf { it.estimatedDurationMinutes }
    
    val currentTask = tasks.firstOrNull { it.status == TaskStatus.Pending }
    val nextTasks = tasks.filter { it.status == TaskStatus.Pending }.drop(1).take(3)
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE8FAF5), Color(0xFFFFFFFF))
                )
            )
            .padding(20.dp)
    ) {
        // Header with animated pulse ring
        FocusModeHeader(
            progress = progress,
            completedCount = completedCount,
            totalCount = totalCount
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Mode Banner
        FocusModeBanner(
            taskCount = totalCount,
            totalTime = totalTime
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Current Task Card - Large, centered
        currentTask?.let { task ->
            CurrentTaskCard(
                task = task,
                onStart = { onStartTask(task.id) },
                onComplete = { onCompleteTask(task.id) }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
        }
        
        // Next Up - Horizontal swipeable
        if (nextTasks.isNotEmpty()) {
            Text(
                text = "Next Up",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2D3748)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(nextTasks) { task ->
                    NextUpTaskCard(task)
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Progress Bar with confetti line
        FocusProgressBar(progress = progress)
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Quick Actions
        FocusQuickActions()
    }
}

@Composable
fun FocusModeHeader(
    progress: Float,
    completedCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚡ ",
                    fontSize = 28.sp
                )
                Text(
                    text = "Good Morning!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
            }
            Text(
                text = "Quick wins to build momentum",
                fontSize = 14.sp,
                color = Color(0xFF718096)
            )
        }
        
        // Animated pulse ring with progress
        Box(
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(60.dp),
                color = Color(0xFFFFC107),
                strokeWidth = 4.dp,
                trackColor = Color(0xFFE2E8F0)
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
        }
    }
}

@Composable
fun FocusModeBanner(
    taskCount: Int,
    totalTime: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$taskCount tasks · $totalTime min total",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
                Text(
                    text = "All under 15 minutes",
                    fontSize = 14.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

@Composable
fun CurrentTaskCard(
    task: Task,
    onStart: () -> Unit,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current Task",
                fontSize = 14.sp,
                color = Color(0xFF718096),
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = task.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Room,
                    contentDescription = null,
                    tint = Color(0xFF38B2AC),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${task.roomId} · ${task.estimatedDurationMinutes} min",
                    fontSize = 14.sp,
                    color = Color(0xFF718096)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Start button (Pomodoro-style)
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF38B2AC)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Focus",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Quick complete button
            TextButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mark as Done")
            }
        }
    }
}

@Composable
fun NextUpTaskCard(task: Task) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2D3748),
                maxLines = 2
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFE6FFFA),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${task.estimatedDurationMinutes} min",
                        fontSize = 12.sp,
                        color = Color(0xFF38B2AC),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FocusProgressBar(progress: Float) {
    Column {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = Color(0xFFFFC107),
            trackColor = Color(0xFFE2E8F0)
        )
    }
}

@Composable
fun FocusQuickActions() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF38B2AC)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Start Focus")
        }
        
        OutlinedButton(
            onClick = { },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Add Task")
        }
    }
}

