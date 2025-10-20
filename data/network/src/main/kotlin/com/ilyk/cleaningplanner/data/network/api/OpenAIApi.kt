package com.ilyk.cleaningplanner.data.network.api

import com.ilyk.cleaningplanner.core.model.OpenAIRequest
import com.ilyk.cleaningplanner.core.model.OpenAIRequestGPT5
import com.ilyk.cleaningplanner.core.model.OpenAIResponse
import com.ilyk.cleaningplanner.core.model.WhisperResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface OpenAIApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIRequest
    ): OpenAIResponse

    @POST("v1/chat/completions")
    suspend fun createChatCompletionGPT5(
        @Header("Authorization") authorization: String,
        @Body request: OpenAIRequestGPT5
    ): OpenAIResponse
    
    @POST("v1/audio/speech")
    @Streaming
    suspend fun createSpeech(
        @Header("Authorization") authorization: String,
        @Body request: RequestBody
    ): ResponseBody
    
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part model: MultipartBody.Part,
        @Part language: MultipartBody.Part
    ): WhisperResponse
}

