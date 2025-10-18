package com.redasgard.cleaningplanner.data.network.api

import com.redasgard.cleaningplanner.core.model.Household
import com.redasgard.cleaningplanner.core.model.Member
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface HouseholdApi {
    
    @GET("households")
    suspend fun list(): List<Household>

    @GET("households/{id}")
    suspend fun getById(@Path("id") id: String): Household

    @POST("households")
    suspend fun create(@Body request: CreateHouseholdRequest): Household

    @POST("households/join")
    suspend fun joinByInviteCode(@Body request: JoinHouseholdRequest): Member

    @GET("households/{id}/members")
    suspend fun getMembers(@Path("id") householdId: String): List<Member>
}

data class CreateHouseholdRequest(val name: String)
data class JoinHouseholdRequest(val inviteCode: String)

