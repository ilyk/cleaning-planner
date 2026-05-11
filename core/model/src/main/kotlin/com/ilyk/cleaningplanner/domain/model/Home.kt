package com.ilyk.cleaningplanner.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Home (Household) model mirroring the backend `Home` struct.
 * Wire contract: snake_case fields, ISO-8601 timestamps via [Instant].
 */
@Serializable
data class Home(
    val id: String,
    @SerialName("owner_user_id")
    val ownerUserId: String,
    val name: String,
    val tz: String,
    val locale: String,
    @SerialName("created_at")
    val createdAt: Instant,
    @SerialName("updated_at")
    val updatedAt: Instant,
    val metadata: JsonObject? = null
)
