# Clara Backend - Quick Start Guide

**One-command dev bring-up for new developers.**

## Prerequisites

- Rust ≥ 1.80
- Docker ≥ 24.0 (for Postgres + Redis)
- `OPENAI_API_KEY` (optional - uses mock if not set)

## 🚀 Quick Start

```bash
# Clone and navigate
cd backend

# One command setup (starts Docker, runs migrations, starts server)
make setup
```

That's it! The server will be running on `http://localhost:8080`

## 📋 What `make setup` Does

1. Starts Postgres and Redis via Docker Compose
2. Runs database migrations
3. Starts the Clara backend server with all features enabled

## 🧪 Verify It's Working

```bash
# Health check
make health

# Or manually:
curl http://localhost:8080/health
```

## 🎯 Common Commands

```bash
# Development server (with all features)
make dev

# Run tests
make test

# Format + lint + test
make check

# Stop Docker services
make docker-down

# Run smoke test
make smoke-test
```

## 🔧 Configuration

Environment variables (with defaults):

- `OPENAI_API_KEY` - OpenAI API key (optional)
- `JWT_SECRET` - JWT signing secret (default: `dev_jwt_secret`)
- `DATABASE_URL` - Postgres connection string
- `REDIS_URL` - Redis connection string
- `RUST_LOG` - Log level (default: `info,clara=debug`)

## 📝 First Steps

1. **Generate a JWT token for testing:**
   ```bash
   cd examples
   python3 generate_jwt.py
   ```

2. **Create a session:**
   ```bash
   curl -H "Authorization: Bearer <token>" \
        -X POST http://localhost:8080/v1/clara/session
   ```

3. **Run smoke test:**
   ```bash
   make smoke-test
   ```

## 🔍 Feature Flags

By default, `make dev` enables:
- `openai_realtime` - Real OpenAI API integration
- `use-llm-security` - Policy pack integration
- `use-pdf` - PDF generation
- `use-path-security` - Path validation

To run without features:
```bash
make dev-mock
```

## 📚 Documentation

- **Architecture**: `docs/architecture.md`
- **ADRs**: `docs/adr/`
- **Checklist**: `CHECKLIST_BACKEND_ARCHITECTURE.md`
- **Examples**: `examples/`

## 🆘 Troubleshooting

**Port already in use:**
```bash
# Check what's using port 8080
lsof -i :8080

# Or change port in .env
PORT=8081 make dev
```

**Docker services not starting:**
```bash
# Check logs
docker-compose logs

# Restart services
make docker-down
make docker-up
```

**Migrations failing:**
```bash
# Reset database (⚠️ deletes data)
docker-compose down -v
make docker-up
make migrate
```

## 📖 Next Steps

1. Read `CHECKLIST_BACKEND_ARCHITECTURE.md` for QA procedures
2. Review `docs/adr/` for architectural decisions
3. Run `make test` to see all tests
4. Check `examples/` for usage examples

---

**Questions?** See `CHECKLIST_BACKEND_ARCHITECTURE.md` for comprehensive QA procedures.

