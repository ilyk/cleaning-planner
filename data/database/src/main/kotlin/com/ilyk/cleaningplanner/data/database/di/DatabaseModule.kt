package com.ilyk.cleaningplanner.data.database.di

import android.content.Context
import androidx.room.Room
import com.ilyk.cleaningplanner.data.database.CleaningPlannerDatabase
import com.ilyk.cleaningplanner.data.database.dao.CommentChipDao
import com.ilyk.cleaningplanner.data.database.dao.HouseholdDao
import com.ilyk.cleaningplanner.data.database.dao.MemberDao
import com.ilyk.cleaningplanner.data.database.dao.RoomDao
import com.ilyk.cleaningplanner.data.database.dao.ScheduleDao
import com.ilyk.cleaningplanner.data.database.dao.TaskDao
import com.ilyk.cleaningplanner.data.database.dao.TemplateDao
import com.ilyk.cleaningplanner.data.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCleaningPlannerDatabase(
        @ApplicationContext context: Context
    ): CleaningPlannerDatabase {
        return Room.databaseBuilder(
            context,
            CleaningPlannerDatabase::class.java,
            "cleaning_planner.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserDao(database: CleaningPlannerDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideHouseholdDao(database: CleaningPlannerDatabase): HouseholdDao {
        return database.householdDao()
    }

    @Provides
    fun provideMemberDao(database: CleaningPlannerDatabase): MemberDao {
        return database.memberDao()
    }

    @Provides
    fun provideRoomDao(database: CleaningPlannerDatabase): RoomDao {
        return database.roomDao()
    }

    @Provides
    fun provideTemplateDao(database: CleaningPlannerDatabase): TemplateDao {
        return database.templateDao()
    }

    @Provides
    fun provideTaskDao(database: CleaningPlannerDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideCommentChipDao(database: CleaningPlannerDatabase): CommentChipDao {
        return database.commentChipDao()
    }

    @Provides
    fun provideScheduleDao(database: CleaningPlannerDatabase): ScheduleDao {
        return database.scheduleDao()
    }
}

