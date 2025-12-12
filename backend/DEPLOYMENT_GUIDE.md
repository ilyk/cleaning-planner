# Clara Backend Deployment Guide

## Quick Start Commands

### 1. Local Development (Docker + In-Memory)

```bash
cd backend

# Copy environment template
cp .env.example .env

# Start Docker services (Postgres + Redis)
docker-compose up -d

# Run server (uses in-memory store by default)
cargo run -p clara-stream-server
```

Server starts on `http://0.0.0.0:8080`

### 2. Generate JWT Token

```bash
# Install PyJWT if needed
pip install pyjwt

# Generate token
python3 examples/generate_jwt.py
```

Copy the token for use in API calls.

### 3. Test Session Flow

```bash
# Set your JWT token
export TOKEN="eyJ..."

# Run test script
bash examples/test_session.sh
```

### 4. Connect via WebSocket

```bash
# Install wscat
npm install -g wscat

# Use session_id and turn_id from test script
wscat -c "ws://localhost:8080/v1/clara/stream?sessionId=sess-xxx&turnId=turn-yyy" \
  -H "Authorization: Bearer $TOKEN"
```

## Configuration

Edit `.env` file:

```bash
# Server
HOST=0.0.0.0
PORT=8080

# Database (use memory:// for in-memory store)
DATABASE_URL=memory://
# Or for real Postgres:
# DATABASE_URL=postgres://clara:password@localhost:5432/clara_db

# Redis
REDIS_URL=redis://localhost:6379

# Auth (change in production!)
JWT_SECRET=your-secret-key-here

# LLM (optional - uses mock if not set)
OPENAI_API_KEY=sk-...

# Logging
RUST_LOG=info,clara_stream_server=debug
```

## Building

```bash
# Development build
cargo build --workspace

# Release build
cargo build --release --workspace

# Run tests
cargo test --workspace

# Run lints
cargo clippy --workspace --all-features

# Format code
cargo fmt --all
```

## API Examples

### Create Session

```bash
curl -X POST http://localhost:8080/v1/clara/session \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

Response:
```json
{
  "session_id": "sess-abc123",
  "stream_url": "/v1/clara/stream?sessionId=sess-abc123"
}
```

### Start Turn

```bash
curl -X POST http://localhost:8080/v1/clara/session/turn \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"session_id":"sess-abc123"}'
```

Response:
```json
{
  "turn_id": "turn-xyz789",
  "stream_url": "/v1/clara/stream?sessionId=sess-abc123&turnId=turn-xyz789"
}
```

### Health Check

```bash
curl http://localhost:8080/health
```

### Metrics

```bash
curl http://localhost:8080/metrics
```

## WebSocket Protocol Example

Connect:
```bash
wscat -c "ws://localhost:8080/v1/clara/stream?sessionId=sess-abc&turnId=turn-xyz" \
  -H "Authorization: Bearer $TOKEN"
```

Send audio delta:
```json
{"type":"input_audio_delta","seq":0,"format":"opus@24000","data":"AQIDBAUG"}
```

Commit input:
```json
{"type":"input_audio_commit"}
```

Interrupt:
```json
{"type":"input_interrupt"}
```

Server responses:
```json
{"type":"turn_started","turn_id":"turn-xyz","policy_version":"...","prompt_version":"..."}
{"type":"output_audio_start","format":"opus@24000"}
{"type":"output_audio_delta","seq":0,"format":"opus@24000","data":"..."}
{"type":"output_audio_commit"}
{"type":"turn_finish","turn_id":"turn-xyz","metadata":{...}}
```

## Production Deployment

### 1. Build Release Binary

```bash
cargo build --release --workspace
strip target/release/clara-stream-server

# Binary at: target/release/clara-stream-server
```

### 2. Configure Environment

```bash
# Set strong JWT secret
export JWT_SECRET=$(openssl rand -base64 32)

# Set database URLs
export DATABASE_URL=postgres://user:pass@db-host:5432/clara
export REDIS_URL=redis://redis-host:6379

# Set OpenAI key (if using real API)
export OPENAI_API_KEY=sk-...

# Set production logging
export RUST_LOG=warn,clara_stream_server=info
```

### 3. Run Server

```bash
./target/release/clara-stream-server
```

### 4. Systemd Service (Example)

Create `/etc/systemd/system/clara-backend.service`:

```ini
[Unit]
Description=Clara Backend Server
After=network.target

[Service]
Type=simple
User=clara
WorkingDirectory=/opt/clara-backend
ExecStart=/opt/clara-backend/clara-stream-server
Restart=always
RestartSec=10

Environment=DATABASE_URL=postgres://...
Environment=REDIS_URL=redis://...
Environment=JWT_SECRET=...
Environment=RUST_LOG=info

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl enable clara-backend
sudo systemctl start clara-backend
sudo systemctl status clara-backend
```

## Monitoring

### Prometheus Scrape Config

```yaml
scrape_configs:
  - job_name: 'clara-backend'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/metrics'
```

### Key Metrics

- `clara_ws_connections` - Active WebSocket connections
- `clara_turns_started_total` - Total turns started
- `clara_turns_finished_total` - Total turns finished
- `clara_ttft_ms` - Time to first token (histogram)
- `clara_errors_total{code}` - Errors by code

### Grafana Dashboard

Import metrics and create panels for:
- Active connections over time
- Turns per minute
- P95/P99 latency
- Error rate by code
- Audio throughput

## Troubleshooting

### Server won't start

Check:
1. Port 8080 is available: `lsof -i :8080`
2. Database URL is correct: `psql $DATABASE_URL`
3. Redis is accessible: `redis-cli -u $REDIS_URL ping`
4. JWT_SECRET is set: `echo $JWT_SECRET`

### Authentication errors

- Verify JWT_SECRET matches token generation
- Check token hasn't expired (`exp` claim)
- Ensure token has `sub`, `sid`, `home_id` claims

### WebSocket connection fails

- Check session exists and is valid
- Verify JWT token in header
- Check server logs for errors
- Try health check: `curl http://localhost:8080/health`

### High latency

- Check LLM response time (if using real API)
- Check database query time
- Check Redis latency
- Review metrics at `/metrics`

### Memory issues

- Check connection count (leak detection)
- Review session cleanup
- Check for goroutine leaks
- Monitor with: `curl http://localhost:8080/metrics | grep clara_ws_connections`

## Security Checklist

- [ ] Change JWT_SECRET from default
- [ ] Use TLS in production
- [ ] Rotate JWT secrets periodically
- [ ] Enable rate limiting
- [ ] Monitor for abuse
- [ ] Review logs regularly
- [ ] Keep dependencies updated
- [ ] Run security audits: `cargo audit`

## Performance Tuning

### Connection Limits

Adjust in `.env`:
```
DATABASE_MAX_CONNECTIONS=20
REDIS_POOL_SIZE=20
```

### Rate Limiting

Adjust in `.env`:
```
RATE_LIMIT_TURNS_PER_MINUTE=5
RATE_LIMIT_BURST_CAPACITY=10
```

### Timeouts

Adjust in `.env`:
```
TIMEOUTS_MAX_INPUT_DURATION_SECS=90
TIMEOUTS_MAX_OUTPUT_DURATION_SECS=120
```

## Scaling

### Horizontal Scaling

1. Use sticky sessions (session ID based routing)
2. Share Redis instance across all nodes
3. Use read replicas for Postgres
4. Load balance with nginx/haproxy

### Redis Sentinel

For high availability:
```
REDIS_URL=redis-sentinel://sentinel-host:26379/mymaster
```

### Database Pooling

Increase connection pool:
```
DATABASE_MAX_CONNECTIONS=50
```

## Backup & Recovery

### Database Backup

```bash
pg_dump $DATABASE_URL > backup.sql
```

### Redis Backup

Redis persistence is enabled by default (RDB).

### Disaster Recovery

1. Restore database from backup
2. Restart server
3. Verify health check passes
4. Check metrics

## Updates

### Rolling Update

1. Build new binary
2. Deploy to one instance
3. Wait for health check
4. Repeat for other instances
5. Monitor metrics

### Database Migrations

```bash
# Apply migrations (once sqlx is configured)
sqlx migrate run
```

## Support

See also:
- `README.md` - Overview and quick start
- `IMPLEMENTATION_NOTES.md` - Implementation details
- `docs/architecture.md` - Architecture overview

For issues: Check logs with `RUST_LOG=debug`

