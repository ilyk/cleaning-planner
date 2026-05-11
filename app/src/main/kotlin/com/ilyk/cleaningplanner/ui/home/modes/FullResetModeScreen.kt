package com.ilyk.cleaningplanner.ui.home.modes
import com.ilyk.cleaningplanner.domain.model.status

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
 * Full Reset Mode - "Deep Clean Mission Control"
 * Weekend warrior mode - structured, satisfying thoroughness
 */
@Composable
fun FullResetModeScreen(
    tasks: List<Task>,
    onCompleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Group tasks by room
    val tasksByRoom = tasks.groupBy { it.roomId ?: "Other" }
    val completedRooms = tasksByRoom.count { (_, tasks) -> 
        tasks.all { it.status == TaskStatus.Done }
    }
    val totalRooms = tasksByRoom.size
    val progress = if (totalRooms > 0) completedRooms.toFloat() / totalRooms else 0f
    val totalTime = tasks.sumOf { it.estimatedDurationMinutes }
    
    var expandedRooms by remember { mutableStateOf(setOf<String>()) }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEFF4FF), Color(0xFFFFFFFF))
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            FullResetModeHeader()
        }
        
        // Progress Overview with Donut Chart
        item {
            FullResetProgressOverview(
                completedRooms = completedRooms,
                totalRooms = totalRooms,
                progress = progress,
                totalTime = totalTime
            )
        }
        
        // Tools Reminder Card
        item {
            ToolsReminderCard()
        }
        
        // Room Sections (Accordion)
        item {
            Text(
                text = "Rooms",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
        }
        
        items(tasksByRoom.toList()) { (room, roomTasks) ->
            RoomSectionCard(
                room = room,
                tasks = roomTasks,
                isExpanded = room in expandedRooms,
                onToggleExpand = {
                    expandedRooms = if (room in expandedRooms) {
                        expandedRooms - room
                    } else {
                        expandedRooms + room
                    }
                },
                onCompleteTask = onCompleteTask
            )
        }
        
        // Summary Button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A7AFE)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Generate Full-Reset PDF Checklist",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun FullResetModeHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🧼 ",
                fontSize = 32.sp
            )
            Text(
                text = "Full Reset",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
        }
        Text(
            text = "Deep clean day — one room at a time",
            fontSize = 14.sp,
            color = Color(0xFF718096)
        )
    }
}

@Composable
fun FullResetProgressOverview(
    completedRooms: Int,
    totalRooms: Int,
    progress: Float,
    totalTime: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Big donut chart
            Box(
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(100.dp),
                    color = Color(0xFF3A7AFE),
                    strokeWidth = 8.dp,
                    trackColor = Color(0xFFE2E8F0)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$completedRooms/$totalRooms",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    Text(
                        text = "rooms",
                        fontSize = 12.sp,
                        color = Color(0xFF718096)
                    )
                }
            }
            
            Column {
                Text(
                    text = "Progress Overview",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D3748)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total time: ${totalTime / 60}h ${totalTime % 60}min",
                    fontSize = 14.sp,
                    color = Color(0xFF718096)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (progress > 0 && progress < 1) {
                    val remainingRooms = totalRooms - completedRooms
                    Text(
                        text = "$remainingRooms rooms remaining",
                        fontSize = 14.sp,
                        color = Color(0xFF3A7AFE),
                        fontWeight = FontWeight.Medium
                    )
                } else if (progress == 1f) {
                    Text(
                        text = "✨ All rooms complete!",
                        fontSize = 14.sp,
                        color = Color(0xFF38B2AC),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ToolsReminderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE6F4FF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = Color(0xFF3A7AFE),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Tools to prepare",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D3748)
                )
                Text(
                    text = "Vacuum, detergent, mop, microfiber cloths",
                    fontSize = 13.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

@Composable
fun RoomSectionCard(
    room: String,
    tasks: List<Task>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCompleteTask: (String) -> Unit
) {
    val completedCount = tasks.count { it.status == TaskStatus.Done }
    val totalCount = tasks.size
    val allDone = completedCount == totalCount && totalCount > 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) Color(0xFFE6FFFA) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Room header - Always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (allDone) Icons.Default.CheckCircle else Icons.Default.Circle,
                        contentDescription = null,
                        tint = if (allDone) Color(0xFF38B2AC) else Color(0xFF3A7AFE),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = room,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748)
                        )
                        Text(
                            text = "$completedCount/$totalCount tasks complete",
                            fontSize = 13.sp,
                            color = Color(0xFF718096)
                        )
                    }
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF718096)
                )
            }
            
            // Expandable task list
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    tasks.forEach { task ->
                        FullResetTaskItem(
                            task = task,
                            onComplete = { onCompleteTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullResetTaskItem(
    task: Task,
    onComplete: () -> Unit
) {
    val isDone = task.status == TaskStatus.Done
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isDone) Color(0xFFF7FAFC) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { if (!isDone) onComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF38B2AC),
                    uncheckedColor = Color(0xFF3A7AFE)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    color = if (isDone) Color(0xFF718096) else Color(0xFF2D3748),
                    fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium
                )
            }
        }
        
        Box(
            modifier = Modifier
                .background(
                    color = if (isDone) Color(0xFFE2E8F0) else Color(0xFFE6F4FF),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${task.estimatedDurationMinutes} min",
                fontSize = 12.sp,
                color = if (isDone) Color(0xFF718096) else Color(0xFF3A7AFE),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

