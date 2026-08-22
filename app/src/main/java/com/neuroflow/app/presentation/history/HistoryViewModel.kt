package com.neuroflow.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class HistoryDateRange {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS
}

internal fun filterTasksByDateRange(
    tasks: List<TaskEntity>,
    range: HistoryDateRange,
    nowMillis: Long = System.currentTimeMillis()
): List<TaskEntity> {
    if (range == HistoryDateRange.ALL) return tasks

    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayStart = calendar.timeInMillis
    val fromMillis = when (range) {
        HistoryDateRange.TODAY -> todayStart
        HistoryDateRange.LAST_7_DAYS -> Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis
        HistoryDateRange.LAST_30_DAYS -> Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -29)
        }.timeInMillis
        HistoryDateRange.ALL -> Long.MIN_VALUE
    }

    return tasks.filter { task ->
        val completedAt = task.completedAt ?: return@filter false
        completedAt >= fromMillis
    }
}

data class HistoryUiState(
    val completedTasks: List<TaskEntity> = emptyList(),
    val selectedDateRange: HistoryDateRange = HistoryDateRange.ALL,
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val selectedDateRange = MutableStateFlow(HistoryDateRange.ALL)
    private val selectedTag = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                taskRepository.observeCompletedTasks(),
                userPreferencesDataStore.preferencesFlow.map { it.tagCatalog },
                selectedDateRange,
                selectedTag
            ) { tasks, catalogTags, range, tag ->
                val dateFiltered = filterTasksByDateRange(tasks, range)
                val tagFiltered = if (tag.isNullOrBlank()) {
                    dateFiltered
                } else {
                    dateFiltered.filter { task ->
                        task.tags.split(",").map { it.trim() }.any { it.equals(tag, ignoreCase = true) }
                    }
                }
                val allTags = (catalogTags + tasks
                    .flatMap { it.tags.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                )
                    .distinctBy { it.lowercase() }
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
                Triple(tagFiltered, range, allTags)
            }.collect { (tasks, range, tags) ->
                _uiState.update {
                    it.copy(
                        completedTasks = tasks,
                        selectedDateRange = range,
                        availableTags = tags,
                        selectedTag = selectedTag.value,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateDateRange(range: HistoryDateRange) {
        selectedDateRange.value = range
    }

    fun updateTagFilter(tag: String?) {
        selectedTag.value = tag
    }

    suspend fun getSessionsForTask(taskId: String): List<TimeSessionEntity> {
        return sessionRepository.getByTaskId(taskId)
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { taskRepository.delete(task) }
    }

    fun restoreTask(task: TaskEntity) {
        viewModelScope.launch { taskRepository.insert(task) }
    }
}
