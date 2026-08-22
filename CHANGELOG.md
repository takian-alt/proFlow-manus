# Changelog

All notable changes to proFlow are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Project documentation: README, CONTRIBUTING, LICENSE, CHANGELOG, CODE_OF_CONDUCT, SECURITY

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
