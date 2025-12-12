Prompt for Cursor AI — CleanFlow Kotlin Implementation (Jetpack Compose)

Goal
Implement the complete CleanFlow functionality in a single-activity Kotlin Android app using Jetpack Compose, Navigation, Hilt, Room, DataStore, and WorkManager. The Welcome flow already exists; persist its answers and skip it on subsequent launches. After Welcome, always open Dashboard/Home. Add the AI avatar as a persistent bottom-right overlay.

Tech Stack (hard requirements)

UI: Jetpack Compose, Material3, one Activity

Navigation: androidx.navigation.compose

DI: Hilt

State: ViewModel + Kotlin Flows (no Rx)

Local storage: Room (tasks/history/family/suggestions), DataStore Proto (user profile & preferences)

Background jobs: WorkManager (daily plan generation at 06:00 local)

PDF generation: Android PdfDocument

QR codes: ZXing (create) + ML Kit Barcode Scanning (scan)

OCR import: ML Kit Text Recognition

Animations: Compose animations (subtle)

Testing: JUnit + Turbine for Flow + Robolectric for ViewModels

App Modules / Packages
app/
 ├─ data/
 │   ├─ local/ (Room DAOs, entities, migrations)
 │   ├─ datastore/ (Proto schemas, serializers)
 │   ├─ repository/ (*Repository impls)
 │   └─ pdf/ (PdfGenerator.kt)
 ├─ domain/
 │   ├─ model/ (Task, Plan, Suggestion, Member, UserProfile, etc.)
 │   ├─ usecase/ (GenerateDailyPlan, CompleteTask, SkipTask, RecordQR, ImportOCR, GetInsights, etc.)
 │   └─ engine/ (PlanEngine, LearningEngine, SuggestionEngine)
 ├─ ui/
 │   ├─ nav/ (NavGraph.kt, Destinations.kt)
 │   ├─ home/ (HomeScreen, HomeViewModel)
 │   ├─ planner/ (PlannerScreen, PlannerViewModel)
 │   ├─ family/ (FamilyScreen, FamilyViewModel)
 │   ├─ kids/ (KidsScreen, KidsViewModel)
 │   ├─ insights/ (InsightsScreen, InsightsViewModel)
 │   ├─ paper/ (PaperBridgeScreen, QRScanScreen)
 │   ├─ components/ (TaskCard, ProgressRing, ModeChips, MemberPills, etc.)
 │   └─ avatar/ (ClaraBubbleOverlay, ClaraState, ClaraController)
 ├─ worker/ (DailyPlanWorker.kt, PdfShareWorker.kt)
 ├─ core/ (Result.kt, TimeOfDay.kt, DispatchersModule.kt)
 └─ MainActivity.kt

Navigation (single activity)

Destinations:

home, planner, family, kids, insights, paper

Guard on app start: if DataStore.userProfile.isComplete == true → home else → (existing) Welcome

Bottom bar tabs: Home, Planner, Family, Kids, AI
Avatar bubble: ClaraBubbleOverlay composable rendered in MainActivity above NavHost with Box alignment BottomEnd, 24dp margins.

Data Models (domain)
data class UserProfile(
  val name: String,
  val rooms: List<String>,
  val floors: Int,
  val hasPets: Boolean,
  val devices: List<String>,
  val preference: Preference // Minimalist | FullControl
)

data class Task(
  val id: String,
  val title: String,
  val room: String,
  val estimatedMin: Int,
  val assigneeId: String?, // null = unassigned
  val dueDate: LocalDate,
  val timeOfDay: TimeOfDay, // Morning/Afternoon/Evening
  val status: TaskStatus // Pending/Done/Skipped
)

data class Suggestion(
  val id: String,
  val text: String,
  val confidence: Int, // 0..100
  val action: SuggestionAction // AdjustSchedule, MergeTasks, AddToShoppingList...
)

data class Member(
  val id: String, val name: String, val color: Long, val emoji: String
)

data class HistoryEntry(
  val taskId: String, val date: LocalDate, val status: TaskStatus, val durationMin: Int?, val note: String?
)


Room entities mirror these with indices for dueDate, status, assigneeId.

Engines (domain/engine)

PlanEngine

generateDailyPlan(profile, history, mode, date): List<Task>

Modes: Focus (<=15 min tasks), LowEnergy (reduce count), FullReset (full list), PetMode (inject pet-related tasks).

LearningEngine

Learns from HistoryEntry: adjust estimatedMin, frequency, reorder by success times, detect day-of-week preferences.

SuggestionEngine

Produces Suggestion items like “Merge similar tasks”, “Adjust schedule to weekends for bathrooms”, “Add detergent to shopping list”.

All pure Kotlin, deterministic, unit-testable.

Core Flows (what to implement now)

App start & Welcome bypass

Read DataStore UserProfile.

If isComplete true → navigate directly to home.

Persist any change in UserProfile via UserRepository.

Home / Dashboard

HomeViewModel exposes:

uiState: StateFlow<HomeUiState>

Actions: onComplete(taskId), onSkip(taskId), onChangeMode(mode)

When complete/skip:

Insert HistoryEntry, update Task.status.

Trigger LearningEngine update (in background via viewModelScope.launch).

Emit confetti event; ask ClaraController to react (smile, nod, message).

Planner

Tabs: Daily/Weekly/Seasonal/Custom.

Add/Edit/Delete tasks.

Group sections by TimeOfDay.

PlannerViewModel interacts with PlanEngine for previews.

Family Mode

FamilyViewModel shows:

Members list, progress ring, workload bars.

Toggle “My Plan / Family Plan”.

Assign/unassign tasks to members; recompute workload.

Persist in Room; compute live progress with Flow.

Kids / Single-Room Mode

Generate plan scoped to one room.

Simple actions: Done / Skip; big UI; haptics.

Optional parent print via Paper Bridge.

AI / Insights

Query history for KPIs: completion %, total mins, streaks, top room/day.

Show Suggestion cards with Accept / Dismiss:

Accept → apply change (e.g., write rule to DataStore/Room), notify ClaraController.

Dismiss → record in aiHistory to avoid repeating.

Paper Bridge

PdfGenerator.generateDailyPlanPdf(options) → returns Uri.

Include optional QR per task:

Generate with ZXing; encode payload {taskId, date}.

QR Scan screen with ML Kit:

On scan → resolve task → open quick form (Done/Skip, actual time, note) → persist HistoryEntry.

OCR Import:

Image picker → ML Kit Text Recognition → extract ✅/⏭️, minutes, comments → map to tasks by title → persist.

Background jobs

DailyPlanWorker @ 06:00:

Generate plan for today using PlanEngine + LearningEngine.

Optionally schedule notification: “Your plan is ready”.

WorkManager + Constraints (charging not required).

Avatar (Clara)

ClaraController holds ClaraState (Idle, Suggesting(text), Happy, Encouraging).

Expose fun react(event: ClaraEvent):

TaskCompleted, ModeChanged, SuggestionAccepted, StreakMilestone, etc.

ClaraBubbleOverlay(state) composable:

Bottom-right, 24dp margin above nav bar; animated in/out; tap to open mini tips.

Acceptance Criteria (non-negotiable)

App is edge-to-edge fullscreen; no black borders.

Welcome runs once; subsequent launches open Home.

All lists are lazy, smooth, and restore scroll position.

Buttons never wrap text (e.g., “Family” stays on one line).

Offline-first: all actions work without network.

Unit tests for: PlanEngine, LearningEngine, SuggestionEngine.

UI tests for: Home complete/skip flow; QR scan → quick form path.

Concrete Tasks for Cursor (implement in this order)

Project wiring

Add Hilt, Room, DataStore Proto, Navigation Compose, WorkManager, ML Kit (barcode + text), ZXing, Lottie (optional).

Create package structure above.

Data layer

Define Room entities/DAOs for Task, HistoryEntry, Member, Suggestion.

Implement UserDataStore with Proto schema for UserProfile + preferences (mode, paper-first toggle).

Domain engines + use cases

PlanEngine, LearningEngine, SuggestionEngine.

Use cases: GenerateDailyPlan, CompleteTask, SkipTask, LoadInsights, CreatePdf, ScanQr, ImportOcr.

ViewModels + screens

Home/Planner/Family/Kids/Insights/Paper with state flows and actions.

Hook up bottom navigation.

Add ClaraBubbleOverlay in MainActivity above NavHost.

WorkManager

DailyPlanWorker registered with periodic work at 06:00.

On success, post notification with deep link to Home.

PDF + QR + OCR

Implement PdfGenerator (single A4 page, simple & detailed variants).

Generate QR per task; Scan screen & quick form; OCR import pipeline.

Tests

Unit tests for engines and use cases (Turbine for flows).

Robolectric tests for HomeViewModel complete/skip.

Snippets (signatures only; Cursor should flesh out)
// engines
class PlanEngine { fun generateDailyPlan(profile: UserProfile, history: List<HistoryEntry>, mode: Mode, date: LocalDate): List<Task> }
class LearningEngine { fun onTaskRecorded(entry: HistoryEntry); fun adjustedEstimate(task: Task): Int }
class SuggestionEngine { fun buildSuggestions(history: List<HistoryEntry>, profile: UserProfile): List<Suggestion> }

// avatar
sealed interface ClaraEvent { object TaskCompleted: ClaraEvent; data class ModeChanged(val mode: Mode): ClaraEvent; object SuggestionAccepted: ClaraEvent }
data class ClaraState(val mood: Mood, val message: String?)
class ClaraController { val state: StateFlow<ClaraState>; fun react(event: ClaraEvent) }
