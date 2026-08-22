package com.neuroflow.app.presentation.launcher.data

import android.graphics.drawable.Drawable
import android.os.UserHandle

/**
 * Data class representing an installed app across all user profiles.
 *
 * @property label The display name of the app
 * @property packageName The package name (e.g., "com.example.app")
 * @property className The main activity class name
 * @property userHandle The user profile this app belongs to (personal or work)
 * @property icon The app icon drawable (loaded from AppRepository cache)
 * @property installedAtMillis First install timestamp used for drawer sorting
 * @property distractionScore User-assigned score (0-100) indicating how distracting the app is
 * @property isWorkProfile True if this app belongs to a work profile
 */
data class AppInfo(
    val label: String,
    val packageName: String,
    val className: String,
    val userHandle: UserHandle,
    val icon: Drawable,
    val installedAtMillis: Long = 0L,
    val distractionScore: Int = 50,
    val isWorkProfile: Boolean = false
)
