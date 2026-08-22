package com.neuroflow.app.presentation.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    val affirmations = preferencesDataStore.preferencesFlow
        .map { it.affirmations }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Emits true when a duplicate was rejected
    private val _duplicateEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val duplicateEvent = _duplicateEvent.asSharedFlow()

    fun addAffirmation(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            var isDuplicate = false
            preferencesDataStore.updatePreferences { prefs ->
                if (prefs.affirmations.any { it.equals(trimmed, ignoreCase = true) }) {
                    isDuplicate = true
                    return@updatePreferences prefs
                }
                prefs.copy(affirmations = prefs.affirmations + trimmed)
            }
            if (isDuplicate) _duplicateEvent.tryEmit(Unit)
        }
    }

    fun removeAffirmation(index: Int) {
        viewModelScope.launch {
            preferencesDataStore.updatePreferences { prefs ->
                if (index < 0 || index >= prefs.affirmations.size) return@updatePreferences prefs
                prefs.copy(affirmations = prefs.affirmations.toMutableList().also { it.removeAt(index) })
            }
        }
    }
}
