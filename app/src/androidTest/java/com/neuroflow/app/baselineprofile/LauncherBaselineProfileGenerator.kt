package com.neuroflow.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile Generator for proFlow Launcher
 *
 * This generates a baseline profile that causes ART to AOT-compile critical launcher code paths,
 * eliminating JIT warm-up jank on first launch.
 *
 * Critical paths covered:
 * - LauncherActivity cold start
 * - AppDrawer open and scroll
 * - FocusTaskCard composition
 * - Icon loading from LruCache
 *
 * To generate the profile:
 * ./gradlew :app:generateBaselineProfile
 *
 * The generated baseline-prof.txt will be placed in app/src/main/
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LauncherBaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.neuroflow.app",
            maxIterations = 5,
            stableIterations = 3
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            // Ensure we're starting from home
            device.pressHome()
            device.waitForIdle()

            // Critical Path 1: LauncherActivity cold start
            // Press home to trigger launcher activity
            startActivityAndWait()
            device.waitForIdle(2000)

            // Critical Path 2: FocusTaskCard composition
            // Wait for the focus task card to be visible
            device.wait(Until.hasObject(By.pkg("com.neuroflow.app")), 3000)
            device.waitForIdle(1000)

            // Critical Path 3: Icon loading from LruCache
            // Icons in DockRow should be loaded and cached
            device.waitForIdle(1000)

            // Critical Path 4: AppDrawer open
            // Swipe up from bottom to open app drawer
        val displayHeight = device.displayHeight
            val displayWidth = device.displayWidth
            device.swipe(
                displayWidth / 2,
                displayHeight - 100,
                displayWidth / 2,
                displayHeight / 4,
                20
            )
            device.waitForIdle(1000)

            // Critical Path 5: AppDrawer scroll
            // Scroll through the app drawer to trigger icon loading
            repeat(3) {
                device.swipe(
                    displayWidth / 2,
                    displayHeight / 2,
        displayWidth / 2,
                    displayHeight / 4,
                    10
                )
                device.waitForIdle(500)
            }

            // Scroll back up
            repeat(2) {
                device.swipe(
                    displayWidth / 2,
                    displayHeight / 4,
                    displayWidth / 2,
                    displayHeight / 2,
                    10
                )
                device.waitForIdle(500)
            }

            // Close app drawer by swiping down
            device.swipe(
                displayWidth /2,
                displayHeight / 4,
                displayWidth / 2,
                displayHeight - 100,
                20
            )
            device.waitForIdle(1000)

            // Critical Path 6: QuickStatsPanel open (swipe left)
            device.swipe(
                100,
                displayHeight / 2,
                displayWidth - 100,
                displayHeight / 2,
                20
            )
            device.waitForIdle(1000)

            // Close QuickStatsPanel by swiping right
            device.swipe(
                displayWidth - 100,
                displayHeight / 2,
                100,
                displayHeight / 2,
                20
            )
            device.waitForIdle(1000)

            // Return to stable state
            device.pressHome()
            device.waitForIdle()
        }
    }
}

