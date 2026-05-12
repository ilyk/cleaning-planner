package com.ilyk.cleaningplanner.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.domain.model.TaskPriority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomTaskSheet(
    rooms: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, roomId: String?, estimatedDurationMinutes: Int, priority: TaskPriority) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var selectedRoom by remember { mutableStateOf(rooms.firstOrNull()) }
    var minutes by remember { mutableStateOf(15f) }
    var priority by remember { mutableStateOf(TaskPriority.NOW) }
    val mint = Color(0xFF54C5B6)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFF9FAFA)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Add a custom task",
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (rooms.isNotEmpty()) {
                Text("Room", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rooms.forEach { room ->
                        val selected = room == selectedRoom
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(
                                selected = selected,
                                onClick = { selectedRoom = room }
                            )
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { selectedRoom = room }
                            )
                            Text(room)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("Estimated duration: ${minutes.toInt()} min", fontWeight = FontWeight.Medium)
            Slider(
                value = minutes,
                onValueChange = { minutes = it },
                valueRange = 5f..120f,
                steps = 22 // 5..120 in 5-min increments
            )
            Spacer(Modifier.height(8.dp))

            Text("Priority", fontWeight = FontWeight.Medium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TaskPriority.values().forEach { p ->
                    val selected = p == priority
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(
                            selected = selected,
                            onClick = { priority = p }
                        )
                    ) {
                        RadioButton(selected = selected, onClick = { priority = p })
                        Text(p.name)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSubmit(title, selectedRoom, minutes.toInt(), priority)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = mint),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add task", color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
