package com.neuroflow.app.presentation.launcher.hyperfocus.util

import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.neuroflow.app.presentation.launcher.hyperfocus.service.AppBlockingService

object AccessibilityUtil {
    /**
     * Checks if AppBlockingService is currently enabled and running.
     * Uses AccessibilityManager which is more reliable than parsing Settings.Secure string.
     */
    fun isAppBlockingServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false

        val enabledServices = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        val targetShortName = "${context.packageName}/.presentation.launcher.hyperfocus.service.AppBlockingService"
        val targetFullName = "${context.packageName}/${AppBlockingService::class.java.name}"

        return enabledServices.any { serviceInfo ->
            val id = serviceInfo.id
            id == targetShortName ||
                id == targetFullName ||
                id.endsWith("/${AppBlockingService::class.java.name}")
        }
    }
}
