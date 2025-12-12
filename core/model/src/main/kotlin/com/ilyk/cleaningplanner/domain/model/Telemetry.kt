package com.ilyk.cleaningplanner.domain.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Telemetry and analytics models
 * Tracks user interactions and task completion
 */
@Serializable
data class TelemetryEvent(
    val id: String,
    val homeId: String,
    val userId: String,
    val taskId: String? = null,
    val eventType: TelemetryEventType,
    val timestamp: Instant,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class TelemetryEventType {
    TASK_COMPLETED,
    TASK_SKIPPED,
    TASK_STARTED,
    PLAN_GENERATED,
    PLAN_REVISED,
    MODE_CHANGED,
    CLARA_SESSION_STARTED,
    CLARA_SESSION_ENDED,
    PRINTABLE_GENERATED,
    FAMILY_ASSIGNMENT_CHANGED,
    APP_OPENED,
    APP_BACKGROUNDED
}

@Serializable
data class TelemetryResult(
    val success: Boolean,
    val message: String? = null,
    val eventId: String? = null
)

/**
 * Error envelope for consistent error handling
 */
@Serializable
data class ErrorEnvelope(
    val error: String,
    val message: String,
    val code: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Instant
)

/**
 * API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: ErrorEnvelope? = null,
    val success: Boolean
)
