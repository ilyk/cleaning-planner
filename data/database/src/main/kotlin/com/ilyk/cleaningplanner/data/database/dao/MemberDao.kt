package com.ilyk.cleaningplanner.data.database.dao

import androidx.room.*
import com.ilyk.cleaningplanner.data.database.entities.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members")
    fun getAllMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :memberId")
    suspend fun getMemberById(memberId: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Update
    suspend fun updateMember(member: MemberEntity)

    @Delete
    suspend fun deleteMember(member: MemberEntity)

    @Query("DELETE FROM members WHERE id = :memberId")
    suspend fun deleteMemberById(memberId: String)

    // Compatibility methods for existing repository code
    @Query("SELECT * FROM members")
    fun observeByHousehold(householdId: String): Flow<List<MemberEntity>> = getAllMembers()

    suspend fun insert(member: MemberEntity) = insertMember(member)
}