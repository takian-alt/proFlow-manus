package com.neuroflow.app.data.local

import android.app.backup.BackupManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles full local-data cleanup and backup-state coordination.
 */
@Singleton
class DatabaseCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: NeuroFlowDatabase,
    private val preferencesDataStore: UserPreferencesDataStore
) {
    companion object {
        private const val TAG = "DatabaseCleaner"
        private const val BACKUP_STATE_PREFS = "backup_state"
        private const val BACKUP_APP_VERSION_CODE = "backup_app_version_code"
    }

    private val backupState by lazy {
        context.getSharedPreferences(BACKUP_STATE_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Clear all app tables and preferences, then request a backup refresh.
     *
     * This is used by Settings -> Clear All Data to ensure both local and
     * cloud-restored state are reset.
     */
    suspend fun clearAllDataAndInvalidateBackup() = withContext(Dispatchers.IO) {
        try {
            database.clearAllTables()
            preferencesDataStore.clearAll()
            writeRecordedBackupVersionCode(currentAppVersionCode())
            requestBackupRefresh()
            android.util.Log.i(TAG, "Cleared all local data and requested backup refresh")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed clearing app data", e)
            throw e
        }
    }

    /**
     * Enforces restore policy on app startup.
     *
     * If this is the first launch after install/reinstall and restored backup data
     * was created by a different app version, local data is purged.
     */
    suspend fun enforceRestoreVersionPolicy(firstLaunchAfterInstall: Boolean): Boolean = withContext(Dispatchers.IO) {
        val currentVersion = currentAppVersionCode()
        val recordedVersion = readRecordedBackupVersionCode()
        val shouldPurge = firstLaunchAfterInstall &&
            recordedVersion > 0L &&
            recordedVersion != currentVersion

        if (shouldPurge) {
            database.clearAllTables()
            preferencesDataStore.clearAll()
            requestBackupRefresh()
            android.util.Log.w(
                TAG,
                "Purged restored data from app version $recordedVersion; current version is $currentVersion"
            )
        }

        writeRecordedBackupVersionCode(currentVersion)
        shouldPurge
    }

    fun requestBackupRefresh() {
        BackupManager(context).dataChanged()
    }

    private fun readRecordedBackupVersionCode(): Long {
        return backupState.getLong(BACKUP_APP_VERSION_CODE, 0L)
    }

    private fun writeRecordedBackupVersionCode(versionCode: Long) {
        backupState.edit()
            .putLong(BACKUP_APP_VERSION_CODE, versionCode)
            .commit()
    }

    private fun currentAppVersionCode(): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
}
