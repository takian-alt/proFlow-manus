# proFlow (NeuroFlow)

> **Science-backed Android task prioritization — because not all tasks are created equal.**

proFlow is an advanced productivity and task management application for Android, built around neuroscience and behavioral psychology principles. It intelligently scores and ranks your tasks using a composite engine that draws on 16+ evidence-based frameworks so you always know what to work on next.

---

## Download

Download the latest release APK from the [GitHub Releases page](https://github.com/takian-alt/proFlow/releases/tag/version_5.0.0).

| Version | Min Android | Release |
|---------|-------------|---------|
| **v5.0.0** (latest) | Android 8.0 (API 26) | [⬇ GitHub Releases](https://github.com/takian-alt/proFlow/releases/tag/version_4.0.0) |

> **Install tip:** You will need to enable *Install from unknown sources* in your Android settings before side-loading the APK.

---

## Features

- **Focus Launcher** — a full Android home-screen replacement purpose-built for deep work; surfaces your top-priority task front and centre, integrates with the neuroscience scoring engine, and removes friction between intention and action
- **Eisenhower Matrix** — visualize tasks across the classic four-quadrant do/schedule/delegate/eliminate grid
- **Smart Scoring Engine** — composite priority scores (0–1000) driven by Temporal Motivation Theory, Cognitive Load Theory, Circadian Rhythm research, the Zeigarnik Effect, BJ Fogg's Tiny Habits, and more
- **Focus Timer** — built-in Pomodoro-style focus sessions with per-task time tracking and post-session WOOP reflection
- **Schedule View** — day and week timeline with locked-slot scheduling and work-hour awareness
- **Analytics Dashboard** — completion rates, productivity streaks, goal progress, Vico-powered 7-day trend charts, XP/gamification points, Neuro Boost Insights (frog tasks, anxiety tasks, public commitments), Procrastination Radar, and Dynamic Peak Energy detection
- **Task History** — searchable archive of completed tasks with full audit trail
- **Goal Linking** — attach tasks to long-term goals and track goal risk exposure; manage yearly and weekly goals from the navigation drawer
- **Background Intelligence** — WorkManager workers for daily planning notifications (7 am), streak checks (9 pm), automatic deadline escalation (every 4 hours), autonomy nudges, Ulysses Contract evaluation, and home screen widget updates; all workers are rescheduled automatically on device reboot
- **Onboarding Flow** — guided first-launch setup for identity, peak energy hours, and first task
- **Identity Affirmations** — dedicated screen for composing and managing personal identity statements (e.g. "I finish what I start"); affirmations are persisted and used as self-concept anchors throughout the app
- **WOOP Framework** — structured Wish-Outcome-Obstacle-Plan prompts before focus sessions and a mini reflection screen after; affective forecasting tracks expectation vs. reality over time
- **Ulysses Contracts** — create binding commitment contracts for critical tasks; outcome (win/loss) is automatically evaluated at the deadline
- **Autonomy Nudge System** — if a task goes untouched for 2 hours a smart notification fires with action buttons: *Not ready yet* (snooze) and *It feels too big* (auto-split into subtasks)
- **Task Splitter** — automatically breaks an oversized task into three equal subtasks inheriting quadrant, priority, and a third of the impact score; the parent task is archived
- **Fresh Start Engine** — detects motivational fresh-start moments (Monday, month start, streak break, 3-day+ absence) and surfaces an encouraging prompt, at most once per ISO week
- **Peak Energy Detector** — learns your personal focus windows from session history using exponential recency weighting; blends with manually configured peak hours
- **Manual Time Logging** — log time spent on a task outside of timed focus sessions via a dedicated bottom sheet and log-time screen
- **Affordance Rating** — after a focus session rate how difficult the task felt vs. your expectation; data feeds into WOOP calibration insights
- **HyperFocus Mode** — whole-phone distraction blocker: select apps to block, set a task target, and activate HyperFocus; blocked apps show a motivational overlay until you hit your goal; exit requires a time-limited AES-encrypted unlock code; backed by biometric app lock and escalating reward tiers
- **Distraction Engine** — correlates Android `UsageStatsManager` data with focus sessions to compute a per-app distraction score (0–100); accounts for app-switch frequency, per-app category weight (social media → messaging → email), interruption depth, recovery time, and circadian timing; scores surface in Launcher Settings and feed the `TaskScoringEngine`
- **App Drawer** — full-screen searchable app drawer with locale-aware alphabetical grouping, swipe-up gesture trigger, and instant filtering powered by `LauncherSearchEngineImpl`
- **App Folders** — long-press any home-screen icon to create or edit folders; folders expand into an animated overlay grid and are persisted in `PinnedAppsDataStore`
- **Dock** — configurable 5-slot bottom dock with folder support, long-press context menu (open/lock/hide/folder/info), and auto-populated defaults on first launch
- **Multi-page Home Screen** — paged app grid (configurable columns/rows), page-dot indicator, drag-and-drop reordering, and widget slots on any page
- **Focus Task Card** — always-visible card on the home screen showing the current top-scored task with its score, quadrant badge, and a one-tap "Start Focus" button; supports *Skip* to advance to the next task without completing it
- **Habit Quick Row** — horizontal row of today's due habits surfaced directly on the home screen for frictionless habit check-in
- **Quick Stats Panel** — swipe-right overlay showing today's completed task count, active streak, current XP, and recent session totals without leaving the launcher
- **Launcher Settings** — comprehensive settings sheet covering distraction scores, dock management, icon packs, icon shapes, grid size, clock style, notification badges, biometric app lock, backup / restore, and HyperFocus configuration
- **Icon Pack Support** — queries all installed icon packs at startup via `IconPackManager`; resolved icons are cached and applied per `IconShape` through `AdaptiveIconProcessor`
- **Notification Badges** — `NotificationBadgeService` (NotificationListenerService) tracks unread counts; `NotificationBadgeManager` exposes them as a `StateFlow` consumed by every `AppIcon` composable
- **Biometric App Lock** — individual apps can be locked via `BiometricAppLock` (BiometricPrompt); launch attempts are intercepted and prompt authentication before forwarding the intent
- **Launcher Backup / Restore** — `LauncherBackupManager` serialises the full launcher state (pinned apps, dock, folders, distraction scores, preferences) to JSON for export/import
- **Theming** — Material 3 dark/light theme with user-configurable priority weights

---

## Screenshots

_Full screenshot gallery coming with the first public release. Key screens include the Eisenhower Matrix, Schedule, Focus Timer with WOOP prompts, Analytics Dashboard, Identity Setup, and the home screen widget._

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose 2.8.5 |
| Architecture | MVVM, Clean Architecture |
| DI | Hilt 2.51.1 |
| Database | Room 2.6.1 (SQLite, 10 migrations, schema v11) |
| Preferences | Preferences DataStore |
| Background | WorkManager |
| Charts | Vico |
| Build | Gradle 8.x with Kotlin DSL, KSP |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |
| App Blocking | AccessibilityService + UsageStatsManager |
| Icon Packs | Custom icon-pack resolution via `IconPackManager` |
| Biometrics | AndroidX BiometricPrompt |
| Encryption | AES-256 (unlock codes via `AESUtil`) |

---

## Architecture

```
app/
└── src/main/java/com/neuroflow/app/
    ├── di/                    # Hilt dependency injection modules
    ├── domain/
    │   ├── engine/            # TaskScoringEngine, AnalyticsEngine,
    │   │                      #   AutonomyNudgeEngine, FreshStartEngine,
    │   │                      #   WoopEngine, TaskSplitter, PeakEnergyDetector
    │   └── model/             # Enums (Quadrant, Priority, TaskStatus …)
    ├── data/
    │   ├── local/             # Room database, DAOs, entities (incl.
    │   │                      #   UlyssesContractEntity, WoopEntity), DataStore
    │   └── repository/        # Repository pattern over DAOs (incl.
    │                          #   UlyssesContractRepository, WoopRepository)
    ├── presentation/
    │   ├── common/            # Navigation scaffold, shared composables, theme,
    │   │                      #   UlyssesContractSheet, ManualTimeLogSheet,
    │   │                      #   NewChapterCard, TopGoalsRefillCard,
    │   │                      #   DrawerGoalsSection
    │   ├── matrix/            # Eisenhower Matrix screen
    │   ├── schedule/          # Day/week schedule screen
    │   ├── focus/             # Focus timer screen, WoopPromptSheet,
    │   │                      #   MiniWoopReflectionScreen, AffordanceRatingSheet
    │   ├── analytics/         # Analytics screen
    │   ├── history/           # Completed tasks screen
    │   ├── identity/          # Identity affirmations screen
    │   ├── logtime/           # Manual time log screen
    │   ├── settings/          # Settings & priority weights screens
    │   ├── onboarding/        # Onboarding & app guide screens
    │   ├── widget/            # Home screen widget (RemoteViews AppWidget)
    │   └── launcher/          # Focus Launcher (home-screen replacement)
    │       ├── LauncherActivity.kt        # HOME intent-filter activity
    │       ├── LauncherViewModel.kt       # Central launcher state (StateFlow)
    │       ├── HomeScreen.kt              # Paged home screen + widget slots
    │       ├── SafeHomeScreen.kt          # Crash-recovery fallback screen
    │       ├── components/                # AppIcon, DateTimeDisplay, DockRow,
    │       │                              #   FocusTaskCard, HabitQuickRow,
    │       │                              #   HomeScreenGrid, HomeScreenPages,
    │       │                              #   ShortcutPopup, WidgetHost, WidgetSlotRow
    │       ├── data/                      # AppRepository, PinnedAppsDataStore,
    │       │                              #   IconPackManager, LauncherBackupManager,
    │       │                              #   NotificationBadgeManager/Service,
    │       │                              #   PackageChangeReceiver
    │       ├── di/                        # LauncherModule (Hilt bindings)
    │       ├── domain/                    # AdaptiveIconProcessor, AppWidgetHostWrapper,
    │       │                              #   BiometricAppLock, LauncherGestureHandler,
    │       │                              #   LauncherModels, LauncherSearchEngine(Impl)
    │       ├── drawer/                    # AppDrawer (searchable full-screen drawer)
    │       ├── folder/                    # AppFolderOverlay
    │       ├── hyperfocus/                # HyperFocus distraction-blocking subsystem
    │       │   ├── HyperFocusActivity.kt
    │       │   ├── HyperFocusViewModel.kt
    │       │   ├── components/            # HyperFocusStatusBar
    │       │   ├── data/                  # HyperFocusDataStore, HyperFocusSession/
    │       │   │                          #   UnlockCode DAOs & Entities
    │       │   ├── domain/                # AESUtil, HyperFocusManager,
    │       │   │                          #   RewardEngine, UnlockCodeRepository
    │       │   ├── screens/               # BlockingOverlayScreen, CodeEntryScreen,
    │       │   │                          #   HyperFocusActivationSheet,
    │       │   │                          #   PermissionSetupScreen, PlanningPromptScreen,
    │       │   │                          #   RewardSection, RewardsScreen
    │       │   ├── service/               # AppBlockingService (AccessibilityService),
    │       │   │                          #   HyperFocusMonitorService, UnlockTimerService
    │       │   └── util/                  # AccessibilityUtil
    │       ├── settings/                  # LauncherSettings composable
    │       ├── stats/                     # QuickStatsPanel
    │       └── theme/                     # LauncherTheme
    ├── worker/                # WorkManager background workers (incl.
    │                          #   AutonomyNudgeWorker, UlyssesEvaluatorWorker,
    │                          #   FocusWidgetUpdateWorker, NotificationWorker,
    │                          #   AccessibilityWatchdogWorker, DistractionSyncWorker)
    └── receiver/              # BroadcastReceivers: BootReceiver,
                               #   NudgeSnoozeReceiver, PackageChangeReceiver
```

Data flows **unidirectionally**: Compose UI → ViewModel (StateFlow) → Repository → Room / DataStore.

---

## Getting Started

### Prerequisites

- Android Studio Koala (2024.1.1) or later
- JDK 17
- Android SDK with API level 26–35

### Build

```bash
# Clone the repository
git clone https://github.com/takian-alt/proFlow.git
cd proFlow

# Build a debug APK
./gradlew assembleDebug

# Run all tests
./gradlew test
```

Open the project in Android Studio, allow Gradle to sync, then run the **app** configuration on an emulator or physical device (Android 8.0+).

### Install a debug APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## How to Best Use proFlow

New to proFlow? Read the **[Usage Guide](USAGE_GUIDE.md)** for a step-by-step walkthrough covering daily workflows, advanced features, and tips for getting the most out of every neuroscience-backed feature.

---

## Contributing

We welcome contributions of all sizes! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a full history of releases and changes.

---

## Security

If you discover a security vulnerability, please follow the responsible disclosure process described in [SECURITY.md](SECURITY.md).

---

## Code of Conduct

This project is governed by the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating you agree to uphold its terms.

---

## License

Distributed under the [MIT License](LICENSE).
