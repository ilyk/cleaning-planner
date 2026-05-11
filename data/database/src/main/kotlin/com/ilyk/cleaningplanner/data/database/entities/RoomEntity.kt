package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local Room-DB representation of a room.
 *
 * Schema retained for backward compatibility with the legacy `householdId` /
 * `qrSlug` / `order` flow. The v1 backend exposes a `Room` shape with
 * `home_id` / `kind` / `metadata`; when a UI screen first needs offline
 * caching for Rooms, this entity will get a schema migration to align with
 * the backend columns.
 */
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
