package com.neuroflow.app.presentation.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.neuroflow.app.presentation.launcher.domain.AdaptiveIconProcessor
import com.neuroflow.app.presentation.launcher.domain.IconShape
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class IconPackInfo(
    val packageName: String,
    val label: String
)

/**
 * Manager for icon pack support (ADW and NOVA formats).
 *
 * Queries icon packs via org.adw.launcher.THEMES intent, parses appfilter.xml,
 * and provides themed icons with adaptive icon shape masking.
 *
 * All loading is on IO threads. Falls back to system icon when themed icon not found.
 */
@Singleton
class IconPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageManager: PackageManager
) {
    // Map of "packageName/activityName" -> drawable resource name
    private var iconMap: Map<String, String> = emptyMap()
    private var currentIconPackPackage: String? = null

    private val _installedPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val installedPacks: StateFlow<List<IconPackInfo>> = _installedPacks.asStateFlow()

    /**
     * Query all installed icon packs.
     * Must be called on IO thread.
     */
    suspend fun queryInstalledPacks() = withContext(Dispatchers.IO) {
        try {
            val intent = Intent("org.adw.launcher.THEMES")
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)

            val packs = resolveInfos.map { resolveInfo ->
                IconPackInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString()
                )
            }

            _installedPacks.value = packs
        } catch (e: Exception) {
            android.util.Log.e("IconPackManager", "Error querying icon packs", e)
            _installedPacks.value = emptyList()
        }
    }

    /**
     * Load icon pack and parse appfilter.xml.
     * Must be called on IO thread.
     */
    suspend fun loadIconPack(packageName: String) = withContext(Dispatchers.IO) {
        try {
            if (packageName == currentIconPackPackage && iconMap.isNotEmpty()) {
                // Already loaded
                return@withContext
            }

            val resources = packageManager.getResourcesForApplication(packageName)
            val appFilterId = resources.getIdentifier("appfilter", "xml", packageName)

            if (appFilterId == 0) {
                android.util.Log.w("IconPackManager", "No appfilter.xml found in $packageName")
                iconMap = emptyMap()
                currentIconPackPackage = packageName
                return@withContext
            }

            val parser = resources.getXml(appFilterId)
            iconMap = parseAppFilter(parser)
            currentIconPackPackage = packageName

            android.util.Log.d("IconPackManager", "Loaded icon pack $packageName with ${iconMap.size} icons")
        } catch (e: Exception) {
            android.util.Log.e("IconPackManager", "Error loading icon pack $packageName", e)
            iconMap = emptyMap()
            currentIconPackPackage = null
        }
    }

    /**
     * Parse appfilter.xml (ADW and NOVA formats).
     * Returns map of "packageName/activityName" -> drawable resource name.
     */
    private fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
        val map = mutableMapOf<String, String>()

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    // ADW format: component="ComponentInfo{packageName/activityName}" drawable="icon_name"
                    // NOVA format: component="packageName/activityName" drawable="icon_name"
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")

                    if (component != null && drawable != null) {
                        // Extract packageName/activityName from component
                        val key = extractComponentKey(component)
                        if (key != null) {
                            map[key] = drawable
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e("IconPackManager", "Error parsing appfilter.xml", e)
        }

        return map
    }

    /**
     * Extract "packageName/activityName" from component string.
     * Handles both ADW format (ComponentInfo{...}) and NOVA format (direct).
     */
    private fun extractComponentKey(component: String): String? {
        return try {
            when {
                component.startsWith("ComponentInfo{") -> {
                    // ADW format: ComponentInfo{packageName/activityName}
                    component.substringAfter("{").substringBefore("}")
                }
                component.contains("/") -> {
                    // NOVA format: packageName/activityName
                    component
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load themed icon for the specified app.
     * Returns null if not found in pack or if no pack is loaded.
     * Falls back to system icon when themed icon not found.
     *
     * Must be called on IO thread.
     */
    suspend fun loadIcon(
        packageName: String,
        activityName: String,
        shape: IconShape
    ): Drawable? = withContext(Dispatchers.IO) {
        try {
            if (currentIconPackPackage == null || iconMap.isEmpty()) {
                return@withContext null
            }

            val key = "$packageName/$activityName"
            val drawableName = iconMap[key] ?: return@withContext null

            val iconPackResources = packageManager.getResourcesForApplication(currentIconPackPackage!!)
            val drawableId = iconPackResources.getIdentifier(
                drawableName,
                "drawable",
                currentIconPackPackage
            )

            if (drawableId == 0) {
                return@withContext null
            }

            val drawable = iconPackResources.getDrawable(drawableId, null)

            // Apply adaptive icon shape mask via AdaptiveIconProcessor
            val sizePx = (48 * context.resources.displayMetrics.density).toInt()
            val bitmap = AdaptiveIconProcessor.process(drawable, shape, sizePx, context)

            // Convert bitmap back to drawable
            android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
        } catch (e: Exception) {
            android.util.Log.e("IconPackManager", "Error loading icon for $packageName/$activityName", e)
            null
        }
    }

    /**
     * Invalidate icon cache.
     * Called when icon pack or icon shape changes.
     */
    fun invalidateCache() {
        iconMap = emptyMap()
        currentIconPackPackage = null
    }
}
