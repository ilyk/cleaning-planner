package com.ilyk.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RoomX(
    val id: String,
    val householdId: String,
    val name: String,
    val qrSlug: String,
    val order: Int = 0
)

