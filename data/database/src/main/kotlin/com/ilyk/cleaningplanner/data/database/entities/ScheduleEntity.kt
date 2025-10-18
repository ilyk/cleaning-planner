package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.core.model.Recurrence
import com.ilyk.cleaningplanner.core.model.Schedule
import kotlinx.datetime.Instant

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
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
        Index(value = ["templateId"]),
        Index(value = ["roomId"]),
        Index(value = ["assigneeId"]),
        Index(value = ["nextRun"])
    ]
)
data class ScheduleEntity(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val title: String,
    val recurrence: Recurrence,
    val daysOfWeek: List<Int>?,
    val dayOfMonth: Int?,
    val templateId: String?,
    val roomId: String?,
    val assigneeId: String?,
    val nextRun: Instant,
    val active: Boolean
)

fun ScheduleEntity.toModel() = Schedule(
    id = id,
    householdId = householdId,
    title = title,
    recurrence = recurrence,
    daysOfWeek = daysOfWeek,
    dayOfMonth = dayOfMonth,
    templateId = templateId,
    roomId = roomId,
    assigneeId = assigneeId,
    nextRun = nextRun,
    active = active
)

fun Schedule.toEntity() = ScheduleEntity(
    id = id,
    householdId = householdId,
    title = title,
    recurrence = recurrence,
    daysOfWeek = daysOfWeek,
    dayOfMonth = dayOfMonth,
    templateId = templateId,
    roomId = roomId,
    assigneeId = assigneeId,
    nextRun = nextRun,
    active = active
)

