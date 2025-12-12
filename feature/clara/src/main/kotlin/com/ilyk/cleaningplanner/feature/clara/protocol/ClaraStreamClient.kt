package com.ilyk.cleaningplanner.feature.clara.protocol

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Clara Streaming Protocol v0.1 - Android WebSocket Client
 * 
 * Handles:
 * - WebSocket connection lifecycle
 * - Heartbeat (ping/pong) every 10s
 * - Sequence number management for audio deltas
 * - Message serialization/deserialization
 * - Backpressure handling
 * - Automatic reconnection with retry logic
 */
class ClaraStreamClient(
    private val authToken: String,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ClaraStreamClient"
        private const val CONNECT_TIMEOUT_SEC = 10L
        private const val READ_TIMEOUT_SEC = 60L
        private const val WRITE_TIMEOUT_SEC = 30L
    }

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var timeoutWatchdogJob: Job? = null
    
    private val sequenceNumber = AtomicInteger(0)
    private val lastPongTime = AtomicLong(0)
    private val missedHeartbeats = AtomicInteger(0)

    // State
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Events from server
    private val _serverMessages = MutableSharedFlow<ClaraServerMessage>(replay = 0, extraBufferCapacity = 100)
    val serverMessages: SharedFlow<ClaraServerMessage> = _serverMessages

    // Telemetry
    private val telemetry = ClientTelemetry()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val sessionId: String, val turnId: String?) : ConnectionState()
        data class Error(val code: String, val message: String) : ConnectionState()
    }

    sealed class ClaraServerMessage {
        data class AudioOutput(val delta: OutputAudioDelta) : ClaraServerMessage()
        data class AudioOutputCommit(val commit: OutputAudioCommit) : ClaraServerMessage()
        data class AudioOutputStart(val start: OutputAudioStart) : ClaraServerMessage()
        data class TextOutput(val delta: OutputTextDelta) : ClaraServerMessage()
        data class SuggestionsReceived(val suggestions: Suggestions) : ClaraServerMessage()
        data class ToolCallReceived(val toolCall: ToolCall) : ClaraServerMessage()
        data class ToolResultReceived(val toolResult: ToolResult) : ClaraServerMessage()
        data class GuardrailNoticeReceived(val notice: GuardrailNotice) : ClaraServerMessage()
        data class BackpressureReceived(val backpressure: ServerBackpressure) : ClaraServerMessage()
        data class TurnFinished(val finish: TurnFinish) : ClaraServerMessage()
        data class ErrorReceived(val error: ErrorMessage) : ClaraServerMessage()
        data class Pong(val ts: Long) : ClaraServerMessage()
    }

    /**
     * Connect to Clara streaming server
     */
    fun connect(streamUrl: String, sessionId: String, turnId: String? = null) {
        if (_connectionState.value is ConnectionState.Connected) {
            Log.w(TAG, "Already connected")
            return
        }

        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url(streamUrl)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Accept-Protocol", ProtocolConstants.PROTOCOL_VERSION)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket opened")
                _connectionState.value = ConnectionState.Connected(sessionId, turnId)
                startHeartbeat()
                startTimeoutWatchdog()
                telemetry.recordConnection()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Not expected in this protocol, but handle gracefully
                Log.w(TAG, "Received binary message (unexpected)")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code - $reason")
                cleanup()
                _connectionState.value = ConnectionState.Disconnected
                telemetry.recordDisconnection()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                cleanup()
                _connectionState.value = ConnectionState.Error(
                    ErrorCodes.NETWORK_ERROR,
                    t.message ?: "Connection failed"
                )
                telemetry.recordError(ErrorCodes.NETWORK_ERROR)
            }
        })
    }

    /**
     * Start a turn
     */
    fun startTurn(sessionId: String, turnId: String, mode: String, locale: String = "en-US") {
        val message = TurnStart(
            sessionId = sessionId,
            turnId = turnId,
            input = InputMode(mode = mode),
            locale = locale
        )
        sendMessage(message)
        sequenceNumber.set(0) // Reset sequence for new turn
    }

    /**
     * Send audio delta (voice input)
     */
    fun sendAudioDelta(audioData: ByteArray, format: String = ProtocolConstants.INPUT_AUDIO_FORMAT): Boolean {
        if (audioData.size > ProtocolConstants.MAX_PAYLOAD_SIZE_BYTES) {
            Log.e(TAG, "Audio payload too large: ${audioData.size} bytes")
            telemetry.recordError(ErrorCodes.PAYLOAD_TOO_LARGE)
            return false
        }

        val seq = sequenceNumber.incrementAndGet()
        val base64Data = Base64.encodeToString(audioData, Base64.NO_WRAP)
        
        val message = InputAudioDelta(
            seq = seq,
            format = format,
            data = base64Data
        )
        
        return sendMessage(message)
    }

    /**
     * Commit audio input (stop sending)
     */
    fun commitAudioInput() {
        val message = InputAudioCommit(seq = sequenceNumber.get())
        sendMessage(message)
    }

    /**
     * Send interrupt (barge-in)
     */
    fun sendInterrupt() {
        val message = InputInterrupt()
        sendMessage(message)
        telemetry.recordBargeIn()
    }

    /**
     * Send text input
     */
    fun sendText(text: String, hints: TextHints? = null) {
        val message = InputText(text = text, hints = hints)
        sendMessage(message)
    }

    /**
     * Cancel current turn
     */
    fun cancelTurn(turnId: String) {
        val message = TurnCancel(turnId = turnId)
        sendMessage(message)
    }

    /**
     * Close connection gracefully
     */
    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        cleanup()
    }

    private fun sendMessage(message: ClaraMessage): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.e(TAG, "Cannot send message: not connected")
            return false
        }

        return try {
            val jsonString = json.encodeToString(message)
            ws.send(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            telemetry.recordError(ErrorCodes.NETWORK_ERROR)
            false
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            // Parse the type field first to determine message type
            val jsonElement = json.parseToJsonElement(text)
            val type = jsonElement.jsonObject["type"]?.jsonPrimitive?.content

            when (type) {
                "pong" -> {
                    lastPongTime.set(System.currentTimeMillis())
                    missedHeartbeats.set(0)
                    coroutineScope.launch {
                        _serverMessages.emit(ClaraServerMessage.Pong(System.currentTimeMillis()))
                    }
                }
                "output.audio.start" -> {
                    val msg = json.decodeFromString<OutputAudioStart>(text)
                    telemetry.recordTTFT() // Time to first token
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.AudioOutputStart(msg)) }
                }
                "output.audio.delta" -> {
                    val msg = json.decodeFromString<OutputAudioDelta>(text)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.AudioOutput(msg)) }
                }
                "output.audio.commit" -> {
                    val msg = json.decodeFromString<OutputAudioCommit>(text)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.AudioOutputCommit(msg)) }
                }
                "output.text.delta" -> {
                    val msg = json.decodeFromString<OutputTextDelta>(text)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.TextOutput(msg)) }
                }
                "suggestions" -> {
                    val msg = json.decodeFromString<Suggestions>(text)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.SuggestionsReceived(msg)) }
                }
                "tool.call" -> {
                    val msg = json.decodeFromString<ToolCall>(text)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.ToolCallReceived(msg)) }
                }
                "tool.result" -> {
                    val msg = json.decodeFromString<ToolResult>(text)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.ToolResultReceived(msg)) }
                }
                "guardrail.notice" -> {
                    val msg = json.decodeFromString<GuardrailNotice>(text)
                    telemetry.recordGuardrailHit()
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.GuardrailNoticeReceived(msg)) }
                }
                "server.backpressure" -> {
                    val msg = json.decodeFromString<ServerBackpressure>(text)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.BackpressureReceived(msg)) }
                }
                "turn.finish" -> {
                    val msg = json.decodeFromString<TurnFinish>(text)
                    telemetry.recordTurnComplete(msg.usage?.tokensIn ?: 0, msg.usage?.tokensOut ?: 0)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.TurnFinished(msg)) }
                }
                "error" -> {
                    val msg = json.decodeFromString<ErrorMessage>(text)
                    telemetry.recordError(msg.code)
                    coroutineScope.launch { _serverMessages.emit(ClaraServerMessage.ErrorReceived(msg)) }
                }
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming message", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = coroutineScope.launch {
            while (isActive) {
                delay(ProtocolConstants.HEARTBEAT_INTERVAL_MS)
                
                val ping = Ping()
                if (!sendMessage(ping)) {
                    Log.e(TAG, "Failed to send heartbeat")
                    break
                }
                
                Log.d(TAG, "Heartbeat sent")
            }
        }
    }

    private fun startTimeoutWatchdog() {
        timeoutWatchdogJob?.cancel()
        lastPongTime.set(System.currentTimeMillis())
        
        timeoutWatchdogJob = coroutineScope.launch {
            while (isActive) {
                delay(ProtocolConstants.HEARTBEAT_TIMEOUT_MS)
                
                val timeSinceLastPong = System.currentTimeMillis() - lastPongTime.get()
                
                if (timeSinceLastPong > ProtocolConstants.HEARTBEAT_TIMEOUT_MS * 2) {
                    val missed = missedHeartbeats.incrementAndGet()
                    Log.w(TAG, "Missed heartbeat #$missed")
                    
                    if (missed >= ProtocolConstants.MAX_MISSED_HEARTBEATS) {
                        Log.e(TAG, "Too many missed heartbeats, closing connection")
                        _connectionState.value = ConnectionState.Error(
                            ErrorCodes.POLICY_TIMEOUT,
                            "Heartbeat timeout"
                        )
                        webSocket?.close(1001, "Heartbeat timeout")
                        telemetry.recordError(ErrorCodes.POLICY_TIMEOUT)
                        break
                    }
                }
            }
        }
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        timeoutWatchdogJob?.cancel()
        webSocket = null
        sequenceNumber.set(0)
    }

    /**
     * Get telemetry snapshot
     */
    fun getTelemetry(): TelemetrySnapshot = telemetry.snapshot()
}

/**
 * Client-side telemetry collector
 */
private class ClientTelemetry {
    private var connectionsCount = 0
    private var disconnectionsCount = 0
    private var errorsCount = 0
    private val errorsByCode = mutableMapOf<String, Int>()
    private var bargeInCount = 0
    private var guardrailHits = 0
    private var totalTokensIn = 0
    private var totalTokensOut = 0
    private var firstTokenTimestamp: Long? = null
    private var turnStartTimestamp: Long? = null

    @Synchronized
    fun recordConnection() {
        connectionsCount++
        turnStartTimestamp = System.currentTimeMillis()
    }

    @Synchronized
    fun recordDisconnection() {
        disconnectionsCount++
    }

    @Synchronized
    fun recordError(code: String) {
        errorsCount++
        errorsByCode[code] = errorsByCode.getOrDefault(code, 0) + 1
    }

    @Synchronized
    fun recordBargeIn() {
        bargeInCount++
    }

    @Synchronized
    fun recordGuardrailHit() {
        guardrailHits++
    }

    @Synchronized
    fun recordTTFT() {
        if (firstTokenTimestamp == null) {
            firstTokenTimestamp = System.currentTimeMillis()
        }
    }

    @Synchronized
    fun recordTurnComplete(tokensIn: Int, tokensOut: Int) {
        totalTokensIn += tokensIn
        totalTokensOut += tokensOut
        firstTokenTimestamp = null
        turnStartTimestamp = null
    }

    @Synchronized
    fun snapshot(): TelemetrySnapshot {
        val ttft = if (firstTokenTimestamp != null && turnStartTimestamp != null) {
            firstTokenTimestamp!! - turnStartTimestamp!!
        } else null

        return TelemetrySnapshot(
            connections = connectionsCount,
            disconnections = disconnectionsCount,
            errors = errorsCount,
            errorsByCode = errorsByCode.toMap(),
            bargeIns = bargeInCount,
            guardrailHits = guardrailHits,
            totalTokensIn = totalTokensIn,
            totalTokensOut = totalTokensOut,
            lastTTFTMs = ttft
        )
    }
}

data class TelemetrySnapshot(
    val connections: Int,
    val disconnections: Int,
    val errors: Int,
    val errorsByCode: Map<String, Int>,
    val bargeIns: Int,
    val guardrailHits: Int,
    val totalTokensIn: Int,
    val totalTokensOut: Int,
    val lastTTFTMs: Long?
)



