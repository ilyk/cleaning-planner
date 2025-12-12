# ADR-002: WebSocket Streaming over Opus@24kHz, 20ms Frames

**Status**: Accepted  
**Date**: 2025-10-29  
**Deciders**: Backend Architecture Team

## Context

Clara requires bidirectional real-time audio streaming between client and backend. The architecture must support low-latency voice interaction with efficient bandwidth usage.

## Decision

- **Protocol**: WebSocket (wss://) for bidirectional streaming
- **Audio Format**: Opus codec at 24kHz sample rate
- **Frame Size**: 20ms per audio frame (480 samples @ 24kHz)
- **Encoding**: Base64-encoded frames in JSON messages
- **Max Frame Size**: 20 KB per frame (enforced server-side)

## Rationale

### Opus@24kHz
- Industry standard for voice applications
- Good balance of quality and bandwidth
- Supported by WebRTC and modern browsers
- Low latency suitable for real-time interaction

### 20ms Frames
- Low enough latency for responsive interaction (< 50ms processing time)
- Large enough for efficient encoding
- Aligns with common Opus frame sizes

### WebSocket
- Native browser support
- Bidirectional communication
- Better for streaming than HTTP polling
- Supports custom headers (authentication)

## Consequences

### Positive
- Low latency voice interaction
- Efficient bandwidth usage
- Standard, well-supported protocols
- Good for mobile and web clients

### Negative
- Requires WebSocket infrastructure
- More complex than REST API
- Frame size limits complexity

## Implementation Details

- Protocol versioning via header: `Clara-Protocol-Version: 2025.10.29`
- Sequence numbers for frame ordering
- Heartbeat: ping/pong every 10s
- Barge-in: InputInterrupt stops output within 50ms

## Alternatives Considered

1. **HTTP long-polling**: Rejected — too high latency
2. **WebRTC**: Rejected — too complex for server architecture
3. **gRPC streaming**: Rejected — less universal than WebSocket

