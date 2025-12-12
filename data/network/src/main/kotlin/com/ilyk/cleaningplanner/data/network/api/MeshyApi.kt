package com.ilyk.cleaningplanner.data.network.api

import com.ilyk.cleaningplanner.core.model.MeshyGenerationRequest
import com.ilyk.cleaningplanner.core.model.MeshyGenerationResponse
import com.ilyk.cleaningplanner.core.model.MeshyTaskStatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Meshy.ai API for text-to-3D generation
 * Docs: https://docs.meshy.ai
 */
interface MeshyApi {
    @POST("v2/text-to-3d")
    suspend fun createTextTo3D(
        @Header("Authorization") authorization: String,
        @Body request: MeshyGenerationRequest
    ): MeshyGenerationResponse

    @GET("v2/text-to-3d/{taskId}")
    suspend fun getTaskStatus(
        @Header("Authorization") authorization: String,
        @Path("taskId") taskId: String
    ): MeshyTaskStatusResponse
}

