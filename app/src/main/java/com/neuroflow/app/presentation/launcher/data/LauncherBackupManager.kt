package com.neuroflow.app.presentation.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of backup import operation.
 *
 * @property success Whether import completed successfully
 * @property skippedPackages List of package names that were skipped (not installed)
 * @property errorMessage Error message if import failed
 */
data class ImportResult(
    val success: Boolean,
    val skippedPackages: List<String>,
    val errorMessage: String? = null
)

/**
 * Manager for launcher configuration backup and restore.
 *
 * Exports LauncherPreferences to JSON string and imports from JSON with validation.
 * Includes schema versioning for migration support.
 * Skips uninstalled packages during import and reports them in ImportResult.
 */
@Singleton
class LauncherBackupManager @Inject constructor(
    private val pinnedAppsDataStore: PinnedAppsDataStore,
    private val appRepository: AppRepository
) {

    /**
     * Export launcher configuration to JSON string.
     * Includes schemaVersion for migration support.
     *
     * @return JSON string containing all launcher configuration
     */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val prefs = pinnedAppsDataStore.launcherPrefsFlow.first()

        val json = JSONObject()
        json.put("schemaVersion", 1)

        // Dock apps
        val dockArray = JSONArray()
        prefs.dockPackages.forEach { dockArray.put(it) }
        json.put("dockPackages", dockArray)

        // Folders
        val foldersArray = JSONArray()
        prefs.folders.forEach { folder ->
            val folderObj = JSONObject()
            folderObj.put("id", folder.id)
            folderObj.put("name", folder.name)
            folderObj.put("gridIndex", folder.gridIndex)

            val packagesArray = JSONArray()
            folder.packages.forEach { packagesArray.put(it) }
            folderObj.put("packages", packagesArray)

            foldersArray.put(folderObj)
        }
        json.put("folders", foldersArray)

        // Hidden packages
        val hiddenArray = JSONArray()
        prefs.hiddenPackages.forEach { hiddenArray.put(it) }
        json.put("hiddenPackages", hiddenArray)

        // Locked packages
        val lockedArray = JSONArray()
        prefs.lockedPackages.forEach { lockedArray.put(it) }
        json.put("lockedPackages", lockedArray)

        // Icon pack
        prefs.iconPackPackageName?.let { json.put("iconPackPackageName", it) }

        // Icon shape
        json.put("iconShape", prefs.iconShape.name)

        // Drawer columns
        json.put("drawerColumns", prefs.drawerColumns)

        // Card alpha
        json.put("cardAlpha", prefs.cardAlpha.toDouble())

        // Clock style
        json.put("clockStyle", prefs.clockStyle.name)

        json.toString(2) // Pretty print with 2-space indent
    }

    /**
     * Import launcher configuration from JSON string.
     * Validates schema and skips uninstalled packages.
     *
     * Requirements:
     * - 18.3: Import validates schema and applies restored configuration
     * - 18.4: Skip uninstalled packages and show summary
     * - 18.5: Apply migration function for older backup schemas
     *
     * @param json JSON string from export()
     * @return ImportResult with success status and list of skipped packages
     */
    suspend fun import(json: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonObj = JSONObject(json)

            // Validate schema version
            val schemaVersion = jsonObj.optInt("schemaVersion", 0)
            if (schemaVersion == 0) {
                return@withContext ImportResult(
                    success = false,
                    skippedPackages = emptyList(),
                    errorMessage = "Invalid backup file: missing schema version"
                )
            }

            // Apply migration for older schemas (Requirement 18.5)
            val migratedJson = when (schemaVersion) {
                1 -> jsonObj // Current schema, no migration needed
                else -> {
                    // Future: add migration functions for older schemas
                    if (schemaVersion > 1) {
                        return@withContext ImportResult(
                            success = false,
                            skippedPackages = emptyList(),
                            errorMessage = "Backup file is from a newer version and cannot be imported"
                        )
                    }
                    jsonObj
                }
            }

            // Get list of installed packages for validation (Requirement 18.4)
            val installedPackages = appRepository.apps.value.map { it.packageName }.toSet()
            val skippedPackages = mutableListOf<String>()

            // Helper function to validate and filter packages
            fun validatePackage(pkg: String): Boolean {
                val isInstalled = installedPackages.contains(pkg)
                if (!isInstalled) {
                    skippedPackages.add(pkg)
                }
                return isInstalled
            }

            // Parse dock packages (Requirement 18.4: skip uninstalled)
            val dockArray = migratedJson.optJSONArray("dockPackages")
            val dockPackages = mutableListOf<String>()
            if (dockArray != null) {
                for (i in 0 until dockArray.length()) {
                    val pkg = dockArray.getString(i)
                    if (validatePackage(pkg)) {
                        dockPackages.add(pkg)
                    }
                }
            }

            // Parse folders (Requirement 18.4: skip uninstalled packages in folders)
            val foldersArray = migratedJson.optJSONArray("folders")
            val folders = mutableListOf<FolderDefinition>()
            if (foldersArray != null) {
                for (i in 0 until foldersArray.length()) {
                    val folderObj = foldersArray.getJSONObject(i)
                    val packagesArray = folderObj.getJSONArray("packages")
                    val packages = mutableListOf<String>()

                    for (j in 0 until packagesArray.length()) {
                        val pkg = packagesArray.getString(j)
                        if (validatePackage(pkg)) {
                            packages.add(pkg)
                        }
                    }

                    // Only add folder if it has at least one installed package
                    if (packages.isNotEmpty()) {
                        folders.add(
                            FolderDefinition(
                                id = folderObj.getString("id"),
                                name = folderObj.getString("name"),
                                packages = packages,
                                gridIndex = folderObj.getInt("gridIndex")
                            )
                        )
                    }
                }
            }

            // Parse hidden packages (Requirement 18.4: skip uninstalled)
            val hiddenArray = migratedJson.optJSONArray("hiddenPackages")
            val hiddenPackages = mutableSetOf<String>()
            if (hiddenArray != null) {
                for (i in 0 until hiddenArray.length()) {
                    val pkg = hiddenArray.getString(i)
                    if (validatePackage(pkg)) {
                        hiddenPackages.add(pkg)
                    }
                }
            }

            // Parse locked packages (Requirement 18.4: skip uninstalled)
            val lockedArray = migratedJson.optJSONArray("lockedPackages")
            val lockedPackages = mutableSetOf<String>()
            if (lockedArray != null) {
                for (i in 0 until lockedArray.length()) {
                    val pkg = lockedArray.getString(i)
                    if (validatePackage(pkg)) {
                        lockedPackages.add(pkg)
                    }
                }
            }

            // Parse icon pack (validate if specified)
            val iconPackPackageName = migratedJson.optString("iconPackPackageName", "").takeIf { it.isNotEmpty() }
            val validatedIconPack = if (iconPackPackageName != null && iconPackPackageName.isNotEmpty()) {
                if (validatePackage(iconPackPackageName)) {
                    iconPackPackageName
                } else {
                    null // Icon pack not installed, skip
                }
            } else {
                null
            }

            // Parse icon shape
            val iconShapeStr = migratedJson.optString("iconShape", IconShape.SYSTEM_DEFAULT.name)
            val iconShape = try {
                IconShape.valueOf(iconShapeStr)
            } catch (e: Exception) {
                IconShape.SYSTEM_DEFAULT
            }

            // Parse drawer columns
            val drawerColumns = migratedJson.optInt("drawerColumns", 4).coerceIn(3, 5)

            // Parse card alpha
            val cardAlpha = migratedJson.optDouble("cardAlpha", 0.85).toFloat().coerceIn(0.5f, 1.0f)

            // Parse clock style
            val clockStyleStr = migratedJson.optString("clockStyle", ClockStyle.DIGITAL.name)
            val clockStyle = try {
                ClockStyle.valueOf(clockStyleStr)
            } catch (e: Exception) {
                ClockStyle.DIGITAL
            }

            // Update preferences (Requirement 18.3)
            pinnedAppsDataStore.updatePreferences { prefs ->
                prefs.copy(
                    dockPackages = dockPackages,
                    folders = folders,
                    hiddenPackages = hiddenPackages,
                    lockedPackages = lockedPackages,
                    iconPackPackageName = validatedIconPack,
                    iconShape = iconShape,
                    drawerColumns = drawerColumns,
                    cardAlpha = cardAlpha,
                    clockStyle = clockStyle,
                    backupMetadata = BackupMetadata(
                        timestamp = System.currentTimeMillis(),
                        version = schemaVersion
                    )
                )
            }

            ImportResult(
                success = true,
                skippedPackages = skippedPackages
            )
        } catch (e: Exception) {
            android.util.Log.e("LauncherBackupManager", "Error importing backup", e)
            ImportResult(
                success = false,
                skippedPackages = emptyList(),
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
}
