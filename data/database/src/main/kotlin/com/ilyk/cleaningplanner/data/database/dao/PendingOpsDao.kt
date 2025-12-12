package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.*
import com.ilyk.cleaningplanner.data.database.entities.PendingOpEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for pending operations
 */
@Dao
interface PendingOpsDao {
    
    @Query("SELECT * FROM pending_ops ORDER BY ts ASC")
    suspend fun getAllPendingOps(): List<PendingOpEntity>
    
    @Query("SELECT * FROM pending_ops ORDER BY ts ASC")
    fun observeAllPendingOps(): Flow<List<PendingOpEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pendingOp: PendingOpEntity)
    
    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun deletePendingOp(id: String)
    
    @Query("DELETE FROM pending_ops")
    suspend fun deleteAll()
}
