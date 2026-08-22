package com.neuroflow.app.presentation.launcher.data

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing installed apps across all user profiles.
 *
 * Uses LauncherApps as the sole data source (never PackageManager.queryIntentActivities).
 * Maintains an LruCache for app icons keyed by "${packageName}:${userHandle.hashCode()}".
 *
 * All LauncherApps calls are wrapped in try-catch and executed on IO thread.
 * Returns last known good state on error.
 */
@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val launcherApps: LauncherApps,
    private val pinnedAppsDataStore: PinnedAppsDataStore
) {
    // Icon cache with key format: "${packageName}:${userHandle.hashCode()}"
    private val iconCache = LruCache<String, Drawable>(200)

    // Last known good state for error recovery
    private var lastKnownGoodApps: List<AppInfo> = emptyList()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    /**
     * Load all installed apps from all user profiles.
     * Must be called on IO thread.
     */
    suspend fun loadAll() = withContext(Dispatchers.IO) {
        try {
            val profiles = launcherApps.profiles
            val distractionScores = pinnedAppsDataStore.launcherPrefsFlow.first().distractionScores
            val hiddenPackages = pinnedAppsDataStore.launcherPrefsFlow.first().hiddenPackages

            val allApps = mutableListOf<AppInfo>()

            for (profile in profiles) {
                try {
                    val activities = launcherApps.getActivityList(null, profile)
                    val isWorkProfile = profile != android.os.Process.myUserHandle()

                    for (activityInfo in activities) {
                        val packageName = activityInfo.applicationInfo.packageName

                        // Skip hidden apps
                        if (packageName in hiddenPackages) continue

                        val label = activityInfo.label.toString()
                        val className = activityInfo.name
                        val cacheKey = getCacheKey(packageName, profile)

                        // Load icon from cache or LauncherApps
                        val icon = iconCache.get(cacheKey) ?: try {
                            activityInfo.getBadgedIcon(0).also { drawable ->
                                iconCache.put(cacheKey, drawable)
                            }
                        } catch (e: Exception) {
                            // Fallback to default icon
                            context.packageManager.defaultActivityIcon
                        }

                        allApps.add(
                            AppInfo(
                                label = label,
                                packageName = packageName,
                                className = className,
                                userHandle = profile,
                                icon = icon,
                                distractionScore = distractionScores[packageName] ?: 50,
                                isWorkProfile = isWorkProfile
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Log error but continue with other profiles
                    android.util.Log.e("AppRepository", "Error loading apps for profile $profile", e)
                }
            }

            lastKnownGoodApps = allApps
            _apps.value = allApps
        } catch (e: Exception) {
            // Return last known good state on error
            android.util.Log.e("AppRepository", "Error loading all apps", e)
            _apps.value = lastKnownGoodApps
        }
    }

    /**
     * Invalidate cache and rebuild app list.
     * Called when packages are added, removed, or replaced.
     */
    suspend fun invalidateAndRebuild() = withContext(Dispatchers.IO) {
        iconCache.evictAll()
        loadAll()
    }

    /**
     * Clear all cached icons.
     * Called on critical memory pressure.
     */
    fun clearIconCache() {
        iconCache.evictAll()
    }

    /**
     * Handle memory trim events.
     * Reduces cache by 50% at MODERATE, clears entirely at CRITICAL.
     */
    fun onTrimMemory(level: Int) {
        when {
            level >= 80 -> { // ComponentCallbacks2.TRIM_MEMORY_CRITICAL
                clearIconCache()
            }
            level >= 60 -> { // ComponentCallbacks2.TRIM_MEMORY_MODERATE
                // Reduce cache size by 50%
                val currentSize = iconCache.size()
                val targetSize = currentSize / 2
                var removed = 0

                // Create snapshot of keys to avoid concurrent modification
                val keys = mutableListOf<String>()
                iconCache.snapshot().keys.forEach { keys.add(it) }

                // Remove oldest entries (LruCache evicts least recently used)
                for (key in keys) {
                    if (removed >= targetSize) break
                    iconCache.remove(key)
                    removed++
                }
            }
        }
    }

    /**
     * Record app launch and prepend to recent list (max 10).
     */
    suspend fun recordLaunch(packageName: String) {
        pinnedAppsDataStore.updatePreferences { prefs ->
            val updated = prefs.recentPackages.toMutableList()
            updated.remove(packageName)  // Remove if already present
            updated.add(0, packageName)  // Prepend to front

            prefs.copy(
                recentPackages = updated.take(10)  // Keep max 10
            )
        }
    }

    /**
     * Launch an app with the specified package name and user handle.
     * Uses FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED.
     */
    suspend fun launchApp(packageName: String, userHandle: UserHandle) = withContext(Dispatchers.IO) {
        try {
            val activities = launcherApps.getActivityList(packageName, userHandle)
            if (activities.isNotEmpty()) {
                val component = activities[0].componentName
                launcherApps.startMainActivity(
                    component,
                    userHandle,
                    null,
                    Bundle().apply {
                        putInt("android.activity.launchFlags",
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                )

                // Record launch
                recordLaunch(packageName)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Error launching app $packageName", e)
        }
    }

    /**
     * Generate cache key for icon storage.
     * Format: "${packageName}:${userHandle.hashCode()}"
     */
    private fun getCacheKey(packageName: String, userHandle: UserHandle): String {
        return "$packageName:${userHandle.hashCode()}"
    }
}
