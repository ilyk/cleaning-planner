package com.ilyk.cleaningplanner.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * Interceptor that adds common headers to all API requests
 * Implements the header requirements from the Mobile Guide
 */
class CommonHeadersInterceptor(
    private val tokenProvider: () -> String,
    private val versions: ClientVersions
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        
        // Add authentication header
        builder.addHeader("Authorization", "Bearer ${tokenProvider()}")
        
        // Add version headers
        builder.addHeader("X-Client-Version", versions.client)
        builder.addHeader("X-Prompt-Version", versions.prompt)
        builder.addHeader("X-Policy-Version", versions.policy)
        
        // Add idempotency key for mutating operations
        if (request.method in listOf("POST", "PATCH", "PUT", "DELETE")) {
            builder.addHeader("Idempotency-Key", UUID.randomUUID().toString())
        }
        
        return chain.proceed(builder.build())
    }
}

/**
 * Client version information
 */
data class ClientVersions(
    val client: String = "0.9.0",
    val prompt: String = "1.0.0",
    val policy: String = "1.0.0"
)
