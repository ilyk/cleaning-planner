package com.redasgard.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.redasgard.cleaningplanner.core.model.TaskStatus
import com.redasgard.cleaningplanner.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface TaskDao {
    
    @Query("SELECT * FROM tasks WHERE householdId = :householdId ORDER BY dueDate ASC")
    fun observeAll(householdId: String): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks 
        WHERE householdId = :householdId 
        AND date(dueDate / 1000, 'unixepoch') = date('now')
        ORDER BY dueDate ASC
    """)
    fun observeToday(householdId: String): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks 
        WHERE householdId = :householdId 
        AND status = :status
        ORDER BY dueDate ASC
    """)
    fun observeByStatus(householdId: String, status: TaskStatus): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE assigneeId = :memberId ORDER BY dueDate ASC")
    fun observeByAssignee(memberId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE roomId = :roomId ORDER BY dueDate ASC")
    fun observeByRoom(roomId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, pendingSync = 1 WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: TaskStatus)

    @Query("UPDATE tasks SET actualMin = :minutes, pendingSync = 1 WHERE id = :taskId")
    suspend fun updateActualTime(taskId: String, minutes: Int)

    @Query("UPDATE tasks SET notes = :notes, pendingSync = 1 WHERE id = :taskId")
    suspend fun updateNotes(taskId: String, notes: String?)

    @Query("UPDATE tasks SET assigneeId = :memberId, pendingSync = 1 WHERE id = :taskId")
    suspend fun updateAssignee(taskId: String, memberId: String?)

    @Query("UPDATE tasks SET pendingSync = 0 WHERE id = :taskId")
    suspend fun clearPendingSync(taskId: String)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: String)

    @Query("DELETE FROM tasks WHERE householdId = :householdId")
    suspend fun deleteAllByHousehold(householdId: String)
}

