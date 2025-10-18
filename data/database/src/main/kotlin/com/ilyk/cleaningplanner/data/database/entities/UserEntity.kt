package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.core.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val email: String,
    val displayName: String?,
    val locale: String
)

fun UserEntity.toModel() = User(
    id = id,
    email = email,
    displayName = displayName,
    locale = locale
)

fun User.toEntity() = UserEntity(
    id = id,
    email = email,
    displayName = displayName,
    locale = locale
)

