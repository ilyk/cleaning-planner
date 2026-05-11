//! Home/Household CRUD service implementation

use crate::models::*;
use anyhow::{Context, Result};
use async_trait::async_trait;
use sqlx::{PgPool, Row};
use tracing::info;

#[async_trait]
pub trait HomeService: Send + Sync {
    /// List all homes for a user
    async fn list_homes(&self, user_id: &str) -> Result<Vec<Home>>;

    /// Get a home by ID
    async fn get_home(&self, home_id: &str) -> Result<Option<Home>>;

    /// Create a new home
    async fn create_home(&self, user_id: &str, request: CreateHomeRequest) -> Result<Home>;

    /// Update a home
    async fn update_home(&self, home_id: &str, request: UpdateHomeRequest) -> Result<Option<Home>>;

    /// Delete a home
    async fn delete_home(&self, home_id: &str) -> Result<bool>;
}

/// Database-backed home service
pub struct DbHomeService {
    pool: PgPool,
}

impl DbHomeService {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl HomeService for DbHomeService {
    async fn list_homes(&self, user_id: &str) -> Result<Vec<Home>> {
        let rows = sqlx::query(
            "SELECT id, owner_user_id, name, tz, locale, metadata, created_at, updated_at
             FROM homes WHERE owner_user_id = $1
             ORDER BY name"
        )
        .bind(user_id)
        .fetch_all(&self.pool)
        .await
        .context("Failed to query homes")?;

        let homes: Vec<Home> = rows
            .into_iter()
            .map(|row| Home {
                id: row.get("id"),
                owner_user_id: row.get("owner_user_id"),
                name: row.get("name"),
                tz: row.get("tz"),
                locale: row.get("locale"),
                metadata: row.get("metadata"),
                created_at: row.get("created_at"),
                updated_at: row.get("updated_at"),
            })
            .collect();

        info!(user_id = %user_id, count = homes.len(), "Listed homes");
        Ok(homes)
    }

    async fn get_home(&self, home_id: &str) -> Result<Option<Home>> {
        let row = sqlx::query(
            "SELECT id, owner_user_id, name, tz, locale, metadata, created_at, updated_at
             FROM homes WHERE id = $1"
        )
        .bind(home_id)
        .fetch_optional(&self.pool)
        .await
        .context("Failed to query home")?;

        let home = row.map(|r| Home {
            id: r.get("id"),
            owner_user_id: r.get("owner_user_id"),
            name: r.get("name"),
            tz: r.get("tz"),
            locale: r.get("locale"),
            metadata: r.get("metadata"),
            created_at: r.get("created_at"),
            updated_at: r.get("updated_at"),
        });

        Ok(home)
    }

    async fn create_home(&self, user_id: &str, request: CreateHomeRequest) -> Result<Home> {
        let home_id = generate_home_id();
        let now = chrono::Utc::now();

        sqlx::query(
            "INSERT INTO homes (id, owner_user_id, name, tz, locale, metadata, created_at, updated_at)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)"
        )
        .bind(&home_id)
        .bind(user_id)
        .bind(&request.name)
        .bind(&request.tz)
        .bind(&request.locale)
        .bind(&request.metadata)
        .bind(now)
        .bind(now)
        .execute(&self.pool)
        .await
        .context("Failed to insert home")?;

        info!(home_id = %home_id, user_id = %user_id, "Created home");

        Ok(Home {
            id: home_id,
            owner_user_id: user_id.to_string(),
            name: request.name,
            tz: request.tz,
            locale: request.locale,
            metadata: request.metadata,
            created_at: now,
            updated_at: now,
        })
    }

    async fn update_home(&self, home_id: &str, request: UpdateHomeRequest) -> Result<Option<Home>> {
        // First check if home exists
        let existing = self.get_home(home_id).await?;
        if existing.is_none() {
            return Ok(None);
        }
        let existing = existing.unwrap();

        let name = request.name.unwrap_or(existing.name);
        let tz = request.tz.unwrap_or(existing.tz);
        let locale = request.locale.unwrap_or(existing.locale);
        let metadata = request.metadata.or(existing.metadata);

        let row = sqlx::query(
            "UPDATE homes SET name = $2, tz = $3, locale = $4, metadata = $5
             WHERE id = $1
             RETURNING updated_at"
        )
        .bind(home_id)
        .bind(&name)
        .bind(&tz)
        .bind(&locale)
        .bind(&metadata)
        .fetch_one(&self.pool)
        .await
        .context("Failed to update home")?;

        let updated_at = row.get("updated_at");
        info!(home_id = %home_id, "Updated home");

        Ok(Some(Home {
            id: home_id.to_string(),
            owner_user_id: existing.owner_user_id,
            name,
            tz,
            locale,
            metadata,
            created_at: existing.created_at,
            updated_at,
        }))
    }

    async fn delete_home(&self, home_id: &str) -> Result<bool> {
        let result = sqlx::query("DELETE FROM homes WHERE id = $1")
            .bind(home_id)
            .execute(&self.pool)
            .await
            .context("Failed to delete home")?;

        let deleted = result.rows_affected() > 0;
        if deleted {
            info!(home_id = %home_id, "Deleted home");
        }
        Ok(deleted)
    }
}
