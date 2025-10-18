package com.ilyk.cleaningplanner.feature.clara.ui.intake

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraFAB
import com.ilyk.cleaningplanner.feature.clara.ui.components.ClaraViewModel
import com.ilyk.cleaningplanner.feature.clara.ui.components.TextBubble
import com.ilyk.cleaningplanner.feature.clara.ui.settings.AvatarSettingsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeIntakeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntakeViewModel = hiltViewModel(),
    claraViewModel: ClaraViewModel = hiltViewModel()
) {
    val avatarPrefs by claraViewModel.avatarPrefs.collectAsState()
    val messages by viewModel.messages.collectAsState()
    var currentInput by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Type My Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ClaraFAB(
                avatarPrefs = avatarPrefs,
                onClick = { showSettings = true }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp)
        ) {
            if (messages.isNotEmpty()) {
                TextBubble(
                    text = messages.last().text,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = "Tell me about your household",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = currentInput,
                onValueChange = { currentInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .semantics {
                        contentDescription = "Type information about your household"
                    },
                placeholder = {
                    Text("Type in your own words — rooms, people, routines, or anything else that comes to mind...")
                },
                maxLines = 15
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (currentInput.isNotBlank()) {
                        viewModel.sendMessage(currentInput)
                        currentInput = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = currentInput.isNotBlank()
            ) {
                Text(
                    text = "Submit",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (showSettings) {
            AvatarSettingsSheet(
                currentPrefs = avatarPrefs,
                onDismiss = { showSettings = false },
                onSave = { newPrefs ->
                    claraViewModel.updateAvatarPrefs(newPrefs)
                    showSettings = false
                }
            )
        }
    }
}

