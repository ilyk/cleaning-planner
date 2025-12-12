package com.ilyk.cleaningplanner.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.*

class CommonHeadersInterceptorTest {
    
    @Mock
    private lateinit var chain: Interceptor.Chain
    
    @Mock
    private lateinit var request: Request
    
    @Mock
    private lateinit var response: Response
    
    private lateinit var interceptor: CommonHeadersInterceptor
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        interceptor = CommonHeadersInterceptor(
            tokenProvider = { "test-token" },
            versions = ClientVersions(
                client = "1.0.0",
                prompt = "1.0.0",
                policy = "1.0.0"
            )
        )
    }
    
    @Test
    fun `intercept should add required headers for all requests`() {
        // Given
        val requestBuilder = Request.Builder()
        `when`(chain.request()).thenReturn(request)
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(chain.proceed(any())).thenReturn(response)
        
        // When
        interceptor.intercept(chain)
        
        // Then
        val builtRequest = requestBuilder.build()
        assertEquals("Bearer test-token", builtRequest.header("Authorization"))
        assertEquals("1.0.0", builtRequest.header("X-Client-Version"))
        assertEquals("1.0.0", builtRequest.header("X-Prompt-Version"))
        assertEquals("1.0.0", builtRequest.header("X-Policy-Version"))
    }
    
    @Test
    fun `intercept should add idempotency key for POST requests`() {
        // Given
        val requestBuilder = Request.Builder()
        `when`(chain.request()).thenReturn(request)
        `when`(request.method).thenReturn("POST")
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(chain.proceed(any())).thenReturn(response)
        
        // When
        interceptor.intercept(chain)
        
        // Then
        val builtRequest = requestBuilder.build()
        assertNotNull(builtRequest.header("Idempotency-Key"))
        assertTrue(builtRequest.header("Idempotency-Key")!!.isNotEmpty())
    }
    
    @Test
    fun `intercept should add idempotency key for PATCH requests`() {
        // Given
        val requestBuilder = Request.Builder()
        `when`(chain.request()).thenReturn(request)
        `when`(request.method).thenReturn("PATCH")
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(chain.proceed(any())).thenReturn(response)
        
        // When
        interceptor.intercept(chain)
        
        // Then
        val builtRequest = requestBuilder.build()
        assertNotNull(builtRequest.header("Idempotency-Key"))
    }
    
    @Test
    fun `intercept should not add idempotency key for GET requests`() {
        // Given
        val requestBuilder = Request.Builder()
        `when`(chain.request()).thenReturn(request)
        `when`(request.method).thenReturn("GET")
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(chain.proceed(any())).thenReturn(response)
        
        // When
        interceptor.intercept(chain)
        
        // Then
        val builtRequest = requestBuilder.build()
        assertNull(builtRequest.header("Idempotency-Key"))
    }
    
    @Test
    fun `intercept should generate unique idempotency keys`() {
        // Given
        val requestBuilder1 = Request.Builder()
        val requestBuilder2 = Request.Builder()
        `when`(chain.request()).thenReturn(request)
        `when`(request.method).thenReturn("POST")
        `when`(request.newBuilder()).thenReturn(requestBuilder1, requestBuilder2)
        `when`(chain.proceed(any())).thenReturn(response)
        
        // When
        interceptor.intercept(chain)
        interceptor.intercept(chain)
        
        // Then
        val builtRequest1 = requestBuilder1.build()
        val builtRequest2 = requestBuilder2.build()
        assertNotEquals(
            builtRequest1.header("Idempotency-Key"),
            builtRequest2.header("Idempotency-Key")
        )
    }
}
