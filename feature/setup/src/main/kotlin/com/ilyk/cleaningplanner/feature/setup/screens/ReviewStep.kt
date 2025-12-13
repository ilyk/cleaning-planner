package com.ilyk.cleaningplanner.feature.setup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.feature.setup.SetupMember
import com.ilyk.cleaningplanner.feature.setup.SetupPet
import com.ilyk.cleaningplanner.feature.setup.SetupPreferences
import com.ilyk.cleaningplanner.feature.setup.SetupRoom

@Composable
fun ReviewStep(
    homeName: String,
    rooms: List<SetupRoom>,
    members: List<SetupMember>,
    pets: List<SetupPet>,
    preferences: SetupPreferences,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onEditRooms: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Review Your Setup",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Make sure everything looks correct before we create your home.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Home name
        ReviewSection(
            icon = Icons.Default.Home,
            title = "Home Name",
            content = homeName
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Rooms
        ReviewSection(
            icon = Icons.Default.MeetingRoom,
            title = "Rooms",
            content = if (rooms.isEmpty()) "No rooms added" else rooms.joinToString(", ") {
                "${it.name} (${it.kind.displayName})"
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Members
        ReviewSection(
            icon = Icons.Default.Person,
            title = "Household Members",
            content = if (members.isEmpty()) "No members added" else members.joinToString(", ") {
                "${it.name} (${it.role.displayName})"
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pets
        ReviewSection(
            icon = Icons.Default.Pets,
            title = "Pets",
            content = if (pets.isEmpty()) "No pets" else pets.joinToString(", ") {
                "${it.name} (${it.type.displayName})"
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Preferences
        ReviewSection(
            icon = Icons.Default.Schedule,
            title = "Preferences",
            content = buildString {
                if (preferences.preferredCleaningTimes.isNotEmpty()) {
                    append("Preferred times: ${preferences.preferredCleaningTimes.joinToString(", ")}")
                }
                if (preferences.busyDays.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append("Busy days: ${preferences.busyDays.joinToString(", ")}")
                }
                if (isEmpty()) {
                    append("Default preferences")
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(24.dp))

        // Summary
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "What happens next?",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                SummaryItem("Your home profile will be created")
                SummaryItem("CleanFlow will generate personalized cleaning plans")
                SummaryItem("You can start using your cleaning schedule right away")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            enabled = !isLoading && rooms.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Create My Home")
            }
        }

        if (rooms.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please add at least one room to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ReviewSection(
    icon: ImageVector,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
