package com.neuroflow.app.presentation.launcher.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class PinnedAppsDataStoreRegressionTest : StringSpec({

    "updating an unrelated field preserves launcher layout and card fields" {
        val original = LauncherPreferences(
            skippedTaskIds = emptySet(),
            taskCardStyle = CardStyle.FLAT,
            distractionDimmingEnabled = false,
            homeScreenGridEnabled = false,
            homeScreenPages = listOf(
                HomeScreenPage(
                    id = "page-main",
                    name = "Main",
                    items = listOf(
                        HomeScreenItem.App(packageName = "com.android.chrome", gridPosition = 0),
                        HomeScreenItem.Folder(folderId = "folder-a", gridPosition = 1)
                    )
                )
            ),
            customQuotes = listOf("Ship daily")
        )

        val updated = original.copy(skippedTaskIds = setOf("task-123"))

        updated.skippedTaskIds shouldContain "task-123"
        updated.taskCardStyle shouldBe CardStyle.FLAT
        updated.distractionDimmingEnabled shouldBe false
        updated.homeScreenGridEnabled shouldBe false
        updated.homeScreenPages shouldBe original.homeScreenPages
        updated.customQuotes shouldBe original.customQuotes
    }

    "copy update can clear optional fields without mutating unrelated launcher preferences" {
        val original = LauncherPreferences(
            iconPackPackageName = "com.example.iconpack",
            backupMetadata = BackupMetadata(timestamp = 1_700_000_000_000L, version = 2),
            dockPackages = listOf("com.neuroflow.app", "com.android.chrome"),
            leftPageBlocks = mapOf(
                "subliminal" to true,
                "quick_note" to true,
                "woop" to false,
                "distraction_top3" to true
            )
        )

        val updated = original.copy(
            iconPackPackageName = null,
            backupMetadata = null
        )

        updated.iconPackPackageName shouldBe null
        updated.backupMetadata shouldBe null
        updated.dockPackages shouldContainAll listOf("com.neuroflow.app", "com.android.chrome")
        updated.leftPageBlocks shouldBe original.leftPageBlocks
    }
})
