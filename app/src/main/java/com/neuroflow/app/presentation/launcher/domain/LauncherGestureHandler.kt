package com.neuroflow.app.presentation.launcher.domain

import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

class LauncherGestureHandler(
    private val context: Context,
    private val onSwipeUp: () -> Unit,
    private val onSwipeDown: () -> Unit,
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit,
    private val onLongPress: () -> Unit,
    private val isDraggingIcon: () -> Boolean = { false }
) {
    val navigationMode: NavigationMode by lazy { detectNavigationMode() }

    private fun detectNavigationMode(): NavigationMode {
        return try {
            val mode = Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)
            when (mode) {
                0 -> NavigationMode.THREE_BUTTON
                1 -> NavigationMode.TWO_BUTTON
                2 -> NavigationMode.GESTURE
                else -> NavigationMode.THREE_BUTTON
            }
        } catch (e: Exception) {
            Log.w("LauncherGesture", "Failed to detect navigation mode", e)
            NavigationMode.THREE_BUTTON
        }
    }

    fun getExclusionRects(screenHeightPx: Int, screenWidthPx: Int): List<Rect> {
        if (navigationMode != NavigationMode.GESTURE) return emptyList()
        val density = context.resources.displayMetrics.density
        val exclusionPx = (200.dp.value * density).toInt()
        return listOf(Rect(0, screenHeightPx - exclusionPx, screenWidthPx, screenHeightPx))
    }

    /**
     * Attach to the top zone (clock/date area) — swipe down opens notification shade.
     * Uses detectVerticalDragGestures: fires after 60dp downward drag.
     * Suppressed entirely if icon drag is active at gesture start.
     */
    @Composable
    fun Modifier.attachSwipeDown(): Modifier {
        val density = LocalDensity.current
        val threshold = with(density) { 60.dp.toPx() }
        return this.pointerInput(Unit) {
            var total = 0f
            var fired = false
            var suppressed = false
            detectVerticalDragGestures(
                onDragStart = {
                    total = 0f
                    fired = false
                    suppressed = isDraggingIcon()
                },
                onDragEnd = { total = 0f; fired = false; suppressed = false },
                onDragCancel = { total = 0f; fired = false; suppressed = false },
                onVerticalDrag = { change, dragAmount ->
                    if (!fired && !suppressed) {
                        total += dragAmount
                        if (total > threshold) {
                            fired = true
                            change.consume()
                            onSwipeDown()
                        }
                    }
                }
            )
        }
    }

    /**
     * Attach to the bottom zone (dock area) — swipe up opens app drawer.
     * Uses detectVerticalDragGestures: fires after 60dp upward drag.
     * Suppressed entirely if icon drag is active at gesture start.
     */
    @Composable
    fun Modifier.attachSwipeUp(): Modifier {
        val density = LocalDensity.current
        val threshold = with(density) { 60.dp.toPx() }
        return this.pointerInput(Unit) {
            var total = 0f
            var fired = false
            var suppressed = false
            detectVerticalDragGestures(
                onDragStart = {
                    total = 0f
                    fired = false
                    suppressed = isDraggingIcon()
                },
                onDragEnd = { total = 0f; fired = false; suppressed = false },
                onDragCancel = { total = 0f; fired = false; suppressed = false },
                onVerticalDrag = { change, dragAmount ->
                    if (!fired && !suppressed) {
                        total += dragAmount
                        if (total < -threshold) { // negative = upward
                            fired = true
                            change.consume()
                            onSwipeUp()
                        }
                    }
                }
            )
        }
    }

    /** Long-press on home screen background opens launcher settings. */
    @Composable
    fun Modifier.attachLongPress(): Modifier {
        return this.pointerInput(Unit) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
    }

    /** Applied to the pager — only long-press, no swipe interception. */
    @Composable
    fun Modifier.attachGestures(): Modifier = this.attachLongPress()
}
