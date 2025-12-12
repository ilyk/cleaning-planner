package com.ilyk.cleaningplanner.data.remote.dto

import com.ilyk.cleaningplanner.domain.model.CleaningMode
import kotlinx.serialization.Serializable

/**
 * API request DTOs for CleanFlow backend
 */

@Serializable
data class GeneratePlanRequest(
    val homeId: String,
    val date: String, // ISO date string
    val mode: CleaningMode,
    val preferences: PlanPreferences? = null
)

@Serializable
data class RevisePlanRequest(
    val planId: String,
    val changes: List<PlanChange>,
    val reason: String? = null
)

@Serializable
data class PlanChange(
    val type: ChangeType,
    val taskId: String? = null,
    val newTitle: String? = null,
    val newDescription: String? = null,
    val newPriority: String? = null,
    val newAssignedTo: String? = null
)

@Serializable
enum class ChangeType {
    ADD_TASK,
    REMOVE_TASK,
    MODIFY_TASK,
    REORDER_TASKS,
    CHANGE_MODE
}

@Serializable
data class PlanPreferences(
    val maxDurationMinutes: Int? = null,
    val preferredTools: List<String> = emptyList(),
    val avoidTasks: List<String> = emptyList(),
    val familyMemberPreferences: Map<String, MemberPreference> = emptyMap()
)

@Serializable
data class MemberPreference(
    val maxTaskDurationMinutes: Int? = null,
    val preferredTasks: List<String> = emptyList(),
    val avoidTasks: List<String> = emptyList()
)

@Serializable
data class FamilyAssignRequest(
    val homeId: String,
    val assignments: List<TaskAssignment>
)

@Serializable
data class TaskAssignment(
    val taskId: String,
    val assignedTo: String, // Family member ID
    val reason: String? = null
)

@Serializable
data class AssignResult(
    val success: Boolean,
    val message: String? = null,
    val assignments: List<TaskAssignment>? = null
)

@Serializable
data class PrintableRequest(
    val planId: String,
    val format: PrintableFormat = PrintableFormat.PDF,
    val includeQR: Boolean = true,
    val includeInstructions: Boolean = true
)

@Serializable
enum class PrintableFormat {
    PDF,
    PNG,
    HTML
}

@Serializable
data class PrintableResult(
    val url: String,
    val expiresAt: String, // ISO timestamp
    val format: PrintableFormat,
    val sizeBytes: Long? = null
)

@Serializable
data class CreateSessionRequest(
    val homeId: String,
    val userId: String,
    val capabilities: List<String> = emptyList()
)

@Serializable
data class StartTurnRequest(
    val sessionId: String,
    val audioFormat: AudioFormat = AudioFormat.OPUS,
    val sampleRate: Int = 16000,
    val channels: Int = 1
)

@Serializable
enum class AudioFormat {
    OPUS,
    PCM
}

@Serializable
data class CancelTurnRequest(
    val sessionId: String,
    val turnId: String,
    val reason: String? = null
)

/**
 * WebSocket message formats
 */
@Serializable
data class AudioDeltaMessage(
    val type: String = "input.audio.delta",
    val data: String, // Base64 encoded audio frame
    val timestamp: Long
)

@Serializable
data class AudioCommitMessage(
    val type: String = "input.audio.commit",
    val turnId: String,
    val timestamp: Long
)

@Serializable
data class InterruptMessage(
    val type: String = "input.interrupt",
    val turnId: String,
    val timestamp: Long
)

@Serializable
data class AudioOutputMessage(
    val type: String = "output.audio.delta",
    val data: String, // Base64 encoded audio
    val timestamp: Long,
    val turnId: String
)
