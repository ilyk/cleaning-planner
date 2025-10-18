package com.redasgard.cleaningplanner.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.redasgard.cleaningplanner.data.database.converters.Converters
import com.redasgard.cleaningplanner.data.database.dao.CommentChipDao
import com.redasgard.cleaningplanner.data.database.dao.HouseholdDao
import com.redasgard.cleaningplanner.data.database.dao.MemberDao
import com.redasgard.cleaningplanner.data.database.dao.RoomDao
import com.redasgard.cleaningplanner.data.database.dao.ScheduleDao
import com.redasgard.cleaningplanner.data.database.dao.TaskDao
import com.redasgard.cleaningplanner.data.database.dao.TemplateDao
import com.redasgard.cleaningplanner.data.database.dao.UserDao
import com.redasgard.cleaningplanner.data.database.entities.ChipUsageEntity
import com.redasgard.cleaningplanner.data.database.entities.CommentChipEntity
import com.redasgard.cleaningplanner.data.database.entities.HouseholdEntity
import com.redasgard.cleaningplanner.data.database.entities.MemberEntity
import com.redasgard.cleaningplanner.data.database.entities.RoomEntity
import com.redasgard.cleaningplanner.data.database.entities.ScheduleEntity
import com.redasgard.cleaningplanner.data.database.entities.TaskEntity
import com.redasgard.cleaningplanner.data.database.entities.TemplateEntity
import com.redasgard.cleaningplanner.data.database.entities.UserEntity

@Database(
    entities = [
        UserEntity::class,
        HouseholdEntity::class,
        MemberEntity::class,
        RoomEntity::class,
        TemplateEntity::class,
        TaskEntity::class,
        CommentChipEntity::class,
        ChipUsageEntity::class,
        ScheduleEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CleaningPlannerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun householdDao(): HouseholdDao
    abstract fun memberDao(): MemberDao
    abstract fun roomDao(): RoomDao
    abstract fun templateDao(): TemplateDao
    abstract fun taskDao(): TaskDao
    abstract fun commentChipDao(): CommentChipDao
    abstract fun scheduleDao(): ScheduleDao
}

