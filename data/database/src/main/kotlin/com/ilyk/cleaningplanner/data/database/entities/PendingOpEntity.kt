package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing pending operations for offline sync
 */
@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey
    val id: String,
    val ts: Long,
    val type: String,
    val payload: String
)
