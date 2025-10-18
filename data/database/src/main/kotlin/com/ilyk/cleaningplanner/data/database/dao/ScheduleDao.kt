package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entities.ScheduleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface ScheduleDao {
    
    @Query("SELECT * FROM schedules WHERE householdId = :householdId ORDER BY nextRun ASC")
    fun observeAll(householdId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE householdId = :householdId AND active = 1 ORDER BY nextRun ASC")
    fun observeActive(householdId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :scheduleId")
    suspend fun getById(scheduleId: String): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE active = 1 AND nextRun <= :now")
    suspend fun getDueSchedules(now: Instant): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET active = :active WHERE id = :scheduleId")
    suspend fun updateActive(scheduleId: String, active: Boolean)

    @Query("UPDATE schedules SET nextRun = :nextRun WHERE id = :scheduleId")
    suspend fun updateNextRun(scheduleId: String, nextRun: Instant)

    @Query("DELETE FROM schedules WHERE id = :scheduleId")
    suspend fun delete(scheduleId: String)

    @Query("DELETE FROM schedules WHERE householdId = :householdId")
    suspend fun deleteAllByHousehold(householdId: String)
}

