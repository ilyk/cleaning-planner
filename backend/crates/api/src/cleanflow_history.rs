//! CleanFlow history and telemetry ingestion endpoints

use axum::{
    extract::{State},
    response::Json,
    Extension,
};
use cleanflow_auth::AuthExtension;
use cleanflow_domain::models::{TelemetryCompleteRequest, TelemetryCompleteResponse};
use serde::Deserialize;
use tracing::info;

use crate::{state::AppState, errors::CleanFlowError};

/// Single history entry in the batch payload
#[derive(Debug, Deserialize)]
pub struct HistoryEntryPayload {
    pub task_id: String,
    pub date: chrono::NaiveDate,
    pub status: String,
    pub duration_min: Option<i32>,
    pub note: Option<String>,
    pub origin: Option<String>,
    pub source: Option<String>,
    pub created_at: Option<chrono::DateTime<chrono::Utc>>,
}

/// Batch ingestion request for device history
#[derive(Debug, Deserialize)]
pub struct HistoryBatchRequest {
    pub home_id: String,
    pub device_id: String,
    pub entries: Vec<HistoryEntryPayload>,
}

/// Simple ack response for history batch ingestion
#[derive(Debug, serde::Serialize)]
pub struct HistoryBatchResponse {
    pub accepted: usize,
}

/// Ingest a batch of history entries for a given home/device.
/// For now this is implemented by fan-out into the existing telemetry service.
pub async fn ingest_history_batch(
    State(state): State<AppState>,
    Extension(auth): Extension<AuthExtension>,
    Json(request): Json<HistoryBatchRequest>,
) -> Result<Json<HistoryBatchResponse>, CleanFlowError> {
    if request.home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden("Access denied to home".to_string()));
    }

    info!(
        user_id = %auth.claims.sub,
        home_id = %request.home_id,
        device_id = %request.device_id,
        entries = request.entries.len(),
        "Ingesting CleanFlow history batch",
    );

    let mut accepted = 0usize;

    for entry in &request.entries {
        let status = match entry.status.to_lowercase().as_str() {
            "done" => "done".to_string(),
            "skipped" => "skipped".to_string(),
            other => other.to_string(),
        };

        let duration_sec = entry.duration_min.map(|m| m * 60);

        let telemetry_request = TelemetryCompleteRequest {
            task_id: entry.task_id.clone(),
            status,
            duration_sec,
            comment: entry.note.clone(),
            source: entry.source.clone().unwrap_or_else(|| "app".to_string()),
        };

        // Ignore individual failures for now; this endpoint is best-effort.
        if state
            .telemetry_service
            .record_telemetry(telemetry_request)
            .await
            .is_ok()
        {
            accepted += 1;
        }
    }

    Ok(Json(HistoryBatchResponse { accepted }))
}

/// Query parameters for a simple history summary.
#[derive(Debug, Deserialize)]
pub struct HistorySummaryQuery {
    pub home_id: String,
}

/// Placeholder summary response; will be backed by store/analytics later.
#[derive(Debug, serde::Serialize)]
pub struct HistorySummaryResponse {
    pub home_id: String,
    pub total_events: u64,
}

/// Return a very simple history summary for a home.
/// Currently a stub; to be wired to telemetry store in a later iteration.
pub async fn get_history_summary(
    Extension(auth): Extension<AuthExtension>,
    axum::extract::Query(query): axum::extract::Query<HistorySummaryQuery>,
) -> Result<Json<HistorySummaryResponse>, CleanFlowError> {
    if query.home_id != auth.claims.home_id {
        return Err(CleanFlowError::Forbidden("Access denied to home".to_string()));
    }

    info!(
        user_id = %auth.claims.sub,
        home_id = %query.home_id,
        "Fetching CleanFlow history summary (stub)",
    );

    Ok(Json(HistorySummaryResponse {
        home_id: query.home_id,
        total_events: 0,
    }))
}
