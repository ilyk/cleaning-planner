package com.ilyk.cleaningplanner.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.datetime.Instant

/**
 * Plan model mirroring backend DTO
 * Represents a daily cleaning plan with tasks organized by priority
 */
@Serializable
data class Plan(
    @SerialName("plan_id")
    val id: String,
    @SerialName("home_id")
    val homeId: String,
    val date: String, // ISO date string (YYYY-MM-DD)
    val mode: CleaningMode,
    val version: Int,
    @SerialName("created_at")
    val createdAt: Instant? = null,
    @SerialName("updated_at")
    val updatedAt: Instant? = null,
    val tasks: List<Task>,
    val metadata: PlanMetadata? = null,
    // Backend fields we ignore (sections is complex nested structure, we don't need it on client)
    @SerialName("sections")
    val sections: kotlinx.serialization.json.JsonArray? = null,
    @SerialName("prompt_version")
    val promptVersion: String? = null,
    @SerialName("policy_version")
    val policyVersion: String? = null,
    val cached: Boolean? = null
)

@Serializable
data class Task(
    @SerialName("task_id")
    val id: String,
    val title: String,
    val description: String? = null,
    val priority: TaskPriority,
    @SerialName("estimate_min")
    val estimatedDurationMinutes: Int,
    val tools: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val qrCode: String? = null,
    @SerialName("assignee")
    val assignedTo: String? = null, // Family member ID (extracted from assignee.member_id)
    val completedAt: Instant? = null,
    val skippedAt: Instant? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    // Backend fields we ignore
    @SerialName("template_id")
    val templateId: String? = null,
    @SerialName("room_id")
    val roomId: String? = null,
    val state: String? = null,
    @SerialName("section_id")
    val sectionId: String? = null,
    val metadata: kotlinx.serialization.json.JsonObject? = null
)

@Serializable
data class PlanMetadata(
    val totalEstimatedMinutes: Int,
    val taskCount: Int,
    val completedCount: Int = 0,
    val skippedCount: Int = 0,
    val efficiencyScore: Float? = null
)

@Serializable
enum class CleaningMode {
    FOCUS,      // ⚡ Focus mode - high intensity, short tasks
    FULL_RESET, // 🧼 Full Reset - comprehensive cleaning
    LOW_ENERGY, // 🌙 Low Energy - gentle, minimal effort
    PET_MODE    // 🐾 Pet Mode - pet-safe products and methods
}

@Serializable
enum class TaskPriority {
    NOW,    // Must do today
    NEXT,   // Should do today if possible
    LATER   // Can be deferred
}

@Serializable
data class FamilyMember(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val preferences: MemberPreferences = MemberPreferences()
)

@Serializable
data class MemberPreferences(
    val preferredMode: CleaningMode? = null,
    val maxTaskDurationMinutes: Int? = null,
    val preferredTasks: List<String> = emptyList(),
    val accessibility: AccessibilitySettings = AccessibilitySettings()
)

@Serializable
data class AccessibilitySettings(
    val highContrast: Boolean = false,
    val largeText: Boolean = false,
    val reducedMotion: Boolean = false,
    val screenReader: Boolean = false,
    val captions: Boolean = false
)
