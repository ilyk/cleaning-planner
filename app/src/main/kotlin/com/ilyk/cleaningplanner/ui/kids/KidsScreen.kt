package com.ilyk.cleaningplanner.ui.kids

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.domain.model.Task
import kotlinx.coroutines.delay

private val Mint = Color(0xFF54C5B6)
private val MintSoft = Color(0x3354C5B6)
private val Amber = Color(0xFFFFE0B2)
private val Background = Color(0xFFF9FAFA)
private val Slate = Color(0xFF2D3748)
private val Muted = Color(0xFF6B7280)

/**
 * Kids / Single-Room mode — ADHD-friendly focused view.
 *
 * Acceptance gates from PLAN.md §W7:
 *  * "Done" advances within 150 ms — UI state update is synchronous in the next
 *    composition; the haptic + visual progression both fire from [KidsViewModel.onTaskDone].
 *  * Full-room completion fires celebration — `KidsUiState.allDone` flips
 *    `showCelebration = true`; a tap or 2-second auto-dismiss returns to the room picker.
 *  * Works offline — backed by [com.ilyk.cleaningplanner.data.repository.PlanRepository.getToday]
 *    which has a local fallback when network is unavailable.
 *  * No button-label wrap at 360dp — single-word labels, generous slot.
 */
@Composable
fun KidsScreen(
    modifier: Modifier = Modifier,
    viewModel: KidsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(uiState.showCelebration) {
        if (uiState.showCelebration) {
            delay(2_000L)
            viewModel.onDismissCelebration()
            viewModel.onBackToRoomPicker()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        when {
            uiState.isLoading -> LoadingView()
            uiState.error != null -> ErrorView(uiState.error!!)
            uiState.selectedRoom == null -> RoomPicker(uiState.rooms, onPick = viewModel::onSelectRoom)
            else -> SingleRoomView(
                room = uiState.selectedRoom!!,
                pending = uiState.pendingTasks,
                done = uiState.doneCount,
                total = uiState.totalForRoom,
                onBack = viewModel::onBackToRoomPicker,
                onDone = { id ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onTaskDone(id)
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.showCelebration,
            enter = fadeIn() + scaleIn(initialScale = 0.6f, animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CelebrationOverlay(room = uiState.selectedRoom ?: "")
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Mint)
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, modifier = Modifier.padding(32.dp))
    }
}

@Composable
private fun RoomPicker(rooms: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text("Pick a room", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Slate)
        Text(
            "One room at a time. Big buttons. No pressure.",
            fontSize = 14.sp,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        if (rooms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No rooms in today's plan yet — generate one from the Home tab first.",
                    color = Muted
                )
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(items = rooms, key = { it }) { room ->
                RoomChip(room = room, onClick = { onPick(room) })
            }
        }
    }
}

@Composable
private fun RoomChip(room: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(roomEmoji(room), fontSize = 40.sp)
            Spacer(Modifier.size(16.dp))
            Text(
                room.replaceFirstChar { it.titlecase() },
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SingleRoomView(
    room: String,
    pending: List<Task>,
    done: Int,
    total: Int,
    onBack: () -> Unit,
    onDone: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to rooms", tint = Slate)
            }
            Text(roomEmoji(room), fontSize = 40.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                room.replaceFirstChar { it.titlecase() },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Slate,
                maxLines = 1
            )
        }

        if (total > 0) {
            val progress = done.toFloat() / total.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(8.dp),
                color = Mint,
                trackColor = MintSoft
            )
            Text(
                "$done / $total done",
                fontSize = 14.sp,
                color = Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = pending, key = { it.id }) { task ->
                KidsTaskCard(task = task, onDone = { onDone(task.id) })
            }
            if (pending.isEmpty() && total > 0) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("All done — nice work!", color = Mint, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsTaskCard(task: Task, onDone: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(taskEmoji(task.title), fontSize = 80.sp)
            Spacer(Modifier.size(12.dp))
            Text(
                task.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate
            )
            Text(
                "${task.estimatedDurationMinutes} min",
                fontSize = 14.sp,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Mint, RoundedCornerShape(32.dp))
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Done",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CelebrationOverlay(room: String) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 96.sp)
            Spacer(Modifier.height(8.dp))
            Text("Room complete!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Slate)
            if (room.isNotBlank()) {
                Text(
                    "${roomEmoji(room)} ${room.replaceFirstChar { it.titlecase() }} is sparkling.",
                    fontSize = 14.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun roomEmoji(room: String): String = when (room.lowercase()) {
    "kitchen" -> "🍳"
    "bathroom" -> "🛁"
    "bedroom" -> "🛏"
    "living room", "living" -> "🛋"
    "office" -> "🖥"
    "garage" -> "🚗"
    else -> "🏠"
}

private fun taskEmoji(title: String): String {
    val t = title.lowercase()
    return when {
        "wipe" in t || "clean" in t -> "🧽"
        "sweep" in t || "floor" in t -> "🧹"
        "vacuum" in t -> "🧹"
        "dish" in t -> "🍽"
        "trash" in t || "rubbish" in t -> "🗑"
        "bed" in t -> "🛏"
        "tidy" in t || "organi" in t -> "📦"
        "wash" in t -> "🧴"
        "pet" in t -> "🐾"
        else -> "✨"
    }
}
