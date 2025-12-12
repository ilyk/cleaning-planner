-- Initial schema for Clara backend

-- Plans table
CREATE TABLE IF NOT EXISTS plans (
    plan_id UUID PRIMARY KEY,
    home_id VARCHAR(255) NOT NULL,
    title TEXT NOT NULL,
    content JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plans_home_id ON plans(home_id);
CREATE INDEX idx_plans_created_at ON plans(created_at DESC);

-- Turn metrics table
CREATE TABLE IF NOT EXISTS turn_metrics (
    id SERIAL PRIMARY KEY,
    turn_id VARCHAR(255) NOT NULL UNIQUE,
    session_id VARCHAR(255) NOT NULL,
    tokens_in INTEGER NOT NULL DEFAULT 0,
    tokens_out INTEGER NOT NULL DEFAULT 0,
    audio_in_seconds REAL NOT NULL DEFAULT 0,
    audio_out_seconds REAL NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL,
    ttft_ms BIGINT,
    policy_version VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    guardrail_hits JSONB NOT NULL DEFAULT '[]'::jsonb,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_turn_metrics_session_id ON turn_metrics(session_id);
CREATE INDEX idx_turn_metrics_recorded_at ON turn_metrics(recorded_at DESC);
CREATE INDEX idx_turn_metrics_turn_id ON turn_metrics(turn_id);

