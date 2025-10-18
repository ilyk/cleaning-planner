package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.core.model.Task
import com.ilyk.cleaningplanner.core.model.TaskStatus
import kotlinx.datetime.Instant

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["assigneeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["householdId"]),
        Index(value = ["dueDate"]),
        Index(value = ["assigneeId"]),
        Index(value = ["roomId"]),
        Index(value = ["templateId"]),
        Index(value = ["status"])
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val title: String,
    val roomId: String?,
    val templateId: String?,
    val assigneeId: String?,
    val dueDate: Instant?,
    val status: TaskStatus,
    val actualMin: Int?,
    val estMin: Int?,
    val notes: String?,
    val pendingSync: Boolean = false
)

fun TaskEntity.toModel() = Task(
    id = id,
    householdId = householdId,
    title = title,
    roomId = roomId,
    templateId = templateId,
    assigneeId = assigneeId,
    dueDate = dueDate,
    status = status,
    actualMin = actualMin,
    estMin = estMin,
    notes = notes
)

fun Task.toEntity(pendingSync: Boolean = false) = TaskEntity(
    id = id,
    householdId = householdId,
    title = title,
    roomId = roomId,
    templateId = templateId,
    assigneeId = assigneeId,
    dueDate = dueDate,
    status = status,
    actualMin = actualMin,
    estMin = estMin,
    notes = notes,
    pendingSync = pendingSync
)

