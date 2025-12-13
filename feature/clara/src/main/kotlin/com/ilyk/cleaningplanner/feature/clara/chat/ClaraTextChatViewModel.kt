package com.ilyk.cleaningplanner.feature.clara.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilyk.cleaningplanner.data.remote.api.ClaraApi
import com.ilyk.cleaningplanner.data.remote.dto.ConversationMessage
import com.ilyk.cleaningplanner.data.remote.dto.CreateSessionRequest
import com.ilyk.cleaningplanner.data.remote.dto.ExtractFromConversationRequest
import com.ilyk.cleaningplanner.data.remote.dto.StartTurnRequest
import com.ilyk.cleaningplanner.feature.clara.protocol.ClaraStreamClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for Clara text-based onboarding chat.
 *
 * Uses ClaraStreamClient for WebSocket communication with the backend,
 * which routes to Claude Opus 4.5 for "guided discovery" style conversation.
 */
@HiltViewModel
class ClaraTextChatViewModel @Inject constructor(
    private val claraApi: ClaraApi,
    private val streamClientFactory: ClaraStreamClientFactory
) : ViewModel() {

    companion object {
        private const val TAG = "ClaraTextChatVM"
    }

    private val _uiState = MutableStateFlow(ClaraTextChatUiState())
    val uiState: StateFlow<ClaraTextChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var streamClient: ClaraStreamClient? = null
    private var currentAssistantMessageId: String? = null

    /**
     * Initialize the chat session
     */
    fun initializeSession() {
        if (_uiState.value.isConnecting || _uiState.value.isConnected) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }

            try {
                // Create session via API
                val sessionRequest = CreateSessionRequest(
                    homeId = "default-home", // TODO: Get from user context
                    userId = "default-user",
                    capabilities = listOf("text") // Text-only for onboarding
                )

                val session = claraApi.createSession(sessionRequest)
                Log.i(TAG, "Session created: ${session.id}")

                // Start a turn
                val turnRequest = StartTurnRequest(sessionId = session.id)
                val turn = claraApi.startTurn(turnRequest)
                Log.i(TAG, "Turn started: ${turn.id}")

                // Create WebSocket client and connect
                streamClient = streamClientFactory.create(viewModelScope)
                val client = streamClient!!

                // Subscribe to server messages
                subscribeToServerMessages(client)

                // Connect to WebSocket (construct URL from session)
                val streamUrl = buildStreamUrl(session.id)
                client.connect(streamUrl, session.id, turn.id)

                // Start the turn on WebSocket
                client.startTurn(session.id, turn.id, "text")

                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = true,
                        sessionId = session.id,
                        turnId = turn.id
                    )
                }

                _events.emit(ChatEvent.SessionStarted)

                // Clara will send initial greeting via WebSocket
                // No need to add a mock message

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        error = e.message ?: "Failed to connect"
                    )
                }
                _events.emit(ChatEvent.Error(e.message ?: "Connection failed"))
            }
        }
    }

    private fun buildStreamUrl(sessionId: String): String {
        // Construct WebSocket URL from base URL
        // 10.0.2.2 is the Android emulator alias for host machine's localhost
        return "ws://10.0.2.2:8090/v1/clara/stream?session=$sessionId"
    }

    private fun subscribeToServerMessages(client: ClaraStreamClient) {
        viewModelScope.launch {
            client.serverMessages.collectLatest { message ->
                handleServerMessage(message)
            }
        }

        viewModelScope.launch {
            client.connectionState.collectLatest { state ->
                handleConnectionState(state)
            }
        }
    }

    private suspend fun handleServerMessage(message: ClaraStreamClient.ClaraServerMessage) {
        when (message) {
            is ClaraStreamClient.ClaraServerMessage.TextOutput -> {
                // Append text to streaming message
                val text = message.delta.text
                _uiState.update { state ->
                    state.copy(currentStreamingText = state.currentStreamingText + text)
                }
            }
            is ClaraStreamClient.ClaraServerMessage.TurnFinished -> {
                // Turn complete - finalize streaming message
                val streamingText = _uiState.value.currentStreamingText
                if (streamingText.isNotEmpty()) {
                    addAssistantMessage(streamingText)

                    // Check for completion marker
                    if (streamingText.contains("[ONBOARDING_COMPLETE]")) {
                        handleOnboardingComplete()
                    }
                }
                _uiState.update { it.copy(isLoading = false) }
            }
            is ClaraStreamClient.ClaraServerMessage.ErrorReceived -> {
                val error = message.error
                Log.e(TAG, "Server error: ${error.code} - ${error.message}")
                _uiState.update { it.copy(error = error.message, isLoading = false) }
                _events.emit(ChatEvent.Error(error.message))
            }
            is ClaraStreamClient.ClaraServerMessage.AudioOutputStart -> {
                // Text started (using audio start as indicator)
                _uiState.update { it.copy(currentStreamingText = "") }
            }
            else -> {
                // Other messages (audio, suggestions, etc.) - not used in text mode
                Log.d(TAG, "Received message: $message")
            }
        }
    }

    private fun handleConnectionState(state: ClaraStreamClient.ConnectionState) {
        when (state) {
            is ClaraStreamClient.ConnectionState.Connected -> {
                _uiState.update { it.copy(isConnected = true, isConnecting = false) }
            }
            is ClaraStreamClient.ConnectionState.Disconnected -> {
                _uiState.update { it.copy(isConnected = false) }
            }
            is ClaraStreamClient.ConnectionState.Connecting -> {
                _uiState.update { it.copy(isConnecting = true) }
            }
            is ClaraStreamClient.ConnectionState.Error -> {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        error = state.message
                    )
                }
            }
        }
    }

    /**
     * Update input text field
     */
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Send a message
     */
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isLoading) {
            return
        }

        viewModelScope.launch {
            // Add user message to UI
            val userMessage = ChatMessage(
                role = MessageRole.USER,
                content = text
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage,
                    inputText = "",
                    isLoading = true,
                    error = null,
                    currentStreamingText = ""
                )
            }

            try {
                // Send via ClaraStreamClient
                val client = streamClient
                if (client != null) {
                    client.sendText(text)
                    Log.d(TAG, "Message sent: $text")
                } else {
                    Log.w(TAG, "Stream client not connected, cannot send message")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Not connected to Clara"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to send message"
                    )
                }
            }
        }
    }

    /**
     * Update a topic as covered (can be called from UI or based on message analysis)
     */
    private fun updateTopicCovered(topicId: String) {
        _uiState.update { state ->
            state.copy(
                topics = state.topics.map { topic ->
                    if (topic.id == topicId) topic.copy(covered = true) else topic
                }
            )
        }
    }

    /**
     * Add an assistant message to the chat
     */
    private fun addAssistantMessage(content: String) {
        val message = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = content.replace("[ONBOARDING_COMPLETE]", "").trim()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + message,
                currentStreamingText = "",
                isLoading = false
            )
        }
    }

    /**
     * Handle when onboarding is complete.
     * Calls the extraction API to persist home data from the conversation.
     */
    private suspend fun handleOnboardingComplete() {
        _uiState.update { it.copy(onboardingComplete = true, isLoading = true) }

        try {
            val sessionId = _uiState.value.sessionId ?: ""
            val messages = _uiState.value.messages

            // Convert chat messages to API format
            val conversationTranscript = messages.map { msg ->
                ConversationMessage(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                    },
                    content = msg.content
                )
            }

            Log.i(TAG, "Extracting home data from ${messages.size} messages")

            // Call extraction API to persist home data
            val extractRequest = ExtractFromConversationRequest(
                sessionId = sessionId,
                conversationTranscript = conversationTranscript
            )

            val extractResponse = claraApi.extractFromConversation(extractRequest)

            Log.i(TAG, "Extraction complete: homeId=${extractResponse.homeId}, rooms=${extractResponse.roomCount}, members=${extractResponse.memberCount}")

            val result = OnboardingResult(
                sessionId = sessionId,
                homeId = extractResponse.homeId,
                rooms = extractResponse.extractedData?.rooms?.map { it.name } ?: emptyList(),
                peopleCount = extractResponse.extractedData?.members?.size ?: 0,
                hasPets = (extractResponse.extractedData?.pets?.size ?: 0) > 0,
                petTypes = extractResponse.extractedData?.pets?.map { it.type } ?: emptyList(),
                preferredSchedule = extractResponse.extractedData?.preferences?.preferredCleaningTimes?.joinToString(", ") ?: "",
                problemAreas = extractResponse.extractedData?.problemAreas?.map { "${it.room}: ${it.issue}" } ?: emptyList(),
                cleaningStyle = extractResponse.extractedData?.preferences?.cleaningStyle ?: "",
                conversationTranscript = messages
            )

            _uiState.update { it.copy(isLoading = false) }
            _events.emit(ChatEvent.OnboardingCompleted(result))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract home data", e)
            _uiState.update { it.copy(isLoading = false, error = "Failed to save home setup: ${e.message}") }

            // Still emit completion event with basic result
            val result = OnboardingResult(
                sessionId = _uiState.value.sessionId ?: "",
                conversationTranscript = _uiState.value.messages
            )
            _events.emit(ChatEvent.OnboardingCompleted(result))
        }
    }

    /**
     * Disconnect and clean up
     */
    fun disconnect() {
        viewModelScope.launch {
            streamClient?.disconnect()
            streamClient = null

            _uiState.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false
                )
            }

            _events.emit(ChatEvent.Disconnected)
        }
    }

    /**
     * Retry connection after error
     */
    fun retry() {
        _uiState.update { it.copy(error = null) }
        initializeSession()
    }

    override fun onCleared() {
        super.onCleared()
        streamClient?.disconnect()
    }
}
