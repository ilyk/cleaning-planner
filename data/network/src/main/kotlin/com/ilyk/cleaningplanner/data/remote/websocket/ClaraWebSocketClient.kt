package com.ilyk.cleaningplanner.data.remote.websocket

import com.ilyk.cleaningplanner.data.remote.dto.*
import com.ilyk.cleaningplanner.domain.model.ClaraEvent
import com.ilyk.cleaningplanner.domain.model.ClaraTurn
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for Clara voice streaming
 * Handles audio streaming and real-time communication
 */
class ClaraWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String
) {
    
    private val json = Json { ignoreUnknownKeys = true }
    private var webSocket: WebSocket? = null
    private val eventsChannel = Channel<ClaraEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isConnected = false
    private var currentSessionId: String? = null
    private var currentTurnId: String? = null
    
    /**
     * Flow of Clara events from the WebSocket
     */
    val events: Flow<ClaraEvent> = eventsChannel.receiveAsFlow()
    
    /**
     * Connect to Clara WebSocket
     */
    suspend fun connect(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isConnected) {
                return@withContext Result.success(Unit)
            }
            
            currentSessionId = sessionId
            val request = Request.Builder()
                .url("$baseUrl/v1/clara/stream?sessionId=$sessionId")
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
            
            // Wait for connection
            delay(1000) // Give time for connection to establish
            
            if (isConnected) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to connect to Clara WebSocket"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from WebSocket
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        isConnected = false
        currentSessionId = null
        currentTurnId = null
    }
    
    /**
     * Send audio delta (20-40ms Opus frames)
     */
    suspend fun sendAudioDelta(audioData: ByteArray, turnId: String) {
        if (!isConnected) return
        
        val message = AudioDeltaMessage(
            data = audioData.toBase64(),
            timestamp = System.currentTimeMillis()
        )
        
        val jsonMessage = json.encodeToString(AudioDeltaMessage.serializer(), message)
        webSocket?.send(jsonMessage)
    }
    
    /**
     * Commit audio input
     */
    suspend fun commitAudio(turnId: String) {
        if (!isConnected) return
        
        val message = AudioCommitMessage(
            turnId = turnId,
            timestamp = System.currentTimeMillis()
        )
        
        val jsonMessage = json.encodeToString(AudioCommitMessage.serializer(), message)
        webSocket?.send(jsonMessage)
    }
    
    /**
     * Send interrupt signal
     */
    suspend fun interrupt(turnId: String) {
        if (!isConnected) return
        
        val message = InterruptMessage(
            turnId = turnId,
            timestamp = System.currentTimeMillis()
        )
        
        val jsonMessage = json.encodeToString(InterruptMessage.serializer(), message)
        webSocket?.send(jsonMessage)
    }
    
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                scope.launch {
                    eventsChannel.send(ClaraEvent.TurnStarted(
                        ClaraTurn(
                            id = currentTurnId ?: "",
                            sessionId = currentSessionId ?: "",
                            status = com.ilyk.cleaningplanner.domain.model.TurnStatus.LISTENING,
                            createdAt = kotlinx.datetime.Clock.System.now()
                        )
                    ))
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    try {
                        val message = json.decodeFromString<AudioOutputMessage>(text)
                        handleAudioOutput(message)
                    } catch (e: Exception) {
                        // Handle other message types or errors
                        eventsChannel.send(ClaraEvent.Error("Failed to parse message: ${e.message}", "PARSE_ERROR"))
                    }
                }
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Handle binary messages if needed
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                scope.launch {
                    eventsChannel.close()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                scope.launch {
                    eventsChannel.send(ClaraEvent.Error(t.message ?: "WebSocket failure", "WEBSOCKET_ERROR"))
                }
            }
        }
    }
    
    private suspend fun handleAudioOutput(message: AudioOutputMessage) {
        try {
            // Keep the data as Base64 string for ClaraEvent.AudioDelta
            eventsChannel.send(ClaraEvent.AudioDelta(message.data))
        } catch (e: Exception) {
            eventsChannel.send(ClaraEvent.Error("Failed to process audio: ${e.message}", "AUDIO_DECODE_ERROR"))
        }
    }
    
    private fun ByteArray.toBase64(): String {
        return android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    }
    
    private fun String.fromBase64(): ByteArray {
        return android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
    }
}
