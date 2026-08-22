# Contributing to proFlow

Thank you for your interest in contributing to proFlow! This document explains how to get involved, what we expect, and how the development workflow works.

---

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Help](#getting-help)
3. [How to Contribute](#how-to-contribute)
4. [Development Setup](#development-setup)
5. [Branching Strategy](#branching-strategy)
6. [Commit Messages](#commit-messages)
7. [Pull Request Process](#pull-request-process)
8. [Code Style](#code-style)
9. [Testing](#testing)
10. [Reporting Bugs](#reporting-bugs)
11. [Suggesting Enhancements](#suggesting-enhancements)

---

## Code of Conduct

By participating in this project you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please read it before contributing.

---

## Getting Help

- Open a [GitHub Discussion](https://github.com/takian-alt/proFlow/discussions) for questions, ideas, or general feedback.
- For confirmed bugs, open a [GitHub Issue](https://github.com/takian-alt/proFlow/issues).

---

## How to Contribute

There are many ways to contribute beyond writing code:

- **Bug reports** — a clear, reproducible report helps us fix issues quickly.
- **Feature requests** — share your ideas through GitHub Issues.
- **Documentation** — improvements to README, inline KDoc, or guides are always welcome.
- **Code** — bug fixes, performance improvements, new features.
- **Testing** — writing or improving unit/instrumented tests.

---

## Development Setup

### Requirements

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK API 26–35
- An Android device or emulator running Android 8.0+

### Steps

```bash
# 1. Fork the repository on GitHub, then clone your fork
git clone https://github.com/<your-username>/proFlow.git
cd proFlow

# 2. Add the upstream remote
git remote add upstream https://github.com/takian-alt/proFlow.git

# 3. Open in Android Studio and let Gradle sync

# 4. Build and verify everything compiles
./gradlew assembleDebug
```

---

## Branching Strategy

| Branch | Purpose |
|---|---|
| `main` | Stable, released code |
| `develop` | Integration branch for new work |
| `feature/<short-description>` | New features |
| `fix/<short-description>` | Bug fixes |
| `chore/<short-description>` | Maintenance (deps, tooling, docs) |

Always branch off from `develop` and target `develop` in your pull request.

```bash
git checkout develop
git pull upstream develop
git checkout -b feature/your-feature-name
```

---

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<optional scope>): <short imperative summary>

[Optional body explaining what and why, not how]

[Optional footer: BREAKING CHANGE, closes #issue]
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`

**Examples:**

```
feat(scoring): add loss-aversion factor to task score calculation

fix(schedule): correct off-by-one in weekly view date range

docs: add architecture diagram to README

chore(deps): upgrade Room to 2.7.0
```

---

## Pull Request Process

1. **Keep PRs focused** — one logical change per pull request.
2. **Sync with upstream** before opening a PR:
   ```bash
   git fetch upstream
   git rebase upstream/develop
   ```
3. **Fill in the PR template** — describe what changed and why, link related issues.
4. **All checks must pass** — builds and any automated tests must be green.
5. **Request a review** — at least one approval is required before merging.
6. **Squash or rebase** — maintainers may ask you to clean up the commit history before merge.

---

## Code Style

This project follows the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).

- Use **4 spaces** for indentation (no tabs).
- Keep lines under **120 characters** where practical.
- Use **named arguments** for composables and data classes with more than two parameters.
- Prefer **immutable** (`val`) over mutable (`var`) wherever possible.
- Compose UI functions are named in **PascalCase** with no trailing `()` in navigation routes.
- ViewModel state is exposed via `StateFlow` — avoid `LiveData`.
- Write **KDoc** comments for all public API members.

Android Studio's built-in formatter (`Code → Reformat Code`) configured with the project's `.editorconfig` should handle most formatting automatically.

---

## Testing

The project uses the standard Android testing stack:

| Test type | Location | Runner |
|---|---|---|
| Unit tests | `app/src/test/` | JUnit 4 / 5 |
| Instrumented tests | `app/src/androidTest/` | `AndroidJUnitRunner` |

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

When adding a new feature, please include matching unit tests for:

- Any new scoring logic in `TaskScoringEngine`
- Any new behavioral engine logic (`FreshStartEngine`, `WoopEngine`, `AutonomyNudgeEngine`, `PeakEnergyDetector`, `TaskSplitter`)
- Any new ViewModel state transitions
- Any new repository methods

---

## Reporting Bugs

Before opening an issue:

1. Search [existing issues](https://github.com/takian-alt/proFlow/issues) to avoid duplicates.
2. Try to reproduce on the latest commit of `develop`.

When opening a bug report, please include:

- A clear, descriptive title.
- Steps to reproduce the problem.
- Expected vs. actual behavior.
- Android version and device model.
- Relevant logcat output (use code blocks).
- Screenshots or screen recordings if applicable.

---

## Suggesting Enhancements

Open a GitHub Issue with the label `enhancement`. Include:

- The problem or use case the feature would solve.
- A description of your proposed solution.
- Any alternatives you have considered.

---

Thank you for helping make proFlow better!
