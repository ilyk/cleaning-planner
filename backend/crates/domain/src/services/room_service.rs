//! Room CRUD service implementation

use crate::models::*;
use anyhow::{Context, Result};
use async_trait::async_trait;
use sqlx::{PgPool, Row};
use tracing::info;

#[async_trait]
pub trait RoomService: Send + Sync {
    /// List all rooms for a home
    async fn list_rooms(&self, home_id: &str) -> Result<Vec<Room>>;

    /// Get a room by ID
    async fn get_room(&self, room_id: &str) -> Result<Option<Room>>;

    /// Create a new room
    async fn create_room(&self, request: CreateRoomRequest) -> Result<Room>;

    /// Update a room
    async fn update_room(&self, room_id: &str, request: UpdateRoomRequest) -> Result<Option<Room>>;

    /// Delete a room
    async fn delete_room(&self, room_id: &str) -> Result<bool>;
}

/// Database-backed room service
pub struct DbRoomService {
    pool: PgPool,
}

impl DbRoomService {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    fn parse_room_kind(kind_str: &str) -> Option<RoomKind> {
        match kind_str.to_lowercase().as_str() {
            "kitchen" => Some(RoomKind::Kitchen),
            "bathroom" => Some(RoomKind::Bathroom),
            "bedroom" => Some(RoomKind::Bedroom),
            "living" => Some(RoomKind::Living),
            "other" => Some(RoomKind::Other),
            _ => None,
        }
    }

    fn room_kind_to_str(kind: &RoomKind) -> &'static str {
        match kind {
            RoomKind::Kitchen => "kitchen",
            RoomKind::Bathroom => "bathroom",
            RoomKind::Bedroom => "bedroom",
            RoomKind::Living => "living",
            RoomKind::Other => "other",
        }
    }
}

#[async_trait]
impl RoomService for DbRoomService {
    async fn list_rooms(&self, home_id: &str) -> Result<Vec<Room>> {
        let rows = sqlx::query(
            "SELECT id, home_id, name, kind::text as kind, metadata
             FROM rooms WHERE home_id = $1
             ORDER BY name"
        )
        .bind(home_id)
        .fetch_all(&self.pool)
        .await
        .context("Failed to query rooms")?;

        let rooms: Vec<Room> = rows
            .into_iter()
            .map(|row| {
                let kind_str: Option<String> = row.get("kind");
                Room {
                    id: row.get("id"),
                    home_id: row.get("home_id"),
                    name: row.get("name"),
                    kind: kind_str.and_then(|k| Self::parse_room_kind(&k)),
                    metadata: row.get("metadata"),
                }
            })
            .collect();

        info!(home_id = %home_id, count = rooms.len(), "Listed rooms");
        Ok(rooms)
    }

    async fn get_room(&self, room_id: &str) -> Result<Option<Room>> {
        let row = sqlx::query(
            "SELECT id, home_id, name, kind::text as kind, metadata
             FROM rooms WHERE id = $1"
        )
        .bind(room_id)
        .fetch_optional(&self.pool)
        .await
        .context("Failed to query room")?;

        let room = row.map(|r| {
            let kind_str: Option<String> = r.get("kind");
            Room {
                id: r.get("id"),
                home_id: r.get("home_id"),
                name: r.get("name"),
                kind: kind_str.and_then(|k| Self::parse_room_kind(&k)),
                metadata: r.get("metadata"),
            }
        });

        Ok(room)
    }

    async fn create_room(&self, request: CreateRoomRequest) -> Result<Room> {
        let room_id = generate_room_id();
        let kind_str = request.kind.as_ref().map(Self::room_kind_to_str);

        sqlx::query(
            "INSERT INTO rooms (id, home_id, name, kind, metadata)
             VALUES ($1, $2, $3, $4::room_kind, $5)"
        )
        .bind(&room_id)
        .bind(&request.home_id)
        .bind(&request.name)
        .bind(kind_str)
        .bind(&request.metadata)
        .execute(&self.pool)
        .await
        .context("Failed to insert room")?;

        info!(room_id = %room_id, home_id = %request.home_id, "Created room");

        Ok(Room {
            id: room_id,
            home_id: request.home_id,
            name: request.name,
            kind: request.kind,
            metadata: request.metadata,
        })
    }

    async fn update_room(&self, room_id: &str, request: UpdateRoomRequest) -> Result<Option<Room>> {
        // First check if room exists
        let existing = self.get_room(room_id).await?;
        if existing.is_none() {
            return Ok(None);
        }
        let existing = existing.unwrap();

        let name = request.name.unwrap_or(existing.name);
        let kind = request.kind.or(existing.kind);
        let metadata = request.metadata.or(existing.metadata);
        let kind_str = kind.as_ref().map(Self::room_kind_to_str);

        sqlx::query(
            "UPDATE rooms SET name = $2, kind = $3::room_kind, metadata = $4 WHERE id = $1"
        )
        .bind(room_id)
        .bind(&name)
        .bind(kind_str)
        .bind(&metadata)
        .execute(&self.pool)
        .await
        .context("Failed to update room")?;

        info!(room_id = %room_id, "Updated room");

        Ok(Some(Room {
            id: room_id.to_string(),
            home_id: existing.home_id,
            name,
            kind,
            metadata,
        }))
    }

    async fn delete_room(&self, room_id: &str) -> Result<bool> {
        let result = sqlx::query("DELETE FROM rooms WHERE id = $1")
            .bind(room_id)
            .execute(&self.pool)
            .await
            .context("Failed to delete room")?;

        let deleted = result.rows_affected() > 0;
        if deleted {
            info!(room_id = %room_id, "Deleted room");
        }
        Ok(deleted)
    }
}
