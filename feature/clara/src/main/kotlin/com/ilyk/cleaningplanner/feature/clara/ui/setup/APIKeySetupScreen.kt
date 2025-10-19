package com.ilyk.cleaningplanner.feature.clara.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.feature.clara.ui.settings.AIAssistantSettingsViewModel

@Composable
fun APIKeySetupScreen(
    onConfigured: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AIAssistantSettingsViewModel = hiltViewModel()
) {
    val config by viewModel.openAIConfig.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var currentApiKey by remember(config) { mutableStateOf(config.apiKey) }
    var currentModel by remember(config) { mutableStateOf(config.model) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(modifier = modifier) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Welcome to Cleaning Planner",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Clara, your AI cleaning assistant, needs an OpenAI API key to provide personalized, conversational guidance.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Configure GPT-5",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = currentModel,
                        onValueChange = { currentModel = it },
                        label = { Text("Model") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        supportingText = { Text("Default: gpt-5") },
                        readOnly = true
                    )

                    OutlinedTextField(
                        value = currentApiKey,
                        onValueChange = {
                            currentApiKey = it
                            viewModel.clearValidationState()
                        },
                        label = { Text("OpenAI API Key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .semantics {
                                contentDescription = "OpenAI API key input"
                            },
                        visualTransformation = if (showApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        supportingText = { Text("Get your key from platform.openai.com") },
                        placeholder = { Text("sk-...") }
                    )

                    Button(
                        onClick = {
                            viewModel.updateConfig(
                                config.copy(
                                    model = currentModel,
                                    apiKey = currentApiKey
                                )
                            )
                            viewModel.validateConfig()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = currentApiKey.isNotBlank() && !uiState.isValidating
                    ) {
                        if (uiState.isValidating) {
                            CircularProgressIndicator()
                        } else {
                            Text("Validate & Continue")
                        }
                    }

                    uiState.validationSuccess?.let { success ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (success) {
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            } else {
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            }
                        ) {
                            Text(
                                text = if (success) {
                                    "✓ Configuration validated! Tap 'Continue' below."
                                } else {
                                    "✗ Validation failed: ${uiState.validationError}"
                                },
                                modifier = Modifier.padding(16.dp),
                                color = if (success) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }

                        if (success) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onConfigured,
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text("Continue to Clara")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Without an API key, Clara will use pre-written responses and won't be as conversational.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TextButton(onClick = onSkip) {
                Text("Skip for Now")
            }
        }
    }
}

