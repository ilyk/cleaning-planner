package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.core.model.CommentChip

@Entity(
    tableName = "comment_chips",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["householdId"])]
)
data class CommentChipEntity(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val text: String,
    val pinned: Boolean
)

fun CommentChipEntity.toModel() = CommentChip(
    id = id,
    householdId = householdId,
    text = text,
    pinned = pinned
)

fun CommentChip.toEntity() = CommentChipEntity(
    id = id,
    householdId = householdId,
    text = text,
    pinned = pinned
)

