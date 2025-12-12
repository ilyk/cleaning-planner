package com.ilyk.cleaningplanner.data.remote.interceptor

import com.ilyk.cleaningplanner.domain.model.ErrorEnvelope
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Interceptor that handles error responses and converts them to ErrorEnvelope
 * Provides consistent error handling across all API calls
 */
class ErrorHandlingInterceptor : Interceptor {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            
            try {
                // Try to parse as ErrorEnvelope
                val errorEnvelope = json.decodeFromString<ErrorEnvelope>(errorBody)
                throw ApiException(errorEnvelope, response.code)
            } catch (e: Exception) {
                // If parsing fails, create a generic error
                val genericError = ErrorEnvelope(
                    error = "HTTP_ERROR",
                    message = "Request failed with status ${response.code}",
                    code = response.code.toString(),
                    details = mapOf("body" to errorBody),
                    timestamp = kotlinx.datetime.Clock.System.now()
                )
                throw ApiException(genericError, response.code)
            }
        }
        
        return response
    }
}

/**
 * Custom exception for API errors
 */
class ApiException(
    val errorEnvelope: ErrorEnvelope,
    val httpCode: Int
) : IOException("API Error: ${errorEnvelope.message}") {
    
    val isNetworkError: Boolean
        get() = httpCode in 500..599
    
    val isClientError: Boolean
        get() = httpCode in 400..499
    
    val isAuthError: Boolean
        get() = httpCode == 401 || httpCode == 403
}
