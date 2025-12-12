package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing Task entities in the CleanFlow database
 */
@Dao
interface TaskDao {
    
    @Query("SELECT * FROM cleanflow_tasks WHERE planId = :planId ORDER BY createdAt ASC")
    fun getTasksByPlanId(planId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM cleanflow_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM cleanflow_tasks WHERE assignedTo = :memberId ORDER BY createdAt ASC")
    fun getTasksByAssignee(memberId: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE cleanflow_tasks SET completedAt = :completedAt WHERE id = :taskId")
    suspend fun markCompleted(taskId: String, completedAt: Long)

    @Query("UPDATE cleanflow_tasks SET skippedAt = :skippedAt WHERE id = :taskId")
    suspend fun markSkipped(taskId: String, skippedAt: Long)

    @Query("UPDATE cleanflow_tasks SET assignedTo = :memberId WHERE id = :taskId")
    suspend fun assignTask(taskId: String, memberId: String?)

    @Query("DELETE FROM cleanflow_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM cleanflow_tasks WHERE planId = :planId")
    suspend fun deleteTasksByPlanId(planId: String)
}

