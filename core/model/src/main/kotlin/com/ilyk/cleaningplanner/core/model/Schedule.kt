package com.ilyk.cleaningplanner.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Schedule(
    val id: String,
    val householdId: String,
    val title: String,
    val recurrence: Recurrence,
    val daysOfWeek: List<Int>? = null,
    val dayOfMonth: Int? = null,
    val templateId: String? = null,
    val roomId: String? = null,
    val assigneeId: String? = null,
    val nextRun: Instant,
    val active: Boolean = true
)

