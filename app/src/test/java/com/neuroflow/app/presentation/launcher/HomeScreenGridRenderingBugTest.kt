package com.neuroflow.app.presentation.launcher

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * Bug Condition Exploration Test — Task 1.3
 *
 * Verifies Bug 6: `PortraitLayout` renders `HomeScreenGrid` unconditionally,
 * ignoring the `homeScreenGridEnabled` flag.
 *
 * Root cause: The `if (homeScreenGridEnabled)` guard was removed from `PortraitLayout`
 * in `HomeScreen.kt`. The comment in the source reads:
 *   "// ALWAYS show home screen grid (removed the if condition)"
 *
 * This test inspects the source code of `HomeScreen.kt` to verify the conditional
 * guard is present. It MUST FAIL on unfixed code because the guard was removed.
 *
 * Validates: Requirement 1.10 (bug condition), Requirement 2.9 (expected behavior)
 */
class HomeScreenGridRenderingBugTest : StringSpec({

    /**
     * Task 1.3 — PortraitLayout must NOT render HomeScreenGrid when homeScreenGridEnabled = false.
     *
     * The correct behavior: `HomeScreenGrid` is rendered if and only if `homeScreenGridEnabled == true`.
     * The buggy behavior: `HomeScreenGrid` is ALWAYS rendered regardless of the flag.
     *
     * This test verifies the `if (homeScreenGridEnabled)` guard exists in `PortraitLayout`.
     * On unfixed code, the guard is absent (replaced with an unconditional call), so this FAILS.
     *
     * This test MUST FAIL on unfixed code.
     */
    "Task 1.3 — PortraitLayout guards HomeScreenGrid with if (homeScreenGridEnabled) (MUST FAIL on unfixed code)" {
        // Locate HomeScreen.kt relative to the project root
        // When running via Gradle, the working directory is the module root (app/)
        val homeScreenFile = findHomeScreenFile()

        val source = homeScreenFile.readText()

        // The bug: the guard was removed and replaced with an unconditional call.
        // The source currently contains this comment confirming the bug:
        //   "// ALWAYS show home screen grid (removed the if condition)"
        // and calls HomeScreenGrid(...) without any if-guard.

        // Assert the conditional guard IS present — this FAILS on unfixed code
        // because the guard was removed.
        source shouldContain "if (homeScreenGridEnabled)"

        // Also assert the bug comment is NOT present (it should be removed when fixed)
        source shouldNotContain "ALWAYS show home screen grid (removed the if condition)"
    }

    /**
     * Verify the bug condition: confirm the unconditional HomeScreenGrid call exists.
     * This test PASSES on unfixed code (confirming the bug) and should be removed after fix.
     */
    "Bug 6 confirmed — HomeScreenGrid is called unconditionally in PortraitLayout (confirms bug exists)" {
        val homeScreenFile = findHomeScreenFile()
        val source = homeScreenFile.readText()

        // On unfixed code, this comment exists confirming the guard was intentionally removed
        val bugCommentPresent = source.contains("ALWAYS show home screen grid (removed the if condition)")

        // This assertion documents the bug: the comment confirms the guard was removed
        // This test PASSES on unfixed code (bug confirmed) and FAILS after fix (comment removed)
        bugCommentPresent shouldBe true
    }
})

/**
 * Finds HomeScreen.kt by searching common locations relative to the working directory.
 */
private fun findHomeScreenFile(): File {
    val candidates = listOf(
        // When running from module root (app/)
        File("src/main/java/com/neuroflow/app/presentation/launcher/HomeScreen.kt"),
        // When running from project root
        File("app/src/main/java/com/neuroflow/app/presentation/launcher/HomeScreen.kt"),
        // Absolute fallback using system property
        File(System.getProperty("user.dir")).let { workDir ->
            File(workDir, "src/main/java/com/neuroflow/app/presentation/launcher/HomeScreen.kt")
                .takeIf { it.exists() }
                ?: File(workDir, "app/src/main/java/com/neuroflow/app/presentation/launcher/HomeScreen.kt")
        }
    )

    return candidates.firstOrNull { it.exists() }
        ?: error(
            "Could not find HomeScreen.kt. Searched:\n" +
            candidates.joinToString("\n") { "  ${it.absolutePath}" } +
            "\nWorking directory: ${System.getProperty("user.dir")}"
        )
}
