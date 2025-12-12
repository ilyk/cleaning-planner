# Clara Backend - Rust Streaming Server

Production-ready backend for Clara voice assistant, implementing audio streaming, guardrails, LLM integration, and tool execution.

## Architecture

```
APP ⇄ BACKEND ⇄ LLM

┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Mobile    │────▶│   Backend    │────▶│  OpenAI     │
│   Client    │◀────│  (Rust/Axum) │◀────│  Realtime   │
└─────────────┘     └──────────────┘     └─────────────┘
                          │
                          ├─ Guardrails (VAD/LID/Keywords)
                          ├─ Session Management
                          ├─ Rate Limiting
                          ├─ Tool Execution
                          └─ Metrics & Telemetry
```

## Features

- ✅ **WebSocket Streaming** - Real-time bidirectional audio streaming (production-ready)
- ✅ **Redis Session Storage** - Fast session/turn management with TTL
- ✅ **Postgres Data Storage** - Plans and telemetry with JSONB
- ✅ **Database Migrations** - sqlx migrations for schema management
- ✅ **Authentication** - JWT-based session authentication
- ✅ **Guardrails Pipeline** - VAD, LID, keyword detection, embeddings
- ✅ **Rate Limiting** - Redis-backed sliding window (3 turns/min configurable)
- ✅ **Barge-in Support** - ≤50ms stop latency target
- ✅ **Metrics** - Prometheus-compatible metrics endpoint
- ✅ **Tool Execution** - Plan generation, revision, telemetry, printables (real DB ops)
- ✅ **Capability Masks** - Dynamic tool access based on guardrail verdicts
- ✅ **Structured Logging** - JSON logs with trace context
- ✅ **Graceful Shutdown** - Clean connection teardown

## Quick Start

### Prerequisites

- Rust 1.75+ 
- Docker & Docker Compose (for Postgres + Redis)
- PostgreSQL 14+ (optional - can use in-memory)
- Redis 7+ (optional - can use in-memory)

### Development

#### Option 1: With Real Databases (Recommended)

```bash
cd backend

# 1. Start Postgres + Redis
docker-compose up -d

# 2. Configure
cat > .env <<EOF
DATABASE_URL=postgres://clara:password@localhost:5432/clara_db
REDIS_URL=redis://localhost:6379
JWT_SECRET=test-secret-change-in-production
RUST_LOG=info,clara_stream_server=debug
EOF

# 3. Run server (migrations run automatically)
cargo run -p clara-stream-server
# Server starts on http://0.0.0.0:8080
```

#### Option 2: In-Memory (Quick Testing)

```bash
cd backend

# Configure for in-memory
cat > .env <<EOF
DATABASE_URL=memory://
REDIS_URL=memory://
JWT_SECRET=test-secret
EOF

# Run server
cargo run -p clara-stream-server
```

### Testing

```bash
# Run all tests
cargo test --workspace

# Run with logs
cargo test --workspace -- --nocapture

# Run specific crate tests
cargo test -p clara-guardrails
```

### Linting

```bash
# Format code
cargo fmt --all

# Run clippy
cargo clippy --workspace --all-features -- -D warnings
```

## Workspace Structure

```
backend/
├── Cargo.toml              # Workspace definition
├── crates/
│   ├── api/                # Axum routers, REST + WS endpoints
│   ├── auth/               # JWT validation, middleware
│   ├── config/             # Configuration management
│   ├── guardrails/         # Audio-first validation pipeline
│   ├── llm/                # LLM adapter trait + implementations
│   ├── protocol/           # Message types, error codes
│   ├── session/            # Session & turn lifecycle
│   ├── store/              # Data access layer (Postgres/Redis)
│   ├── stream/             # WebSocket hub, heartbeats, backpressure
│   ├── telemetry/          # Metrics & tracing
│   └── tools/              # Plan engine, tool execution
└── bin/
    └── clara-stream-server/ # Main server binary
```

## API Endpoints

### REST

- `POST /v1/clara/session` - Create new session
  - Returns: `{ sessionId, streamUrl }`
  - Requires: JWT auth

- `POST /v1/clara/session/turn` - Start new turn
  - Body: `{ session_id }`
  - Returns: `{ turnId, streamUrl }`
  - Requires: JWT auth

- `GET /health` - Health check
- `GET /metrics` - Prometheus metrics

### WebSocket

- `GET /v1/clara/stream?sessionId=<id>&turnId=<id>` - Connect to stream
  - Protocol: Clara Streaming Protocol v0.1.0
  - Requires: JWT auth

## WebSocket Protocol

### Client → Server (Inbound)

```json
{"type": "turn_start", "turn_id": "...", "session_id": "..."}
{"type": "input_audio_delta", "seq": 0, "format": "opus@24000", "data": "<base64>"}
{"type": "input_audio_commit"}
{"type": "input_interrupt"}
{"type": "ping"}
```

### Server → Client (Outbound)

```json
{"type": "turn_started", "turn_id": "...", "policy_version": "...", "prompt_version": "..."}
{"type": "output_audio_start", "format": "opus@24000"}
{"type": "output_audio_delta", "seq": 0, "format": "opus@24000", "data": "<base64>"}
{"type": "output_audio_commit"}
{"type": "output_text_delta", "text": "..."}
{"type": "guardrail_notice", "code": "...", "message": "...", "categories": [...]}
{"type": "turn_finish", "turn_id": "...", "metadata": {...}}
{"type": "error", "code": "...", "message": "..."}
{"type": "pong"}
```

## Configuration

See `.env.example` for all configuration options.

Key settings:

- `DATABASE_URL` - PostgreSQL connection string
- `REDIS_URL` - Redis connection string
- `JWT_SECRET` - JWT signing secret (HS256)
- `OPENAI_API_KEY` - OpenAI API key (optional, uses mock if not set)
- `RUST_LOG` - Log level

## JWT Token Generation (Example)

```bash
# Using a simple script or tool
# Claims: { sub, sid, home_id, exp }

# Example token generation (Python):
python3 <<EOF
import jwt
import time

secret = "your-secret-key-here"
payload = {
    "sub": "user-123",
    "sid": "session-456",
    "home_id": "home-789",
    "exp": int(time.time()) + 3600
}
token = jwt.encode(payload, secret, algorithm="HS256")
print(token)
EOF
```

## Example Session (wscat)

```bash
# Install wscat
npm install -g wscat

# Generate token (see above)
export TOKEN="eyJ..."

# 1. Create session
curl -X POST http://localhost:8080/v1/clara/session \
  -H "Authorization: Bearer $TOKEN"
# Response: {"session_id":"sess-abc","stream_url":"/v1/clara/stream?sessionId=sess-abc"}

# 2. Start turn
curl -X POST http://localhost:8080/v1/clara/session/turn \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"session_id":"sess-abc"}'
# Response: {"turn_id":"turn-xyz","stream_url":"..."}

# 3. Connect WebSocket
wscat -c "ws://localhost:8080/v1/clara/stream?sessionId=sess-abc&turnId=turn-xyz" \
  -H "Authorization: Bearer $TOKEN"

# Send messages:
> {"type":"input_audio_delta","seq":0,"format":"opus@24000","data":"AQIDBA=="}
> {"type":"input_audio_commit"}

# Receive responses (mock adapter sends simulated audio)
```

## Metrics

Prometheus metrics available at `/metrics`:

- `clara_ws_connections` - Active WebSocket connections
- `clara_turns_started_total` - Total turns started
- `clara_turns_finished_total` - Total turns finished
- `clara_guardrail_hits_total{category}` - Guardrail hits by category
- `clara_tokens_in_total` - Input tokens processed
- `clara_tokens_out_total` - Output tokens generated
- `clara_ttft_ms` - Time to first token (histogram)
- `clara_barge_in_stop_ms` - Barge-in stop latency (histogram)
- `clara_errors_total{code}` - Errors by code
- `clara_audio_bytes_in_total` - Audio bytes received
- `clara_audio_bytes_out_total` - Audio bytes sent

## Production Deployment

### Build Release

```bash
cargo build --release --workspace
# Binary: target/release/clara-stream-server
```

### Docker

```bash
# TODO: Add Dockerfile
```

### Environment

- Set strong `JWT_SECRET`
- Configure `DATABASE_URL` and `REDIS_URL` for production
- Set `RUST_LOG=info` (or `warn` for production)
- Enable feature flags as needed

## Security

- **Zero-trust client** - All validation on server
- **No audio persistence** - Audio never stored
- **No PII in logs** - Structured JSON logs with no sensitive data
- **Rate limiting** - 3 turns/minute (burst 6)
- **Capability masks** - Dynamic tool access based on guardrails
- **JWT auth** - HS256 tokens with expiration

## Performance Targets

- **First-token audio** - ≤350ms p95
- **Barge-in stop** - ≤50ms
- **WebSocket uptime** - ≥99.9%
- **Error budget** - ≤1% of turns

## Troubleshooting

### Connection refused

```bash
# Check if server is running
curl http://localhost:8080/health

# Check Docker services
docker-compose ps

# Check logs
RUST_LOG=debug cargo run -p clara-stream-server
```

### JWT errors

- Verify `JWT_SECRET` matches between token generation and server
- Check token expiration (`exp` claim)
- Ensure `sub`, `sid`, `home_id` are present

### Database errors

```bash
# Reset database
docker-compose down -v
docker-compose up -d
```

## Development

### Adding a new crate

1. Create crate directory: `crates/my-crate/`
2. Add to workspace in root `Cargo.toml`
3. Implement functionality
4. Add tests
5. Update dependencies

### Running specific tests

```bash
cargo test -p clara-protocol -- test_name
```

## License

MIT

## Authors

CleanFlow / Clara Backend Team

## Version

0.1.0 (2025-10-28)
