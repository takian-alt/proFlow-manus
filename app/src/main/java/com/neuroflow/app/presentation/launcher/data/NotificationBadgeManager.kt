package com.neuroflow.app.presentation.launcher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for notification badge counts.
 *
 * Provides a StateFlow that is updated by NotificationBadgeService.
 * This separation allows Hilt to inject a non-null instance while
 * the actual NotificationListenerService lifecycle is managed by Android.
 */
@Singleton
class NotificationBadgeManager @Inject constructor() {

    private val _badgeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val badgeCounts: StateFlow<Map<String, Int>> = _badgeCounts.asStateFlow()

    /**
     * Update badge counts. Called by NotificationBadgeService.
     */
    internal fun updateBadgeCounts(counts: Map<String, Int>) {
        _badgeCounts.value = counts
    }
}
