package com.ilyk.cleaningplanner.data.repository

import com.ilyk.cleaningplanner.core.common.result.Result
import com.ilyk.cleaningplanner.core.model.Household
import com.ilyk.cleaningplanner.core.model.Member
import com.ilyk.cleaningplanner.data.database.dao.HouseholdDao
import com.ilyk.cleaningplanner.data.database.dao.MemberDao
import com.ilyk.cleaningplanner.data.database.entities.toEntity
import com.ilyk.cleaningplanner.data.database.entities.toModel
import com.ilyk.cleaningplanner.data.network.api.CreateHouseholdRequest
import com.ilyk.cleaningplanner.data.network.api.HouseholdApi
import com.ilyk.cleaningplanner.data.network.api.JoinHouseholdRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseholdRepository @Inject constructor(
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val householdApi: HouseholdApi
) {
    
    fun observeHouseholds(): Flow<List<Household>> {
        return householdDao.observeAll().map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun observeMembers(householdId: String): Flow<List<Member>> {
        return memberDao.observeByHousehold(householdId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun createHousehold(name: String): Result<Household> {
        return try {
            val household = householdApi.create(CreateHouseholdRequest(name))
            householdDao.insert(household.toEntity())
            Result.Success(household)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun joinHousehold(inviteCode: String): Result<Member> {
        return try {
            val member = householdApi.joinByInviteCode(JoinHouseholdRequest(inviteCode))
            memberDao.insert(member.toEntity())
            Result.Success(member)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun syncFromNetwork(): Result<Unit> {
        return try {
            val households = householdApi.list()
            householdDao.insertAll(households.map { it.toEntity() })
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

