package com.ilyk.cleaningplanner.ui.qr

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed interface Action {
    data class Start(val minutes: Int) : Action
    data class Done(val minutes: Int) : Action
    data object Skip : Action
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFormSheet(
    roomName: String,
    estMinutes: Int,
    onAction: (Action) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp)) {
            Text("Quick action: $roomName", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            var minutes by remember { mutableStateOf(estMinutes) }
            OutlinedTextField(
                value = minutes.toString(),
                onValueChange = { it.toIntOrNull()?.let { m -> minutes = m } },
                label = { Text("Minutes") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text("Comments")
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf("Ran out of detergent", "Low trash bags", "Blocked access").forEach { chip ->
                    AssistChip(onClick = { }, label = { Text(chip) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onAction(Action.Start(minutes)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start")
                }
                OutlinedButton(
                    onClick = { onAction(Action.Done(minutes)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
                TextButton(onClick = { onAction(Action.Skip) }) {
                    Text("Skip")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

