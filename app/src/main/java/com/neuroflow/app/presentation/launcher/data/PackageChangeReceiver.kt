package com.neuroflow.app.presentation.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BroadcastReceiver for package install, uninstall, and replace events.
 *
 * Calls AppRepository.invalidateAndRebuild() on package changes.
 * Removes uninstalled packages from dock, folders, hidden list, locked list.
 *
 * Must be registered in LauncherActivity.onStart() and unregistered in onStop().
 */
@Singleton
class PackageChangeReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val pinnedAppsDataStore: PinnedAppsDataStore
) : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /**
         * Create IntentFilter for package change events.
         */
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Rebuild app list
                scope.launch {
                    appRepository.invalidateAndRebuild()
                }
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                // Remove from all launcher lists and rebuild
                scope.launch {
                    removePackageFromLauncher(packageName)
                    appRepository.invalidateAndRebuild()
                }
            }
        }
    }

    /**
     * Remove uninstalled package from dock, folders, hidden list, locked list.
     */
    private suspend fun removePackageFromLauncher(packageName: String) {
        pinnedAppsDataStore.updatePreferences { prefs ->
            prefs.copy(
                dockPackages = prefs.dockPackages.filter { it != packageName },
                folders = prefs.folders.map { folder ->
                    val updatedPackages = folder.packages.filter { it != packageName }
                    // Auto-dissolve folder if only 1 app remains
                    if (updatedPackages.size <= 1) {
                        null  // Mark for removal
                    } else {
                        folder.copy(packages = updatedPackages)
                    }
                }.filterNotNull(),
                hiddenPackages = prefs.hiddenPackages - packageName,
                lockedPackages = prefs.lockedPackages - packageName
            )
        }
    }
}
