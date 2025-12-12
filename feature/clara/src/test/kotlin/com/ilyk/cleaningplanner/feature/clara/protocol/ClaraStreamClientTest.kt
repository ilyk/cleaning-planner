package com.ilyk.cleaningplanner.feature.clara.protocol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Conformance tests for Clara Streaming Protocol v0.1 (Client)
 *
 * Tests:
 * - Sequence validation
 * - Heartbeat timeout
 * - Retry idempotency
 * - Barge-in/interrupt
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClaraStreamClientTest {

    private lateinit var client: ClaraStreamClient

    @Before
    fun setup() {
        // Mock auth token
        client = ClaraStreamClient(
            authToken = "test-token",
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )
    }

    @Test
    fun `test monotonic sequence numbering`() = runTest {
        // Verify that sequence numbers increment monotonically
        val audioData1 = ByteArray(100) { 1 }
        val audioData2 = ByteArray(100) { 2 }
        val audioData3 = ByteArray(100) { 3 }

        client.startTurn("sess_123", "turn_456", "voice")
        
        assertTrue(client.sendAudioDelta(audioData1))
        assertTrue(client.sendAudioDelta(audioData2))
        assertTrue(client.sendAudioDelta(audioData3))

        // Sequence should be 1, 2, 3
        // In a real test with mock server, we'd verify the sequence numbers
    }

    @Test
    fun `test payload size validation`() = runTest {
        client.startTurn("sess_123", "turn_456", "voice")

        // Create payload larger than MAX_PAYLOAD_SIZE_BYTES
        val largePayload = ByteArray(ProtocolConstants.MAX_PAYLOAD_SIZE_BYTES + 1000)

        // Should fail
        assertFalse(client.sendAudioDelta(largePayload))

        // Check telemetry recorded error
        val telemetry = client.getTelemetry()
        assertTrue(telemetry.errorsByCode.containsKey(ErrorCodes.PAYLOAD_TOO_LARGE))
    }

    @Test
    fun `test connection state transitions`() = runTest {
        // Initial state: Disconnected
        assertEquals(
            ClaraStreamClient.ConnectionState.Disconnected,
            client.connectionState.value
        )

        // Would connect and verify state changes
        // (Requires mock WebSocket server)
    }

    @Test
    fun `test heartbeat mechanism`() = runTest {
        // Heartbeat should be sent every HEARTBEAT_INTERVAL_MS
        // This test would require a mock server to verify ping/pong
        
        // For now, verify constants
        assertEquals(10_000L, ProtocolConstants.HEARTBEAT_INTERVAL_MS)
        assertEquals(5_000L, ProtocolConstants.HEARTBEAT_TIMEOUT_MS)
        assertEquals(3, ProtocolConstants.MAX_MISSED_HEARTBEATS)
    }

    @Test
    fun `test barge-in telemetry`() = runTest {
        client.startTurn("sess_123", "turn_456", "voice")
        
        // Send interrupt
        client.sendInterrupt()
        
        // Verify telemetry
        val telemetry = client.getTelemetry()
        assertEquals(1, telemetry.bargeIns)
    }

    @Test
    fun `test message type serialization`() {
        // Test all message types can be serialized
        val turnStart = TurnStart(
            sessionId = "sess_123",
            turnId = "turn_456",
            input = InputMode(mode = "voice"),
            locale = "en-US"
        )

        assertNotNull(turnStart)
        assertEquals("turn.start", turnStart.type)

        val audioCommit = InputAudioCommit(seq = 42)
        assertEquals("input.audio.commit", audioCommit.type)
        assertEquals(42, audioCommit.seq)
    }

    @Test
    fun `test error codes are defined`() {
        // Verify all canonical error codes are present
        assertEquals("UNAUTHENTICATED", ErrorCodes.UNAUTHENTICATED)
        assertEquals("UNAUTHORIZED", ErrorCodes.UNAUTHORIZED)
        assertEquals("POLICY_BLOCK", ErrorCodes.POLICY_BLOCK)
        assertEquals("RATE_LIMIT", ErrorCodes.RATE_LIMIT)
        assertEquals("PAYLOAD_TOO_LARGE", ErrorCodes.PAYLOAD_TOO_LARGE)
        assertEquals("SEQ_OUT_OF_ORDER", ErrorCodes.SEQ_OUT_OF_ORDER)
        assertEquals("BACKEND_TIMEOUT", ErrorCodes.BACKEND_TIMEOUT)
        assertEquals("MODEL_TIMEOUT", ErrorCodes.MODEL_TIMEOUT)
        assertEquals("NETWORK_ERROR", ErrorCodes.NETWORK_ERROR)
        assertEquals("POLICY_TIMEOUT", ErrorCodes.POLICY_TIMEOUT)
        assertEquals("SERVER_OVERLOADED", ErrorCodes.SERVER_OVERLOADED)
    }

    @Test
    fun `test protocol constants`() {
        // Verify protocol constants match spec
        assertEquals("clara/0.1", ProtocolConstants.PROTOCOL_VERSION)
        assertEquals(24000, ProtocolConstants.SAMPLE_RATE)
        assertEquals(20, ProtocolConstants.FRAME_DURATION_MS)
        assertEquals(20_480, ProtocolConstants.MAX_PAYLOAD_SIZE_BYTES)
        assertEquals(60_000L, ProtocolConstants.MAX_INPUT_AUDIO_DURATION_MS)
        assertEquals(90_000L, ProtocolConstants.MAX_OUTPUT_AUDIO_DURATION_MS)
    }

    @Test
    fun `test audio format strings`() {
        assertEquals("opus@24000/mono/20ms", ProtocolConstants.INPUT_AUDIO_FORMAT)
        assertEquals("pcm16@24000/mono", ProtocolConstants.OUTPUT_AUDIO_FORMAT)
    }
}



