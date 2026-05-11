package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local Room-DB representation of a household.
 *
 * Schema retained for backward compatibility with the legacy `inviteCode`-based
 * household flow. The v1 backend exposes a richer `Home` shape (`owner_user_id`,
 * `tz`, `locale`, `metadata`); when a UI screen first needs offline caching for
 * Homes, this entity will get a schema migration to add those columns.
 */
@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val inviteCode: String
)
