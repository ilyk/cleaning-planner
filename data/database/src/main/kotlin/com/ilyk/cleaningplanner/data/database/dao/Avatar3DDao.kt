package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entity.Avatar3DEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface Avatar3DDao {
    @Query("SELECT * FROM avatar_assets WHERE is_active = 1 ORDER BY created_at DESC")
    fun getAllActiveAvatars(): Flow<List<Avatar3DEntity>>
    
    @Query("SELECT * FROM avatar_assets WHERE id = :id")
    suspend fun getAvatarById(id: String): Avatar3DEntity?
    
    @Query("SELECT * FROM avatar_assets WHERE id = :id")
    fun getAvatarByIdFlow(id: String): Flow<Avatar3DEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvatar(avatar: Avatar3DEntity)
    
    @Update
    suspend fun updateAvatar(avatar: Avatar3DEntity)
    
    @Query("UPDATE avatar_assets SET is_active = 0 WHERE id = :id")
    suspend fun deleteAvatar(id: String)
    
    @Query("SELECT COUNT(*) FROM avatar_assets WHERE is_active = 1")
    suspend fun getActiveAvatarCount(): Int
    
    @Query("SELECT SUM(file_size_bytes) FROM avatar_assets WHERE is_active = 1")
    suspend fun getTotalStorageUsed(): Long?
}

