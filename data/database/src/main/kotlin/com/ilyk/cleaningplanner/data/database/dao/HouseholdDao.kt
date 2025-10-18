package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entities.HouseholdEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
    
    @Query("SELECT * FROM households")
    fun observeAll(): Flow<List<HouseholdEntity>>

    @Query("SELECT * FROM households WHERE id = :householdId")
    suspend fun getById(householdId: String): HouseholdEntity?

    @Query("SELECT * FROM households WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getByInviteCode(inviteCode: String): HouseholdEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(household: HouseholdEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(households: List<HouseholdEntity>)

    @Update
    suspend fun update(household: HouseholdEntity)

    @Query("DELETE FROM households WHERE id = :householdId")
    suspend fun delete(householdId: String)
}

