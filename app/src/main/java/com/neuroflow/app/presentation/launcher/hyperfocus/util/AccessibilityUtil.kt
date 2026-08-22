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

        val targetServiceName = "${context.packageName}/.presentation.launcher.hyperfocus.service.AppBlockingService"

        return enabledServices.any { serviceInfo ->
            serviceInfo.id == targetServiceName
        }
    }
}
