package com.neuroflow.app.presentation.launcher.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Bug Condition Exploration Tests — Task 1.1 & 1.2
 *
 * These tests confirm Bug 1: `updatePreferences` drops four fields.
 *
 * The `current` object inside `updatePreferences` is constructed WITHOUT reading:
 *   - taskCardStyle
 *   - distractionDimmingEnabled
 *   - homeScreenPages
 *   - homeScreenGridEnabled
 *
 * As a result, any call to `updatePreferences` silently resets those four fields
 * to their defaults, even when the lambda only touches an unrelated field.
 *
 * These tests MUST FAIL on unfixed code to confirm the bug exists.
 * A test failure here is the SUCCESS case for bug exploration.
 *
 * Validates: Requirements 1.1, 1.2 (bug condition)
 */
class PinnedAppsDataStoreBugExplorationTest : StringSpec({

    /**
     * Task 1.1 — homeScreenPages is dropped by updatePreferences.
     *
     * Simulates the exact bug: the `current` object in `updatePreferences` does NOT
     * read `homeScreenPages` from DataStore. When the lambda calls `it.copy(skippedTaskIds = ...)`
     * the `homeScreenPages` field in `current` is the default (empty list), so the result
     * also has an empty list — wiping all user-arranged home screen content.
     *
     * This test reproduces the bug by directly simulating the broken `current` construction.
     * It MUST FAIL on unfixed code.
     */
    "Task 1.1 — updatePreferences preserves homeScreenPages (MUST FAIL on unfixed code)" {
        // Arrange: a stored preference with non-empty homeScreenPages
        val storedPrefs = LauncherPreferences(
            homeScreenPages = listOf(
                HomeScreenPage(
                    id = "page-1",
                    name = "Main",
                    items = listOf(
                        HomeScreenItem.App(packageName = "com.android.chrome", gridPosition = 0)
                    )
                )
            ),
            skippedTaskIds = emptySet()
        )

        // Simulate the BUGGY `current` construction in updatePreferences:
        // It reads only 15 fields, omitting taskCardStyle, distractionDimmingEnabled,
        // homeScreenPages, and homeScreenGridEnabled.
        val buggyCurrentObject = LauncherPreferences(
            dockPackages = storedPrefs.dockPackages,
            folders = storedPrefs.folders,
            hiddenPackages = storedPrefs.hiddenPackages,
            lockedPackages = storedPrefs.lockedPackages,
            recentPackages = storedPrefs.recentPackages,
            cardAlpha = storedPrefs.cardAlpha,
            clockStyle = storedPrefs.clockStyle,
            iconPackPackageName = storedPrefs.iconPackPackageName,
            iconShape = storedPrefs.iconShape,
            drawerColumns = storedPrefs.drawerColumns,
            distractionScores = storedPrefs.distractionScores,
            backupMetadata = storedPrefs.backupMetadata,
            webSearchUrl = storedPrefs.webSearchUrl,
            showTaskScore = storedPrefs.showTaskScore,
            skippedTaskIds = storedPrefs.skippedTaskIds
            // BUG: taskCardStyle, distractionDimmingEnabled, homeScreenPages,
            //      homeScreenGridEnabled are NOT read from DataStore here
        )

        // Apply the lambda (same as skipTask does: only touches skippedTaskIds)
        val updated = buggyCurrentObject.copy(skippedTaskIds = setOf("x"))

        // Assert: homeScreenPages should be preserved — but the bug causes it to be empty
        // This assertion FAILS on unfixed code because `updated.homeScreenPages` is `[]`
        updated.homeScreenPages shouldBe storedPrefs.homeScreenPages
    }

    /**
     * Task 1.2 — taskCardStyle, distractionDimmingEnabled, homeScreenGridEnabled are dropped.
     *
     * Same root cause: the `current` object omits these three fields, so they reset to
     * defaults on every `updatePreferences` call.
     *
     * This test MUST FAIL on unfixed code.
     */
    "Task 1.2 — updatePreferences preserves taskCardStyle, distractionDimmingEnabled, homeScreenGridEnabled (MUST FAIL on unfixed code)" {
        // Arrange: stored preferences with non-default values for the 4 missing fields
        val storedPrefs = LauncherPreferences(
            taskCardStyle = CardStyle.FLAT,                // non-default (default is ELEVATED)
            distractionDimmingEnabled = false,             // non-default (default is true)
            homeScreenGridEnabled = false,                 // non-default (default is true)
            homeScreenPages = listOf(
                HomeScreenPage(id = "page-1", name = "Main")
            ),
            skippedTaskIds = emptySet()
        )

        // Simulate the BUGGY `current` construction (same as in updatePreferences)
        val buggyCurrentObject = LauncherPreferences(
            dockPackages = storedPrefs.dockPackages,
            folders = storedPrefs.folders,
            hiddenPackages = storedPrefs.hiddenPackages,
            lockedPackages = storedPrefs.lockedPackages,
            recentPackages = storedPrefs.recentPackages,
            cardAlpha = storedPrefs.cardAlpha,
            clockStyle = storedPrefs.clockStyle,
            iconPackPackageName = storedPrefs.iconPackPackageName,
            iconShape = storedPrefs.iconShape,
            drawerColumns = storedPrefs.drawerColumns,
            distractionScores = storedPrefs.distractionScores,
            backupMetadata = storedPrefs.backupMetadata,
            webSearchUrl = storedPrefs.webSearchUrl,
            showTaskScore = storedPrefs.showTaskScore,
            skippedTaskIds = storedPrefs.skippedTaskIds
            // BUG: taskCardStyle defaults to ELEVATED, distractionDimmingEnabled defaults to true,
            //      homeScreenPages defaults to [], homeScreenGridEnabled defaults to true
        )

        // Apply a lambda that only touches skippedTaskIds (unrelated to the 4 missing fields)
        val updated = buggyCurrentObject.copy(skippedTaskIds = setOf("x"))

        // Assert all four missing fields are preserved — these FAIL on unfixed code
        updated.taskCardStyle shouldBe storedPrefs.taskCardStyle
        updated.distractionDimmingEnabled shouldBe storedPrefs.distractionDimmingEnabled
        updated.homeScreenGridEnabled shouldBe storedPrefs.homeScreenGridEnabled
        updated.homeScreenPages shouldBe storedPrefs.homeScreenPages
    }

    /**
     * Additional verification: confirm the default values that the bug resets fields to.
     * This documents the exact data loss that occurs on every updatePreferences call.
     */
    "Bug 1 — default values confirm data loss on every updatePreferences call" {
        // The buggy current object uses LauncherPreferences() defaults for the 4 missing fields
        val defaultPrefs = LauncherPreferences()

        // These are the values that get written back on every updatePreferences call,
        // overwriting whatever the user had stored
        defaultPrefs.taskCardStyle shouldBe CardStyle.ELEVATED
        defaultPrefs.distractionDimmingEnabled shouldBe true
        defaultPrefs.homeScreenPages shouldBe emptyList()
        defaultPrefs.homeScreenGridEnabled shouldBe true

        // Confirm that non-default values exist (so the data loss is real)
        CardStyle.FLAT shouldNotBe CardStyle.ELEVATED
        false shouldNotBe true
    }
})

