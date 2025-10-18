package com.redasgard.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.redasgard.cleaningplanner.core.model.ChipUsage
import kotlinx.datetime.Instant

@Entity(
    tableName = "chip_usages",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CommentChipEntity::class,
            parentColumns = ["id"],
            childColumns = ["chipId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["chipId"])
    ]
)
data class ChipUsageEntity(
    @PrimaryKey
    val id: String,
    val taskId: String,
    val chipId: String,
    val createdAt: Instant
)

fun ChipUsageEntity.toModel() = ChipUsage(
    id = id,
    taskId = taskId,
    chipId = chipId,
    createdAt = createdAt
)

fun ChipUsage.toEntity() = ChipUsageEntity(
    id = id,
    taskId = taskId,
    chipId = chipId,
    createdAt = createdAt
)

