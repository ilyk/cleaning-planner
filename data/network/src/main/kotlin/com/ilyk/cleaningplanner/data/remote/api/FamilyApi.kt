package com.ilyk.cleaningplanner.data.remote.api

import com.ilyk.cleaningplanner.data.remote.dto.AssignResult
import com.ilyk.cleaningplanner.data.remote.dto.FamilyAssignRequest
import com.ilyk.cleaningplanner.domain.model.FamilyMember
import retrofit2.http.*

/**
 * Family API interface for CleanFlow backend
 * Handles family member management and task assignments
 */
interface FamilyApi {
    
    @POST("/v1/family/assign")
    suspend fun assign(@Body request: FamilyAssignRequest): AssignResult
    
    @GET("/v1/family/members")
    suspend fun getMembers(@Query("homeId") homeId: String): List<FamilyMember>
    
    @GET("/v1/family/member/{memberId}")
    suspend fun getMember(@Path("memberId") memberId: String): FamilyMember
}
