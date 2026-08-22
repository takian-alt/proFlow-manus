package com.neuroflow.app.presentation.launcher.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.data.PinnedAppsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LauncherSearchEngine.
 * Queries apps, contacts, settings, and web search in parallel.
 *
 * Performance targets:
 * - Apps and settings: 150ms
 * - Contacts: 300ms
 *
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7, 16.8
 */
@Singleton
class LauncherSearchEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val pinnedAppsDataStore: PinnedAppsDataStore
) : LauncherSearchEngine {

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]"), "")

    override suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext SearchResults()
        }

        val normalizedQuery = normalize(query.trim())

        // Launch all searches in parallel
        val appsDeferred = async { searchApps(normalizedQuery) }
        val contactsDeferred = async { searchContacts(normalizedQuery) }
        val settingsDeferred = async { searchSettings(normalizedQuery) }

        // Await results with timeouts
        val apps = withTimeoutOrNull(150) { appsDeferred.await() } ?: emptyList()
        val contacts = withTimeoutOrNull(300) { contactsDeferred.await() } ?: emptyList()
        val settings = withTimeoutOrNull(150) { settingsDeferred.await() } ?: emptyList()

        SearchResults(
            apps = apps,
            contacts = contacts,
            settings = settings,
            webQuery = query
        )
    }

    /**
     * Search installed apps from AppRepository.
     * Returns results within 150ms.
     *
     * Requirements: 16.1, 16.3
     */
    private suspend fun searchApps(query: String): List<com.neuroflow.app.presentation.launcher.data.AppInfo> {
        return try {
            val allApps = appRepository.apps.first()
            allApps.filter { app ->
                normalize(app.label).contains(query) ||
                normalize(app.packageName).contains(query)
            }.take(10) // Limit to top 10 results
        } catch (e: Exception) {
            android.util.Log.e("LauncherSearchEngine", "Error searching apps", e)
            emptyList()
        }
    }

    /**
     * Search contacts via ContactsContract if READ_CONTACTS permission granted.
     * Returns results within 300ms.
     * Silently returns empty list if permission denied.
     *
     * Requirements: 16.1, 16.3, 16.4, 16.5
     */
    private suspend fun searchContacts(query: String): List<ContactResult> {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return emptyList() // Silently omit contacts section
        }

        return try {
            val contacts = mutableListOf<ContactResult>()
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val selectionArgs = arrayOf("%${query.trim()}%")
            val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val lookupKeyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    while (it.moveToNext() && contacts.size < 10) { // Limit to 10 results
                        val id = it.getLong(idIndex)
                        val displayName = it.getString(nameIndex) ?: continue
                        val lookupKey = it.getString(lookupKeyIndex) ?: continue
                        val hasPhone = it.getInt(hasPhoneIndex) > 0

                        // Get primary phone number if available
                        val phoneNumber = if (hasPhone) {
                            getPhoneNumber(id)
                        } else {
                            null
                        }

                        contacts.add(
                            ContactResult(
                                id = id,
                                displayName = displayName,
                                phoneNumber = phoneNumber,
                                lookupKey = lookupKey
                            )
                        )
                    }
                }
            } finally {
                cursor?.close()
            }

            contacts
        } catch (e: Exception) {
            android.util.Log.e("LauncherSearchEngine", "Error searching contacts", e)
            emptyList()
        }
    }

    /**
     * Get primary phone number for a contact.
     */
    private fun getPhoneNumber(contactId: Long): String? {
        return try {
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
            val selectionArgs = arrayOf(contactId.toString())

            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        it.getString(numberIndex)
                    } else {
                        null
                    }
                }
            } finally {
                cursor?.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Search system settings.
     * Returns results within 150ms.
     *
     * Requirements: 16.1, 16.6, 16.7, 16.8
     */
    private suspend fun searchSettings(query: String): List<SettingsResult> {
        return try {
            // Common system settings
            val commonSettings = listOf(
                SettingsResult(
                    title = "Wi-Fi",
                    action = Settings.ACTION_WIFI_SETTINGS,
                    description = "Manage Wi-Fi connections"
                ),
                SettingsResult(
                    title = "Bluetooth",
                    action = Settings.ACTION_BLUETOOTH_SETTINGS,
                    description = "Manage Bluetooth devices"
                ),
                SettingsResult(
                    title = "Display",
                    action = Settings.ACTION_DISPLAY_SETTINGS,
                    description = "Brightness, wallpaper, sleep"
                ),
                SettingsResult(
                    title = "Sound",
                    action = Settings.ACTION_SOUND_SETTINGS,
                    description = "Volume, ringtone, vibration"
                ),
                SettingsResult(
                    title = "Battery",
                    action = Settings.ACTION_BATTERY_SAVER_SETTINGS,
                    description = "Battery usage and optimization"
                ),
                SettingsResult(
                    title = "Storage",
                    action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                    description = "Storage usage and management"
                ),
                SettingsResult(
                    title = "Apps",
                    action = Settings.ACTION_APPLICATION_SETTINGS,
                    description = "Manage installed apps"
                ),
                SettingsResult(
                    title = "Notifications",
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    description = "Notification settings"
                ),
                SettingsResult(
                    title = "Location",
                    action = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                    description = "Location services"
                ),
                SettingsResult(
                    title = "Security",
                    action = Settings.ACTION_SECURITY_SETTINGS,
                    description = "Screen lock, encryption"
                ),
                SettingsResult(
                    title = "Privacy",
                    action = Settings.ACTION_PRIVACY_SETTINGS,
                    description = "Privacy controls"
                ),
                SettingsResult(
                    title = "Accounts",
                    action = Settings.ACTION_SYNC_SETTINGS,
                    description = "Manage accounts and sync"
                ),
                SettingsResult(
                    title = "Accessibility",
                    action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                    description = "Accessibility features"
                ),
                SettingsResult(
                    title = "Language",
                    action = Settings.ACTION_LOCALE_SETTINGS,
                    description = "Language and input"
                ),
                SettingsResult(
                    title = "Date & Time",
                    action = Settings.ACTION_DATE_SETTINGS,
                    description = "Date, time, and time zone"
                ),
                SettingsResult(
                    title = "Developer Options",
                    action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                    description = "Developer settings"
                ),
                SettingsResult(
                    title = "About Phone",
                    action = Settings.ACTION_DEVICE_INFO_SETTINGS,
                    description = "Device information"
                )
            )

            // Filter settings by query
            commonSettings.filter { setting ->
                normalize(setting.title).contains(query) ||
                setting.description?.let { normalize(it).contains(query) } == true
            }.take(5) // Limit to top 5 results
        } catch (e: Exception) {
            android.util.Log.e("LauncherSearchEngine", "Error searching settings", e)
            emptyList()
        }
    }
}
