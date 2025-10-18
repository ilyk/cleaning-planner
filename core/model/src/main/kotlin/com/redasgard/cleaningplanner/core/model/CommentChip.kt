package com.redasgard.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CommentChip(
    val id: String,
    val householdId: String,
    val text: String,
    val pinned: Boolean = false
)

