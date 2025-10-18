package com.redasgard.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.redasgard.cleaningplanner.core.model.RoomX

@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["householdId"]),
        Index(value = ["qrSlug"], unique = true)
    ]
)
data class RoomEntity(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val name: String,
    val qrSlug: String,
    val order: Int
)

fun RoomEntity.toModel() = RoomX(
    id = id,
    householdId = householdId,
    name = name,
    qrSlug = qrSlug,
    order = order
)

fun RoomX.toEntity() = RoomEntity(
    id = id,
    householdId = householdId,
    name = name,
    qrSlug = qrSlug,
    order = order
)

