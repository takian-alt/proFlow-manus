package com.neuroflow.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.repository.SessionRepository
import com.neuroflow.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val completedTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            taskRepository.observeCompletedTasks().collect { tasks ->
                _uiState.update { it.copy(completedTasks = tasks, isLoading = false) }
            }
        }
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
