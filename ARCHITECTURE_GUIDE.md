# 🏗️ Architecture Guide - NeuroFlow App

## 📐 Clean Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER INTERFACE                           │
│                     (What user sees)                            │
└─────────────────────────────────────────────────────────────────┘
                              ↕
┌─────────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Screens    │  │  ViewModels  │  │  UI State    │         │
│  │  (Compose)   │←→│   (Logic)    │←→│   (Data)     │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
│  Files: presentation/matrix/, presentation/focus/, etc.         │
└─────────────────────────────────────────────────────────────────┘
                              ↕
┌─────────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Models     │  │   Engines    │  │  Schedulers  │         │
│  │ (Business)   │  │  (Logic)     │  │ (Algorithms) │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
│  Files: domain/model/, domain/engine/, domain/scheduler/        │
└─────────────────────────────────────────────────────────────────┘
                              ↕
┌─────────────────────────────────────────────────────────────────┐
│                       DATA LAYER                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ Repositories │  │     DAOs     │  │   Entities   │         │
│  │ (Mediator)   │←→│  (Queries)   │←→│   (Tables)   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
│  Files: data/repository/, data/local/dao/, data/local/entity/   │
└─────────────────────────────────────────────────────────────────┘
                              ↕
┌─────────────────────────────────────────────────────────────────┐
│                      DATABASE / STORAGE                         │
│                    (SQLite + DataStore)                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Data Flow Example: Displaying Tasks

### **Scenario**: User opens the Matrix screen to see their tasks

```
1. USER ACTION
   │
   ├─→ Opens Matrix Screen
   │

2. PRESENTATION LAYER
   │
   ├─→ MatrixScreen.kt (Composable)
   │   │
   │   └─→ Gets MatrixViewModel (via Hilt)
   │       │
   │       └─→ Collects tasks StateFlow
   │

3. VIEW MODEL
   │
   ├─→ MatrixViewModel.kt
   │   │
   │   └─→ Calls taskRepository.getAllTasks()
   │

4. DOMAIN LAYER
   │
   ├─→ TaskRepository.kt
   │   │
   │   └─→ Calls taskDao.getAllTasksFlow()
   │       │
   │       └─→ Maps TaskEntity → Task (domain model)
   │

5. DATA LAYER
   │
   ├─→ TaskDao.kt
   │   │
   │   └─→ Executes SQL query
   │       │
   │       └─→ Returns Flow<List<TaskEntity>>
   │

6. DATABASE
   │
   └─→ Room Database
       │
       └─→ SQLite reads from tasks table

7. DATA FLOWS BACK UP
   │
   ├─→ TaskEntity → Repository → ViewModel → UI
   │
   └─→ UI recomposes with new data
```

---

## 🔄 Data Flow Example: Creating a Task

### **Scenario**: User creates a new task

```
1. USER ACTION
   │
   ├─→ Clicks "Add Task" button
   │   │
   │   └─→ Fills in task details
   │       │
   │       └─→ Clicks "Save"
   │

2. PRESENTATION LAYER
   │
   ├─→ AddTaskScreen.kt
   │   │
   │   └─→ Calls viewModel.createTask(title, quadrant)
   │

3. VIEW MODEL
   │
   ├─→ MatrixViewModel.kt
   │   │
   │   └─→ Launches coroutine in viewModelScope
   │       │
   │       └─→ Calls repository.insertTask(task)
   │

4. DOMAIN LAYER
   │
   ├─→ TaskRepository.kt
   │   │
   │   └─→ Converts Task → TaskEntity
   │       │
   │       └─→ Calls taskDao.insert(taskEntity)
   │

5. DATA LAYER
   │
   ├─→ TaskDao.kt
   │   │
   │   └─→ Executes INSERT SQL
   │

6. DATABASE
   │
   └─→ Room Database
       │
       └─→ SQLite inserts into tasks table

7. REACTIVE UPDATE
   │
   ├─→ Flow emits new list
   │   │
   │   └─→ ViewModel receives update
   │       │
   │       └─→ UI automatically recomposes
   │           │
   │           └─→ New task appears in list!
```

---

## 🧩 Component Responsibilities

### **Presentation Layer** 📱
**Responsibility**: Display UI and handle user interactions

| Component | Purpose | Example |
|-----------|---------|---------|
| **Screen** | Composable UI | `MatrixScreen.kt` |
| **ViewModel** | Manage UI state | `MatrixViewModel.kt` |
| **UI State** | Data for UI | `data class MatrixUiState` |
| **Navigation** | Screen routing | `NeuroFlowApp.kt` |

**Rules**:
- ✅ Can call Domain/Data layers
- ❌ Cannot access database directly
- ✅ Should be platform-specific (Android)
- ✅ Contains UI logic only

---

### **Domain Layer** 🧠
**Responsibility**: Business logic and rules

| Component | Purpose | Example |
|-----------|---------|---------|
| **Models** | Business entities | `Task.kt`, `Quadrant.kt` |
| **Engines** | Complex logic | `FreshStartEngine.kt` |
| **Schedulers** | Algorithms | `TaskScheduler.kt` |
| **Interfaces** | Contracts | Repository interfaces |

**Rules**:
- ✅ Pure Kotlin (no Android dependencies)
- ✅ Contains business rules
- ❌ No UI code
- ❌ No database code
- ✅ Reusable across platforms

---

### **Data Layer** 💾
**Responsibility**: Data access and storage

| Component | Purpose | Example |
|-----------|---------|---------|
| **Repository** | Data mediator | `TaskRepository.kt` |
| **DAO** | Database queries | `TaskDao.kt` |
| **Entity** | Database table | `TaskEntity.kt` |
| **DataStore** | Key-value storage | `UserPreferencesDataStore.kt` |

**Rules**:
- ✅ Handles data sources
- ✅ Converts Entity ↔ Model
- ❌ No business logic
- ✅ Can use Android libraries (Room, DataStore)

---

## 🔌 Dependency Injection Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    @HiltAndroidApp                          │
│                 NeuroFlowApplication                        │
│                  (App Entry Point)                          │
└─────────────────────────────────────────────────────────────┘
                          │
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                      AppModule.kt                           │
│                  (Dependency Provider)                      │
│                                                             │
│  @Provides                                                  │
│  fun provideDatabase(context): Database                     │
│                                                             │
│  @Provides                                                  │
│  fun provideTaskDao(database): TaskDao                      │
│                                                             │
│  @Provides                                                  │
│  fun provideTaskRepository(dao): TaskRepository             │
└─────────────────────────────────────────────────────────────┘
                          │
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                   @AndroidEntryPoint                        │
│                     MainActivity                            │
└─────────────────────────────────────────────────────────────┘
                          │
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                    @HiltViewModel                           │
│                   MatrixViewModel                           │
│                                                             │
│  @Inject constructor(                                       │
│      private val repository: TaskRepository  ← INJECTED!    │
│  )                                                          │
└─────────────────────────────────────────────────────────────┘
                          │
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                    @Composable                              │
│                   MatrixScreen()                            │
│                                                             │
│  val viewModel: MatrixViewModel = hiltViewModel() ← MAGIC! │
└─────────────────────────────────────────────────────────────┘
```

**How it works**:
1. `@HiltAndroidApp` initializes Hilt
2. `AppModule` tells Hilt how to create dependencies
3. `@AndroidEntryPoint` enables injection in Activity
4. `@HiltViewModel` enables injection in ViewModel
5. `hiltViewModel()` automatically provides ViewModel with dependencies

---

## 🔄 Coroutine Scopes

```
┌─────────────────────────────────────────────────────────────┐
│                  Application Scope                          │
│              (Lives entire app lifetime)                    │
│                                                             │
│  val applicationScope = CoroutineScope(                     │
│      SupervisorJob() + Dispatchers.IO                       │
│  )                                                          │
│                                                             │
│  Use for: Background tasks that survive Activity death      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  ViewModel Scope                            │
│            (Lives while ViewModel exists)                   │
│                                                             │
│  viewModelScope.launch {                                    │
│      // Automatically cancelled when ViewModel cleared      │
│  }                                                          │
│                                                             │
│  Use for: UI-related operations                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  Lifecycle Scope                            │
│          (Lives while Activity/Fragment active)             │
│                                                             │
│  lifecycleScope.launch {                                    │
│      // Cancelled when Activity destroyed                   │
│  }                                                          │
│                                                             │
│  Use for: Activity-specific operations                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎨 Compose UI Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    NeuroFlowApp.kt                          │
│                  (Navigation Container)                     │
│                                                             │
│  NavHost(navController) {                                   │
│      composable("matrix") { MatrixScreen() }                │
│      composable("focus") { FocusScreen() }                  │
│      composable("schedule") { ScheduleScreen() }            │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                 ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ MatrixScreen │  │ FocusScreen  │  │ScheduleScreen│
│              │  │              │  │              │
│ @Composable  │  │ @Composable  │  │ @Composable  │
└──────────────┘  └──────────────┘  └──────────────┘
        │
        ↓
┌─────────────────────────────────────────────────────────────┐
│                   MatrixViewModel                           │
│                                                             │
│  val tasks: StateFlow<List<Task>>                           │
│  fun createTask(...)                                        │
│  fun updateTask(...)                                        │
│  fun deleteTask(...)                                        │
└─────────────────────────────────────────────────────────────┘
        │
        ↓
┌─────────────────────────────────────────────────────────────┐
│                   TaskRepository                            │
│                                                             │
│  suspend fun getAllTasks(): Flow<List<Task>>                │
│  suspend fun insertTask(task: Task)                         │
│  suspend fun updateTask(task: Task)                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔔 Background Work Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              NeuroFlowApplication.onCreate()                │
│                                                             │
│  scheduleDailyWorkers() {                                   │
│      WorkManager.enqueue(NotificationWorker)                │
│      WorkManager.enqueue(DistractionSyncWorker)             │
│      WorkManager.enqueue(FocusWidgetUpdateWorker)           │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                 ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│Notification  │  │ Distraction  │  │ Widget Update│
│   Worker     │  │ Sync Worker  │  │   Worker     │
└──────────────┘  └──────────────┘  └──────────────┘
        │
        ↓
┌─────────────────────────────────────────────────────────────┐
│                  @HiltWorker                                │
│              NotificationWorker                             │
│                                                             │
│  @AssistedInject constructor(                               │
│      context: Context,                                      │
│      params: WorkerParameters,                              │
│      private val repository: TaskRepository  ← INJECTED!    │
│  )                                                          │
│                                                             │
│  override suspend fun doWork(): Result {                    │
│      val tasks = repository.getPendingTasks()               │
│      sendNotification(tasks)                                │
│      return Result.success()                                │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 State Management Pattern

### **Unidirectional Data Flow**

```
┌─────────────────────────────────────────────────────────────┐
│                         UI (Screen)                         │
│                                                             │
│  @Composable                                                │
│  fun MatrixScreen(viewModel: MatrixViewModel) {             │
│      val uiState by viewModel.uiState.collectAsState()      │
│                                                             │
│      when (uiState) {                                       │
│          is Loading -> LoadingSpinner()                     │
│          is Success -> TaskList(uiState.tasks)              │
│          is Error -> ErrorMessage(uiState.message)          │
│      }                                                      │
│                                                             │
│      Button(onClick = { viewModel.createTask(...) })        │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
                          ↑                 │
                          │                 │
                    STATE │                 │ EVENTS
                          │                 │
                          │                 ↓
┌─────────────────────────────────────────────────────────────┐
│                      ViewModel                              │
│                                                             │
│  private val _uiState = MutableStateFlow<UiState>(Loading)  │
│  val uiState: StateFlow<UiState> = _uiState.asStateFlow()  │
│                                                             │
│  fun createTask(title: String) {                            │
│      viewModelScope.launch {                                │
│          repository.insertTask(Task(title = title))         │
│          // State automatically updates via Flow            │
│      }                                                      │
│  }                                                          │
│                                                             │
│  init {                                                     │
│      viewModelScope.launch {                                │
│          repository.getAllTasks().collect { tasks ->        │
│              _uiState.value = Success(tasks)                │
│          }                                                  │
│      }                                                      │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
                          ↑
                          │
                     DATA │
                          │
┌─────────────────────────────────────────────────────────────┐
│                      Repository                             │
│                                                             │
│  fun getAllTasks(): Flow<List<Task>> {                      │
│      return taskDao.getAllTasksFlow()                       │
│          .map { entities -> entities.map { it.toTask() } }  │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 🗂️ Package Structure

```
com.neuroflow.app/
│
├── 📱 MainActivity.kt                    # UI entry point
├── 🚀 NeuroFlowApplication.kt            # App entry point
│
├── 📊 data/                              # DATA LAYER
│   ├── local/                            # Local storage
│   │   ├── NeuroFlowDatabase.kt          # Database setup
│   │   ├── UserPreferencesDataStore.kt   # Settings
│   │   ├── Converters.kt                 # Type converters
│   │   ├── entity/                       # Database tables
│   │   │   ├── TaskEntity.kt
│   │   │   ├── GoalEntity.kt
│   │   │   └── SleepLogEntity.kt
│   │   └── dao/                          # Database queries
│   │       ├── TaskDao.kt
│   │       ├── GoalDao.kt
│   │       └── SleepLogDao.kt
│   └── repository/                       # Data access
│       ├── TaskRepository.kt
│       ├── GoalRepository.kt
│       └── SleepPressureRepository.kt
│
├── 🧠 domain/                            # DOMAIN LAYER
│   ├── model/                            # Business models
│   │   ├── Task.kt
│   │   ├── Quadrant.kt
│   │   ├── AppTheme.kt
│   │   └── Goal.kt
│   ├── engine/                           # Business logic
│   │   ├── FreshStartEngine.kt
│   │   ├── AutonomyNudgeEngine.kt
│   │   ├── SleepPressureEngine.kt
│   │   └── DistractionEngine.kt
│   ├── scheduler/                        # Algorithms
│   │   ├── TaskScheduler.kt
│   │   └── EnergyAwareScheduler.kt
│   └── repository/                       # Interfaces
│
├── 🎨 presentation/                      # PRESENTATION LAYER
│   ├── common/                           # Shared UI
│   │   ├── NeuroFlowApp.kt               # Navigation
│   │   ├── components/                   # Reusable components
│   │   └── theme/                        # App theme
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   ├── matrix/                           # Matrix screen
│   │   ├── MatrixScreen.kt
│   │   ├── MatrixViewModel.kt
│   │   └── components/
│   ├── focus/                            # Focus screen
│   ├── schedule/                         # Schedule screen
│   ├── analytics/                        # Analytics screen
│   ├── settings/                         # Settings screen
│   ├── onboarding/                       # Onboarding flow
│   └── widget/                           # Home screen widget
│
├── 💉 di/                                # DEPENDENCY INJECTION
│   └── AppModule.kt                      # Hilt module
│
├── ⚙️ worker/                            # BACKGROUND WORK
│   ├── NotificationWorker.kt
│   ├── DistractionSyncWorker.kt
│   ├── FocusWidgetUpdateWorker.kt
│   └── AutonomyNudgeWorker.kt
│
├── 🔒 kiosk/                             # KIOSK MODE
│   └── DeviceOwnerKioskManager.kt
│
└── 📡 receiver/                          # BROADCAST RECEIVERS
    ├── BootReceiver.kt
    ├── DeviceAdminReceiver.kt
    └── NudgeSnoozeReceiver.kt
```

---

## 🎯 Key Architectural Principles

### 1. **Separation of Concerns**
Each layer has a specific responsibility and doesn't mix concerns.

### 2. **Dependency Rule**
Dependencies point inward: Presentation → Domain ← Data

### 3. **Single Source of Truth**
Database is the single source of truth for app data.

### 4. **Unidirectional Data Flow**
Data flows down (state), events flow up (user actions).

### 5. **Reactive Programming**
Use Flow for reactive data streams that automatically update UI.

### 6. **Dependency Injection**
Use Hilt to provide dependencies, making code testable and modular.

---

## 🔍 Tracing a Feature: Task Creation

Let's trace how creating a task works through all layers:

```
1. USER INTERACTION
   └─→ User clicks "Add Task" button in MatrixScreen.kt

2. UI EVENT
   └─→ onClick = { viewModel.createTask(title, quadrant) }

3. VIEWMODEL
   └─→ MatrixViewModel.createTask()
       └─→ viewModelScope.launch {
               repository.insertTask(task)
           }

4. REPOSITORY
   └─→ TaskRepository.insertTask()
       └─→ Converts Task (domain) → TaskEntity (data)
       └─→ withContext(Dispatchers.IO) {
               taskDao.insert(taskEntity)
           }

5. DAO
   └─→ TaskDao.insert()
       └─→ @Insert suspend fun insert(task: TaskEntity)

6. ROOM
   └─→ Generates SQL: INSERT INTO tasks VALUES (...)
       └─→ Executes on background thread

7. DATABASE
   └─→ SQLite writes to disk

8. REACTIVE UPDATE (Automatic!)
   └─→ TaskDao.getAllTasksFlow() emits new list
       └─→ Repository receives update
           └─→ ViewModel receives update
               └─→ UI State updates
                   └─→ Compose recomposes
                       └─→ New task appears in UI!
```

---

## 📚 Further Reading

- [Android App Architecture Guide](https://developer.android.com/topic/architecture)
- [Clean Architecture by Uncle Bob](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [MVVM Pattern](https://developer.android.com/topic/architecture#recommended-app-arch)
- [Repository Pattern](https://developer.android.com/codelabs/basic-android-kotlin-training-repository-pattern)

---

**Understanding architecture is key to mastering this codebase! 🏗️**
