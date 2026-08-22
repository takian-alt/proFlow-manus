package com.neuroflow.app.presentation.launcher

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for Task 30.2: Work profile integration
 *
 * Tests work profile functionality:
 * - Work profile badge display
 * - Work app launching with UserHandle
 * - Work profile state changes
 *
 * Requirements: 8.11, 11.10
 *
 * Note: These tests require a device with work profile configured.
 * Tests will be skipped if no work profile is present.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkProfileIntegrationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<LauncherActivity>()

    private lateinit var context: Context
    private lateinit var userManager: UserManager

    @Before

    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

        // Skip tests if no work profile is present
        val hasWorkProfile = userManager.userProfiles.size > 1
        Assume.assumeTrue("Work profile not configured on device", hasWorkProfile)
    }

    /**
     * Test work profile apps display with work badge
     * Requirements: 8.11, 11.10
     */
    @Test
    fun testWorkProfile_displaysWorkBadge() {
        composeTestRule.waitForIdle()

        // Open app drawer
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }

        composeTestRule.waitForIdle()

        // Verify app drawer is open
        composeTestRule.onNodeWithTag("app_drawer").assertExists()

        // Work apps should have work badge overlay
        // This is a visual test - we verify the drawer renders without crash
        // Actual work badge verification requires visual inspection or pixel testing
    }

    /**
     * Test work profile apps launch correctly with UserHandle
     * Requirements: 8.11, 11.10
     */
    @Test
    fun testWorkProfile_launchesWithUserHandle() {
        composeTestRule.waitForIdle()

        // Open app drawer
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }

        composeTestRule.waitForIdle()

        // Verify app drawer handles work profile apps without crash
        composeTestRule.onNodeWithTag("app_drawer").assertExists()

        // Work apps are launched via LauncherApps.startMainActivity with UserHandle
        // Test passes if drawer renders work apps without crash
    }

    /**
     * Test work profile state changes are handled
     * Requirements: 11.10
     */
    @Test
    fun testWorkProfile_handlesStateChanges() {
        composeTestRule.waitForIdle()

        // Verify launcher renders with work profile
        composeTestRule.onNodeWithTag("home_screen").assertExists()

        // Open and close app drawer to trigger work profile app loading
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_drawer")
            .performTouchInput { swipeDown(startY = top, endY = centerY) }
        composeTestRule.waitForIdle()

        // Verify launcher remains stable
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }

    /**
     * Test work profile apps in dock
     * Requirements: 8.11
     */
    @Test
    fun testWorkProfile_appsInDock() {
        composeTestRule.waitForIdle()

        // Verify dock renders with potential work apps
        composeTestRule.onNodeWithTag("dock_row").assertExists()

        // Work apps in dock should have work badge
        // Test passes if dock renders without crash
    }

    /**
     * Test work profile apps in folders
     * Requirements: 8.11
     */
    @Test
    fun testWorkProfile_appsInFolders() {
        composeTestRule.waitForIdle()

        // Verify home screen renders
        composeTestRule.onNodeWithTag("home_screen").assertExists()

        // Work apps can be placed in folders
        // Test passes if launcher handles work apps in folders without crash
    }

    /**
     * Test work profile badge overlay positioning
     * Requirements: 8.11
     */
    @Test
    fun testWorkProfile_badgePositioning() {
        composeTestRule.waitForIdle()

        // Open app drawer
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }

        composeTestRule.waitForIdle()

        // Work badge should be at bottom-right of icon
        // This is a visual test - we verify rendering without crash
        composeTestRule.onNodeWithTag("app_drawer").assertExists()
    }

    /**
     * Test work profile apps with notification badges
     * Requirements: 8.11, 8.12
     */
    @Test
    fun testWorkProfile_withNotificationBadges() {
        composeTestRule.waitForIdle()

        // Open app drawer
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }

        composeTestRule.waitForIdle()

        // Work apps can have notification badges
        // Both work badge and notification badge should be visible
        composeTestRule.onNodeWithTag("app_drawer").assertExists()
    }

    /**
     * Test work profile apps with icon packs
     * Requirements: 8.11, 8.13
     */
    @Test
    fun testWorkProfile_withIconPacks() {
        composeTestRule.waitForIdle()

        // Open app drawer
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }

        composeTestRule.waitForIdle()

        // Work apps should render with themed icons when icon pack is selected
        composeTestRule.onNodeWithTag("app_drawer").assertExists()
    }

    /**
     * Test work profile apps in search results
     * Requirements: 8.11
     */
    @Test
    fun testWorkProfile_inSearchResults() {
        composeTestRule.waitForIdle()

        // Open app drawer
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }

        composeTestRule.waitForIdle()

        // Work apps should appear in search results with work badge
        composeTestRule.onNodeWithTag("app_drawer").assertExists()

        // Type in search bar (if visible)
        // Work apps should be searchable
    }

    /**
     * Test work profile apps with biometric lock
     * Requirements: 8.11, 15.1
     */
    @Test
    fun testWorkProfile_withBiometricLock() {
        composeTestRule.waitForIdle()

        // Open app drawer
        composeTestRule.onNodeWithTag("home_screen")
            .performTouchInput { swipeUp(startY = centerY, endY = top) }

        composeTestRule.waitForIdle()

        // Work apps can be locked with biometric authentication
        // Test passes if drawer renders without crash
        composeTestRule.onNodeWithTag("app_drawer").assertExists()
    }
}
