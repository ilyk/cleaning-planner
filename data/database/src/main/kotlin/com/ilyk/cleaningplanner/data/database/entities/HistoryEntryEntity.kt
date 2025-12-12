package com.ilyk.cleaningplanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ilyk.cleaningplanner.core.model.TaskStatus
import kotlinx.datetime.LocalDate

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val date: LocalDate,
    val status: TaskStatus,
    val durationMin: Int?,
    val note: String?,
    val origin: String = "app",
    val deviceId: String? = null,
    val source: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
