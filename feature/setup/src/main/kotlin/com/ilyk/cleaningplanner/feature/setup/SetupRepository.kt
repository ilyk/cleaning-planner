package com.ilyk.cleaningplanner.feature.setup

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for setup wizard API calls
 */
interface SetupRepository {
    suspend fun submitSetup(request: ManualSetupRequest): ManualSetupResponse
}

/**
 * Implementation using OkHttp and the backend API
 */
@Singleton
class DefaultSetupRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String
) : SetupRepository {

    override suspend fun submitSetup(request: ManualSetupRequest): ManualSetupResponse {
        val requestBody = json.encodeToString(ManualSetupRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/onboarding/setup")
            .post(requestBody)
            .build()

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = httpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                throw SetupException("Setup failed with status: ${response.code}")
            }

            val body = response.body?.string()
                ?: throw SetupException("Empty response from server")

            json.decodeFromString(ManualSetupResponse.serializer(), body)
        }
    }
}

/**
 * Exception for setup-related errors
 */
class SetupException(message: String, cause: Throwable? = null) : Exception(message, cause)
