package com.ilyk.cleaningplanner.feature.clara.ui.import

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

sealed class ImportSource {
    data object File : ImportSource()
    data object Url : ImportSource()
}

@Composable
fun AvatarImportDialog(
    source: ImportSource,
    onDismiss: () -> Unit,
    onImport: (path: String, displayName: String, licenseNote: String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf("") }
    var licenseNote by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Get real path from URI
            val path = it.path ?: return@let
            selectedFilePath = path
        }
    }

    AlertDialog(
        onDismissRequest = if (!isLoading) onDismiss else {{}},
        title = {
            Text(
                when (source) {
                    ImportSource.File -> "Import from File"
                    ImportSource.Url -> "Import from URL"
                }
            )
        },
        text = {
            Column {
                when (source) {
                    ImportSource.File -> {
                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("model/gltf-binary", "application/octet-stream"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("Select GLB File")
                        }

                        selectedFilePath?.let { path ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Selected: ${path.substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ImportSource.Url -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("HTTPS URL") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Avatar URL input"
                                },
                            placeholder = { Text("https://example.com/avatar.glb") },
                            enabled = !isLoading,
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Avatar Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Avatar name input"
                        },
                    enabled = !isLoading,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = licenseNote,
                    onValueChange = { licenseNote = it },
                    label = { Text("License Note") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "License note input"
                        },
                    placeholder = { Text("e.g., CC BY 4.0, personal use only, etc.") },
                    enabled = !isLoading,
                    maxLines = 3
                )

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val path = when (source) {
                        ImportSource.File -> selectedFilePath
                        ImportSource.Url -> url
                    }
                    path?.let {
                        onImport(it, displayName, licenseNote)
                    }
                },
                enabled = !isLoading && displayName.isNotBlank() && licenseNote.isNotBlank() &&
                        when (source) {
                            ImportSource.File -> selectedFilePath != null
                            ImportSource.Url -> url.isNotBlank() && url.startsWith("https://")
                        }
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}

