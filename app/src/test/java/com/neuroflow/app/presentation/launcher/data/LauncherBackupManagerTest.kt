package com.neuroflow.app.presentation.launcher.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject

/**
 * Unit tests for LauncherBackupManager.
 *
 * Tests:
 * - Export creates valid JSON with schemaVersion
 * - Import validates schema and applies configuration
 * - Import skips uninstalled packages and reports them
 * - Import handles invalid JSON gracefully
 */
class LauncherBackupManagerTest : StringSpec({

    "export creates valid JSON with schemaVersion" {
        // Arrange
        val mockDataStore = mockk<PinnedAppsDataStore>()
        val mockAppRepository = mockk<AppRepository>()

        val testPrefs = LauncherPreferences(
            dockPackages = listOf("com.example.app1", "com.example.app2"),
            folders = listOf(
                FolderDefinition(
                    id = "folder1",
                    name = "Test Folder",
                    packages = listOf("com.example.app3", "com.example.app4"),
                    gridIndex = 0
                )
            ),
            hiddenPackages = setOf("com.example.hidden"),
            lockedPackages = setOf("com.example.locked"),
            iconPackPackageName = "com.example.iconpack",
            iconShape = IconShape.CIRCLE,
            drawerColumns = 4,
            cardAlpha = 0.85f,
            clockStyle = ClockStyle.DIGITAL
        )

        coEvery { mockDataStore.launcherPrefsFlow } returns flowOf(testPrefs)

        val manager = LauncherBackupManager(mockDataStore, mockAppRepository)

        // Act
        val json = manager.export()

        // Assert
        val jsonObj = JSONObject(json)
        jsonObj.getInt("schemaVersion") shouldBe 1
        jsonObj.getJSONArray("dockPackages").length() shouldBe 2
        jsonObj.getJSONArray("folders").length() shouldBe 1
        jsonObj.getString("iconShape") shouldBe "CIRCLE"
        jsonObj.getInt("drawerColumns") shouldBe 4
    }

    "import skips uninstalled packages and reports them" {
        // Arrange
        val mockDataStore = mockk<PinnedAppsDataStore>(relaxed = true)
        val mockAppRepository = mockk<AppRepository>()

        // Only app1 and app3 are installed
        val installedApps = listOf(
            AppInfo(
                label = "App 1",
                packageName = "com.example.app1",
                className = "MainActivity",
                userHandle = mockk(),
                icon = mockk(),
                distractionScore = 50,
                isWorkProfile = false
            ),
            AppInfo(
                label = "App 3",
                packageName = "com.example.app3",
                className = "MainActivity",
                userHandle = mockk(),
                icon = mockk(),
                distractionScore = 50,
                isWorkProfile = false
            )
        )

        coEvery { mockAppRepository.apps } returns MutableStateFlow(installedApps)
        coEvery { mockDataStore.updatePreferences(any()) } returns Unit

        val manager = LauncherBackupManager(mockDataStore, mockAppRepository)

        // Create backup JSON with some uninstalled packages
        val json = """
        {
            "schemaVersion": 1,
            "dockPackages": ["com.example.app1", "com.example.app2"],
            "folders": [{
                "id": "folder1",
                "name": "Test",
                "packages": ["com.example.app3", "com.example.app4"],
                "gridIndex": 0
            }],
            "hiddenPackages": ["com.example.hidden"],
            "lockedPackages": ["com.example.locked"],
            "iconShape": "CIRCLE",
            "drawerColumns": 4,
            "cardAlpha": 0.85,
            "clockStyle": "DIGITAL"
        }
        """.trimIndent()

        // Act
        val result = manager.import(json)

        // Assert
        result.success shouldBe true
        result.skippedPackages shouldContain "com.example.app2"
        result.skippedPackages shouldContain "com.example.app4"
        result.skippedPackages shouldContain "com.example.hidden"
        result.skippedPackages shouldContain "com.example.locked"
        result.skippedPackages shouldNotContain "com.example.app1"
        result.skippedPackages shouldNotContain "com.example.app3"
    }

    "import handles invalid JSON gracefully" {
        // Arrange
        val mockDataStore = mockk<PinnedAppsDataStore>()
        val mockAppRepository = mockk<AppRepository>()
        val manager = LauncherBackupManager(mockDataStore, mockAppRepository)

        // Act
        val result = manager.import("invalid json")

        // Assert
        result.success shouldBe false
        result.errorMessage shouldNotBe null
    }

    "import rejects backup with missing schemaVersion" {
        // Arrange
        val mockDataStore = mockk<PinnedAppsDataStore>()
        val mockAppRepository = mockk<AppRepository>()
        val manager = LauncherBackupManager(mockDataStore, mockAppRepository)

        val json = """
        {
            "dockPackages": ["com.example.app1"]
        }
        """.trimIndent()

        // Act
        val result = manager.import(json)

        // Assert
        result.success shouldBe false
        result.errorMessage shouldBe "Invalid backup file: missing schema version"
    }

    "import rejects backup from newer version" {
        // Arrange
        val mockDataStore = mockk<PinnedAppsDataStore>()
        val mockAppRepository = mockk<AppRepository>()
        val manager = LauncherBackupManager(mockDataStore, mockAppRepository)

        val json = """
        {
            "schemaVersion": 999,
            "dockPackages": ["com.example.app1"]
        }
        """.trimIndent()

        // Act
        val result = manager.import(json)

        // Assert
        result.success shouldBe false
        result.errorMessage shouldBe "Backup file is from a newer version and cannot be imported"
    }
})
