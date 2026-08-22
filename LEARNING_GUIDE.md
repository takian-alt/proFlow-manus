# 🎓 Complete Learning Guide for NeuroFlow (PROCUS) Codebase

## 📋 Table of Contents
1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Prerequisites](#prerequisites)
4. [Architecture Overview](#architecture-overview)
5. [Learning Path](#learning-path)
6. [File-by-File Study Guide](#file-by-file-study-guide)
7. [Key Concepts to Learn](#key-concepts-to-learn)
8. [Hands-On Exercises](#hands-on-exercises)
9. [Resources](#resources)

---

## 🎯 Project Overview

**NeuroFlow** is a productivity and focus management Android application built with modern Android development practices. It helps users:
- Manage tasks using the Eisenhower Matrix (4 quadrants)
- Track energy levels and optimize scheduling
- Implement focus sessions and time blocking
- Set and track goals (daily, weekly, yearly)
- Use kiosk mode for distraction-free work

**Language**: Kotlin
**Platform**: Android
**Architecture**: Clean Architecture with MVVM pattern
**UI Framework**: Jetpack Compose (modern declarative UI)

---

## 🛠️ Technology Stack

### Core Technologies
- **Kotlin**: Modern programming language for Android
- **Jetpack Compose**: Declarative UI framework (replaces XML layouts)
- **Coroutines & Flow**: Asynchronous programming and reactive streams
- **Hilt**: Dependency injection framework (built on Dagger)

### Android Jetpack Components
- **Room**: Local database (SQLite wrapper)
- **DataStore**: Key-value storage (replaces SharedPreferences)
- **WorkManager**: Background task scheduling
- **Navigation Compose**: Screen navigation
- **ViewModel & LiveData**: UI state management
- **Lifecycle**: Activity/Fragment lifecycle management

### Additional Libraries
- **Vico Charts**: Data visualization
- **Coil**: Image loading
- **Material 3**: Modern Material Design components
- **Biometric**: Fingerprint/face authentication

---

## 📚 Prerequisites

Before diving into this codebase, you should understand:

### 1. **Kotlin Basics** (Start Here!)
- Variables (val vs var)
- Functions and lambdas
- Classes and data classes
- Null safety (?, !!, ?.)
- Extension functions
- Coroutines basics (suspend, launch, async)
- Flow and StateFlow

### 2. **Android Fundamentals**
- Activity lifecycle
- Context
- Intents
- Permissions
- Resources (strings, colors, dimensions)

### 3. **Jetpack Compose Basics**
- Composable functions (@Composable)
- State management (remember, mutableStateOf)
- Recomposition
- Modifiers
- Common layouts (Column, Row, Box)

---

## 🏗️ Architecture Overview

This app follows **Clean Architecture** with three main layers:

```
┌─────────────────────────────────────────┐
│         PRESENTATION LAYER              │
│  (UI, ViewModels, Compose Screens)      │
│  📁 presentation/                        │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│          DOMAIN LAYER                   │
│  (Business Logic, Use Cases, Models)    │
│  📁 domain/                              │
└─────────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────────┐
│           DATA LAYER                    │
│  (Repositories, Database, DataStore)    │
│  📁 data/                                │
└─────────────────────────────────────────┘
```

### Key Folders:
- **`data/`**: Database entities, DAOs, repositories, data sources
- **`domain/`**: Business logic, models, engines, schedulers
- **`presentation/`**: UI screens, ViewModels, Compose components
- **`di/`**: Dependency injection modules (Hilt)
- **`worker/`**: Background tasks (WorkManager)
- **`kiosk/`**: Device owner/kiosk mode management

---

## 🗺️ Learning Path

### **Phase 1: Foundation (Week 1-2)**
Learn Kotlin and Android basics before touching the codebase.

### **Phase 2: Entry Points (Week 3)**
Understand how the app starts and flows.

### **Phase 3: Data Layer (Week 4-5)**
Learn how data is stored and retrieved.

### **Phase 4: Domain Layer (Week 6)**
Understand business logic and rules.

### **Phase 5: Presentation Layer (Week 7-8)**
Learn UI implementation with Compose.

### **Phase 6: Advanced Features (Week 9+)**
Explore background work, notifications, kiosk mode.

---

## 📖 File-by-File Study Guide

### 🚀 **PHASE 1: Start Here (Entry Points)**

#### 1. **NeuroFlowApplication.kt** ⭐ START HERE
**Location**: `app/src/main/java/com/neuroflow/app/NeuroFlowApplication.kt`

**What it does**: Application entry point - runs when app starts

**Key concepts**:
- `@HiltAndroidApp`: Enables dependency injection
- `onCreate()`: Runs once when app launches
- `applicationScope`: Background coroutine scope
- WorkManager initialization
- Database cleanup
- Notification channels

**Study order**: Read this FIRST to understand app initialization

**Questions to answer**:
- What happens when the app first launches?
- What background workers are scheduled?
- How is dependency injection set up?

---

#### 2. **MainActivity.kt** ⭐ SECOND FILE
**Location**: `app/src/main/java/com/neuroflow/app/MainActivity.kt`

**What it does**: Main UI entry point - hosts all screens

**Key concepts**:
- `@AndroidEntryPoint`: Enables Hilt injection in Activity
- `setContent { }`: Sets up Compose UI
- Intent handling (deep links, notifications)
- Theme management (light/dark mode)
- Onboarding flow
- Permission requests

**Study order**: Read this SECOND to understand UI flow

**Questions to answer**:
- How does the app decide to show onboarding vs main app?
- How are themes applied?
- How does the app handle notification clicks?

---

### 📊 **PHASE 2: Data Layer (Database & Storage)**

#### 3. **NeuroFlowDatabase.kt**
**Location**: `app/src/main/java/com/neuroflow/app/data/local/NeuroFlowDatabase.kt`

**What it does**: Defines the Room database schema

**Key concepts**:
- `@Database`: Marks this as a Room database
- Entities: Tables in the database
- DAOs: Data Access Objects (queries)
- Migrations: Database version upgrades

**Study order**: Read this to understand data structure

---

#### 4. **TaskEntity.kt** (in data/local/entity/)
**What it does**: Defines the Task table structure

**Key concepts**:
- `@Entity`: Marks this as a database table
- `@PrimaryKey`: Unique identifier
- `@ColumnInfo`: Column definitions
- Data classes in Kotlin

---

#### 5. **TaskDao.kt**
**Location**: `app/src/main/java/com/neuroflow/app/data/local/dao/TaskDao.kt`

**What it does**: Defines database queries for tasks

**Key concepts**:
- `@Dao`: Data Access Object
- `@Query`: SQL queries
- `@Insert`, `@Update`, `@Delete`: CRUD operations
- `Flow<>`: Reactive data streams
- `suspend`: Coroutine functions

**Example queries to understand**:
```kotlin
@Query("SELECT * FROM tasks WHERE id = :id")
suspend fun getTaskById(id: String): TaskEntity?

@Query("SELECT * FROM tasks ORDER BY createdAt DESC")
fun getAllTasksFlow(): Flow<List<TaskEntity>>
```

---

#### 6. **TaskRepository.kt**
**Location**: `app/src/main/java/com/neuroflow/app/data/repository/TaskRepository.kt`

**What it does**: Mediates between ViewModels and database

**Key concepts**:
- Repository pattern
- Dependency injection (`@Inject`)
- Coroutine dispatchers (IO, Main)
- Business logic layer

---

#### 7. **UserPreferencesDataStore.kt**
**Location**: `app/src/main/java/com/neuroflow/app/data/local/UserPreferencesDataStore.kt`

**What it does**: Stores user settings and preferences

**Key concepts**:
- DataStore (modern SharedPreferences)
- Preferences API
- Flow-based reactive updates
- Type-safe key-value storage

---

### 🧠 **PHASE 3: Domain Layer (Business Logic)**

#### 8. **Task.kt** (domain model)
**Location**: `app/src/main/java/com/neuroflow/app/domain/model/`

**What it does**: Domain model (business representation of a task)

**Key concepts**:
- Separation of concerns (Entity vs Model)
- Domain-driven design
- Quadrant enum (Eisenhower Matrix)

---

#### 9. **Schedulers** (domain/scheduler/)
**What they do**: Implement task scheduling algorithms

**Key files**:
- `TaskScheduler.kt`: Main scheduling logic
- `EnergyAwareScheduler.kt`: Considers user energy levels

**Key concepts**:
- Algorithm implementation
- Business rules
- Pure functions (no side effects)

---

#### 10. **Engines** (domain/engine/)
**What they do**: Implement complex business logic

**Key files**:
- `FreshStartEngine.kt`: Weekly reset logic
- `AutonomyNudgeEngine.kt`: Smart notification timing
- `SleepPressureEngine.kt`: Sleep debt calculation

---

### 🎨 **PHASE 4: Presentation Layer (UI)**

#### 11. **NeuroFlowApp.kt**
**Location**: `app/src/main/java/com/neuroflow/app/presentation/common/NeuroFlowApp.kt`

**What it does**: Main app navigation and screen container

**Key concepts**:
- Navigation Compose
- NavHost and NavController
- Bottom navigation
- Screen routing

---

#### 12. **Matrix Screen** (presentation/matrix/)
**What it does**: Shows Eisenhower Matrix (4 quadrants)

**Key files**:
- `MatrixScreen.kt`: UI layout
- `MatrixViewModel.kt`: State management

**Key concepts**:
- Composable functions
- State hoisting
- ViewModel pattern
- Collecting Flow in Compose

**Example pattern**:
```kotlin
@Composable
fun MatrixScreen(viewModel: MatrixViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsState()

    Column {
        tasks.forEach { task ->
            TaskCard(task = task)
        }
    }
}
```

---

#### 13. **ViewModels** (presentation/*/viewmodel/)
**What they do**: Manage UI state and business logic

**Key concepts**:
- ViewModel lifecycle
- StateFlow and MutableStateFlow
- viewModelScope
- UI state classes

**Example pattern**:
```kotlin
@HiltViewModel
class MatrixViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.getAllTasks().collect { taskList ->
                _tasks.value = taskList
            }
        }
    }
}
```

---

#### 14. **Theme** (presentation/common/theme/)
**What it does**: Defines app colors, typography, shapes

**Key files**:
- `Color.kt`: Color definitions
- `Theme.kt`: Material 3 theme setup
- `Type.kt`: Typography

---

### ⚙️ **PHASE 5: Dependency Injection**

#### 15. **AppModule.kt**
**Location**: `app/src/main/java/com/neuroflow/app/di/AppModule.kt`

**What it does**: Provides dependencies for Hilt

**Key concepts**:
- `@Module` and `@InstallIn`
- `@Provides` and `@Singleton`
- Dependency graph
- Constructor injection vs module provision

**Example**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NeuroFlowDatabase {
        return Room.databaseBuilder(
            context,
            NeuroFlowDatabase::class.java,
            "neuroflow_db"
        ).build()
    }
}
```

---

### 🔔 **PHASE 6: Background Work**

#### 16. **Workers** (worker/)
**What they do**: Run background tasks

**Key files**:
- `NotificationWorker.kt`: Sends notifications
- `DistractionSyncWorker.kt`: Syncs app usage data
- `FocusWidgetUpdateWorker.kt`: Updates home screen widget

**Key concepts**:
- WorkManager API
- PeriodicWorkRequest
- OneTimeWorkRequest
- Work constraints
- HiltWorker

---

### 🔒 **PHASE 7: Advanced Features**

#### 17. **Kiosk Mode** (kiosk/)
**Location**: `app/src/main/java/com/neuroflow/app/kiosk/DeviceOwnerKioskManager.kt`

**What it does**: Locks device to prevent distractions

**Key concepts**:
- Device Owner API
- Lock Task Mode
- Package suspension
- System permissions

---

## 🎓 Key Concepts to Learn

### 1. **Kotlin Coroutines**
```kotlin
// Launch a coroutine
viewModelScope.launch {
    val result = repository.getData() // suspend function
    _state.value = result
}

// Collect Flow
repository.dataFlow.collect { data ->
    // React to data changes
}
```

### 2. **Jetpack Compose State**
```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }

    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

### 3. **Room Database**
```kotlin
// Entity (Table)
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val completed: Boolean
)

// DAO (Queries)
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(task: TaskEntity)
}
```

### 4. **Dependency Injection with Hilt**
```kotlin
// In ViewModel
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel()

// In Composable
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    // Use viewModel
}
```

---

## 🏋️ Hands-On Exercises

### **Exercise 1: Add a New Field to Task**
1. Add a `priority: Int` field to `TaskEntity`
2. Update the database version
3. Create a migration
4. Update the UI to display priority

### **Exercise 2: Create a Simple Screen**
1. Create a new Composable function
2. Add it to the navigation graph
3. Display a list of tasks
4. Add a button to navigate to it

### **Exercise 3: Implement a New Repository Method**
1. Add a new query to `TaskDao`
2. Add a corresponding method to `TaskRepository`
3. Call it from a ViewModel
4. Display the result in UI

### **Exercise 4: Schedule a Background Worker**
1. Create a new Worker class
2. Schedule it in `NeuroFlowApplication`
3. Make it run periodically
4. Log when it executes

---

## 📚 Resources

### **Kotlin Learning**
- [Kotlin Koans](https://play.kotlinlang.org/koans) - Interactive exercises
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)

### **Android Development**
- [Android Basics with Compose](https://developer.android.com/courses/android-basics-compose/course)
- [Android Developer Documentation](https://developer.android.com/)
- [Jetpack Compose Tutorial](https://developer.android.com/jetpack/compose/tutorial)

### **Architecture**
- [Guide to App Architecture](https://developer.android.com/topic/architecture)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)
- [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android)

### **Video Courses**
- [Philipp Lackner YouTube](https://www.youtube.com/@PhilippLackner) - Android tutorials
- [Coding in Flow YouTube](https://www.youtube.com/@codinginflow) - Android architecture

---

## 🎯 Recommended Study Order

### **Week 1-2: Kotlin Fundamentals**
- Complete Kotlin Koans
- Learn about coroutines and Flow
- Practice with small Kotlin programs

### **Week 3: App Entry Points**
1. Read `NeuroFlowApplication.kt` - understand app initialization
2. Read `MainActivity.kt` - understand UI setup
3. Trace the flow from app launch to first screen

### **Week 4: Data Layer**
1. Study `NeuroFlowDatabase.kt` - database setup
2. Read `TaskEntity.kt` - data structure
3. Study `TaskDao.kt` - database queries
4. Read `TaskRepository.kt` - data access layer
5. Explore `UserPreferencesDataStore.kt` - settings storage

### **Week 5: Domain Layer**
1. Study domain models in `domain/model/`
2. Read scheduling algorithms in `domain/scheduler/`
3. Explore business logic engines in `domain/engine/`

### **Week 6-7: Presentation Layer**
1. Study `NeuroFlowApp.kt` - navigation
2. Pick one screen (e.g., Matrix) and study:
   - The Screen composable
   - The ViewModel
   - How state flows from repository to UI
3. Study the theme system

### **Week 8: Dependency Injection**
1. Read `AppModule.kt`
2. Understand how dependencies are provided
3. Trace how a repository gets injected into a ViewModel

### **Week 9+: Advanced Topics**
1. Study WorkManager and background tasks
2. Explore notification system
3. Learn about kiosk mode implementation
4. Study widget implementation

---

## 🔍 Debugging Tips

### **1. Use Logcat**
```kotlin
import android.util.Log

Log.d("MyTag", "Debug message: $variable")
Log.e("MyTag", "Error message", exception)
```

### **2. Add Breakpoints**
- Click left margin in Android Studio
- Run in Debug mode
- Inspect variables

### **3. Compose Preview**
```kotlin
@Preview(showBackground = true)
@Composable
fun MyScreenPreview() {
    NeuroFlowTheme {
        MyScreen()
    }
}
```

### **4. Database Inspector**
- Tools → Database Inspector
- View tables and data in real-time

---

## 🎉 Final Tips

1. **Don't rush** - This is a complex, production-quality codebase
2. **Start small** - Focus on one feature at a time
3. **Run the app** - See how changes affect the UI
4. **Ask questions** - Use comments to document your understanding
5. **Experiment** - Make small changes and observe results
6. **Read documentation** - Official Android docs are excellent
7. **Join communities** - r/androiddev, Kotlin Slack, Stack Overflow

---

## 📝 Study Checklist

- [ ] Set up Android Studio and run the app
- [ ] Complete Kotlin basics course
- [ ] Read and understand `NeuroFlowApplication.kt`
- [ ] Read and understand `MainActivity.kt`
- [ ] Study the database schema
- [ ] Understand one DAO completely
- [ ] Understand one Repository completely
- [ ] Study one complete feature (data → domain → UI)
- [ ] Create a simple new screen
- [ ] Add a new database field
- [ ] Implement a new background worker
- [ ] Understand the navigation system
- [ ] Master Jetpack Compose basics

---

**Good luck with your learning journey! 🚀**

Remember: Every expert was once a beginner. Take it one step at a time, and you'll master this codebase!
