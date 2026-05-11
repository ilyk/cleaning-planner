package com.ilyk.cleaningplanner.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Room model mirroring the backend `Room` struct.
 *
 * `kind` is optional because the backend allows unset on creation;
 * `metadata` carries free-form constraints.
 */
@Serializable
data class Room(
    val id: String,
    @SerialName("home_id")
    val homeId: String,
    val name: String,
    val kind: RoomKind? = null,
    val metadata: JsonObject? = null
)

@Serializable
enum class RoomKind {
    @SerialName("kitchen")
    Kitchen,
    @SerialName("bathroom")
    Bathroom,
    @SerialName("bedroom")
    Bedroom,
    @SerialName("living")
    Living,
    @SerialName("other")
    Other
}
