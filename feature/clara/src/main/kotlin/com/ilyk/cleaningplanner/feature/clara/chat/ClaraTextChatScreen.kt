package com.ilyk.cleaningplanner.feature.clara.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ilyk.cleaningplanner.core.ui.components.ChatHeader
import com.ilyk.cleaningplanner.core.ui.components.ChatInputField
import com.ilyk.cleaningplanner.core.ui.components.ChatMessageBubble
import com.ilyk.cleaningplanner.core.ui.components.StreamingMessageBubble
import com.ilyk.cleaningplanner.core.ui.components.TypingIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Clara text chat screen for onboarding.
 *
 * Uses "guided discovery" conversational style powered by Claude Opus 4.5.
 */
@Composable
fun ClaraTextChatScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onOnboardingComplete: (OnboardingResult) -> Unit = {},
    viewModel: ClaraTextChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Initialize session on first composition
    LaunchedEffect(Unit) {
        viewModel.initializeSession()
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ChatEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is ChatEvent.OnboardingCompleted -> {
                    onOnboardingComplete(event.result)
                }
                is ChatEvent.SessionStarted -> {
                    // Session started successfully
                }
                is ChatEvent.Disconnected -> {
                    snackbarHostState.showSnackbar("Disconnected from Clara")
                }
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.currentStreamingText) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(0) // We're using reverseLayout
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatHeader(
                onNavigateBack = onNavigateBack,
                topicsCovered = uiState.topics.count { it.covered },
                totalTopics = uiState.topics.size,
                isConnected = uiState.isConnected
            )
        },
        bottomBar = {
            ChatInputField(
                text = uiState.inputText,
                onTextChange = viewModel::updateInputText,
                onSend = viewModel::sendMessage,
                isEnabled = uiState.isConnected && !uiState.isLoading,
                placeholder = if (uiState.isLoading) "Clara is typing..." else "Type a message..."
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                // Connecting state
                uiState.isConnecting -> {
                    ConnectingState(modifier = Modifier.align(Alignment.Center))
                }

                // Error state
                uiState.error != null && !uiState.isConnected -> {
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = viewModel::retry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Connected - show chat
                else -> {
                    ChatContent(
                        messages = uiState.messages,
                        currentStreamingText = uiState.currentStreamingText,
                        isLoading = uiState.isLoading,
                        listState = listState
                    )
                }
            }

            // Onboarding complete overlay
            AnimatedVisibility(
                visible = uiState.onboardingComplete,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                OnboardingCompleteOverlay()
            }
        }
    }
}

@Composable
private fun ChatContent(
    messages: List<ChatMessage>,
    currentStreamingText: String,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Streaming text (shows at top when reversed)
        if (currentStreamingText.isNotEmpty()) {
            item(key = "streaming") {
                StreamingMessageBubble(
                    text = currentStreamingText,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Typing indicator
        if (isLoading && currentStreamingText.isEmpty()) {
            item(key = "typing") {
                TypingIndicator(
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Messages (reversed, so newest first)
        items(
            items = messages.reversed(),
            key = { it.id }
        ) { message ->
            ChatMessageBubble(
                message = message.content,
                isFromUser = message.role == MessageRole.USER,
                modifier = Modifier.animateItem()
            )
        }

        // Welcome spacer at the bottom (top when reversed)
        item(key = "spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConnectingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connecting to Clara...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

@Composable
private fun OnboardingCompleteOverlay(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "All set!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Creating your personalized cleaning plan...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
