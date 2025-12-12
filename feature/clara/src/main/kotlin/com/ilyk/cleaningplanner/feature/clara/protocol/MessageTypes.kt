package com.ilyk.cleaningplanner.feature.clara.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Clara Streaming Protocol v0.1 - Message Type Definitions
 * 
 * Based on: clara_streaming_protocol_spec_v_0.md
 */

// ============================================================================
// Base Types
// ============================================================================

@Serializable
sealed class ClaraMessage {
    abstract val type: String
    abstract val ts: Long
}

// ============================================================================
// Control Messages
// ============================================================================

@Serializable
data class TurnStart(
    override val type: String = "turn.start",
    override val ts: Long = System.currentTimeMillis(),
    val sessionId: String,
    val turnId: String,
    val input: InputMode,
    val locale: String = "en-US"
) : ClaraMessage()

@Serializable
data class InputMode(
    val mode: String // "voice" | "text"
)

@Serializable
data class TurnCancel(
    override val type: String = "turn.cancel",
    override val ts: Long = System.currentTimeMillis(),
    val turnId: String
) : ClaraMessage()

@Serializable
data class TurnFinish(
    override val type: String = "turn.finish",
    override val ts: Long = System.currentTimeMillis(),
    val turnId: String,
    val usage: Usage? = null,
    val latencyMs: Long? = null
) : ClaraMessage()

@Serializable
data class Usage(
    val tokensIn: Int,
    val tokensOut: Int
)

@Serializable
data class Ping(
    override val type: String = "ping",
    override val ts: Long = System.currentTimeMillis()
) : ClaraMessage()

@Serializable
data class Pong(
    override val type: String = "pong",
    override val ts: Long = System.currentTimeMillis()
) : ClaraMessage()

// ============================================================================
// Voice Input (Client → Server)
// ============================================================================

@Serializable
data class InputAudioDelta(
    override val type: String = "input.audio.delta",
    override val ts: Long = System.currentTimeMillis(),
    val seq: Int,
    val format: String, // "opus@24000/mono/20ms"
    val data: String, // base64 encoded audio
    val mac: String? = null // optional HMAC
) : ClaraMessage()

@Serializable
data class InputAudioCommit(
    override val type: String = "input.audio.commit",
    override val ts: Long = System.currentTimeMillis(),
    val seq: Int
) : ClaraMessage()

@Serializable
data class InputInterrupt(
    override val type: String = "input.interrupt",
    override val ts: Long = System.currentTimeMillis()
) : ClaraMessage()

// ============================================================================
// Text Input (Client → Server)
// ============================================================================

@Serializable
data class InputText(
    override val type: String = "input.text",
    override val ts: Long = System.currentTimeMillis(),
    val text: String,
    val hints: TextHints? = null
) : ClaraMessage()

@Serializable
data class TextHints(
    val mode: String? = null // e.g., "focus"
)

// ============================================================================
// Output (Server → Client)
// ============================================================================

@Serializable
data class OutputAudioStart(
    override val type: String = "output.audio.start",
    override val ts: Long = System.currentTimeMillis(),
    val turnId: String
) : ClaraMessage()

@Serializable
data class OutputAudioDelta(
    override val type: String = "output.audio.delta",
    override val ts: Long = System.currentTimeMillis(),
    val seq: Int,
    val format: String, // "pcm16@24000/mono"
    val data: String // base64 encoded PCM samples
) : ClaraMessage()

@Serializable
data class OutputAudioCommit(
    override val type: String = "output.audio.commit",
    override val ts: Long = System.currentTimeMillis(),
    val seq: Int
) : ClaraMessage()

@Serializable
data class OutputTextDelta(
    override val type: String = "output.text.delta",
    override val ts: Long = System.currentTimeMillis(),
    val text: String
) : ClaraMessage()

@Serializable
data class Suggestions(
    override val type: String = "suggestions",
    override val ts: Long = System.currentTimeMillis(),
    val chips: List<String>
) : ClaraMessage()

// ============================================================================
// Tools & Results
// ============================================================================

@Serializable
data class ToolCall(
    override val type: String = "tool.call",
    override val ts: Long = System.currentTimeMillis(),
    val callId: String,
    val tool: String,
    val args: JsonElement
) : ClaraMessage()

@Serializable
data class ToolResult(
    override val type: String = "tool.result",
    override val ts: Long = System.currentTimeMillis(),
    val callId: String,
    val result: JsonElement
) : ClaraMessage()

// ============================================================================
// Guardrails & Notices
// ============================================================================

@Serializable
data class GuardrailNotice(
    override val type: String = "guardrail.notice",
    override val ts: Long = System.currentTimeMillis(),
    val code: String,
    val message: String
) : ClaraMessage()

// ============================================================================
// Errors
// ============================================================================

@Serializable
data class ErrorMessage(
    override val type: String = "error",
    override val ts: Long = System.currentTimeMillis(),
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null
) : ClaraMessage()

// ============================================================================
// Server Backpressure
// ============================================================================

@Serializable
data class ServerBackpressure(
    override val type: String = "server.backpressure",
    override val ts: Long = System.currentTimeMillis(),
    val level: String // "high", "medium", "low"
) : ClaraMessage()

// ============================================================================
// Session Management (REST API)
// ============================================================================

@Serializable
data class CreateSessionRequest(
    val mode: String, // "voice" | "text"
    val device: DeviceInfo
)

@Serializable
data class DeviceInfo(
    val platform: String,
    val appVersion: String
)

@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val streamUrl: String,
    val policyVersion: String,
    val promptVersion: String
)

@Serializable
data class StartTurnResponse(
    val turnId: String,
    val streamUrl: String
)

// ============================================================================
// Error Codes (Canonical)
// ============================================================================

object ErrorCodes {
    const val UNAUTHENTICATED = "UNAUTHENTICATED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val POLICY_BLOCK = "POLICY_BLOCK"
    const val CAPABILITY_DOWNGRADED = "CAPABILITY_DOWNGRADED"
    const val RATE_LIMIT = "RATE_LIMIT"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    const val SEQ_OUT_OF_ORDER = "SEQ_OUT_OF_ORDER"
    const val BACKEND_TIMEOUT = "BACKEND_TIMEOUT"
    const val MODEL_TIMEOUT = "MODEL_TIMEOUT"
    const val UPSTREAM_ERROR = "UPSTREAM_ERROR"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val POLICY_TIMEOUT = "POLICY_TIMEOUT"
    const val SERVER_OVERLOADED = "SERVER_OVERLOADED"
}

// ============================================================================
// Constants
// ============================================================================

object ProtocolConstants {
    const val PROTOCOL_VERSION = "clara/0.1"
    const val HEARTBEAT_INTERVAL_MS = 10_000L
    const val HEARTBEAT_TIMEOUT_MS = 5_000L
    const val MAX_MISSED_HEARTBEATS = 3
    const val IDLE_TIMEOUT_MS = 45_000L
    const val MAX_PAYLOAD_SIZE_BYTES = 20_480 // 20 KB
    const val MAX_INPUT_AUDIO_DURATION_MS = 60_000L
    const val MAX_OUTPUT_AUDIO_DURATION_MS = 90_000L
    const val JITTER_BUFFER_MIN_MS = 80
    const val JITTER_BUFFER_MAX_MS = 120
    
    // Audio formats
    const val INPUT_AUDIO_FORMAT = "opus@24000/mono/20ms"
    const val OUTPUT_AUDIO_FORMAT = "pcm16@24000/mono"
    const val SAMPLE_RATE = 24000
    const val FRAME_DURATION_MS = 20
}



