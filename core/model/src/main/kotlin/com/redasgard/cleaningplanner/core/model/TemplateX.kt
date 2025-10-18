package com.redasgard.cleaningplanner.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TemplateX(
    val id: String,
    val roomId: String,
    val title: String,
    val steps: List<TemplateStep> = emptyList(),
    val defaultRecurrence: Recurrence = Recurrence.NONE
)

