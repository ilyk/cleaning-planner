package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.*
import com.ilyk.cleaningplanner.data.database.entities.PlanEntity
import com.ilyk.cleaningplanner.data.database.entities.TaskEntity
import com.ilyk.cleaningplanner.data.database.entities.PendingOpEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Plan operations with offline caching
 */
@Dao
interface PlanDao {
    
    // Plan operations
    @Query("SELECT * FROM plans WHERE homeId = :homeId AND date = :date AND mode = :mode LIMIT 1")
    suspend fun getByKey(homeId: String, date: String, mode: String): PlanEntity?
    
    @Query("SELECT * FROM plans WHERE homeId = :homeId AND date = :date AND mode = :mode LIMIT 1")
    fun observeByKey(homeId: String, date: String, mode: String): Flow<PlanEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: PlanEntity)
    
    @Query("DELETE FROM plans WHERE homeId = :homeId AND date = :date AND mode = :mode")
    suspend fun deleteByKey(homeId: String, date: String, mode: String)
    
    // Task operations - simplified for now
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)
    
    @Update
    suspend fun updateTask(task: TaskEntity)
    
    // Pending operations
    @Query("SELECT * FROM pending_ops ORDER BY ts ASC")
    suspend fun getAllPendingOps(): List<PendingOpEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOp(pendingOp: PendingOpEntity)
    
    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun deletePendingOp(id: String)
}
