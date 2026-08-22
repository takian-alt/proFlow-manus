# Changelog

All notable changes to proFlow are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added

**Focus Launcher (PR #6 — feature/launcher)**

The app can now be set as the Android home screen. The launcher is intentionally minimal — every pixel is either helping you focus or getting out of your way.

*Core launcher*
- `LauncherActivity` — `FragmentActivity` registered with `MAIN / HOME` intent filters; `singleTask` + empty task affinity; transparent theme to show system wallpaper; Dynamic Color on API 31+; crash-recovery fallback via `SafeHomeScreen`
- `LauncherViewModel` — central `StateFlow`-based state holder for all launcher state: top task, apps, dock, folders, distraction scores, HyperFocus preferences, widget slots, notification badges, and user preferences
- `HomeScreen` / `HomeScreenPages` — paged app grid (configurable columns × rows) with drag-and-drop reordering, page-dot indicator, and per-page widget slots
- `FocusTaskCard` — always-visible card showing the top-scored task (score badge, quadrant badge, *Start Focus* and *Skip* buttons); integrated with `TaskScoringEngine`
- `HabitQuickRow` — horizontal row of today's due habits for one-tap check-in without leaving the home screen
- `DockRow` — configurable 5-slot bottom dock; supports individual apps and folders; long-press context menu (open / lock / hide / move to folder / app info); auto-populates with common apps on first launch
- `AppIcon` — unified app icon composable with adaptive icon support, notification badge overlay, and configurable `IconShape` masking
- `DateTimeDisplay` — clock and date header with Digital and Minimal styles
- `ShortcutPopup` — long-press popup for per-icon actions (open, move, lock, hide, add to folder, app info)
- `WidgetHost` / `WidgetSlotRow` — embed Android `AppWidget`s in home screen page slots using `AppWidgetHostWrapper`
- `HomeScreenGrid` — virtual grid layout managing item positions and drag-drop state
- `QuickStatsPanel` — swipe-right overlay showing today's completed tasks, active streak, XP, and recent session totals

*App Drawer*
- `AppDrawer` — full-screen searchable app drawer; locale-aware alphabetical section headers; swipe-up gesture trigger; powered by `LauncherSearchEngineImpl`

*App Folders*
- `AppFolderOverlay` — animated full-screen folder overlay; long-press any icon to create / edit a folder; folder definitions persisted in `PinnedAppsDataStore`

*HyperFocus distraction-blocking subsystem*
- `HyperFocusManager` (`HyperFocusManagerImpl`) — activates / deactivates HyperFocus sessions: generates an AES-256 code pool via `AESUtil`, persists session state in `HyperFocusDataStore`, starts `AppBlockingService` + `HyperFocusMonitorService` + `UnlockTimerService`, and cancels `AccessibilityWatchdogWorker` on deactivation
- `AppBlockingService` — `AccessibilityService` that intercepts `TYPE_WINDOW_STATE_CHANGED` events; redirects blocked apps to `BlockingOverlayScreen`; exempts the launcher itself and system UI
- `HyperFocusMonitorService` — foreground service that periodically checks HyperFocus state and re-arms the block if the session is tampered with
- `UnlockTimerService` — foreground service that counts down time-limited unlock windows and auto-reactivates blocking when the window expires
- `HyperFocusActivity` — dedicated activity hosting HyperFocus screens; handles biometric re-authentication on resume
- `HyperFocusViewModel` — manages activation, code submission, heartbeat, tamper detection, and reward state
- `HyperFocusStatusBar` — persistent status bar composable shown on all launcher screens during an active HyperFocus session
- `HyperFocusActivationSheet` — bottom sheet for selecting blocked apps, daily task target, and locked task IDs before activating HyperFocus
- `PermissionSetupScreen` — guides the user through granting Accessibility and Usage Stats permissions required for app blocking
- `BlockingOverlayScreen` — full-screen overlay shown when a blocked app is launched; displays motivational messaging and a *Get back to work* action
- `CodeEntryScreen` — AES-256 code entry screen that gates time-limited unlock windows; enforces lockout after repeated failures
- `PlanningPromptScreen` — pre-session planning prompt (WOOP-style) surfaced before activating HyperFocus
- `RewardsScreen` / `RewardSection` — displays earned reward tiers and streak progress; `RewardEngine` assigns Bronze / Silver / Gold / Platinum tiers based on completed tasks and focus time
- `UnlockCodeRepository` — manages the AES-encrypted unlock code pool in Room; enforces per-session code rotation and expiry

*Distraction Engine*
- `DistractionEngine` — correlates `UsageStatsManager` data with focus sessions via five scoring layers: (1) app-switch frequency (logarithmic), (2) per-app distraction weight (social media → messaging → video → email), (3) interruption depth, (4) recovery-time penalty, (5) circadian multiplier; exposes `computeDistractionScore(task, sessions, peakHours)` returning a normalised 0–100 score

*Data*
- `AppRepository` — loads installed apps via `LauncherApps`, resolves labels and icons with memory/disk caching, exposes a `StateFlow<List<AppInfo>>`; forwards `onTrimMemory` from `LauncherActivity`
- `PinnedAppsDataStore` — Proto DataStore holding the full launcher configuration: dock packages, home screen pages (items + positions), folder definitions, hidden/locked packages, distraction scores, clock style, icon shape, grid size, and HyperFocus package selections
- `IconPackManager` — queries installed icon packs via `PackageManager`; resolves drawable resources for a given package name; caches results
- `LauncherBackupManager` — serialises / deserialises the full launcher state to JSON for export and import; versioned with `BackupMetadata`
- `NotificationBadgeManager` — exposes unread badge counts as `StateFlow<Map<String, Int>>`
- `NotificationBadgeService` — `NotificationListenerService` that maintains a live count of unread notifications per package
- `PackageChangeReceiver` — `BroadcastReceiver` for `PACKAGE_ADDED` / `PACKAGE_REMOVED` / `PACKAGE_CHANGED`; triggers `AppRepository` refresh
- `HyperFocusDataStore` / `HyperFocusSessionEntity` / `HyperFocusSessionDao` — persists HyperFocus session history
- `UnlockCodeEntity` / `UnlockCodeDao` — Room table for the AES-encrypted unlock code pool

*Domain*
- `AdaptiveIconProcessor` — renders adaptive icons into a masked `Bitmap` for any `IconShape`
- `AppWidgetHostWrapper` — thin wrapper around `AppWidgetHost` exposing lifecycle-safe `startListening` / `stopListening`
- `BiometricAppLock` — wraps `BiometricPrompt` to authenticate before forwarding a blocked-app launch intent
- `LauncherGestureHandler` — detects swipe-up (open drawer), swipe-down (expand notifications), and long-press (enter edit mode) gestures on the home screen
- `LauncherSearchEngineImpl` — `Trie`-backed fuzzy search over app names and package names; updated on every `AppRepository` emission

*Settings*
- `LauncherSettings` — full-screen settings sheet:
  - Per-app distraction score sliders (0–100 integer, default 50, persisted immediately)
  - *Quick Categorize* flow that bulk-assigns sensible defaults by category
  - Dock editor, hidden apps management, locked apps (biometric) management
  - Clock style selector (Digital / Minimal)
  - Card transparency slider
  - Icon pack picker
  - Icon shape selector (Circle / Squircle / Rounded-Square / Teardrop / System Default)
  - Home-screen grid size (3 × 5 to 6 × 8)
  - Notification badges toggle
  - Show task score toggle
  - Web search URL configuration
  - Backup export and import buttons
  - HyperFocus activation entry point
  - Rewards navigation link

*Background workers (new)*
- `AccessibilityWatchdogWorker` — periodic WorkManager task that verifies the `AppBlockingService` accessibility service is still enabled; re-prompts the user if it was revoked
- `DistractionSyncWorker` — periodic WorkManager task that runs `DistractionEngine` over recent sessions and writes updated distraction scores back to `PinnedAppsDataStore`

*Theme*
- `LauncherTheme` — Material 3 `ColorScheme` tailored for the dark, minimal launcher aesthetic; respects system Dynamic Color on API 31+

*Database*
- Schema versions 9–11: added `HyperFocusSessionEntity` and `UnlockCodeEntity` tables with full Room migrations

*Tests*
- `LauncherSearchEngineTest` — 282-line property-based test suite covering prefix matching, special-character handling, empty queries, and concurrent search calls
- `LauncherBackupManagerTest` — round-trip serialisation tests for backup / restore
- `PinnedAppsDataStoreBugExplorationTest` — regression tests for concurrent write races
- `HomeScreenGridRenderingBugTest` — rendering edge-case coverage (empty pages, single item, max-capacity grid)
- `ShortcutPopupTest` — composable interaction tests for the long-press context menu
- `AESUtilTest` — encryption / decryption correctness and tamper-detection tests
- `HyperFocusManagerTest` — unit tests for activate / deactivate / submit-code lifecycle
- `RewardEngineTest` — tier assignment boundary tests
- `UnlockCodeRepositoryTest` — code generation, expiry, and lockout enforcement tests
- `LauncherIntegrationTest` — 477-line end-to-end integration test suite
- `WorkProfileIntegrationTest` — work-profile app enumeration and launch tests
- `LauncherBaselineProfileGenerator` — Macrobenchmark baseline profile for launcher startup and scrolling
- `TimeUtilsTest` — unit tests for the new `TimeUtils` helpers added to support the launcher

*Scripts*
- `scripts/check-launcher-isolation.sh` — verifies the launcher module does not leak imports into the main app module
- `scripts/generate-baseline-profile.sh` — automates baseline profile generation via Macrobenchmark

**Neuroscience & Behavioral Psychology Engines**
- `AutonomyNudgeEngine` — detects tasks that have been untouched for 2+ hours and triggers a smart notification with "Not ready yet" (snooze) and "It feels too big" (auto-split) action buttons
- `FreshStartEngine` — identifies motivational fresh-start moments (Monday, month start, streak break, 3-day+ absence) and surfaces an encouraging prompt, capped at once per ISO week to avoid repetition
- `WoopEngine` — implements the Wish-Outcome-Obstacle-Plan framework with affective forecasting: tracks expected vs. actual difficulty and surfaces calibration insights
- `TaskSplitter` — auto-splits an oversized task into three subtasks (Part 1/3, 2/3, 3/3) that inherit the parent's quadrant, priority, and one-third of the impact score; the parent is archived
- `PeakEnergyDetector` — learns personal focus windows from session history using exponential recency weighting (last 7 days = 3×, last 30 days = 1.5×, older = 0.5×); blends detected windows with user-configured peak hours via linear interpolation

**Data Layer**
- `UlyssesContractEntity` / `UlyssesContractDao` / `UlyssesContractRepository` — binding commitment contracts attached to tasks; stores contract text, deadline, and win/loss outcome
- `WoopEntity` / `WoopDao` / `WoopRepository` — persists WOOP entries (wish, outcome, obstacle, plan) and affective forecast results per task

**Screens**
- **Identity** — dedicated screen for composing and managing personal identity affirmations (e.g. "I finish what I start"); duplicates are rejected; affirmations are persisted and used as self-concept anchors throughout the app
- **Log Time** — manual time-logging screen for recording effort spent outside of timed focus sessions
- **Mini WOOP Reflection** — post-focus reflection screen walking users through the four WOOP fields to close the loop on a completed session

**Analytics additions**
- **XP / Points card** — gamification layer surfacing total XP, today's XP, this-week's XP, and the top-5 point-earning tasks
- **Neuro Boost Insights card** — completion breakdown for frog tasks, anxiety tasks, public commitments, and if-then plan usage rate
- **Procrastination Radar card** — lists the top repeatedly-postponed active tasks as a motivational nudge
- **Dynamic Peak Energy card** — shows the auto-detected peak window vs. the manually configured one and the detection confidence level
- **Ulysses Contracts card** — live view of active contracts and a WIN/LOSS record for closed contracts

**Background Workers**
- `AutonomyNudgeWorker` — fires the autonomy nudge notification with smart action buttons; tagged per task so it can be cancelled if the task is touched before the 2-hour window
- `UlyssesEvaluatorWorker` — evaluates Ulysses Contract outcomes at deadline; marks WIN if the task was completed before the deadline, LOSS otherwise
- `FocusWidgetUpdateWorker` — periodically queries `TaskScoringEngine` and pushes the top-scored task to the home screen widget
- `NotificationWorker` — general-purpose notification dispatching used by the daily plan and streak check flows

**BroadcastReceivers**
- `BootReceiver` — listens for `BOOT_COMPLETED` and reschedules all WorkManager periodic workers so background intelligence resumes after a device restart
- `NudgeSnoozeReceiver` — handles the *Not ready yet* action from autonomy nudge notifications, re-queuing the nudge for a later window

**UI Components**
- `UlyssesContractSheet` — bottom sheet for creating and viewing a Ulysses Contract on a task
- `ManualTimeLogSheet` — bottom sheet for quick manual time entry without navigating to the full Log Time screen
- `WoopPromptSheet` — pre-focus bottom sheet that invites users to set a WOOP wish and anticipate obstacles before starting the timer
- `AffordanceRatingSheet` — post-focus bottom sheet for rating perceived task difficulty vs. expectation; data feeds WOOP calibration
- `NewChapterCard` — card composable for creating a new goal or life chapter in the drawer goals section
- `TopGoalsRefillCard` — card composable for surfacing and replenishing the top yearly/weekly goals in the drawer
- `DrawerGoalsSection` / `DrawerViewModel` — navigation drawer section displaying yearly and weekly goals with inline editing
- `FocusWidgetProvider` — RemoteViews-based AppWidget (`AppWidgetProvider`) showing the current top-priority task; tapping the **Start** button launches MainActivity; update is triggered via `FocusWidgetUpdateWorker`

**Project documentation**
- README, CONTRIBUTING, LICENSE, CHANGELOG, CODE_OF_CONDUCT, SECURITY

---

## [1.0.0] — 2024-01-01

### Added

**Core Application**
- `MainActivity` — entry point with onboarding flow and theme switching
- `NeuroFlowApplication` — Hilt initialization, notification channels, background worker scheduling

**Neuroscience Scoring Engine**
- `TaskScoringEngine` — composite priority scores (0–1000) integrating 16+ evidence-based frameworks:
  - Eisenhower Matrix quadrant weighting
  - Temporal Motivation Theory (deadline discounting)
  - Cognitive Load Theory (energy matching)
  - Circadian Rhythm optimization (peak hours)
  - "Eat the Frog" methodology (tackle hardest tasks first)
  - BJ Fogg Tiny Habits (momentum building)
  - Zeigarnik Effect (psychological pressure of open loops)
  - Progress Principle (motivation from partial completion)
  - Implementation Intentions (if-then planning)
  - Temptation Bundling (enjoyment modifiers)
  - Commitment Devices (social accountability)
  - Loss Aversion (goal-risk amplification)
- `AnalyticsEngine` — productivity metrics: completion rates, streaks, time tracking, goal progress

**Data Layer**
- Room database (`NeuroFlowDatabase`) with schema versions 1–6 and full migration support
- `TaskEntity` — task model with 40+ fields covering scoring inputs, timing, reminders, energy, and neuroscience boosters
- `TimeSessionEntity` — time tracking sessions with pause support
- `GoalEntity` — long-term goals linked to tasks
- Repository pattern (`TaskRepository`, `SessionRepository`, `GoalRepository`)
- Proto DataStore (`UserPreferencesDataStore`) for user preferences

**Screens**
- **Eisenhower Matrix** — four-quadrant task visualization with quadrant detail view
- **Schedule** — day/week timeline with locked-slot scheduling and work-hour awareness
- **Focus Timer** — Pomodoro-style sessions with per-task time tracking
- **Analytics** — Vico-powered charts for completion rates, streaks, and goal progress
- **History** — searchable archive of completed tasks
- **Settings** — theme selection, work hours, energy peak configuration, priority weight customization
- **Onboarding** — guided first-launch setup and in-app guide

**Background Workers**
- `DailyPlanWorker` — daily 7 am notification with top 3 prioritized tasks
- `StreakCheckWorker` — daily 9 pm habit streak verification
- `DeadlineEscalationWorker` — 4-hourly escalation of tasks ≤48 hours from deadline (SCHEDULE → DO_FIRST)

**UI**
- Material 3 theming with dynamic dark/light mode
- `NewTaskSheet` — bottom sheet composable for full task creation and editing
- `TaskRow` — reusable task list item component

---

[Unreleased]: https://github.com/takian-alt/proFlow/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/takian-alt/proFlow/releases/tag/v1.0.0
