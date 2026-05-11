package com.ilyk.cleaningplanner.data.remote.dto

import com.ilyk.cleaningplanner.domain.model.RoomKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request/response DTOs for the v1 households / rooms / task-skip endpoints.
 * Field names follow the backend wire contract (snake_case) — match
 * `backend/crates/domain/src/models.rs` exactly.
 */

// /v1/households

@Serializable
data class CreateHomeRequest(
    val name: String,
    val tz: String,
    val locale: String,
    val metadata: JsonObject? = null
)

@Serializable
data class UpdateHomeRequest(
    val name: String? = null,
    val tz: String? = null,
    val locale: String? = null,
    val metadata: JsonObject? = null
)

// /v1/rooms

@Serializable
data class CreateRoomRequestV1(
    @SerialName("home_id")
    val homeId: String,
    val name: String,
    val kind: RoomKind? = null,
    val metadata: JsonObject? = null
)

@Serializable
data class UpdateRoomRequestV1(
    val name: String? = null,
    val kind: RoomKind? = null,
    val metadata: JsonObject? = null
)

// /v1/tasks/{task_id}/skip

@Serializable
data class SkipTaskRequestV1(
    val reason: String? = null
)

@Serializable
data class SkipTaskResponseV1(
    val ok: Boolean,
    @SerialName("task_id")
    val taskId: String,
    /** Backend `TaskState` enum: one of "pending", "in_progress", "done", "skipped". Kept as String to tolerate future variants. */
    @SerialName("new_state")
    val newState: String,
    @SerialName("telemetry_id")
    val telemetryId: String
)

// /v1/households delete + /v1/rooms delete

@Serializable
data class OkResponse(
    val ok: Boolean
)
