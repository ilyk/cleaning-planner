package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ilyk.cleaningplanner.domain.model.Plan
import com.ilyk.cleaningplanner.domain.model.Task
import kotlinx.datetime.Instant

/**
 * Room entity for Plan with offline caching
 */
@Entity(
    tableName = "plans",
    indices = [
        Index(value = ["homeId", "date", "mode"], unique = true)
    ]
)
data class PlanEntity(
    @PrimaryKey
    val id: String,
    val homeId: String,
    val date: String,
    val mode: String,
    val version: Int,
    val createdAt: Long, // Store as timestamp
    val updatedAt: Long, // Store as timestamp
    val tasksJson: String, // JSON serialized tasks
    val metadataJson: String // JSON serialized metadata
) {
    fun toModel(): Plan {
        // Simplified for now - return a basic plan
        return Plan(
            id = id,
            homeId = homeId,
            date = date,
            mode = com.ilyk.cleaningplanner.domain.model.CleaningMode.valueOf(mode),
            version = version,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            tasks = emptyList(), // TODO: Parse from JSON
            metadata = com.ilyk.cleaningplanner.domain.model.PlanMetadata(
                totalEstimatedMinutes = 0,
                taskCount = 0
            ),
            sections = null,
            promptVersion = null,
            policyVersion = null,
            cached = null
        )
    }
    
    companion object {
        fun fromModel(plan: Plan): PlanEntity {
            return PlanEntity(
                id = plan.id,
                homeId = plan.homeId,
                date = plan.date,
                mode = plan.mode.name,
                version = plan.version,
                createdAt = plan.createdAt?.toEpochMilliseconds() ?: System.currentTimeMillis(),
                updatedAt = plan.updatedAt?.toEpochMilliseconds() ?: System.currentTimeMillis(),
                tasksJson = "[]", // TODO: Serialize tasks to JSON
                metadataJson = "{}" // TODO: Serialize metadata to JSON
            )
        }
    }
}

