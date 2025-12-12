package com.ilyk.cleaningplanner.core.model.domain

import com.ilyk.cleaningplanner.core.model.TaskStatus
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val title: String,
    val room: String,
    val estimatedMin: Int,
    val assigneeId: String?, // null = unassigned
    val dueDate: LocalDate,
    val timeOfDay: TimeOfDay,
    val status: TaskStatus
)

@Serializable
enum class TimeOfDay {
    Morning,
    Afternoon,
    Evening
}
