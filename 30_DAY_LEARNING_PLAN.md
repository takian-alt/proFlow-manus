# 📅 30-Day Learning Plan for NeuroFlow Codebase

## 🎯 Goal
By the end of 30 days, you'll understand the architecture, be able to read and modify the code, and implement simple features.

---

## 📊 Week 1: Kotlin & Android Fundamentals

### Day 1: Setup & Kotlin Basics
- [ ] Install Android Studio
- [ ] Clone and build the project
- [ ] Run the app on emulator/device
- [ ] Complete Kotlin basics: variables, functions, classes
- **Resource**: [Kotlin Basics](https://kotlinlang.org/docs/basic-syntax.html)

### Day 2: Kotlin Data Classes & Null Safety
- [ ] Learn about data classes
- [ ] Understand null safety (?, !!, ?:)
- [ ] Practice with examples
- **Exercise**: Create a simple data class for a Person with nullable fields

### Day 3: Kotlin Collections & Lambdas
- [ ] Learn List, Set, Map
- [ ] Understand lambda expressions
- [ ] Practice filter, map, forEach
- **Exercise**: Filter a list of numbers to get only even numbers

### Day 4: Kotlin Coroutines Basics
- [ ] Understand suspend functions
- [ ] Learn about launch and async
- [ ] Understand coroutine scopes
- **Resource**: [Coroutines Guide](https://kotlinlang.org/docs/coroutines-basics.html)

### Day 5: Kotlin Flow
- [ ] Understand Flow basics
- [ ] Learn about StateFlow and SharedFlow
- [ ] Practice collecting flows
- **Exercise**: Create a simple Flow that emits numbers

### Day 6: Android Activity Lifecycle
- [ ] Learn Activity lifecycle methods
- [ ] Understand onCreate, onStart, onResume, onPause, onStop
- [ ] Read about Context
- **Resource**: [Activity Lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)

### Day 7: Review & Practice
- [ ] Review all concepts from Week 1
- [ ] Complete coding exercises
- [ ] Read through Kotlin documentation
- **Exercise**: Build a simple console app using coroutines

---

## 📱 Week 2: Jetpack Compose & App Entry Points

### Day 8: Compose Basics
- [ ] Learn @Composable functions
- [ ] Understand Column, Row, Box
- [ ] Learn about Modifiers
- **Resource**: [Compose Tutorial](https://developer.android.com/jetpack/compose/tutorial)

### Day 9: Compose State Management
- [ ] Learn remember and mutableStateOf
- [ ] Understand recomposition
- [ ] Practice with simple counters
- **Exercise**: Build a counter app in Compose

### Day 10: Read NeuroFlowApplication.kt
- [ ] Open `NeuroFlowApplication.kt`
- [ ] Read through the entire file
- [ ] Understand what happens in onCreate()
- [ ] Note down questions
- **Questions to answer**:
  - What is @HiltAndroidApp?
  - What workers are scheduled?
  - What is applicationScope?

### Day 11: Read MainActivity.kt
- [ ] Open `MainActivity.kt`
- [ ] Understand the onCreate() method
- [ ] See how Compose is set up with setContent
- [ ] Understand intent handling
- **Questions to answer**:
  - How does the app handle deep links?
  - How is the theme applied?
  - What is enableEdgeToEdge()?

### Day 12: Trace App Launch Flow
- [ ] Start from NeuroFlowApplication.onCreate()
- [ ] Follow to MainActivity.onCreate()
- [ ] See how NeuroFlowApp is rendered
- [ ] Understand the navigation setup
- **Exercise**: Add a Log statement in onCreate() and see it in Logcat

### Day 13: Explore Compose Layouts
- [ ] Study LazyColumn and LazyRow
- [ ] Learn about Scaffold
- [ ] Understand TopAppBar and BottomNavigation
- **Exercise**: Create a simple list screen

### Day 14: Review & Mini Project
- [ ] Review Week 2 concepts
- [ ] Build a simple multi-screen Compose app
- **Exercise**: Create an app with 2 screens and navigation

---

## 🗄️ Week 3: Data Layer (Database & Storage)

### Day 15: Room Database Basics
- [ ] Learn about @Entity, @Dao, @Database
- [ ] Understand primary keys and columns
- [ ] Read Room documentation
- **Resource**: [Room Guide](https://developer.android.com/training/data-storage/room)

### Day 16: Read NeuroFlowDatabase.kt
- [ ] Open `NeuroFlowDatabase.kt`
- [ ] See all entities (tables)
- [ ] Understand database version
- [ ] Note the DAOs
- **Questions to answer**:
  - How many tables are there?
  - What is the current database version?
  - What are TypeConverters?

### Day 17: Study TaskEntity.kt
- [ ] Open `data/local/entity/TaskEntity.kt`
- [ ] Understand each field
- [ ] See the annotations
- [ ] Understand the data structure
- **Exercise**: Draw the table structure on paper

### Day 18: Study TaskDao.kt
- [ ] Open `data/local/dao/TaskDao.kt`
- [ ] Read all query methods
- [ ] Understand @Insert, @Update, @Delete
- [ ] See how Flow is used
- **Questions to answer**:
  - What queries return Flow?
  - What queries are suspend functions?
  - How do you get all tasks?

### Day 19: Study TaskRepository.kt
- [ ] Open `data/repository/TaskRepository.kt`
- [ ] Understand the repository pattern
- [ ] See how it uses TaskDao
- [ ] Understand dependency injection
- **Questions to answer**:
  - Why use a repository?
  - How does it transform data?
  - What is @Inject?

### Day 20: Study UserPreferencesDataStore.kt
- [ ] Open `UserPreferencesDataStore.kt`
- [ ] Understand DataStore vs SharedPreferences
- [ ] See how preferences are stored
- [ ] Understand Flow-based updates
- **Exercise**: Add a new preference field (on paper)

### Day 21: Review & Practice
- [ ] Review all data layer concepts
- [ ] Trace a data flow: DAO → Repository → ViewModel → UI
- **Exercise**: Write pseudo-code for adding a new entity

---

## 🧠 Week 4: Domain & Presentation Layers

### Day 22: Study Domain Models
- [ ] Open `domain/model/Task.kt`
- [ ] Compare with TaskEntity
- [ ] Understand the difference
- [ ] Read about Quadrant enum
- **Questions to answer**:
  - Why have both Task and TaskEntity?
  - What is the Eisenhower Matrix?

### Day 23: Study Business Logic Engines
- [ ] Open `domain/engine/FreshStartEngine.kt`
- [ ] Understand the business logic
- [ ] See how it calculates weekly resets
- [ ] Read other engines
- **Exercise**: Trace how FreshStartEngine is used in MainActivity

### Day 24: Study NeuroFlowApp.kt (Navigation)
- [ ] Open `presentation/common/NeuroFlowApp.kt`
- [ ] Understand NavHost and NavController
- [ ] See all navigation routes
- [ ] Understand bottom navigation
- **Questions to answer**:
  - How many screens are there?
  - How do you navigate between screens?

### Day 25: Study MatrixScreen.kt
- [ ] Open `presentation/matrix/MatrixScreen.kt`
- [ ] Read the Composable function
- [ ] Understand the UI structure
- [ ] See how tasks are displayed
- **Exercise**: Identify all Composable functions used

### Day 26: Study MatrixViewModel.kt
- [ ] Open `presentation/matrix/MatrixViewModel.kt`
- [ ] Understand @HiltViewModel
- [ ] See how StateFlow is used
- [ ] Understand viewModelScope
- **Questions to answer**:
  - How does the ViewModel get the repository?
  - How is state exposed to the UI?
  - What happens in init block?

### Day 27: Trace Complete Feature Flow
- [ ] Pick the "Task List" feature
- [ ] Trace from UI → ViewModel → Repository → DAO → Database
- [ ] Understand data flow in both directions
- **Exercise**: Draw a diagram of the data flow

### Day 28: Study Dependency Injection
- [ ] Open `di/AppModule.kt`
- [ ] Understand @Module and @Provides
- [ ] See how dependencies are created
- [ ] Understand singleton scope
- **Questions to answer**:
  - How is the database provided?
  - What is @InstallIn?
  - How does Hilt know what to inject?

---

## 🚀 Week 5: Advanced Topics & Practice

### Day 29: Study Background Workers
- [ ] Open `worker/NotificationWorker.kt`
- [ ] Understand WorkManager
- [ ] See how workers are scheduled
- [ ] Read about @HiltWorker
- **Exercise**: Trace how NotificationWorker is scheduled in NeuroFlowApplication

### Day 30: Build a Simple Feature
- [ ] Plan a simple feature (e.g., add a note field to tasks)
- [ ] Identify all files you need to modify
- [ ] Make the changes
- [ ] Test the feature
- **Exercise**: Add a "description" field to tasks
  1. Add to TaskEntity
  2. Update TaskDao (if needed)
  3. Update UI to display it
  4. Test!

---

## 📝 Daily Study Template

Use this template for each day:

```
Date: ___________
Day: ___________

✅ Tasks Completed:
- [ ]
- [ ]
- [ ]

📚 What I Learned:
-
-
-

❓ Questions I Have:
-
-

💡 Aha Moments:
-
-

🔗 Resources Used:
-
-

⏰ Time Spent: _____ hours

📊 Confidence Level (1-10): _____
```

---

## 🎯 Learning Milestones

### Week 1 Milestone
- [ ] Can write basic Kotlin code
- [ ] Understand coroutines and Flow
- [ ] Know Android Activity lifecycle

### Week 2 Milestone
- [ ] Can create simple Compose UIs
- [ ] Understand app entry points
- [ ] Can trace app launch flow

### Week 3 Milestone
- [ ] Understand Room database
- [ ] Can read and understand DAOs
- [ ] Know the repository pattern

### Week 4 Milestone
- [ ] Understand complete data flow
- [ ] Can read ViewModels
- [ ] Know dependency injection basics

### Week 5 Milestone
- [ ] Can modify existing features
- [ ] Understand background work
- [ ] Can implement simple features

---

## 🏆 Final Project Ideas

After 30 days, try one of these:

### Project 1: Add a Priority Field
1. Add `priority: Int` to TaskEntity
2. Create a database migration
3. Update UI to show priority
4. Add ability to set priority

### Project 2: Add a Search Feature
1. Add search query to TaskDao
2. Create a search UI
3. Connect to ViewModel
4. Display filtered results

### Project 3: Add a New Screen
1. Create a new Composable screen
2. Add to navigation graph
3. Create a ViewModel
4. Display some data

### Project 4: Add a Background Task
1. Create a new Worker
2. Schedule it in NeuroFlowApplication
3. Make it do something useful
4. Test it works

---

## 📊 Progress Tracker

### Week 1: Fundamentals
- Day 1: ⬜ Day 2: ⬜ Day 3: ⬜ Day 4: ⬜ Day 5: ⬜ Day 6: ⬜ Day 7: ⬜

### Week 2: Compose & Entry Points
- Day 8: ⬜ Day 9: ⬜ Day 10: ⬜ Day 11: ⬜ Day 12: ⬜ Day 13: ⬜ Day 14: ⬜

### Week 3: Data Layer
- Day 15: ⬜ Day 16: ⬜ Day 17: ⬜ Day 18: ⬜ Day 19: ⬜ Day 20: ⬜ Day 21: ⬜

### Week 4: Domain & Presentation
- Day 22: ⬜ Day 23: ⬜ Day 24: ⬜ Day 25: ⬜ Day 26: ⬜ Day 27: ⬜ Day 28: ⬜

### Week 5: Advanced & Practice
- Day 29: ⬜ Day 30: ⬜

---

## 💡 Study Tips

### 1. **Don't Skip Days**
Consistency is key. Even 30 minutes is better than nothing.

### 2. **Take Notes**
Write down what you learn. It helps retention.

### 3. **Ask Questions**
Use Stack Overflow, Reddit, or AI assistants when stuck.

### 4. **Run the Code**
Don't just read - run the app and see how it works.

### 5. **Make Small Changes**
Add Log statements, change colors, modify text - experiment!

### 6. **Use Debugger**
Set breakpoints and step through code to understand flow.

### 7. **Draw Diagrams**
Visual representations help understand architecture.

### 8. **Review Regularly**
Spend 10 minutes each day reviewing previous concepts.

### 9. **Join Communities**
- r/androiddev on Reddit
- Kotlin Slack
- Android Discord servers

### 10. **Be Patient**
This is a complex codebase. It's okay to not understand everything immediately.

---

## 🎓 Recommended Daily Schedule

### Option 1: Morning Study (2 hours)
- 7:00 - 7:30: Review previous day's notes
- 7:30 - 8:30: Learn new concepts (videos/docs)
- 8:30 - 9:00: Practice with code

### Option 2: Evening Study (2 hours)
- 7:00 - 8:00: Learn new concepts
- 8:00 - 8:45: Read and understand code
- 8:45 - 9:00: Review and take notes

### Option 3: Split Study (1 hour morning + 1 hour evening)
- Morning: Theory and documentation
- Evening: Hands-on coding and practice

---

## 📚 Essential Bookmarks

Save these links:

1. **Kotlin Docs**: https://kotlinlang.org/docs/home.html
2. **Android Docs**: https://developer.android.com/
3. **Compose Docs**: https://developer.android.com/jetpack/compose
4. **Room Guide**: https://developer.android.com/training/data-storage/room
5. **Hilt Guide**: https://developer.android.com/training/dependency-injection/hilt-android
6. **Stack Overflow**: https://stackoverflow.com/questions/tagged/android
7. **Reddit r/androiddev**: https://reddit.com/r/androiddev

---

## 🎉 Completion Certificate

After completing all 30 days, fill this out:

```
🎓 CERTIFICATE OF COMPLETION 🎓

I, _________________, have successfully completed
the 30-Day NeuroFlow Codebase Learning Plan.

Start Date: ___________
End Date: ___________

Key Achievements:
✅ Learned Kotlin fundamentals
✅ Mastered Jetpack Compose basics
✅ Understood Room database
✅ Learned Clean Architecture
✅ Implemented a feature

Next Steps:
- [ ] Build a personal project
- [ ] Contribute to open source
- [ ] Learn advanced topics
- [ ] Apply for Android developer positions

Signed: ________________
Date: ___________
```

---

**You've got this! 🚀 Start with Day 1 and take it one day at a time!**

Remember: Every expert was once a beginner. The only difference is they didn't give up.
