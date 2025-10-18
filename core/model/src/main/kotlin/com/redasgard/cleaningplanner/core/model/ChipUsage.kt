package com.redasgard.cleaningplanner.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ChipUsage(
    val id: String,
    val taskId: String,
    val chipId: String,
    val createdAt: Instant
)

