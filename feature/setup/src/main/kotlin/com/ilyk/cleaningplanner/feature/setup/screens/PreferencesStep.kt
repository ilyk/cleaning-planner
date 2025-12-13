package com.ilyk.cleaningplanner.feature.setup.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.feature.setup.SetupPreferences

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesStep(
    preferences: SetupPreferences,
    onUpdatePreferences: (SetupPreferences) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val timeOptions = listOf(
        "morning" to "Morning (6-12)",
        "afternoon" to "Afternoon (12-18)",
        "evening" to "Evening (18-22)"
    )

    val dayOptions = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "When do you prefer to clean?",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Help us schedule tasks at times that work best for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Preferred cleaning times
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Preferred Cleaning Times",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timeOptions.forEach { (value, label) ->
                        val isSelected = value in preferences.preferredCleaningTimes

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newTimes = if (isSelected) {
                                    preferences.preferredCleaningTimes - value
                                } else {
                                    preferences.preferredCleaningTimes + value
                                }
                                onUpdatePreferences(preferences.copy(preferredCleaningTimes = newTimes))
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Busy days
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Busy Days (Avoid Scheduling)",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Select days when you're too busy to clean",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dayOptions.forEach { day ->
                        val isSelected = day in preferences.busyDays

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newDays = if (isSelected) {
                                    preferences.busyDays - day
                                } else {
                                    preferences.busyDays + day
                                }
                                onUpdatePreferences(preferences.copy(busyDays = newDays))
                            },
                            label = { Text(day.take(3)) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You can always adjust these preferences later in settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip")
            }

            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
        }
    }
}
