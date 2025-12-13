# CleanFlow

A smart adaptive cleaning planner for families, featuring AI-assisted onboarding, flexible cleaning modes, and a paper-digital hybrid workflow.

## Overview

CleanFlow helps people who struggle to stay focused with household cleaning by providing:

- **Flexible Setup**: Specify home size, rooms, floors, pets, devices - as detailed or minimal as you want
- **Multiple Cleaning Modes**: Focus (quick wins), Full Reset (deep clean), Low Energy, Pet-focused
- **Clara AI Assistant**: Conversational onboarding that learns your home setup
- **Paper-Digital Hybrid**: Print daily checklists, scan QR codes to track completion
- **Family Mode**: One home, multiple members, task distribution

## Project Structure

```
cleanflow/
├── app/                    # Android application entry point
├── core/
│   ├── model/              # Domain models (pure Kotlin)
│   ├── ui/                 # Design system, theming, components
│   └── common/             # Utilities, result types
├── data/
│   ├── database/           # Room entities, DAOs
│   ├── network/            # Retrofit APIs, DTOs
│   └── repository/         # Repositories, sync engine
├── feature/
│   ├── clara/              # Clara AI chat interface
│   ├── setup/              # Manual home setup wizard
│   ├── household/          # Family member management
│   ├── rooms/              # Room management
│   ├── board/              # Family task board
│   ├── kidmode/            # Kid-friendly task view
│   ├── qr/                 # QR scanning
│   ├── printables/         # PDF generation
│   ├── auth/               # Authentication
│   └── settings/           # App settings
├── backend/                # Rust backend server
│   ├── bin/cleanflow-server/   # Server binary
│   └── crates/
│       ├── api/            # HTTP/WebSocket handlers
│       ├── auth/           # JWT authentication
│       ├── config/         # Configuration management
│       ├── domain/         # Business logic & services
│       ├── guardrails/     # Content safety & policy
│       ├── llm/            # Claude/OpenAI integration
│       ├── protocol/       # WebSocket protocol types
│       ├── session/        # Session management
│       ├── store/          # Database layer (SQLx/Postgres)
│       ├── stream/         # Streaming handlers
│       ├── telemetry/      # Metrics & logging
│       └── tools/          # LLM tool implementations
└── docs/                   # Documentation
```

## Tech Stack

### Android

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Navigation | Navigation Compose |
| Async | Coroutines + Flows |
| Local DB | Room + DataStore |
| Network | Retrofit + OkHttp |
| Camera/QR | CameraX + ML Kit |

**Requirements:**
- Min SDK: 24 (Android 7.0)
- Target SDK: 35 (Android 15)
- Kotlin 2.0+

### Backend (Rust)

| Component | Technology |
|-----------|-----------|
| Web Framework | Axum |
| Database | PostgreSQL + SQLx |
| Cache | Redis |
| LLM | Claude API (Anthropic) |
| Auth | JWT |
| Observability | Tracing + Prometheus |

## Getting Started

### Prerequisites

- Android Studio Hedgehog+ or JDK 17+
- Rust 1.75+ (for backend)
- PostgreSQL 16+
- Redis

### Backend Setup

```bash
cd backend

# Copy environment config
cp .env.example .env
# Edit .env with your ANTHROPIC_API_KEY and database credentials

# Start dependencies
docker-compose up -d

# Run migrations
sqlx database create
sqlx migrate run

# Start server
cargo run --bin cleanflow-server
```

### Android Setup

```bash
# Build the project
./gradlew build

# Install on device/emulator
./gradlew installDebug
```

For emulator testing, the app connects to `10.0.2.2:8090` (host machine's localhost).

## Features

### Implemented

- **Clara Text Chat**: AI-assisted onboarding conversation
- **Home Extraction**: LLM parses conversation to structured home data
- **Plan Generation**: LLM-based cleaning plan creation
- **Mode UI Screens**: Focus, Full Reset, Low Energy, Pet modes
- **Manual Setup Wizard**: Alternative to Clara for home configuration
- **Database Schema**: Full PostgreSQL schema with migrations

### In Progress

- Telemetry persistence (task completion tracking)
- Learning engine (adaptive plan adjustments)
- Family task assignment

### Planned

- Printable PDF generation with QR codes
- QR code scanning for task completion
- OCR import of handwritten checklists
- Single Room Mode for kids/teens

## API Endpoints

### Clara Streaming
- `POST /v1/clara/session` - Create new Clara session
- `GET /v1/clara/stream?session={id}` - WebSocket for streaming chat
- `GET /v1/clara/history/{session_id}` - Get conversation history

### Onboarding
- `POST /v1/onboarding/extract` - Extract home data from conversation
- `POST /v1/onboarding/setup` - Manual home setup

### Plans
- `GET /v1/plans/{home_id}/{date}/{mode}` - Get plan for date/mode
- `POST /v1/plans` - Create new plan

### Home
- `GET /v1/lookup/home/{home_id}` - Get home profile with rooms/members

## Configuration

Environment variables (`.env`):

```bash
DATABASE_URL=postgres://user:pass@localhost:5432/cleanflow
REDIS_URL=redis://localhost:6379
ANTHROPIC_API_KEY=sk-ant-...
RUST_LOG=info,cleanflow_server=debug
PORT=8090
```

## License

BSD 3-Clause License - see [LICENSE](LICENSE)
