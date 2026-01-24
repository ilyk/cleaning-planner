package com.ilyk.cleaningplanner.feature.clara.chat

import com.ilyk.cleaningplanner.feature.clara.protocol.ClaraStreamClient
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory interface for creating ClaraStreamClient instances.
 * This allows for proper scoping of the WebSocket client to the ViewModel's lifecycle.
 */
interface ClaraStreamClientFactory {
    fun create(scope: CoroutineScope): ClaraStreamClient
}

/**
 * Default implementation that creates ClaraStreamClient instances.
 * Uses the configured OkHttpClient which includes the CommonHeadersInterceptor
 * for authentication headers.
 */
@Singleton
class DefaultClaraStreamClientFactory @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ClaraStreamClientFactory {

    override fun create(scope: CoroutineScope): ClaraStreamClient {
        return ClaraStreamClient(
            okHttpClient = okHttpClient,
            coroutineScope = scope
        )
    }
}
