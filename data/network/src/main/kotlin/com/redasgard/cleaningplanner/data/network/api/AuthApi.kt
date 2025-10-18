package com.redasgard.cleaningplanner.data.network.api

import com.redasgard.cleaningplanner.core.model.User
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/verify")
    suspend fun verifyMagicLink(@Body request: VerifyRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(): Unit
}

data class LoginRequest(val email: String)
data class VerifyRequest(val token: String)
data class RefreshRequest(val refreshToken: String)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

