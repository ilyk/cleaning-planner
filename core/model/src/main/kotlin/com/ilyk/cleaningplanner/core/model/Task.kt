package com.ilyk.cleaningplanner.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val householdId: String,
    val title: String,
    val roomId: String? = null,
    val templateId: String? = null,
    val assigneeId: String? = null,
    val dueDate: Instant? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val actualMin: Int? = null,
    val estMin: Int? = null,
    val notes: String? = null
)

