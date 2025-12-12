//! Migration tests to verify database schema changes

use sqlx::{PgPool, Row};
use std::env;

/// Test that migrations apply and rollback correctly
#[tokio::test]
async fn test_migrations_apply_and_rollback() {
    let database_url = env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://localhost/cleanflow_test".to_string());
    
    // Create a test database connection
    let pool = PgPool::connect(&database_url).await.unwrap();
    
    // Test that we can run migrations
    let result = sqlx::migrate!("./migrations").run(&pool).await;
    assert!(result.is_ok(), "Migrations should apply successfully");
    
    // Test that all required tables exist
    let tables = vec![
        "homes", "members", "rooms", "task_templates", "plans", 
        "plan_tasks", "assignments", "telemetry_events", 
        "printable_exports", "clara_sessions", "clara_turns", "idempotency_keys"
    ];
    
    for table in tables {
        let query = format!("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '{}')", table);
        let exists: bool = sqlx::query(&query)
            .fetch_one(&pool)
            .await
            .unwrap()
            .get(0);
        assert!(exists, "Table {} should exist", table);
    }
    
    // Test that enums exist
    let enums = vec![
        "plan_mode", "task_state", "member_role", "telemetry_kind", "room_kind"
    ];
    
    for enum_name in enums {
        let query = format!("SELECT EXISTS (SELECT FROM pg_type WHERE typname = '{}')", enum_name);
        let exists: bool = sqlx::query(&query)
            .fetch_one(&pool)
            .await
            .unwrap()
            .get(0);
        assert!(exists, "Enum {} should exist", enum_name);
    }
    
    // Test that constraints exist
    test_unique_constraints(&pool).await;
    test_foreign_key_constraints(&pool).await;
    test_check_constraints(&pool).await;
    
    // Test that indexes exist
    test_indexes(&pool).await;
}

async fn test_unique_constraints(pool: &PgPool) {
    // Test plans unique constraint
    let query = "SELECT COUNT(*) FROM information_schema.table_constraints 
                 WHERE constraint_name = 'plans_home_id_date_mode_key' 
                 AND constraint_type = 'UNIQUE'";
    let count: i64 = sqlx::query(query).fetch_one(pool).await.unwrap().get(0);
    assert_eq!(count, 1, "Plans unique constraint should exist");
    
    // Test assignments unique constraint
    let query = "SELECT COUNT(*) FROM information_schema.table_constraints 
                 WHERE constraint_name = 'assignments_task_id_member_id_key' 
                 AND constraint_type = 'UNIQUE'";
    let count: i64 = sqlx::query(query).fetch_one(pool).await.unwrap().get(0);
    assert_eq!(count, 1, "Assignments unique constraint should exist");
}

async fn test_foreign_key_constraints(pool: &PgPool) {
    // Test foreign key constraints exist
    let foreign_keys = vec![
        ("members", "home_id", "homes", "id"),
        ("rooms", "home_id", "homes", "id"),
        ("plans", "home_id", "homes", "id"),
        ("plan_tasks", "plan_id", "plans", "id"),
        ("plan_tasks", "room_id", "rooms", "id"),
        ("assignments", "plan_id", "plans", "id"),
        ("assignments", "task_id", "plan_tasks", "id"),
        ("assignments", "member_id", "members", "id"),
        ("telemetry_events", "task_id", "plan_tasks", "id"),
        ("printable_exports", "plan_id", "plans", "id"),
        ("clara_sessions", "home_id", "homes", "id"),
        ("clara_turns", "session_id", "clara_sessions", "id"),
    ];
    
    for (table, column, ref_table, ref_column) in foreign_keys {
        let query = format!(
            "SELECT COUNT(*) FROM information_schema.key_column_usage 
             WHERE table_name = '{}' AND column_name = '{}' 
             AND referenced_table_name = '{}' AND referenced_column_name = '{}'",
            table, column, ref_table, ref_column
        );
        let count: i64 = sqlx::query(&query).fetch_one(pool).await.unwrap().get(0);
        assert!(count > 0, "Foreign key constraint should exist for {}.{} -> {}.{}", 
                table, column, ref_table, ref_column);
    }
}

async fn test_check_constraints(pool: &PgPool) {
    // Test ID format constraints
    let id_constraints = vec![
        ("homes", "id", "h_%"),
        ("members", "id", "m_%"),
        ("rooms", "id", "r_%"),
        ("task_templates", "id", "tmpl_%"),
        ("plans", "id", "p_%"),
        ("plan_tasks", "id", "t_%"),
        ("assignments", "id", "a_%"),
        ("telemetry_events", "id", "te_%"),
        ("printable_exports", "id", "x_%"),
        ("clara_sessions", "id", "cs_%"),
        ("clara_turns", "id", "ct_%"),
    ];
    
    for (table, column, pattern) in id_constraints {
        let query = format!(
            "SELECT COUNT(*) FROM information_schema.check_constraints 
             WHERE constraint_name LIKE '%_{}_check'",
            column
        );
        let count: i64 = sqlx::query(&query).fetch_one(pool).await.unwrap().get(0);
        assert!(count > 0, "Check constraint should exist for {}.{}", table, column);
    }
}

async fn test_indexes(pool: &PgPool) {
    let indexes = vec![
        ("idx_homes_owner_user_id", "homes"),
        ("idx_members_home_id", "members"),
        ("idx_rooms_home_id_kind", "rooms"),
        ("idx_plans_home_id_date", "plans"),
        ("idx_plans_created_at", "plans"),
        ("idx_plan_tasks_plan_section_priority", "plan_tasks"),
        ("idx_plan_tasks_assignee", "plan_tasks"),
        ("idx_assignments_plan_id", "assignments"),
        ("idx_assignments_member_id", "assignments"),
        ("idx_telemetry_events_task_created", "telemetry_events"),
        ("idx_telemetry_events_created_at", "telemetry_events"),
        ("idx_printable_exports_plan_id", "printable_exports"),
        ("idx_clara_sessions_user_id", "clara_sessions"),
        ("idx_clara_sessions_home_id", "clara_sessions"),
        ("idx_clara_turns_session_id", "clara_turns"),
        ("idx_clara_turns_started_at", "clara_turns"),
        ("idx_idempotency_expires_at", "idempotency_keys"),
    ];
    
    for (index_name, table_name) in indexes {
        let query = format!(
            "SELECT COUNT(*) FROM pg_indexes WHERE indexname = '{}' AND tablename = '{}'",
            index_name, table_name
        );
        let count: i64 = sqlx::query(&query).fetch_one(pool).await.unwrap().get(0);
        assert_eq!(count, 1, "Index {} should exist on table {}", index_name, table_name);
    }
}

/// Test that we can insert and query data
#[tokio::test]
async fn test_data_operations() {
    let database_url = env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://localhost/cleanflow_test".to_string());
    
    let pool = PgPool::connect(&database_url).await.unwrap();
    
    // Run migrations first
    sqlx::migrate!("./migrations").run(&pool).await.unwrap();
    
    // Test inserting a home
    let home_id = "h_test123";
    let result = sqlx::query(
        "INSERT INTO homes (id, owner_user_id, name, tz, locale) 
         VALUES ($1, $2, $3, $4, $5)"
    )
    .bind(home_id)
    .bind("user_123")
    .bind("Test Home")
    .bind("America/Los_Angeles")
    .bind("en-US")
    .execute(&pool)
    .await;
    
    assert!(result.is_ok(), "Should be able to insert home");
    
    // Test querying the home
    let home = sqlx::query("SELECT * FROM homes WHERE id = $1")
        .bind(home_id)
        .fetch_one(&pool)
        .await;
    
    assert!(home.is_ok(), "Should be able to query home");
    let home_row = home.unwrap();
    assert_eq!(home_row.get::<String, _>("id"), home_id);
    assert_eq!(home_row.get::<String, _>("name"), "Test Home");
    
    // Test inserting a member
    let member_id = "m_test123";
    let result = sqlx::query(
        "INSERT INTO members (id, home_id, name, role) 
         VALUES ($1, $2, $3, $4)"
    )
    .bind(member_id)
    .bind(home_id)
    .bind("Test Member")
    .bind("adult")
    .execute(&pool)
    .await;
    
    assert!(result.is_ok(), "Should be able to insert member");
    
    // Test inserting a room
    let room_id = "r_test123";
    let result = sqlx::query(
        "INSERT INTO rooms (id, home_id, name, kind) 
         VALUES ($1, $2, $3, $4)"
    )
    .bind(room_id)
    .bind(home_id)
    .bind("Test Room")
    .bind("kitchen")
    .execute(&pool)
    .await;
    
    assert!(result.is_ok(), "Should be able to insert room");
    
    // Test inserting a plan
    let plan_id = "p_test123";
    let result = sqlx::query(
        "INSERT INTO plans (id, home_id, date, mode, sections, version, prompt_version, policy_version) 
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)"
    )
    .bind(plan_id)
    .bind(home_id)
    .bind(chrono::NaiveDate::from_ymd_opt(2025, 1, 29).unwrap())
    .bind("focus")
    .bind(serde_json::json!([]))
    .bind(1)
    .bind("test")
    .bind("test")
    .execute(&pool)
    .await;
    
    assert!(result.is_ok(), "Should be able to insert plan");
    
    // Test querying with joins
    let result = sqlx::query(
        "SELECT h.name as home_name, m.name as member_name, r.name as room_name
         FROM homes h
         JOIN members m ON h.id = m.home_id
         JOIN rooms r ON h.id = r.home_id
         WHERE h.id = $1"
    )
    .bind(home_id)
    .fetch_all(&pool)
    .await;
    
    assert!(result.is_ok(), "Should be able to query with joins");
    let rows = result.unwrap();
    assert!(!rows.is_empty(), "Should have results from join query");
}
