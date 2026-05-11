//! Shared application state

use cleanflow_auth::JwtValidator;
use cleanflow_config::AppConfig;
use cleanflow_domain::services::{
	PlanService, PrintableService, IdempotencyService, TelemetryService,
	IdempotencyStore, RealPlanService, RealPrintableService, RealLookupService,
	DbIdempotencyStore, DbPlanService, CleanFlowOptimizer, DbCleanFlowOptimizer,
	CleanFlowSuggestionService, LlmSuggestionService,
	HomeExtractionService, LlmHomeExtractionService,
	RoomService, DbRoomService, HomeService, DbHomeService,
};
use cleanflow_session::SessionManager;
use cleanflow_store::{Store, DbPool};
use cleanflow_telemetry::Metrics;
use std::sync::Arc;

/// Application state shared across handlers
#[derive(Clone)]
pub struct AppState {
    pub config: Arc<AppConfig>,
    pub jwt_validator: Arc<JwtValidator>,
    pub session_manager: Arc<SessionManager>,
    pub store: Store,
    pub db_pool: DbPool,
    pub metrics: Arc<Metrics>,
    pub plan_service: Arc<dyn PlanService>,
    pub printable_service: Arc<dyn PrintableService>,
    pub idempotency_service: Arc<dyn IdempotencyService>,
    pub telemetry_service: Arc<dyn TelemetryService>,
    pub idempotency_store: Arc<dyn IdempotencyStore>,
    pub real_plan_service: Arc<dyn RealPlanService>,
	pub real_printable_service: Arc<dyn RealPrintableService>,
	pub real_lookup_service: Arc<dyn RealLookupService>,
	pub cleanflow_optimizer: Arc<dyn CleanFlowOptimizer>,
	pub cleanflow_suggestion_service: Arc<dyn CleanFlowSuggestionService>,
	pub home_extraction_service: Arc<dyn HomeExtractionService>,
	pub room_service: Arc<dyn RoomService>,
	pub home_service: Arc<dyn HomeService>,
}

impl AppState {
    /// Create application state with minimal required arguments
    /// Services are created internally based on config and db_pool
    pub fn new(
        config: AppConfig,
        jwt_validator: JwtValidator,
        session_manager: SessionManager,
        store: Store,
        db_pool: DbPool,
        metrics: Arc<Metrics>,
    ) -> Self {
        use cleanflow_domain::services::{
            plan_service, printable_service, idempotency_service, telemetry_service,
            real_printable_service, real_lookup_service,
        };

        // Create services
        let plan_service: Arc<dyn PlanService> = Arc::new(plan_service::MockPlanService);
        let printable_service: Arc<dyn PrintableService> = Arc::new(printable_service::MockPrintableService);
        let idempotency_service: Arc<dyn IdempotencyService> = Arc::new(idempotency_service::MockIdempotencyService::new());
        let telemetry_service: Arc<dyn TelemetryService> = Arc::new(telemetry_service::DbTelemetryService::new());
        let real_printable_service: Arc<dyn RealPrintableService> = Arc::new(real_printable_service::DbPrintableService::new());
        let real_lookup_service: Arc<dyn RealLookupService> = Arc::new(real_lookup_service::DbLookupService::with_pool(db_pool.clone()));
        let idempotency_store = Arc::new(DbIdempotencyStore::new(db_pool.clone()));
		// Initialize plan service with Anthropic API key for LLM plan generation
		let anthropic_api_key = config.llm.anthropic_api_key.clone();
		let real_plan_service = Arc::new(DbPlanService::with_anthropic(db_pool.clone(), anthropic_api_key.clone()));
		
		// Initialize optimizer with optional LLM support
		let openai_api_key = config.llm.openai_api_key.clone();
		let model = config.llm.openai_model.clone();
		let cleanflow_optimizer: Arc<dyn CleanFlowOptimizer> =
			Arc::new(DbCleanFlowOptimizer::new(real_plan_service.clone())
				.with_llm(db_pool.clone(), openai_api_key.clone(), model.clone()));
		
		// Initialize LLM suggestion service with DB pool for history access
		let openai_api_key = config.llm.openai_api_key.clone();
		let model = config.llm.openai_model.clone();
		let cleanflow_suggestion_service: Arc<dyn CleanFlowSuggestionService> =
			Arc::new(LlmSuggestionService::new(
				real_plan_service.clone(),
				openai_api_key,
				model,
			).with_db_pool(db_pool.clone()));

		// Initialize home extraction service with Anthropic API key
		let anthropic_api_key = config.llm.anthropic_api_key.clone();
		let home_extraction_service: Arc<dyn HomeExtractionService> =
			Arc::new(LlmHomeExtractionService::new(db_pool.clone(), anthropic_api_key));

		// Initialize room and home CRUD services
		let room_service: Arc<dyn RoomService> = Arc::new(DbRoomService::new(db_pool.clone()));
		let home_service: Arc<dyn HomeService> = Arc::new(DbHomeService::new(db_pool.clone()));

        Self {
            config: Arc::new(config),
            jwt_validator: Arc::new(jwt_validator),
            session_manager: Arc::new(session_manager),
            store,
            db_pool,
            metrics,
            plan_service,
            printable_service,
            idempotency_service,
            telemetry_service,
            idempotency_store,
            real_plan_service,
            real_printable_service,
            real_lookup_service,
            cleanflow_optimizer,
            cleanflow_suggestion_service,
            home_extraction_service,
            room_service,
            home_service,
        }
    }
}

