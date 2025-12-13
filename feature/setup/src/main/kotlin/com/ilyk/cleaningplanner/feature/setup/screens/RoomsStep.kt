package com.ilyk.cleaningplanner.feature.setup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.feature.setup.RoomKind
import com.ilyk.cleaningplanner.feature.setup.SetupRoom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsStep(
    rooms: List<SetupRoom>,
    onAddRoom: (name: String, kind: RoomKind, notes: String) -> Unit,
    onUpdateRoom: (roomId: String, name: String, kind: RoomKind, notes: String) -> Unit,
    onRemoveRoom: (roomId: String) -> Unit,
    onNext: () -> Unit,
    canProceed: Boolean
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newRoomName by remember { mutableStateOf("") }
    var newRoomKind by remember { mutableStateOf(RoomKind.Living) }
    var kindDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Add your rooms",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add the rooms in your home that need cleaning. This helps personalize your cleaning plans.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Add room form
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = newRoomName,
                    onValueChange = { newRoomName = it },
                    label = { Text("Room Name") },
                    placeholder = { Text("e.g., Master Bedroom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = kindDropdownExpanded,
                    onExpandedChange = { kindDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = newRoomKind.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Room Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = kindDropdownExpanded,
                        onDismissRequest = { kindDropdownExpanded = false }
                    ) {
                        RoomKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(kind.displayName) },
                                onClick = {
                                    newRoomKind = kind
                                    kindDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (newRoomName.isNotBlank()) {
                            onAddRoom(newRoomName, newRoomKind, "")
                            newRoomName = ""
                        }
                    },
                    enabled = newRoomName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Room")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Room list
        if (rooms.isNotEmpty()) {
            Text(
                text = "Your Rooms (${rooms.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rooms, key = { it.id }) { room ->
                RoomItem(
                    room = room,
                    onRemove = { onRemoveRoom(room.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun RoomItem(
    room: SetupRoom,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = room.kind.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove room",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
