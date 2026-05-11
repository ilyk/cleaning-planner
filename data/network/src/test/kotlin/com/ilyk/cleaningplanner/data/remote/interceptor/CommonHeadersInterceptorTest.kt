package com.ilyk.cleaningplanner.data.remote.interceptor

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Verifies the headers contract on outgoing requests:
 *  - `Authorization: Bearer <token>` from the supplied token provider.
 *  - `X-Client-Version`, `X-Prompt-Version`, `X-Policy-Version` from [ClientVersions].
 *  - `Idempotency-Key` on mutating methods only (POST / PATCH / PUT / DELETE), unique per call.
 */
class CommonHeadersInterceptorTest {

    private val versions = ClientVersions(
        client = "1.0.0",
        prompt = "1.2.3",
        policy = "2.3.4"
    )

    private val interceptor = CommonHeadersInterceptor(
        tokenProvider = { "test-token" },
        versions = versions
    )

    private fun request(method: String): Request {
        val builder = Request.Builder().url("https://example.test/v1/ping")
        return when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(ByteArray(0).toRequestBody()).build()
            "PATCH" -> builder.patch(ByteArray(0).toRequestBody()).build()
            "PUT" -> builder.put(ByteArray(0).toRequestBody()).build()
            "DELETE" -> builder.delete().build()
            else -> throw IllegalArgumentException("Unknown method: $method")
        }
    }

    private fun captureBuiltRequest(originalRequest: Request): Request {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns originalRequest
        val captured = slot<Request>()
        every { chain.proceed(capture(captured)) } returns Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        interceptor.intercept(chain)
        return captured.captured
    }

    @Test
    fun `adds auth and version headers on every request`() {
        val built = captureBuiltRequest(request("GET"))
        assertEquals("Bearer test-token", built.header("Authorization"))
        assertEquals("1.0.0", built.header("X-Client-Version"))
        assertEquals("1.2.3", built.header("X-Prompt-Version"))
        assertEquals("2.3.4", built.header("X-Policy-Version"))
    }

    @Test
    fun `adds idempotency key on POST`() {
        val built = captureBuiltRequest(request("POST"))
        val key = built.header("Idempotency-Key")
        assertNotNull("Idempotency-Key must be present on POST", key)
        assertTrue("Idempotency-Key must be non-empty", key!!.isNotEmpty())
    }

    @Test
    fun `adds idempotency key on PATCH PUT DELETE`() {
        listOf("PATCH", "PUT", "DELETE").forEach { method ->
            val built = captureBuiltRequest(request(method))
            assertNotNull("Idempotency-Key must be present on $method", built.header("Idempotency-Key"))
        }
    }

    @Test
    fun `does not add idempotency key on GET`() {
        val built = captureBuiltRequest(request("GET"))
        assertNull("Idempotency-Key must not be present on GET", built.header("Idempotency-Key"))
    }

    @Test
    fun `idempotency keys are unique across calls`() {
        val key1 = captureBuiltRequest(request("POST")).header("Idempotency-Key")
        val key2 = captureBuiltRequest(request("POST")).header("Idempotency-Key")
        assertNotNull(key1); assertNotNull(key2)
        assertNotEquals("Idempotency keys must be freshly generated per call", key1, key2)
    }

    @Test
    fun `token provider is called per request so token refresh is observed`() {
        var counter = 0
        val freshInterceptor = CommonHeadersInterceptor(
            tokenProvider = { counter++; "token-$counter" },
            versions = versions
        )
        val chain1 = mockk<Interceptor.Chain>()
        val chain2 = mockk<Interceptor.Chain>()
        val req = request("GET")
        val captured1 = slot<Request>()
        val captured2 = slot<Request>()
        every { chain1.request() } returns req
        every { chain1.proceed(capture(captured1)) } returns
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
        every { chain2.request() } returns req
        every { chain2.proceed(capture(captured2)) } returns
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
        freshInterceptor.intercept(chain1)
        freshInterceptor.intercept(chain2)
        assertEquals("Bearer token-1", captured1.captured.header("Authorization"))
        assertEquals("Bearer token-2", captured2.captured.header("Authorization"))
    }
}
