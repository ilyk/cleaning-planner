package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.model.domain.Member
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepositoryImpl @Inject constructor() : MemberRepository {
    override fun getAllMembers(): Flow<List<Member>> {
        return flowOf(emptyList())
    }
    
    override suspend fun insertMember(member: Member) {
        // Stub implementation
    }
    
    override suspend fun updateMember(member: Member) {
        // Stub implementation
    }
    
    override suspend fun deleteMember(memberId: String) {
        // Stub implementation
    }
}
