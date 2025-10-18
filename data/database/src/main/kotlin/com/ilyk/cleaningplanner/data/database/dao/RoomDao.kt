package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entities.RoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    
    @Query("SELECT * FROM rooms WHERE householdId = :householdId ORDER BY `order` ASC, name ASC")
    fun observeAll(householdId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :roomId")
    suspend fun getById(roomId: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE qrSlug = :qrSlug LIMIT 1")
    suspend fun getByQrSlug(qrSlug: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rooms: List<RoomEntity>)

    @Update
    suspend fun update(room: RoomEntity)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun delete(roomId: String)

    @Query("DELETE FROM rooms WHERE householdId = :householdId")
    suspend fun deleteAllByHousehold(householdId: String)
}

