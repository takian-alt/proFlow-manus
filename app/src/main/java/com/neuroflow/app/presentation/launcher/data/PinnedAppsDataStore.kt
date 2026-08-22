package com.neuroflow.app.presentation.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

enum class ClockStyle {
    DIGITAL, MINIMAL
}

enum class IconShape {
    CIRCLE, SQUIRCLE, ROUNDED_SQUARE, TEARDROP, SYSTEM_DEFAULT
}

enum class CardStyle {
    ELEVATED, FLAT, OUTLINED
}


data class FolderDefinition(
    val id: String,
    val name: String,
    val packages: List<String>,
    val gridIndex: Int
)

data class BackupMetadata(
    val timestamp: Long,
    val version: Int
)

/**
 * Home screen page containing apps and folders in a grid.
 */
data class HomeScreenPage(
    val id: String,
    val name: String = "",
    val items: List<HomeScreenItem> = emptyList()
)

/**
 * Item on a home screen page (app or folder).
 */
sealed class HomeScreenItem {
    abstract val gridPosition: Int

    data class App(
        val packageName: String,
        override val gridPosition: Int
    ) : HomeScreenItem()

    data class Folder(
        val folderId: String,
        override val gridPosition: Int
    ) : HomeScreenItem()
}

data class LauncherPreferences(
    val dockPackages: List<String> = emptyList(),
    val folders: List<FolderDefinition> = emptyList(),
    val hiddenPackages: Set<String> = emptySet(),
    val lockedPackages: Set<String> = emptySet(),
    val recentPackages: List<String> = emptyList(),
    val cardAlpha: Float = 0.85f,
    val clockStyle: ClockStyle = ClockStyle.DIGITAL,
    val iconPackPackageName: String? = null,
    val iconShape: IconShape = IconShape.SYSTEM_DEFAULT,
    val drawerColumns: Int = 4,
    val distractionScores: Map<String, Int> = emptyMap(),
    val backupMetadata: BackupMetadata? = null,
    val webSearchUrl: String = "https://www.google.com/search?q=",
    val showTaskScore: Boolean = false,
    val skippedTaskIds: Set<String> = emptySet(),
    val taskCardStyle: CardStyle = CardStyle.ELEVATED,
    val distractionDimmingEnabled: Boolean = true,
    val homeScreenPages: List<HomeScreenPage> = emptyList(),
    val homeScreenGridEnabled: Boolean = true,  // Enable by default
    // Left page block visibility: key = block id, value = visible
    val leftPageBlocks: Map<String, Boolean> = mapOf(
        "subliminal" to true,
        "quick_note" to true,
        "woop" to true,
        "distraction_top3" to true
    ),
    // Custom quotes for central quote page
    val customQuotes: List<String> = emptyList()
)

@Singleton
class PinnedAppsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DOCK_PACKAGES = stringPreferencesKey("dock_packages")
        val FOLDERS = stringPreferencesKey("folders")
        val HIDDEN_PACKAGES = stringPreferencesKey("hidden_packages")
        val LOCKED_PACKAGES = stringPreferencesKey("locked_packages")
        val RECENT_PACKAGES = stringPreferencesKey("recent_packages")
        val CARD_ALPHA = floatPreferencesKey("card_alpha")
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val ICON_PACK_PACKAGE_NAME = stringPreferencesKey("icon_pack_package_name")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val DISTRACTION_SCORES = stringPreferencesKey("distraction_scores")
        val BACKUP_METADATA = stringPreferencesKey("backup_metadata")
        val WEB_SEARCH_URL = stringPreferencesKey("web_search_url")
        val SHOW_TASK_SCORE = booleanPreferencesKey("show_task_score")
        val SKIPPED_TASK_IDS = stringPreferencesKey("skipped_task_ids")
        val TASK_CARD_STYLE = stringPreferencesKey("task_card_style")
        val DISTRACTION_DIMMING_ENABLED = booleanPreferencesKey("distraction_dimming_enabled")
        val HOME_SCREEN_PAGES = stringPreferencesKey("home_screen_pages")
        val HOME_SCREEN_GRID_ENABLED = booleanPreferencesKey("home_screen_grid_enabled")
        val LEFT_PAGE_BLOCKS = stringPreferencesKey("left_page_blocks")
        val CUSTOM_QUOTES = stringPreferencesKey("custom_quotes")
    }

    val launcherPrefsFlow: Flow<LauncherPreferences> = context.launcherDataStore.data.map { prefs ->
        mapToLauncherPreferences(prefs)
    }

    private fun mapToLauncherPreferences(prefs: Preferences): LauncherPreferences {
        return LauncherPreferences(
            dockPackages = parseStringList(prefs[Keys.DOCK_PACKAGES]),
            folders = parseFolders(prefs[Keys.FOLDERS]),
            hiddenPackages = parseStringSet(prefs[Keys.HIDDEN_PACKAGES]),
            lockedPackages = parseStringSet(prefs[Keys.LOCKED_PACKAGES]),
            recentPackages = parseStringList(prefs[Keys.RECENT_PACKAGES]),
            cardAlpha = prefs[Keys.CARD_ALPHA] ?: 0.85f,
            clockStyle = try {
                ClockStyle.valueOf(prefs[Keys.CLOCK_STYLE] ?: ClockStyle.DIGITAL.name)
            } catch (_: Exception) { ClockStyle.DIGITAL },
            iconPackPackageName = prefs[Keys.ICON_PACK_PACKAGE_NAME],
            iconShape = try {
                IconShape.valueOf(prefs[Keys.ICON_SHAPE] ?: IconShape.SYSTEM_DEFAULT.name)
            } catch (_: Exception) { IconShape.SYSTEM_DEFAULT },
            drawerColumns = prefs[Keys.DRAWER_COLUMNS] ?: 4,
            distractionScores = parseDistractionScores(prefs[Keys.DISTRACTION_SCORES]),
            backupMetadata = parseBackupMetadata(prefs[Keys.BACKUP_METADATA]),
            webSearchUrl = prefs[Keys.WEB_SEARCH_URL] ?: "https://www.google.com/search?q=",
            showTaskScore = prefs[Keys.SHOW_TASK_SCORE] ?: false,
            skippedTaskIds = parseStringSet(prefs[Keys.SKIPPED_TASK_IDS]),
            taskCardStyle = try {
                CardStyle.valueOf(prefs[Keys.TASK_CARD_STYLE] ?: CardStyle.ELEVATED.name)
            } catch (_: Exception) { CardStyle.ELEVATED },
            distractionDimmingEnabled = prefs[Keys.DISTRACTION_DIMMING_ENABLED] ?: true,
            homeScreenPages = parseHomeScreenPages(prefs[Keys.HOME_SCREEN_PAGES]),
            homeScreenGridEnabled = prefs[Keys.HOME_SCREEN_GRID_ENABLED] ?: true,
            leftPageBlocks = parseLeftPageBlocks(prefs[Keys.LEFT_PAGE_BLOCKS]),
            customQuotes = parseStringList(prefs[Keys.CUSTOM_QUOTES])
        )
    }

    private fun parseStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseStringSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    private fun parseFolders(json: String?): List<FolderDefinition> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                val packagesArr = obj.getJSONArray("packages")
                FolderDefinition(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    packages = List(packagesArr.length()) { packagesArr.getString(it) },
                    gridIndex = obj.getInt("gridIndex")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseDistractionScores(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getInt(it) }
        } catch (_: Exception) { emptyMap() }
    }

    private fun parseBackupMetadata(json: String?): BackupMetadata? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            BackupMetadata(
                timestamp = obj.getLong("timestamp"),
                version = obj.getInt("version")
            )
        } catch (_: Exception) { null }
    }

    private fun encodeStringList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun encodeStringSet(set: Set<String>): String {
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun encodeFolders(folders: List<FolderDefinition>): String {
        val arr = JSONArray()
        folders.forEach { folder ->
            val obj = JSONObject()
            obj.put("id", folder.id)
            obj.put("name", folder.name)
            val packagesArr = JSONArray()
            folder.packages.forEach { packagesArr.put(it) }
            obj.put("packages", packagesArr)
            obj.put("gridIndex", folder.gridIndex)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun encodeDistractionScores(scores: Map<String, Int>): String {
        val obj = JSONObject()
        scores.forEach { (pkg, score) -> obj.put(pkg, score) }
        return obj.toString()
    }

    private fun encodeBackupMetadata(metadata: BackupMetadata?): String? {
        if (metadata == null) return null
        val obj = JSONObject()
        obj.put("timestamp", metadata.timestamp)
        obj.put("version", metadata.version)
        return obj.toString()
    }

    suspend fun updatePreferences(update: (LauncherPreferences) -> LauncherPreferences) {
        context.launcherDataStore.edit { prefs ->
            val current = mapToLauncherPreferences(prefs)
            val updated = update(current)
            prefs[Keys.DOCK_PACKAGES] = encodeStringList(updated.dockPackages)
            prefs[Keys.FOLDERS] = encodeFolders(updated.folders)
            prefs[Keys.HIDDEN_PACKAGES] = encodeStringSet(updated.hiddenPackages)
            prefs[Keys.LOCKED_PACKAGES] = encodeStringSet(updated.lockedPackages)
            prefs[Keys.RECENT_PACKAGES] = encodeStringList(updated.recentPackages)
            prefs[Keys.CARD_ALPHA] = updated.cardAlpha
            prefs[Keys.CLOCK_STYLE] = updated.clockStyle.name
            if (updated.iconPackPackageName == null) {
                prefs.remove(Keys.ICON_PACK_PACKAGE_NAME)
            } else {
                prefs[Keys.ICON_PACK_PACKAGE_NAME] = updated.iconPackPackageName
            }
            prefs[Keys.ICON_SHAPE] = updated.iconShape.name
            prefs[Keys.DRAWER_COLUMNS] = updated.drawerColumns
            prefs[Keys.DISTRACTION_SCORES] = encodeDistractionScores(updated.distractionScores)
            val backupMetadata = encodeBackupMetadata(updated.backupMetadata)
            if (backupMetadata == null) {
                prefs.remove(Keys.BACKUP_METADATA)
            } else {
                prefs[Keys.BACKUP_METADATA] = backupMetadata
            }
            prefs[Keys.WEB_SEARCH_URL] = updated.webSearchUrl
            prefs[Keys.SHOW_TASK_SCORE] = updated.showTaskScore
            prefs[Keys.SKIPPED_TASK_IDS] = encodeStringSet(updated.skippedTaskIds)
            prefs[Keys.TASK_CARD_STYLE] = updated.taskCardStyle.name
            prefs[Keys.DISTRACTION_DIMMING_ENABLED] = updated.distractionDimmingEnabled
            prefs[Keys.HOME_SCREEN_PAGES] = encodeHomeScreenPages(updated.homeScreenPages)
            prefs[Keys.HOME_SCREEN_GRID_ENABLED] = updated.homeScreenGridEnabled
            prefs[Keys.LEFT_PAGE_BLOCKS] = encodeLeftPageBlocks(updated.leftPageBlocks)
            prefs[Keys.CUSTOM_QUOTES] = encodeStringList(updated.customQuotes)
        }
    }

    private fun parseHomeScreenPages(json: String?): List<HomeScreenPage> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val pageJson = jsonArray.getJSONObject(i)
                val itemsArray = pageJson.optJSONArray("items") ?: org.json.JSONArray()
                val items = (0 until itemsArray.length()).mapNotNull { j ->
                    val itemJson = itemsArray.getJSONObject(j)
                    when (itemJson.getString("type")) {
                        "app" -> HomeScreenItem.App(
                            packageName = itemJson.getString("packageName"),
                            gridPosition = itemJson.getInt("gridPosition")
                        )
                        "folder" -> HomeScreenItem.Folder(
                            folderId = itemJson.getString("folderId"),
                            gridPosition = itemJson.getInt("gridPosition")
                        )
                        else -> null
                    }
                }
                HomeScreenPage(
                    id = pageJson.getString("id"),
                    name = pageJson.optString("name", ""),
                    items = items
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun encodeHomeScreenPages(pages: List<HomeScreenPage>): String {
        val jsonArray = org.json.JSONArray()
        pages.forEach { page ->
            val pageJson = org.json.JSONObject()
            pageJson.put("id", page.id)
            pageJson.put("name", page.name)
            val itemsArray = org.json.JSONArray()
            page.items.forEach { item ->
                val itemJson = org.json.JSONObject()
                when (item) {
                    is HomeScreenItem.App -> {
                        itemJson.put("type", "app")
                        itemJson.put("packageName", item.packageName)
                        itemJson.put("gridPosition", item.gridPosition)
                    }
                    is HomeScreenItem.Folder -> {
                        itemJson.put("type", "folder")
                        itemJson.put("folderId", item.folderId)
                        itemJson.put("gridPosition", item.gridPosition)
                    }
                }
                itemsArray.put(itemJson)
            }
            pageJson.put("items", itemsArray)
            jsonArray.put(pageJson)
        }
        return jsonArray.toString()
    }

    private fun parseLeftPageBlocks(json: String?): Map<String, Boolean> {
        val defaults = mapOf(
            "subliminal" to true,
            "quick_note" to true,
            "woop" to true,
            "distraction_top3" to true
        )
        if (json.isNullOrBlank()) return defaults
        return try {
            val obj = JSONObject(json)
            // Merge: existing keys from JSON, new keys get their default value
            defaults.mapValues { (key, default) ->
                if (obj.has(key)) obj.getBoolean(key) else default
            }
        } catch (_: Exception) { defaults }
    }

    private fun encodeLeftPageBlocks(map: Map<String, Boolean>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
