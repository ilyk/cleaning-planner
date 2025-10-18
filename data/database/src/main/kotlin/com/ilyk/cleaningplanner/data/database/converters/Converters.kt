package com.ilyk.cleaningplanner.data.database.converters

import androidx.room.TypeConverter
import com.ilyk.cleaningplanner.core.model.Recurrence
import com.ilyk.cleaningplanner.core.model.Role
import com.ilyk.cleaningplanner.core.model.TaskStatus
import com.ilyk.cleaningplanner.core.model.TemplateStep
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    
    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun fromRole(value: Role): String {
        return value.name
    }

    @TypeConverter
    fun toRole(value: String): Role {
        return Role.valueOf(value)
    }

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String {
        return value.name
    }

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus {
        return TaskStatus.valueOf(value)
    }

    @TypeConverter
    fun fromRecurrence(value: Recurrence): String {
        return value.name
    }

    @TypeConverter
    fun toRecurrence(value: String): Recurrence {
        return Recurrence.valueOf(value)
    }

    @TypeConverter
    fun fromTemplateStepList(value: List<TemplateStep>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toTemplateStepList(value: String): List<TemplateStep> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        return value?.let { Json.decodeFromString(it) }
    }
}

