package com.ilyk.cleaningplanner.feature.clara.ui.pronunciation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ilyk.cleaningplanner.core.model.PronunciationMode

@Composable
fun PronunciationEditorDialog(
    currentName: String,
    currentMode: PronunciationMode,
    currentValue: String,
    supportsIPA: Boolean,
    supportsSSML: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, PronunciationMode, String) -> Unit,
    onPreview: (String, PronunciationMode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(currentName) }
    var mode by remember { mutableStateOf(currentMode) }
    var value by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Name & Pronunciation")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Avatar display name input"
                        },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pronunciation",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == PronunciationMode.NONE,
                        onClick = { mode = PronunciationMode.NONE },
                        label = { Text("None") }
                    )

                    FilterChip(
                        selected = mode == PronunciationMode.PHONETIC,
                        onClick = { mode = PronunciationMode.PHONETIC },
                        label = { Text("Phonetic") }
                    )

                    if (supportsIPA) {
                        FilterChip(
                            selected = mode == PronunciationMode.IPA,
                            onClick = { mode = PronunciationMode.IPA },
                            label = { Text("IPA") }
                        )
                    }

                    if (supportsSSML) {
                        FilterChip(
                            selected = mode == PronunciationMode.SSML,
                            onClick = { mode = PronunciationMode.SSML },
                            label = { Text("SSML") }
                        )
                    }
                }

                if (mode != PronunciationMode.NONE) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = {
                            Text(
                                when (mode) {
                                    PronunciationMode.PHONETIC -> "Sounds like..."
                                    PronunciationMode.IPA -> "IPA notation"
                                    PronunciationMode.SSML -> "SSML tag"
                                    else -> "Value"
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Pronunciation value input"
                            },
                        supportingText = {
                            Text(
                                when (mode) {
                                    PronunciationMode.PHONETIC -> "e.g., KLAIR-uh"
                                    PronunciationMode.IPA -> "e.g., /ˈklɛərə/"
                                    PronunciationMode.SSML -> "e.g., <phoneme>...</phoneme>"
                                    else -> ""
                                }
                            )
                        },
                        maxLines = if (mode == PronunciationMode.SSML) 3 else 1
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onPreview(name, mode, value) },
                            modifier = Modifier.semantics {
                                contentDescription = "Preview pronunciation"
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                        }
                        Text(
                            text = "Preview: \"Hi, I'm $name.\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!supportsIPA || !supportsSSML) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = buildString {
                                append("Note: ")
                                if (!supportsIPA) append("IPA ")
                                if (!supportsIPA && !supportsSSML) append("and ")
                                if (!supportsSSML) append("SSML ")
                                append("not supported by current TTS provider")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, mode, value) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

