package com.ilyk.cleaningplanner.ui.home.modes

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ilyk.cleaningplanner.domain.model.CleaningMode

/**
 * Floating mode selector button that opens a dialog
 */
@Composable
fun FloatingModeSelector(
    currentMode: CleaningMode,
    onModeChange: (CleaningMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    
    // Floating action button
    FloatingActionButton(
        onClick = { showDialog = true },
        modifier = modifier,
        containerColor = Color(0xFF38B2AC),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = getModeEmoji(currentMode),
                fontSize = 20.sp
            )
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Switch mode",
                modifier = Modifier.size(20.dp)
            )
        }
    }
    
    // Mode selector dialog
    if (showDialog) {
        ModeSelectorDialog(
            currentMode = currentMode,
            onModeChange = { mode ->
                onModeChange(mode)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Dialog for selecting cleaning mode
 */
@Composable
fun ModeSelectorDialog(
    currentMode: CleaningMode,
    onModeChange: (CleaningMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Switch Cleaning Mode",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF718096)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Mode options
                val modes = listOf(
                    Triple(CleaningMode.FOCUS, "⚡ Focus Mode", "Quick wins & momentum"),
                    Triple(CleaningMode.FULL_RESET, "🧼 Full Reset", "Deep clean mission control"),
                    Triple(CleaningMode.LOW_ENERGY, "🌙 Low Energy", "Gentle guidance"),
                    Triple(CleaningMode.PET_MODE, "🐾 Pet Mode", "Playful & pet-aware")
                )
                
                modes.forEach { (mode, title, description) ->
                    ModeDialogOption(
                        emoji = getModeEmoji(mode),
                        title = title,
                        description = description,
                        isSelected = currentMode == mode,
                        onClick = { onModeChange(mode) }
                    )
                    if (mode != modes.last().first) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Single mode option in the dialog
 */
@Composable
fun ModeDialogOption(
    emoji: String,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE6FFFA) else Color(0xFFF7FAFC)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            2.dp,
            Color(0xFF38B2AC)
        ) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji in circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color(0xFF38B2AC).copy(alpha = 0.2f)
                        else Color.White
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Title and description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D3748)
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color(0xFF718096)
                )
            }
            
            // Checkmark if selected
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF38B2AC),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Get emoji for a mode
 */
fun getModeEmoji(mode: CleaningMode): String = when (mode) {
    CleaningMode.FOCUS -> "⚡"
    CleaningMode.FULL_RESET -> "🧼"
    CleaningMode.LOW_ENERGY -> "🌙"
    CleaningMode.PET_MODE -> "🐾"
}



