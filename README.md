# proFlow (NeuroFlow)

> **Science-backed Android task prioritization — because not all tasks are created equal.**

proFlow is an advanced productivity and task management application for Android, built around neuroscience and behavioral psychology principles. It intelligently scores and ranks your tasks using a composite engine that draws on 16+ evidence-based frameworks so you always know what to work on next.

---

## Features

- **Eisenhower Matrix** — visualize tasks across the classic four-quadrant do/schedule/delegate/eliminate grid
- **Smart Scoring Engine** — composite priority scores (0–1000) driven by Temporal Motivation Theory, Cognitive Load Theory, Circadian Rhythm research, the Zeigarnik Effect, BJ Fogg's Tiny Habits, and more
- **Focus Timer** — built-in Pomodoro-style focus sessions with per-task time tracking
- **Schedule View** — day and week timeline with locked-slot scheduling and work-hour awareness
- **Analytics Dashboard** — completion rates, productivity streaks, goal progress, and Vico-powered charts
- **Task History** — searchable archive of completed tasks with full audit trail
- **Goal Linking** — attach tasks to long-term goals and track goal risk exposure
- **Background Intelligence** — WorkManager workers for daily planning notifications (7 am), streak checks (9 pm), and automatic deadline escalation (every 4 hours)
- **Onboarding Flow** — guided first-launch setup for identity, peak energy hours, and first task
- **Theming** — Material 3 dark/light theme with user-configurable priority weights

---

## Screenshots

_Screenshots will be added here once the first release build is available._

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose 2.8.5 |
| Architecture | MVVM, Clean Architecture |
| DI | Hilt 2.51.1 |
| Database | Room 2.6.1 (SQLite, 6 migrations) |
| Preferences | Proto DataStore |
| Background | WorkManager |
| Charts | Vico |
| Build | Gradle 8.x with Kotlin DSL, KSP |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |

---

## Architecture

```
app/
└── src/main/java/com/neuroflow/app/
    ├── di/                    # Hilt dependency injection modules
    ├── domain/
    │   ├── engine/            # TaskScoringEngine, AnalyticsEngine
    │   └── model/             # Enums (Quadrant, Priority, TaskStatus …)
    ├── data/
    │   ├── local/             # Room database, DAOs, entities, DataStore
    │   └── repository/        # Repository pattern over DAOs
    ├── presentation/
    │   ├── common/            # Navigation scaffold, shared composables, theme
    │   ├── matrix/            # Eisenhower Matrix screen
    │   ├── schedule/          # Day/week schedule screen
    │   ├── focus/             # Focus timer screen
    │   ├── analytics/         # Analytics screen
    │   ├── history/           # Completed tasks screen
    │   ├── settings/          # Settings & priority weights screens
    │   └── onboarding/        # Onboarding & app guide screens
    └── worker/                # WorkManager background workers
```

Data flows **unidirectionally**: Compose UI → ViewModel (StateFlow) → Repository → Room / DataStore.

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
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
