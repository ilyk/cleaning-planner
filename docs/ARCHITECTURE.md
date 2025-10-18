# Architecture Overview

## Design Principles

The Cleaning Planner app follows modern Android development best practices:

### 1. Multi-Module Architecture

Benefits:
- **Faster build times**: Only changed modules are rebuilt
- **Clear separation of concerns**: Each module has a single responsibility
- **Reusability**: Core modules can be shared across features
- **Parallel development**: Teams can work on different modules independently

### 2. Clean Architecture Layers

```
┌─────────────────────────────────────┐
│         Presentation Layer          │
│  (Compose UI + ViewModels + State)  │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│         Domain Layer                │
│    (Models + Use Cases + Rules)     │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│         Data Layer                  │
│  (Repositories + DB + Network)      │
└─────────────────────────────────────┘
```

### 3. MVVM Pattern

- **Model**: Data models from domain/data layers
- **View**: Jetpack Compose UI components
- **ViewModel**: Manages UI state, handles user actions, coordinates data flow

```kotlin
// Example flow
User Action → ViewModel → Repository → Network/DB → ViewModel → UI Update
```

### 4. Unidirectional Data Flow

State flows in one direction:

```
┌──────────┐      ┌──────────────┐      ┌──────────┐
│   View   │─────▶│  ViewModel   │─────▶│  Model   │
│          │◀─────│              │◀─────│          │
└──────────┘      └──────────────┘      └──────────┘
   Events              State               Data
```

### 5. Dependency Injection (Hilt)

- **Constructor injection**: Preferred for ViewModels and repositories
- **Module providers**: For framework types (Room, Retrofit)
- **Scoping**: Singletons for repositories, ViewModelScoped for use cases

### 6. Offline-First

1. **Local database (Room)** is the single source of truth
2. **UI observes** local data via Flow
3. **Background sync** keeps data fresh
4. **Optimistic updates** for better UX

### 7. Reactive Programming

Using Kotlin Coroutines and Flow:
- **Flow**: For data streams (database, network)
- **StateFlow**: For UI state management
- **suspend functions**: For one-shot operations

## Module Details

### `:core:model`

Pure Kotlin module with no Android dependencies.

**Contents:**
- Domain models (User, Task, Room, etc.)
- Enums (Role, TaskStatus, Recurrence)
- Serialization annotations

**Purpose:** Shared data structures across all layers

### `:core:ui`

Android library with Compose dependencies.

**Contents:**
- Material 3 theme (colors, typography, shapes)
- Reusable components (LoadingIndicator, ErrorMessage)
- Design tokens and utilities

**Purpose:** Consistent UI/UX across features

### `:core:common`

Android library with minimal dependencies.

**Contents:**
- Result wrapper type
- Extension functions
- Common utilities
- Error types

**Purpose:** Shared utilities and patterns

### `:data:database`

Room database implementation.

**Contents:**
- Entity definitions with foreign keys
- DAOs with Flow-based queries
- Type converters (Instant, enums, JSON lists)
- Database class and migrations

**Purpose:** Local data persistence and caching

### `:data:network`

Retrofit API interfaces.

**Contents:**
- API interfaces (AuthApi, TaskApi, etc.)
- Request/Response DTOs
- OkHttp interceptors (auth, logging)
- Serialization setup

**Purpose:** Backend communication

### `:data:repository`

Repository implementations.

**Contents:**
- Repository classes (TaskRepository, etc.)
- Sync worker (WorkManager)
- Mapping between network/DB/domain models
- Caching and offline strategies

**Purpose:** Data access abstraction and coordination

### `:feature:*`

Feature-specific modules.

**Contents:**
- Screens (Compose functions)
- ViewModels (state management)
- Navigation graphs
- Feature-specific components

**Purpose:** Isolated, testable features

## Data Flow Example: Task Status Update

```kotlin
// 1. User clicks "Mark Done" button
TaskDetailScreen(
    onStatusChange = { viewModel.updateStatus(TaskStatus.DONE) }
)

// 2. ViewModel updates state and calls repository
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {
    fun updateStatus(status: TaskStatus) {
        viewModelScope.launch {
            taskRepository.updateStatus(taskId, status)
                .onSuccess { /* Update UI state */ }
                .onError { /* Show error */ }
        }
    }
}

// 3. Repository updates local DB (optimistic)
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val taskApi: TaskApi
) {
    suspend fun updateStatus(taskId: String, status: TaskStatus): Result<Unit> {
        return try {
            // Optimistic local update with sync flag
            taskDao.updateStatus(taskId, status)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

// 4. SyncWorker picks up pending changes
class SyncWorker @AssistedInject constructor(
    private val taskRepository: TaskRepository
) : CoroutineWorker() {
    override suspend fun doWork(): Result {
        taskRepository.syncPendingChanges()
        return Result.success()
    }
}

// 5. UI automatically updates via Flow observation
taskRepository.observeById(taskId)
    .collectAsState() // Compose state
```

## Testing Strategy

### Unit Tests
- **Repositories**: Mock DAO and API, test data flow
- **ViewModels**: Mock repositories, test state changes
- **Use cases**: Mock dependencies, test business logic

### Integration Tests
- **Database**: Test DAOs with in-memory database
- **Network**: Test APIs with MockWebServer
- **Repositories**: Test with real Room + mock network

### UI Tests
- **Compose**: Use ComposeTestRule
- **Navigation**: Test screen transitions
- **User flows**: Test complete user journeys

## Performance Considerations

1. **Lazy loading**: Use Paging 3 for large lists
2. **Image loading**: Use Coil with caching
3. **Database queries**: Index frequently queried columns
4. **Network calls**: Batch requests, use caching headers
5. **Background work**: Use WorkManager constraints (network, battery)

## Security

1. **API tokens**: Store in EncryptedSharedPreferences
2. **Database**: Optional SQLCipher encryption
3. **Network**: HTTPS only, certificate pinning (production)
4. **Code obfuscation**: R8 minification in release builds
5. **Input validation**: Server-side + client-side

## Future Enhancements

- **GraphQL**: Replace REST APIs for flexible queries
- **Kotlin Multiplatform**: Share business logic with iOS
- **Jetpack Compose Multiplatform**: Share UI code
- **On-device ML**: TensorFlow Lite for suggestions
- **Wear OS**: Smartwatch companion app

