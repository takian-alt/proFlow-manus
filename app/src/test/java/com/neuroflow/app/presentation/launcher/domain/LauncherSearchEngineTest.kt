package com.neuroflow.app.presentation.launcher.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import com.neuroflow.app.presentation.launcher.data.AppInfo
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.data.PinnedAppsDataStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for LauncherSearchEngine.
 *
 * Tests:
 * - App search returns matching apps
 * - Contact search requires READ_CONTACTS permission
 * - Settings search returns matching settings
 * - Web query is included in results
 * - Empty query returns empty results
 * - Search results are grouped by type
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O]) // API 26
class LauncherSearchEngineTest : StringSpec({
    val testDispatcher = StandardTestDispatcher()

    beforeTest {
        Dispatchers.setMain(testDispatcher)
    }

    afterTest {
        Dispatchers.resetMain()
    }

    "should return empty results for blank query" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results = searchEngine.search("")
            results.apps.shouldBeEmpty()
            results.contacts.shouldBeEmpty()
            results.settings.shouldBeEmpty()
            results.webQuery shouldBe null
        }
    }

    "should search apps by label" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            val mockApps = listOf(
                AppInfo(
                    label = "Chrome",
                    packageName = "com.android.chrome",
                    className = "com.android.chrome.MainActivity",
                    userHandle = mockk<UserHandle>(),
                    icon = mockk(),
                    distractionScore = 50,
                    isWorkProfile = false
                ),
                AppInfo(
                    label = "Gmail",
                    packageName = "com.google.android.gm",
                    className = "com.google.android.gm.MainActivity",
                    userHandle = mockk<UserHandle>(),
                    icon = mockk(),
                    distractionScore = 50,
                    isWorkProfile = false
                ),
                AppInfo(
                    label = "Calendar",
                    packageName = "com.google.android.calendar",
                    className = "com.google.android.calendar.MainActivity",
                    userHandle = mockk<UserHandle>(),
                    icon = mockk(),
                    distractionScore = 50,
                    isWorkProfile = false
                )
            )

            every { appRepository.apps } returns MutableStateFlow(mockApps)

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results = searchEngine.search("chr")
            results.apps shouldHaveSize 1
            results.apps[0].label shouldBe "Chrome"
            results.webQuery shouldBe "chr"
        }
    }

    "should search apps by package name" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            val mockApps = listOf(
                AppInfo(
                    label = "Chrome",
                    packageName = "com.android.chrome",
                    className = "com.android.chrome.MainActivity",
                    userHandle = mockk<UserHandle>(),
                    icon = mockk(),
                    distractionScore = 50,
                    isWorkProfile = false
                ),
                AppInfo(
                    label = "Gmail",
                    packageName = "com.google.android.gm",
                    className = "com.google.android.gm.MainActivity",
                    userHandle = mockk<UserHandle>(),
                    icon = mockk(),
                    distractionScore = 50,
                    isWorkProfile = false
                )
            )

            every { appRepository.apps } returns MutableStateFlow(mockApps)

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results = searchEngine.search("google")
            results.apps shouldHaveSize 1
            results.apps[0].label shouldBe "Gmail"
        }
    }

    "should return empty contacts when READ_CONTACTS permission denied" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            every { appRepository.apps } returns MutableStateFlow(emptyList())
            every {
                context.checkPermission(
                    Manifest.permission.READ_CONTACTS,
                    any(),
                    any()
                )
            } returns PackageManager.PERMISSION_DENIED

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results = searchEngine.search("john")
            results.contacts.shouldBeEmpty()
        }
    }

    "should search settings by title" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            every { appRepository.apps } returns MutableStateFlow(emptyList())

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results = searchEngine.search("wifi")
            results.settings shouldHaveSize 1
            results.settings[0].title shouldBe "Wi-Fi"
        }
    }

    "should search settings by description" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            every { appRepository.apps } returns MutableStateFlow(emptyList())

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results = searchEngine.search("brightness")
            results.settings shouldHaveSize 1
            results.settings[0].title shouldBe "Display"
        }
    }

    "should limit app results to 10" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            val mockApps = (1..20).map { index ->
                AppInfo(
                    label = "App $index",
                    packageName = "com.example.app$index",
                    className = "com.example.app$index.MainActivity",
                    userHandle = mockk<UserHandle>(),
                    icon = mockk(),
                    distractionScore = 50,
                    isWorkProfile = false
                )
            }

            every { appRepository.apps } returns MutableStateFlow(mockApps)

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results = searchEngine.search("app")
            results.apps shouldHaveSize 10
        }
    }

    "should limit settings results to 5" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            every { appRepository.apps } returns MutableStateFlow(emptyList())

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            // Search for a term that matches many settings
            val results = searchEngine.search("settings")
            (results.settings.size <= 5) shouldBe true
            (results.settings.isNotEmpty()) shouldBe true
        }
    }

    "should be case insensitive" {
        runTest {
            val context = mockk<Context>(relaxed = true)
            val appRepository = mockk<AppRepository>()
            val pinnedAppsDataStore = mockk<PinnedAppsDataStore>()

            val mockApps = listOf(
                AppInfo(
                    label = "Chrome",
                    packageName = "com.android.chrome",
                    className = "com.android.chrome.MainActivity",
                    userHandle = mockk<UserHandle>(),
                    icon = mockk(),
                    distractionScore = 50,
                    isWorkProfile = false
                )
            )

            every { appRepository.apps } returns MutableStateFlow(mockApps)

            val searchEngine = LauncherSearchEngineImpl(context, appRepository, pinnedAppsDataStore)

            val results1 = searchEngine.search("CHROME")
            val results2 = searchEngine.search("chrome")
            val results3 = searchEngine.search("ChRoMe")

            results1.apps shouldHaveSize 1
            results2.apps shouldHaveSize 1
            results3.apps shouldHaveSize 1
        }
    }
})
