.PHONY: dev test build fmt clippy clean docker-up docker-down migrate health

# Development environment variables
OPENAI_API_KEY ?= $(shell echo $$OPENAI_API_KEY)
JWT_SECRET ?= dev_jwt_secret_$(shell date +%s)
DATABASE_URL ?= postgres://postgres:postgres@localhost:5432/clara
REDIS_URL ?= redis://localhost:6379
RUST_LOG ?= info,clara=debug

# Feature flags for development
FEATURES ?= openai_realtime,use-llm-security,use-pdf,use-path-security

## Start development server
dev:
	@echo "🚀 Starting Clara backend (dev)..."
	@echo "   OpenAI: $(if $(OPENAI_API_KEY),✅ set,⚠️  not set - will use mock)"
	@echo "   Features: $(FEATURES)"
	@echo ""
	OPENAI_API_KEY=$(OPENAI_API_KEY) \
	JWT_SECRET=$(JWT_SECRET) \
	DATABASE_URL=$(DATABASE_URL) \
	REDIS_URL=$(REDIS_URL) \
	RUST_LOG=$(RUST_LOG) \
	cargo run -p clara-stream-server --features $(FEATURES)

## Run all tests
test:
	@echo "🧪 Running tests..."
	cargo test --workspace --features $(FEATURES)

## Build with all features
build:
	@echo "🔨 Building workspace..."
	cargo build --workspace --all-features

## Format code
fmt:
	@echo "📝 Formatting code..."
	cargo fmt --all

## Lint code
clippy:
	@echo "🔍 Running clippy..."
	cargo clippy --workspace --all-features -- -D warnings

## Clean build artifacts
clean:
	@echo "🧹 Cleaning..."
	cargo clean

## Start Docker services (Postgres + Redis)
docker-up:
	@echo "🐳 Starting Docker services..."
	docker-compose up -d
	@echo "⏳ Waiting for services to be ready..."
	@sleep 3
	@echo "✅ Services ready!"

## Stop Docker services
docker-down:
	@echo "🛑 Stopping Docker services..."
	docker-compose down

## Run database migrations
migrate:
	@echo "🗄️  Running migrations..."
	@cd crates/store && sqlx migrate run --database-url $(DATABASE_URL)

## Health check
health:
	@echo "🏥 Checking health..."
	@curl -s http://localhost:8080/health | jq '.' || echo "❌ Server not running"

## Generate JWT token (for testing)
jwt:
	@cd examples && python3 generate_jwt.py

## Run integration smoke test
smoke-test:
	@echo "💨 Running smoke test..."
	@bash examples/smoke_test.sh

## Check code quality (fmt + clippy + test)
check: fmt clippy test
	@echo "✅ All checks passed!"

## Full setup (docker + migrate + dev)
setup: docker-up migrate dev

## Development with mocks (no external dependencies)
dev-mock:
	@echo "🚀 Starting Clara backend (mock mode)..."
	@FEATURES="" make dev FEATURES=""
