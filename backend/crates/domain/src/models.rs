//! Domain models matching CleanFlow API Reference & Data Model v0.9

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

// Enums matching the spec
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum PlanMode {
    Focus,
    FullReset,
    LowEnergy,
    Pet,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TaskState {
    Pending,
    InProgress,
    Done,
    Skipped,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum MemberRole {
    Adult,
    Kid,
    Guest,
    PetProxy,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TelemetryKind {
    Done,
    Skip,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum RoomKind {
    Kitchen,
    Bathroom,
    Bedroom,
    Living,
    Other,
}

// Implement Display and FromStr for enum types used in database queries
impl std::fmt::Display for PlanMode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = match self {
            PlanMode::Focus => "focus",
            PlanMode::FullReset => "full_reset",
            PlanMode::LowEnergy => "low_energy",
            PlanMode::Pet => "pet",
        };
        write!(f, "{}", s)
    }
}

impl std::str::FromStr for PlanMode {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "focus" => Ok(PlanMode::Focus),
            "full_reset" => Ok(PlanMode::FullReset),
            "low_energy" => Ok(PlanMode::LowEnergy),
            "pet" => Ok(PlanMode::Pet),
            _ => Err(format!("Unknown plan mode: {}", s)),
        }
    }
}

impl std::fmt::Display for TaskState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = match self {
            TaskState::Pending => "pending",
            TaskState::InProgress => "in_progress",
            TaskState::Done => "done",
            TaskState::Skipped => "skipped",
        };
        write!(f, "{}", s)
    }
}

impl std::str::FromStr for TaskState {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s {
            "pending" => Ok(TaskState::Pending),
            "in_progress" => Ok(TaskState::InProgress),
            "done" => Ok(TaskState::Done),
            "skipped" => Ok(TaskState::Skipped),
            _ => Err(format!("Unknown task state: {}", s)),
        }
    }
}

// Core entities
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Home {
    pub id: String,
    pub owner_user_id: String,
    pub name: String,
    pub tz: String,
    pub locale: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    /// Optional JSON metadata for constraints and preferences (quiet hours, allergies, etc.)
    pub metadata: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Member {
    pub id: String,
    pub home_id: String,
    pub name: String,
    pub role: MemberRole,
    pub avatar_url: Option<String>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Room {
    pub id: String,
    pub home_id: String,
    pub name: String,
    pub kind: Option<RoomKind>,
    pub metadata: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TaskTemplate {
    pub id: String,
    pub title: String,
    pub default_estimate_min: i32,
    pub room_kind: Option<RoomKind>,
    pub frequency: Option<String>,
    pub tools: Option<serde_json::Value>,
    pub policy_tags: Option<Vec<String>>,
    pub i18n: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Plan {
    pub id: String,
    pub home_id: String,
    pub date: chrono::NaiveDate,
    pub mode: PlanMode,
    pub sections: Vec<PlanSection>,
    pub tasks: Vec<PlanTask>,
    pub version: i32,
    pub prompt_version: String,
    pub policy_version: String,
    pub cached: bool,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlanSection {
    pub id: String,
    pub title: String,
    pub tasks: Vec<String>, // task IDs
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlanTask {
    pub task_id: String,
    pub template_id: Option<String>,
    pub room_id: String,
    pub title: String,
    pub estimate_min: i32,
    pub state: TaskState,
    pub priority: i32,
    pub section_id: String,
    pub assignee: Option<TaskAssignee>,
    pub metadata: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TaskAssignee {
    pub member_id: String,
    pub name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Assignment {
    pub id: String,
    pub plan_id: String,
    pub task_id: String,
    pub member_id: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelemetryEvent {
    pub id: String,
    pub task_id: String,
    pub kind: TelemetryKind,
    pub duration_sec: Option<i32>,
    pub comment: Option<String>,
    pub source: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PrintableExport {
    pub id: String,
    pub plan_id: String,
    pub pdf_url: String,
    pub options: serde_json::Value,
    pub qr_map: Vec<QrMapping>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QrMapping {
    pub task_id: String,
    pub qr_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClaraSession {
    pub id: String,
    pub user_id: String,
    pub home_id: String,
    pub state: String,
    pub created_at: DateTime<Utc>,
    pub ended_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClaraTurn {
    pub id: String,
    pub session_id: String,
    pub policy_version: String,
    pub prompt_version: String,
    pub started_at: DateTime<Utc>,
    pub finished_at: Option<DateTime<Utc>>,
    pub usage: Option<serde_json::Value>,
    pub verdicts: Option<serde_json::Value>,
}

// API Request/Response DTOs
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct GeneratePlanRequest {
    pub home_id: String,
    pub date: chrono::NaiveDate,
    pub mode: PlanMode,
    pub constraints: Option<PlanConstraints>,
    pub client: Option<ClientInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PlanConstraints {
    pub timebox_minutes: Option<i32>,
    pub rooms: Option<Vec<String>>,
    pub include: Option<Vec<String>>,
    pub exclude: Option<Vec<String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ClientInfo {
    pub locale: String,
    pub tz: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct GeneratePlanResponse {
    pub plan_id: String,
    pub home_id: String,
    pub date: chrono::NaiveDate,
    pub mode: PlanMode,
    pub sections: Vec<PlanSection>,
    pub tasks: Vec<PlanTask>,
    pub version: i32,
    pub prompt_version: String,
    pub policy_version: String,
    pub cached: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct RevisePlanRequest {
    pub plan_id: String,
    pub edits: Vec<PlanEdit>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "op", deny_unknown_fields)]
pub enum PlanEdit {
    #[serde(rename = "reorder")]
    Reorder {
        task_id: String,
        after_task_id: String,
    },
    #[serde(rename = "postpone")]
    Postpone {
        task_id: String,
        to_date: chrono::NaiveDate,
    },
    #[serde(rename = "assign")]
    Assign {
        task_id: String,
        member_id: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PrintableRequest {
    pub plan_id: String,
    pub options: PrintableOptions,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PrintableOptions {
    pub paper_size: Option<String>,
    pub kids_friendly: Option<bool>,
    pub qr_density: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PrintableResponse {
    pub export_id: String,
    pub pdf_url: String,
    pub qr: Vec<QrMapping>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct FamilyAssignRequest {
    pub plan_id: String,
    pub assignments: Vec<TaskAssignment>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct TaskAssignment {
    pub task_id: String,
    pub member_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct TelemetryCompleteRequest {
    pub task_id: String,
    pub status: String,
    pub duration_sec: Option<i32>,
    pub comment: Option<String>,
    pub source: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct TelemetryCompleteResponse {
    pub ok: bool,
    pub telemetry_id: String,
}

// Pagination
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PaginatedResponse<T> {
    pub items: Vec<T>,
    pub next_cursor: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PaginationParams {
    pub limit: Option<i32>,
    pub cursor: Option<String>,
}

// Error handling
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ApiError {
    pub error: ErrorDetails,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ErrorDetails {
    pub code: String,
    pub message: String,
    pub details: Option<serde_json::Value>,
    #[serde(rename = "requestId")]
    pub request_id: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ErrorCode {
    Unauthorized,
    Forbidden,
    RateLimited,
    ValidationFailed,
    Conflict,
    NotFound,
    Internal,
    PlanNotFound,
    SessionNotFound,
    SessionAlreadyConnected,
    InvalidRequest,
}

impl ErrorCode {
    pub fn as_str(&self) -> &'static str {
        match self {
            ErrorCode::Unauthorized => "UNAUTHORIZED",
            ErrorCode::Forbidden => "FORBIDDEN",
            ErrorCode::RateLimited => "RATE_LIMITED",
            ErrorCode::ValidationFailed => "VALIDATION_FAILED",
            ErrorCode::Conflict => "CONFLICT",
            ErrorCode::NotFound => "NOT_FOUND",
            ErrorCode::Internal => "INTERNAL",
            ErrorCode::PlanNotFound => "PLAN_NOT_FOUND",
            ErrorCode::SessionNotFound => "SESSION_NOT_FOUND",
            ErrorCode::SessionAlreadyConnected => "SESSION_ALREADY_CONNECTED",
            ErrorCode::InvalidRequest => "INVALID_REQUEST",
        }
    }
}

// ID generation helpers
pub fn generate_home_id() -> String {
    format!("h_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_member_id() -> String {
    format!("m_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_room_id() -> String {
    format!("r_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_plan_id() -> String {
    format!("p_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_task_id() -> String {
    format!("t_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_assignment_id() -> String {
    format!("a_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_telemetry_id() -> String {
    format!("te_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_export_id() -> String {
    format!("x_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_session_id() -> String {
    format!("cs_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_turn_id() -> String {
    format!("ct_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}

pub fn generate_qr_id() -> String {
    format!("qr_{}", uuid::Uuid::new_v4().to_string()[..8].to_lowercase())
}
