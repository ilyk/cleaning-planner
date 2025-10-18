package com.redasgard.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.redasgard.cleaningplanner.core.model.Recurrence
import com.redasgard.cleaningplanner.core.model.TemplateStep
import com.redasgard.cleaningplanner.core.model.TemplateX

@Entity(
    tableName = "templates",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["roomId"])]
)
data class TemplateEntity(
    @PrimaryKey
    val id: String,
    val roomId: String,
    val title: String,
    val steps: List<TemplateStep>,
    val defaultRecurrence: Recurrence
)

fun TemplateEntity.toModel() = TemplateX(
    id = id,
    roomId = roomId,
    title = title,
    steps = steps,
    defaultRecurrence = defaultRecurrence
)

fun TemplateX.toEntity() = TemplateEntity(
    id = id,
    roomId = roomId,
    title = title,
    steps = steps,
    defaultRecurrence = defaultRecurrence
)

