# Cleaning Planner - Android App

A family-first native Android application that makes cleaning easy with room-focused plans, QR check-ins, printable kid checklists, shared assignments, and light ML suggestions.

## 🎯 Project Overview

**Version:** 0.1.0 (MVP)  
**Min SDK:** 24 (Android 7.0)  
**Target SDK:** 35 (Android 15)  
**Language:** Kotlin  
**UI Framework:** Jetpack Compose with Material 3

### Key Features

- **Household Management**: Create/join households, manage family members with different roles (Owner/Parent/Kid/Guest)
- **Room-based Organization**: Create rooms with QR codes for quick check-ins
- **Task Templates**: Reusable cleaning templates with estimated times
- **Task Scheduling**: Daily, weekly, monthly schedules with assignments
- **QR Check-in Flow**: Scan room QR codes to quickly start/skip/complete tasks
- **Kid Mode**: Simplified, ADHD-friendly interface with large controls and optional TTS
- **Printable Checklists**: Generate PDF checklists for offline use
- **Family Board**: "Today / Upcoming / Completed" view with drag-to-assign
- **Offline-First**: Local-first architecture with background sync
- **Internationalization**: English and Ukrainian support

## 🏗️ Architecture

### Multi-Module Structure

```
planner/
├── app/                          # Main application module
├── core/
│   ├── model/                    # Domain models (pure Kotlin)
│   ├── ui/                       # Design system, theming, reusable components
│   └── common/                   # Utilities, result types, error handling
├── data/
│   ├── database/                 # Room entities, DAOs, migrations
│   ├── network/                  # Retrofit APIs, DTOs, interceptors
│   └── repository/               # Repositories, sync engine
├── feature/                      # Feature modules
│   ├── auth/                     # Authentication
│   ├── household/                # Household & member management
│   ├── rooms/                    # Room management & templates
│   ├── qr/                       # QR scanning & quick actions
│   ├── kidmode/                  # Kid-friendly task view
│   ├── board/                    # Family task board
│   ├── printables/               # PDF generation
│   └── settings/                 # App settings & preferences
└── testing/
    └── core/                     # Test utilities, fakes
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose, Material 3 |
| **Architecture** | MVVM + Repository pattern |
| **DI** | Hilt |
| **Navigation** | Navigation Compose |
| **Concurrency** | Coroutines + Flows |
| **Local DB** | Room + DataStore |
| **Network** | Retrofit + OkHttp + Kotlinx Serialization |
| **Camera/QR** | CameraX + ML Kit Barcode Scanning |
| **Background** | WorkManager |
| **Push** | Firebase Cloud Messaging |
| **Testing** | JUnit5, Robolectric, Turbine, MockK |

## 📦 Key Dependencies

See `gradle/libs.versions.toml` for complete version catalog. Major dependencies:

- **Compose BOM**: 2024.09.03
- **Kotlin**: 2.0.20
- **Hilt**: 2.52
- **Room**: 2.6.1
- **Retrofit**: 2.11.0
- **CameraX**: 1.3.4
- **ML Kit Barcode**: 17.3.0
- **WorkManager**: 2.9.1
- **Firebase BOM**: 33.4.0

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Hedgehog (2023.1.1) or later
- **JDK**: 17 or later
- **Android SDK**: API 35
- **Gradle**: 8.6+

### Building the Project

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd planner
   ```

2. **Configure Firebase** (optional for MVP, required for FCM)
   - Create a Firebase project at https://console.firebase.google.com/
   - Download `google-services.json` and place in `app/`
   - If skipping: comment out `google-services` plugin in `app/build.gradle.kts`

3. **Update API base URL**
   - Edit `data/network/src/main/kotlin/.../di/NetworkModule.kt`
   - Change `baseUrl` to your backend endpoint (or mock server)

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Run on device/emulator**
   ```bash
   ./gradlew installDebug
   ```
   Or use Android Studio's "Run" button.

### Running Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest
```

## 🗂️ Data Models

### Core Entities

- **User**: `id`, `email`, `displayName`, `locale`
- **Household**: `id`, `name`, `inviteCode`
- **Member**: `id`, `role`, `nickname`, `colorHex`, `userId`, `householdId`
- **Room**: `id`, `householdId`, `name`, `qrSlug`, `order`
- **Template**: `id`, `roomId`, `title`, `steps[]`, `defaultRecurrence`
- **Task**: `id`, `householdId`, `title`, `roomId`, `templateId`, `assigneeId`, `dueDate`, `status`, `actualMin`, `estMin`, `notes`
- **Schedule**: `id`, `householdId`, `title`, `recurrence`, `daysOfWeek`, `dayOfMonth`, `templateId`, `roomId`, `assigneeId`, `nextRun`, `active`
- **CommentChip**: `id`, `householdId`, `text`, `pinned`

### Enums

- **Role**: `OWNER`, `PARENT`, `KID`, `GUEST`
- **TaskStatus**: `TODO`, `DOING`, `DONE`, `SKIPPED`
- **Recurrence**: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`

## 🌐 Internationalization

Supported locales:
- **English** (`values/strings.xml`)
- **Ukrainian** (`values-uk/strings.xml`)

To add a new language:
1. Create `app/src/main/res/values-{locale}/strings.xml`
2. Translate all strings from `values/strings.xml`
3. Update user locale preference in settings

## 🎨 Design System

### Theme

Material 3 theme with light/dark mode support. Colors defined in `core/ui/theme/Color.kt`:

- **Primary**: Blue (#2196F3)
- **Secondary**: Green (#4CAF50)
- **Status Colors**: Gray (TODO), Blue (DOING), Green (DONE), Orange (SKIPPED)

### Typography

Material 3 type scale with 15 text styles from `displayLarge` to `labelSmall`.

### Components

Reusable components in `core/ui/components/`:
- `LoadingIndicator`: Centered circular progress
- `ErrorMessage`: Error display with optional retry

## 📱 Offline-First Strategy

### Read Path
1. UI observes Room database via Flow
2. Background sync worker fetches latest from API
3. Database updates trigger UI refresh

### Write Path
1. User action → Optimistic local update
2. Mark as `pendingSync = true`
3. WorkManager enqueues sync job
4. Sync worker retries with exponential backoff
5. Clear `pendingSync` on success

### Conflict Resolution
- Server wins for schema changes
- Client merges user notes/comments

## 🔔 Push Notifications

Firebase Cloud Messaging handles:
- Task assigned
- Task due soon
- Parent approval needed
- Weekly digest

Configure quiet hours per household in settings.

## 📄 Printables

Generate PDF checklists using Android's `PdfDocument`:
1. Compose → Canvas render → PDF
2. Options: font size, bilingual (EN/UK), iconography
3. Share via Android Sharesheet or print via `PrintManager`

## 🧪 Testing Strategy

### Unit Tests
- **Location**: `src/test/`
- **Mocking**: MockK
- **Coroutines**: `kotlinx-coroutines-test` with `TestDispatcher`
- **Flows**: Turbine for testing

### Integration Tests
- **Location**: `src/androidTest/`
- **Framework**: Robolectric for faster tests
- **UI Testing**: Compose UI Testing with Kaspresso

### Test Coverage
- Target: 80%+ coverage for repositories and ViewModels
- Use `./gradlew testDebugUnitTestCoverage` for reports

## 🚧 Roadmap

### MVP (Current)
- [x] Project structure & Gradle setup
- [x] Core domain models
- [x] Room database with DAOs
- [x] Retrofit networking layer
- [x] Repository pattern with sync
- [x] Material 3 theming
- [x] Basic navigation & home screen
- [ ] Auth screens (magic link)
- [ ] Household creation/join flow
- [ ] Room management & QR generation
- [ ] QR scanning with CameraX
- [ ] Task creation & assignment
- [ ] Kid mode screen
- [ ] Family board with drag-to-assign
- [ ] Printable PDF generation

### v1.1
- [ ] Gamification (points, badges, streaks)
- [ ] ML duration suggestions
- [ ] Inventory tracking & reorder links
- [ ] Calendar integration
- [ ] Widget support

### v2.0
- [ ] iOS app (KMP or native Swift)
- [ ] Web admin panel
- [ ] Advanced ML schedule optimization
- [ ] Shared household notes
- [ ] Photo attachments for task verification

## 🤝 Contributing

1. Create a feature branch: `git checkout -b feature/amazing-feature`
2. Follow Kotlin coding conventions
3. Write tests for new features
4. Ensure all tests pass: `./gradlew test`
5. Update documentation as needed
6. Submit a pull request

## 📝 License

This project is licensed under the BSD 3-Clause License - see the [LICENSE](LICENSE) file for details.

## 👥 Authors

- **Initial work**: [Your Name]

## 🙏 Acknowledgments

- Material Design guidelines
- Android Architecture Components team
- Jetpack Compose community

---

**Note:** This is an MVP scaffold. Many features are marked as TODO and require full implementation. The architecture is production-ready, but screens, ViewModels, and business logic need to be completed per the feature specification in the architecture document.

For detailed feature specs, see the original architecture document: `docs/architecture-v1.md` (if available).

