package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.model.domain.Member
import kotlinx.coroutines.flow.Flow

interface MemberRepository {
    fun getAllMembers(): Flow<List<Member>>
    suspend fun insertMember(member: Member)
    suspend fun updateMember(member: Member)
    suspend fun deleteMember(memberId: String)
}
