package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.domain.model.Task
import kotlinx.datetime.Instant

/**
 * Room entity for Task with offline caching
 */
@Entity(
    tableName = "cleanflow_tasks",
    indices = [
        Index(value = ["id"]),
        Index(value = ["planId"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val planId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val estimatedDurationMinutes: Int,
    val toolsJson: String, // JSON serialized list
    val tipsJson: String, // JSON serialized list
    val qrCode: String?,
    val assignedTo: String?,
    val completedAt: Long?, // Store as timestamp
    val skippedAt: Long?, // Store as timestamp
    val createdAt: Long, // Store as timestamp
    val updatedAt: Long // Store as timestamp
) {
    fun toModel(): Task {
        return Task(
            id = id,
            title = title,
            description = description,
            priority = com.ilyk.cleaningplanner.domain.model.TaskPriority.valueOf(priority),
            estimatedDurationMinutes = estimatedDurationMinutes,
            tools = emptyList(), // TODO: Parse from JSON
            tips = emptyList(), // TODO: Parse from JSON
            qrCode = qrCode,
            assignedTo = assignedTo,
            completedAt = completedAt?.let { Instant.fromEpochMilliseconds(it) },
            skippedAt = skippedAt?.let { Instant.fromEpochMilliseconds(it) },
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            updatedAt = Instant.fromEpochMilliseconds(updatedAt)
        )
    }
    
    companion object {
        fun fromModel(task: Task, planId: String): TaskEntity {
            return TaskEntity(
                id = task.id,
                planId = planId,
                title = task.title,
                description = task.description,
                priority = task.priority.name,
                estimatedDurationMinutes = task.estimatedDurationMinutes,
                toolsJson = "[]", // TODO: Serialize tools to JSON
                tipsJson = "[]", // TODO: Serialize tips to JSON
                qrCode = task.qrCode,
                assignedTo = task.assignedTo,
                completedAt = task.completedAt?.toEpochMilliseconds(),
                skippedAt = task.skippedAt?.toEpochMilliseconds(),
                createdAt = task.createdAt?.toEpochMilliseconds() ?: System.currentTimeMillis(),
                updatedAt = task.updatedAt?.toEpochMilliseconds() ?: System.currentTimeMillis()
            )
        }
    }
}

