package com.ilyk.cleaningplanner.data.remote.api

import com.ilyk.cleaningplanner.data.remote.dto.*
import com.ilyk.cleaningplanner.domain.model.ClaraSession
import com.ilyk.cleaningplanner.domain.model.ClaraTurn
import retrofit2.http.*

/**
 * Clara voice assistant API interface
 * Handles session management and turn initiation
 */
interface ClaraApi {

    @POST("/v1/clara/session")
    suspend fun createSession(@Body request: CreateSessionRequest): ClaraSession

    @POST("/v1/clara/session/turn")
    suspend fun startTurn(@Body request: StartTurnRequest): ClaraTurn

    @POST("/v1/clara/cancel")
    suspend fun cancel(@Body request: CancelTurnRequest)

    @GET("/v1/clara/session/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: String): ClaraSession

    @DELETE("/v1/clara/session/{sessionId}")
    suspend fun endSession(@Path("sessionId") sessionId: String)

    /**
     * Extract structured home data from onboarding conversation
     * POST /v1/onboarding/extract
     */
    @POST("/v1/onboarding/extract")
    suspend fun extractFromConversation(@Body request: ExtractFromConversationRequest): ExtractFromConversationResponse
}
