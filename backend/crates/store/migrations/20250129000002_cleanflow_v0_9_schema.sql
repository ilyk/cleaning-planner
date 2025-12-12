-- CleanFlow API Reference & Data Model v0.9 Schema
-- This migration creates all tables required by the spec

-- Drop existing plans table and recreate with correct schema
DROP TABLE IF EXISTS plans CASCADE;

-- Create enums
CREATE TYPE plan_mode AS ENUM ('focus', 'full_reset', 'low_energy', 'pet');
CREATE TYPE task_state AS ENUM ('pending', 'in_progress', 'done', 'skipped');
CREATE TYPE member_role AS ENUM ('adult', 'kid', 'guest', 'pet_proxy');
CREATE TYPE telemetry_kind AS ENUM ('done', 'skip');
CREATE TYPE room_kind AS ENUM ('kitchen', 'bathroom', 'bedroom', 'living', 'other');

-- homes table
CREATE TABLE homes (
    id TEXT PRIMARY KEY CHECK (id LIKE 'h_%'),
    owner_user_id TEXT NOT NULL,
    name TEXT NOT NULL,
    tz TEXT NOT NULL,
    locale TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_homes_owner_user_id ON homes(owner_user_id);

-- members table
CREATE TABLE members (
    id TEXT PRIMARY KEY CHECK (id LIKE 'm_%'),
    home_id TEXT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    role member_role NOT NULL,
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_members_home_id ON members(home_id);

-- rooms table
CREATE TABLE rooms (
    id TEXT PRIMARY KEY CHECK (id LIKE 'r_%'),
    home_id TEXT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    kind room_kind,
    metadata JSONB
);

CREATE INDEX idx_rooms_home_id_kind ON rooms(home_id, kind);

-- task_templates table
CREATE TABLE task_templates (
    id TEXT PRIMARY KEY CHECK (id LIKE 'tmpl_%'),
    title TEXT NOT NULL,
    default_estimate_min INTEGER NOT NULL,
    room_kind room_kind,
    frequency TEXT,
    tools JSONB,
    policy_tags TEXT[],
    i18n JSONB
);

-- plans table (recreated to match spec)
CREATE TABLE plans (
    id TEXT PRIMARY KEY CHECK (id LIKE 'p_%'),
    home_id TEXT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    mode plan_mode NOT NULL,
    sections JSONB NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    prompt_version TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    cached BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(home_id, date, mode)
);

CREATE INDEX idx_plans_home_id_date ON plans(home_id, date);
CREATE INDEX idx_plans_created_at ON plans(created_at DESC);

-- plan_tasks table
CREATE TABLE plan_tasks (
    id TEXT PRIMARY KEY CHECK (id LIKE 't_%'),
    plan_id TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    template_id TEXT REFERENCES task_templates(id),
    room_id TEXT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    estimate_min INTEGER NOT NULL,
    state task_state NOT NULL DEFAULT 'pending',
    priority INTEGER NOT NULL,
    section_id TEXT NOT NULL,
    assignee_member_id TEXT REFERENCES members(id),
    metadata JSONB
);

CREATE INDEX idx_plan_tasks_plan_section_priority ON plan_tasks(plan_id, section_id, priority);
CREATE INDEX idx_plan_tasks_assignee ON plan_tasks(assignee_member_id);

-- assignments table
CREATE TABLE assignments (
    id TEXT PRIMARY KEY CHECK (id LIKE 'a_%'),
    plan_id TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    task_id TEXT NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(task_id, member_id)
);

CREATE INDEX idx_assignments_plan_id ON assignments(plan_id);
CREATE INDEX idx_assignments_member_id ON assignments(member_id);

-- telemetry_events table
CREATE TABLE telemetry_events (
    id TEXT PRIMARY KEY CHECK (id LIKE 'te_%'),
    task_id TEXT NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    kind telemetry_kind NOT NULL,
    duration_sec INTEGER,
    comment TEXT,
    source TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_telemetry_events_task_created ON telemetry_events(task_id, created_at);
CREATE INDEX idx_telemetry_events_created_at ON telemetry_events(created_at DESC);

-- printable_exports table
CREATE TABLE printable_exports (
    id TEXT PRIMARY KEY CHECK (id LIKE 'x_%'),
    plan_id TEXT NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    pdf_url TEXT NOT NULL,
    options JSONB NOT NULL,
    qr_map JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_printable_exports_plan_id ON printable_exports(plan_id);

-- clara_sessions table
CREATE TABLE clara_sessions (
    id TEXT PRIMARY KEY CHECK (id LIKE 'cs_%'),
    user_id TEXT NOT NULL,
    home_id TEXT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    state TEXT NOT NULL DEFAULT 'active' CHECK (state IN ('active', 'ended')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ
);

CREATE INDEX idx_clara_sessions_user_id ON clara_sessions(user_id);
CREATE INDEX idx_clara_sessions_home_id ON clara_sessions(home_id);

-- clara_turns table
CREATE TABLE clara_turns (
    id TEXT PRIMARY KEY CHECK (id LIKE 'ct_%'),
    session_id TEXT NOT NULL REFERENCES clara_sessions(id) ON DELETE CASCADE,
    policy_version TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    usage JSONB,
    verdicts JSONB
);

CREATE INDEX idx_clara_turns_session_id ON clara_turns(session_id);
CREATE INDEX idx_clara_turns_started_at ON clara_turns(started_at DESC);

-- idempotency table for request deduplication
CREATE TABLE idempotency_keys (
    key TEXT PRIMARY KEY,
    request_hash TEXT NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);

-- Add triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_homes_updated_at BEFORE UPDATE ON homes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_plans_updated_at BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
