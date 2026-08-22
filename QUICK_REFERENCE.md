# 🚀 Quick Reference Guide - NeuroFlow Codebase

## 📁 Project Structure at a Glance

```
app/src/main/java/com/neuroflow/app/
│
├── 🚀 NeuroFlowApplication.kt      # App entry point (START HERE!)
├── 🎨 MainActivity.kt              # UI entry point (READ SECOND!)
│
├── 📊 data/                        # DATA LAYER
│   ├── local/                      # Local storage
│   │   ├── NeuroFlowDatabase.kt   # Room database setup
│   │   ├── UserPreferencesDataStore.kt  # Settings storage
│   │   ├── entity/                # Database tables
│   │   │   ├── TaskEntity.kt      # Task table
│   │   │   ├── GoalEntity.kt      # Goal table
│   │   │   └── SleepLogEntity.kt  # Sleep log table
│   │   └── dao/                   # Database queries
│   │       ├── TaskDao.kt         # Task queries
│   │       └── GoalDao.kt         # Goal queries
│   └── repository/                # Data access layer
│       ├── TaskRepository.kt      # Task data operations
│       └── GoalRepository.kt      # Goal data operations
│
├── 🧠 domain/                      # DOMAIN LAYER (Business Logic)
│   ├── model/                     # Business models
│   │   ├── Task.kt               # Task domain model
│   │   ├── Quadrant.kt           # Eisenhower Matrix quadrants
│   │   └── AppTheme.kt           # Theme enum
│   ├── engine/                    # Business logic engines
│   │   ├── FreshStartEngine.kt   # Weekly reset logic
│   │   ├── AutonomyNudgeEngine.kt # Smart notifications
│   │   └── SleepPressureEngine.kt # Sleep debt calculation
│   ├── scheduler/                 # Task scheduling algorithms
│   │   └── TaskScheduler.kt      # Auto-scheduling logic
│   └── repository/                # Repository interfaces
│
├── 🎨 presentation/                # PRESENTATION LAYER (UI)
│   ├── common/                    # Shared UI components
│   │   ├── NeuroFlowApp.kt       # Main navigation
│   │   └── theme/                # App theme
│   │       ├── Color.kt          # Colors
│   │       ├── Theme.kt          # Material 3 theme
│   │       └── Type.kt           # Typography
│   ├── matrix/                    # Eisenhower Matrix screen
│   │   ├── MatrixScreen.kt       # UI
│   │   └── MatrixViewModel.kt    # State management
│   ├── focus/                     # Focus session screen
│   ├── schedule/                  # Schedule screen
│   ├── analytics/                 # Analytics screen
│   ├── settings/                  # Settings screen
│   └── onboarding/                # First-time user flow
│
├── 💉 di/                          # DEPENDENCY INJECTION
│   └── AppModule.kt               # Hilt module (provides dependencies)
│
├── ⚙️ worker/                      # BACKGROUND TASKS
│   ├── NotificationWorker.kt      # Sends notifications
│   ├── DistractionSyncWorker.kt   # Syncs app usage
│   └── FocusWidgetUpdateWorker.kt # Updates widget
│
├── 🔒 kiosk/                       # KIOSK MODE
│   └── DeviceOwnerKioskManager.kt # Device locking
│
└── 📡 receiver/                    # BROADCAST RECEIVERS
    ├── BootReceiver.kt            # Handles device boot
    └── DeviceAdminReceiver.kt     # Device admin events
```

---

## 🎯 Top 10 Files to Read First

| # | File | Why Read It | Difficulty |
|---|------|-------------|------------|
| 1 | `NeuroFlowApplication.kt` | Understand app initialization | ⭐ Easy |
| 2 | `MainActivity.kt` | Understand UI flow and navigation | ⭐⭐ Medium |
| 3 | `NeuroFlowDatabase.kt` | See all database tables | ⭐ Easy |
| 4 | `TaskEntity.kt` | Understand task data structure | ⭐ Easy |
| 5 | `TaskDao.kt` | Learn database queries | ⭐⭐ Medium |
| 6 | `TaskRepository.kt` | See data access patterns | ⭐⭐ Medium |
| 7 | `NeuroFlowApp.kt` | Understand navigation | ⭐⭐⭐ Hard |
| 8 | `MatrixScreen.kt` | Learn Compose UI patterns | ⭐⭐⭐ Hard |
| 9 | `MatrixViewModel.kt` | Understand state management | ⭐⭐ Medium |
| 10 | `AppModule.kt` | Learn dependency injection | ⭐⭐⭐ Hard |

---

## 🔑 Key Kotlin Patterns Used

### 1. **Data Classes**
```kotlin
data class Task(
    val id: String,
    val title: String,
    val completed: Boolean
)
```
- Automatically generates `equals()`, `hashCode()`, `toString()`, `copy()`
- Used for immutable data

### 2. **Coroutines**
```kotlin
// Launch a coroutine
viewModelScope.launch {
    val data = repository.getData() // suspend function
}

// Suspend function (can be paused)
suspend fun getData(): List<Task> {
    return dao.getAllTasks()
}
```

### 3. **Flow (Reactive Streams)**
```kotlin
// Emit data
val tasksFlow: Flow<List<Task>> = dao.getAllTasksFlow()

// Collect data
tasksFlow.collect { tasks ->
    println("Got ${tasks.size} tasks")
}
```

### 4. **StateFlow (UI State)**
```kotlin
private val _uiState = MutableStateFlow(UiState())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// Update state
_uiState.value = UiState(loading = true)
```

### 5. **Sealed Classes (Type-Safe States)**
```kotlin
sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<Task>) : UiState()
    data class Error(val message: String) : UiState()
}
```

### 6. **Extension Functions**
```kotlin
fun String.toTitleCase(): String {
    return this.lowercase().replaceFirstChar { it.uppercase() }
}

// Usage
val title = "hello world".toTitleCase() // "Hello world"
```

### 7. **Null Safety**
```kotlin
val name: String? = null  // Nullable
val length = name?.length  // Safe call (returns null if name is null)
val length2 = name?.length ?: 0  // Elvis operator (default value)
val length3 = name!!.length  // Force unwrap (crashes if null!)
```

---

## 🎨 Jetpack Compose Patterns

### 1. **Basic Composable**
```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hello, $name!")
}
```

### 2. **State Management**
```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }

    Column {
        Text("Count: $count")
        Button(onClick = { count++ }) {
            Text("Increment")
        }
    }
}
```

### 3. **Collecting Flow in Compose**
```kotlin
@Composable
fun TaskList(viewModel: TaskViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsState()

    LazyColumn {
        items(tasks) { task ->
            TaskItem(task = task)
        }
    }
}
```

### 4. **Common Layouts**
```kotlin
// Vertical stack
Column {
    Text("First")
    Text("Second")
}

// Horizontal stack
Row {
    Text("Left")
    Text("Right")
}

// Overlay
Box {
    Image(...)
    Text("Overlay text")
}

// Scrollable list
LazyColumn {
    items(list) { item ->
        ItemCard(item)
    }
}
```

### 5. **Modifiers**
```kotlin
Text(
    text = "Hello",
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .background(Color.Blue)
        .clickable { /* click handler */ }
)
```

---

## 🗄️ Room Database Patterns

### 1. **Entity (Table)**
```kotlin
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "completed") val completed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

### 2. **DAO (Queries)**
```kotlin
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY created_at DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE completed = 1")
    suspend fun deleteCompleted()
}
```

### 3. **Database Class**
```kotlin
@Database(
    entities = [TaskEntity::class, GoalEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NeuroFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun goalDao(): GoalDao
}
```

---

## 💉 Hilt Dependency Injection

### 1. **Application Class**
```kotlin
@HiltAndroidApp
class NeuroFlowApplication : Application()
```

### 2. **Activity**
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity()
```

### 3. **ViewModel**
```kotlin
@HiltViewModel
class TaskViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel()
```

### 4. **Module (Providing Dependencies)**
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

    @Provides
    fun provideTaskDao(database: NeuroFlowDatabase): TaskDao {
        return database.taskDao()
    }
}
```

### 5. **Using in Composable**
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    // viewModel is automatically injected
}
```

---

## ⚙️ WorkManager Patterns

### 1. **Worker Class**
```kotlin
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TaskRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Do background work
            val tasks = repository.getPendingTasks()
            sendNotification(tasks)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

### 2. **Scheduling Work**
```kotlin
// One-time work
val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
    .setInitialDelay(1, TimeUnit.HOURS)
    .build()

WorkManager.getInstance(context).enqueue(workRequest)

// Periodic work (minimum 15 minutes)
val periodicWork = PeriodicWorkRequestBuilder<NotificationWorker>(
    15, TimeUnit.MINUTES
).build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "notification_work",
    ExistingPeriodicWorkPolicy.KEEP,
    periodicWork
)
```

---

## 🎯 Common Tasks & Where to Look

| Task | Files to Check |
|------|----------------|
| Add a new screen | `presentation/` + `NeuroFlowApp.kt` (navigation) |
| Add a database table | `data/local/entity/` + `NeuroFlowDatabase.kt` |
| Add a database query | `data/local/dao/` |
| Change app colors | `presentation/common/theme/Color.kt` |
| Add a background task | `worker/` + `NeuroFlowApplication.kt` |
| Add a notification | `worker/NotificationWorker.kt` |
| Change app icon | `app/src/main/res/mipmap/` |
| Add a setting | `UserPreferencesDataStore.kt` + `presentation/settings/` |
| Modify business logic | `domain/engine/` or `domain/scheduler/` |
| Add a dependency | `app/build.gradle.kts` |

---

## 🐛 Common Issues & Solutions

### Issue: "Unresolved reference"
**Solution**: Sync Gradle files (File → Sync Project with Gradle Files)

### Issue: "Cannot access database on main thread"
**Solution**: Use `suspend` functions or `Flow` in DAO

### Issue: "lateinit property has not been initialized"
**Solution**: Check Hilt injection is set up correctly

### Issue: Compose not recomposing
**Solution**: Make sure you're using `State` or `StateFlow`

### Issue: App crashes on launch
**Solution**: Check Logcat for stack trace

---

## 📚 Essential Keyboard Shortcuts (Android Studio)

| Action | Shortcut (Windows/Linux) | Shortcut (Mac) |
|--------|-------------------------|----------------|
| Search everywhere | Double Shift | Double Shift |
| Find class | Ctrl + N | Cmd + O |
| Find file | Ctrl + Shift + N | Cmd + Shift + O |
| Go to declaration | Ctrl + B | Cmd + B |
| Find usages | Alt + F7 | Opt + F7 |
| Rename | Shift + F6 | Shift + F6 |
| Format code | Ctrl + Alt + L | Cmd + Opt + L |
| Run app | Shift + F10 | Ctrl + R |
| Debug app | Shift + F9 | Ctrl + D |

---

## 🎓 Learning Resources Quick Links

- **Kotlin**: https://kotlinlang.org/docs/home.html
- **Compose**: https://developer.android.com/jetpack/compose/tutorial
- **Room**: https://developer.android.com/training/data-storage/room
- **Hilt**: https://developer.android.com/training/dependency-injection/hilt-android
- **Coroutines**: https://kotlinlang.org/docs/coroutines-guide.html
- **WorkManager**: https://developer.android.com/topic/libraries/architecture/workmanager

---

## 💡 Pro Tips

1. **Use Android Studio's "Find Usages"** (Alt+F7) to see where a function/class is used
2. **Use "Go to Declaration"** (Ctrl+B) to jump to definitions
3. **Enable auto-import** in Settings → Editor → General → Auto Import
4. **Use Logcat filters** to reduce noise
5. **Install Kotlin plugin** for better syntax highlighting
6. **Use Database Inspector** to view Room database in real-time
7. **Use Layout Inspector** to debug Compose UI hierarchy
8. **Read error messages carefully** - they often tell you exactly what's wrong

---

**Happy Coding! 🚀**
