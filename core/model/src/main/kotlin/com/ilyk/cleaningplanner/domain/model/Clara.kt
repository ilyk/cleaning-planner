package com.ilyk.cleaningplanner.domain.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Clara voice assistant models
 * Handles session management and streaming events
 */
@Serializable
data class ClaraSession(
    val id: String,
    val homeId: String,
    val userId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: SessionStatus
)

@Serializable
data class ClaraTurn(
    val id: String,
    val sessionId: String,
    val status: TurnStatus,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    val transcript: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val audioUrl: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val parameters: Map<String, String> = emptyMap(),
    val result: String? = null,
    val status: ToolCallStatus
)

@Serializable
enum class SessionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED
}

@Serializable
enum class TurnStatus {
    LISTENING,
    THINKING,
    SPEAKING,
    COMPLETED,
    CANCELLED,
    ERROR
}

@Serializable
enum class ToolCallStatus {
    PENDING,
    EXECUTING,
    COMPLETED,
    FAILED
}

/**
 * Clara streaming events
 */
sealed class ClaraEvent {
    @Serializable
    data class AudioDelta(val data: String) : ClaraEvent() // Base64 encoded audio
    
    @Serializable
    data class AudioCommit(val turnId: String) : ClaraEvent()
    
    @Serializable
    data class Interrupt(val turnId: String) : ClaraEvent()
    
    @Serializable
    data class TurnStarted(val turn: ClaraTurn) : ClaraEvent()
    
    @Serializable
    data class TurnCompleted(val turn: ClaraTurn) : ClaraEvent()
    
    @Serializable
    data class ToolCallStarted(val toolCall: ToolCall) : ClaraEvent()
    
    @Serializable
    data class ToolCallCompleted(val toolCall: ToolCall) : ClaraEvent()
    
    @Serializable
    data class Error(val message: String, val code: String) : ClaraEvent()
    
    @Serializable
    data class Caption(val text: String, val isPartial: Boolean = false) : ClaraEvent()
}

/**
 * Clara UI states
 */
enum class ClaraUIState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    READY
}
