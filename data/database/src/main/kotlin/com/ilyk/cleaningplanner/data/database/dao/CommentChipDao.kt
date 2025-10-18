package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entities.CommentChipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentChipDao {
    
    @Query("SELECT * FROM comment_chips WHERE householdId = :householdId ORDER BY pinned DESC, text ASC")
    fun observeAll(householdId: String): Flow<List<CommentChipEntity>>

    @Query("SELECT * FROM comment_chips WHERE id = :chipId")
    suspend fun getById(chipId: String): CommentChipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chip: CommentChipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chips: List<CommentChipEntity>)

    @Update
    suspend fun update(chip: CommentChipEntity)

    @Query("DELETE FROM comment_chips WHERE id = :chipId")
    suspend fun delete(chipId: String)

    @Query("DELETE FROM comment_chips WHERE householdId = :householdId")
    suspend fun deleteAllByHousehold(householdId: String)
}

