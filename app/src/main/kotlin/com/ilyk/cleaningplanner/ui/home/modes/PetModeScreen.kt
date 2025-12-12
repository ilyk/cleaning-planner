package com.ilyk.cleaningplanner.ui.home.modes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import com.ilyk.cleaningplanner.core.model.domain.Task
import com.ilyk.cleaningplanner.core.model.TaskStatus

/**
 * Pet Mode - "Playful, Adaptive, and Aware of Fur & Fun"
 * For homes with pets - integrates extra tasks, dynamic sensors, and joyful vibe
 */
@Composable
fun PetModeScreen(
    tasks: List<Task>,
    onCompleteTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val petTasks = tasks // In real app, filter by pet-related tags
    val completedCount = petTasks.count { it.status == TaskStatus.Done }
    val progress = if (petTasks.isNotEmpty()) completedCount.toFloat() / petTasks.size else 0f
    
    val funFacts = listOf(
        "Did you know microfiber grabs more fur?",
        "Pets feel calmer in clean spaces!",
        "Regular vacuuming reduces pet dander by 80%",
        "Clean floors = happy paws!",
        "Lint rollers are your best friend 🐾"
    )
    val currentFact by remember { mutableStateOf(funFacts.random()) }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE6F4FD), Color(0xFFE8FAF5))
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            PetModeHeader()
        }
        
        // Pet Tracker Card
        item {
            PetTrackerCard()
        }
        
        // Fun Fact Card
        item {
            FunFactCard(fact = currentFact)
        }
        
        // Today's Plan header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's pet-friendly plan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
                Text(
                    text = "${petTasks.size} tasks",
                    fontSize = 14.sp,
                    color = Color(0xFF718096)
                )
            }
        }
        
        // Task list
        items(petTasks) { task ->
            PetModeTaskCard(
                task = task,
                onComplete = { onCompleteTask(task.id) }
            )
        }
        
        // Progress Meter with paw prints
        item {
            Spacer(modifier = Modifier.height(8.dp))
            PawPrintProgressMeter(
                progress = progress,
                completedCount = completedCount,
                totalCount = petTasks.size
            )
        }
        
        // Add Pet Reminder button
        item {
            OutlinedButton(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF49B4F2)
                )
            ) {
                Icon(imageVector = Icons.Default.Pets, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Pet Reminder")
            }
        }
    }
}

@Composable
fun PetModeHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🐾 ",
                fontSize = 32.sp
            )
            Text(
                text = "Pet Mode Active",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
        }
        Text(
            text = "Keeping it fresh for furry friends",
            fontSize = 14.sp,
            color = Color(0xFF718096)
        )
    }
}

@Composable
fun PetTrackerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Pet Tracker",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
                
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = Color(0xFF49B4F2),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pet avatars with info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PetAvatar(
                    emoji = "🐕",
                    name = "Max",
                    lastCleaned = "3 days ago"
                )
                PetAvatar(
                    emoji = "🐈",
                    name = "Luna",
                    lastCleaned = "2 days ago"
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Focus: Floors, couches, air filters",
                fontSize = 13.sp,
                color = Color(0xFF718096)
            )
        }
    }
}

@Composable
fun PetAvatar(emoji: String, name: String, lastCleaned: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFFE6F4FD)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D3748)
        )
        Text(
            text = lastCleaned,
            fontSize = 11.sp,
            color = Color(0xFF718096)
        )
    }
}

@Composable
fun FunFactCard(fact: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFE8E8)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B9D).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💡",
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "Pet Tip",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF718096)
                )
                Text(
                    text = fact,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2D3748)
                )
            }
        }
    }
}

@Composable
fun PetModeTaskCard(
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
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Paw icon badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE6F4FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🐾",
                        fontSize = 20.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDone) Color(0xFF718096) else Color(0xFF2D3748)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${task.room} · ${task.estimatedMin} min",
                        fontSize = 13.sp,
                        color = Color(0xFF718096)
                    )
                }
            }
            
            if (!isDone) {
                IconButton(
                    onClick = onComplete
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = "Complete",
                        tint = Color(0xFF49B4F2),
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = Color(0xFF38B2AC),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun PawPrintProgressMeter(
    progress: Float,
    completedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progress",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D3748)
                )
                Text(
                    text = "$completedCount/$totalCount tasks",
                    fontSize = 14.sp,
                    color = Color(0xFF718096)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Paw prints marching forward
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF49B4F2),
                    trackColor = Color(0xFFE2E8F0)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (progress == 1f) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🐾 Doggo approved ✅",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF38B2AC)
                    )
                }
            }
        }
    }
}

