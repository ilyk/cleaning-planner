package com.redasgard.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.redasgard.cleaningplanner.core.model.Household

@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val inviteCode: String
)

fun HouseholdEntity.toModel() = Household(
    id = id,
    name = name,
    inviteCode = inviteCode
)

fun Household.toEntity() = HouseholdEntity(
    id = id,
    name = name,
    inviteCode = inviteCode
)

