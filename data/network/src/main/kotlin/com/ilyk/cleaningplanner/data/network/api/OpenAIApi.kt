package com.ilyk.cleaningplanner.data.network.api

import com.ilyk.cleaningplanner.core.model.OpenAIRequest
import com.ilyk.cleaningplanner.core.model.OpenAIResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenAIApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIRequest
    ): OpenAIResponse
}

