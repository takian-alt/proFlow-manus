package com.neuroflow.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskTagViewModel @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    val tags: StateFlow<List<String>> = userPreferencesDataStore.preferencesFlow
        .map { prefs -> prefs.tagCatalog }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTags(newTags: Collection<String>) {
        val cleaned = normalizeTags(newTags)
        if (cleaned.isEmpty()) return

        viewModelScope.launch {
            userPreferencesDataStore.mergeTagCatalog(cleaned)
        }
    }

    fun removeTag(tag: String) {
        val cleaned = tag.trim()
        if (cleaned.isBlank()) return

        viewModelScope.launch {
            userPreferencesDataStore.removeTagFromCatalog(cleaned)
        }
    }

    fun seedFromTasks(taskTags: Collection<String>) {
        addTags(taskTags)
    }

    private fun normalizeTags(tags: Collection<String>): List<String> {
        return tags.mapNotNull { it.trim().takeIf(String::isNotBlank) }
    }
}