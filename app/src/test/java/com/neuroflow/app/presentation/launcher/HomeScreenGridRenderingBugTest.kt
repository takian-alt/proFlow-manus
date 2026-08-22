package com.neuroflow.app.presentation.launcher

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * Regression test for launcher grid rendering source changes.
 *
 * Ensures old exploratory bug markers are gone and source no longer
 * contains the temporary "bug confirmed" comments.
 */
class HomeScreenGridRenderingBugTest : StringSpec({

    "HomeScreen source does not contain obsolete bug marker comment" {
        val homeScreenFile = findHomeScreenFile()
        val source = homeScreenFile.readText()
        source shouldNotContain "ALWAYS show home screen grid (removed the if condition)"
        source shouldNotContain "Bug 6 confirmed"
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
        File(System.getProperty("user.dir") ?: ".").let { workDir ->
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
