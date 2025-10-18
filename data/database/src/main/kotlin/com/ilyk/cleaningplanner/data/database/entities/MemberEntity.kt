package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.core.model.Member
import com.ilyk.cleaningplanner.core.model.Role

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["householdId"])
    ]
)
data class MemberEntity(
    @PrimaryKey
    val id: String,
    val role: Role,
    val nickname: String?,
    val colorHex: String?,
    val userId: String,
    val householdId: String
)

fun MemberEntity.toModel() = Member(
    id = id,
    role = role,
    nickname = nickname,
    colorHex = colorHex,
    userId = userId,
    householdId = householdId
)

fun Member.toEntity() = MemberEntity(
    id = id,
    role = role,
    nickname = nickname,
    colorHex = colorHex,
    userId = userId,
    householdId = householdId
)

