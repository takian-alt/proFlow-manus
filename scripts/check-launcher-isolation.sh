#!/bin/bash
# Custom Lint check for launcher module isolation.
#
# Ensures the launcher module (presentation/launcher/) has no compile-time dependency
# on presentation/ ViewModels or UseCases from other features.
#
# Requirements: 21.5

set -e

LAUNCHER_DIR="app/src/main/java/com/neuroflow/app/presentation/launcher"

if [ ! -d "$LAUNCHER_DIR" ]; then
    echo "Launcher directory not found, skipping check"
    exit 0
fi

# Forbidden import patterns
FORBIDDEN_PATTERNS=(
    "com\.neuroflow\.app\.presentation\.analytics\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.focus\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.history\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.identity\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.logtime\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.matrix\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.onboarding\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.schedule\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.settings\..*ViewModel"
    "com\.neuroflow\.app\.presentation\.analytics\..*UseCase"
    "com\.neuroflow\.app\.presentation\.focus\..*UseCase"
    "com\.neuroflow\.app\.presentation\.history\..*UseCase"
    "com\.neuroflow\.app\.presentation\.identity\..*UseCase"
    "com\.neuroflow\.app\.presentation\.logtime\..*UseCase"
    "com\.neuroflow\.app\.presentation\.matrix\..*UseCase"
    "com\.neuroflow\.app\.presentation\.onboarding\..*UseCase"
    "com\.neuroflow\.app\.presentation\.schedule\..*UseCase"
    "com\.neuroflow\.app\.presentation\.settings\..*UseCase"
)

VIOLATIONS=()

# Check all Kotlin files in launcher directory
while IFS= read -r -d '' file; do
    for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
        # Search for import statements matching the pattern
        matches=$(grep -n "^import $pattern" "$file" 2>/dev/null || true)
        if [ -n "$matches" ]; then
            while IFS= read -r match; do
                VIOLATIONS+=("$file:$match")
            done <<< "$matches"
        fi
    done
done < <(find "$LAUNCHER_DIR" -name "*.kt" -type f -print0)

if [ ${#VIOLATIONS[@]} -gt 0 ]; then
    echo ""
    echo "❌ Launcher module isolation violation detected!"
    echo ""
    echo "The launcher module must not import ViewModels or UseCases from other presentation features."
    echo "Shared access to domain layer classes (TaskRepository, ScoringEngine, AnalyticsEngine)"
    echo "should be via Hilt injection only."
    echo ""
    echo "Violations found:"
    for violation in "${VIOLATIONS[@]}"; do
        echo "  - $violation"
    done
    echo ""
    exit 1
fi

echo "✅ Launcher module isolation check passed"
exit 0
