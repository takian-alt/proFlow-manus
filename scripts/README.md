# Launcher Module Isolation Check

This directory contains build-time verification scripts for the proFlow Launcher.

## check-launcher-isolation.sh

**Purpose**: Enforces module isolation for the launcher feature by preventing compile-time dependencies on ViewModels or UseCases from other presentation modules.

**Requirements**: Validates Requirement 21.5 from the Focus Launcher spec.

### What it checks

The script scans all Kotlin files in `app/src/main/java/com/neuroflow/app/presentation/launcher/` and fails the build if it finds imports matching these patterns:

- `com.neuroflow.app.presentation.analytics.*ViewModel`
- `com.neuroflow.app.presentation.focus.*ViewModel`
- `com.neuroflow.app.presentation.history.*ViewModel`
- `com.neuroflow.app.presentation.identity.*ViewModel`
- `com.neuroflow.app.presentation.logtime.*ViewModel`
- `com.neuroflow.app.presentation.matrix.*ViewModel`
- `com.neuroflow.app.presentation.onboarding.*ViewModel`
- `com.neuroflow.app.presentation.schedule.*ViewModel`
- `com.neuroflow.app.presentation.settings.*ViewModel`
- Similar patterns for `*UseCase` classes

### Why this matters

The launcher module must remain isolated from other presentation features to:

1. Prevent circular dependencies
2. Ensure the launcher can be tested independently
3. Allow the launcher to be extracted into a separate module in the future
4. Maintain clear architectural boundaries

### Allowed dependencies

The launcher module CAN depend on:

- Domain layer classes (TaskRepository, TaskScoringEngine, AnalyticsEngine, etc.) via Hilt injection
- Data layer classes (Room entities, DataStore, etc.)
- Common UI components from `presentation/common/`
- Android framework classes

### Running the check

The check runs automatically as part of the standard Gradle `check` task:

```bash
./gradlew check
```

Or run it directly:

```bash
./gradlew checkLauncherModuleIsolation
```

Or run the script manually:

```bash
bash scripts/check-launcher-isolation.sh
```

### CI Integration

This check is integrated into the CI pipeline via the `check` task. Any pull request that violates module isolation will fail the build.

### Example violation

```kotlin
// ❌ FORBIDDEN - importing ViewModel from another presentation module
import com.neuroflow.app.presentation.analytics.AnalyticsViewModel

class LauncherViewModel @Inject constructor(
    private val analyticsViewModel: AnalyticsViewModel  // WRONG!
)
```

### Correct approach

```kotlin
// ✅ CORRECT - inject domain layer classes directly
import com.neuroflow.app.domain.engine.AnalyticsEngine

class LauncherViewModel @Inject constructor(
    private val analyticsEngine: AnalyticsEngine  // RIGHT!
)
```

## Adding new checks

To add additional verification scripts:

1. Create a new `.sh` file in this directory
2. Make it executable: `chmod +x scripts/your-script.sh`
3. Register it as a Gradle task in `app/build.gradle.kts` or a separate `.gradle.kts` file
4. Add it as a dependency of the `check` task
5. Document it in this README

