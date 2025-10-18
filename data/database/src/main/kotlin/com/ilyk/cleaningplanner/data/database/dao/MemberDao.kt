package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ilyk.cleaningplanner.data.database.entities.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    
    @Query("SELECT * FROM members WHERE householdId = :householdId")
    fun observeByHousehold(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE userId = :userId")
    fun observeByUser(userId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :memberId")
    suspend fun getById(memberId: String): MemberEntity?

    @Query("SELECT * FROM members WHERE userId = :userId AND householdId = :householdId LIMIT 1")
    suspend fun getByUserAndHousehold(userId: String, householdId: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    @Update
    suspend fun update(member: MemberEntity)

    @Query("DELETE FROM members WHERE id = :memberId")
    suspend fun delete(memberId: String)

    @Query("DELETE FROM members WHERE householdId = :householdId")
    suspend fun deleteAllByHousehold(householdId: String)
}

