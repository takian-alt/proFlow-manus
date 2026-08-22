package com.neuroflow.app.presentation.launcher.domain

import com.neuroflow.app.presentation.launcher.data.AppInfo

/**
 * Universal search engine for the launcher.
 * Provides a single search entry point that queries apps, contacts, settings, and web.
 *
 * Requirements: 16.1, 26.7
 */
interface LauncherSearchEngine {
    /**
     * Search across all data sources.
     * Returns results grouped by type within performance constraints:
     * - Apps and settings: 150ms
     * - Contacts: 300ms
     *
     * @param query Search query string
     * @return SearchResults with grouped results
     */
    suspend fun search(query: String): SearchResults
}

/**
 * Search results grouped by type.
 *
 * @property apps Matching installed apps
 * @property contacts Matching contacts (empty if READ_CONTACTS not granted)
 * @property settings Matching system settings
 * @property webQuery Web search query (null if query is empty)
 */
data class SearchResults(
    val apps: List<AppInfo> = emptyList(),
    val contacts: List<ContactResult> = emptyList(),
    val settings: List<SettingsResult> = emptyList(),
    val webQuery: String? = null
)

/**
 * Contact search result.
 *
 * @property id Contact ID from ContactsContract
 * @property displayName Contact display name
 * @property phoneNumber Primary phone number (null if none)
 * @property lookupKey Lookup key for stable contact reference
 */
data class ContactResult(
    val id: Long,
    val displayName: String,
    val phoneNumber: String?,
    val lookupKey: String
)

/**
 * Settings search result.
 *
 * @property title Settings screen title
 * @property action Intent action to open the settings screen
 * @property description Optional description of the setting
 */
data class SettingsResult(
    val title: String,
    val action: String,
    val description: String? = null
)
