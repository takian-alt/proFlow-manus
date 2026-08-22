package com.neuroflow.app.presentation.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.entity.WoopEntity
import com.neuroflow.app.data.repository.WoopRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MiniWoopUiState(
    val wish: String = "",
    val outcome: String = "",
    val obstacle: String = "",
    val plan: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class MiniWoopViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val woopRepository: WoopRepository
) : ViewModel() {

    private val taskId: String = savedStateHandle["taskId"] ?: ""
    private val _uiState = MutableStateFlow(MiniWoopUiState())
    val uiState: StateFlow<MiniWoopUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = woopRepository.getByTaskId(taskId)
            _uiState.value = MiniWoopUiState(
                wish = existing?.wish ?: "",
                outcome = existing?.outcome ?: "",
                obstacle = existing?.obstacle ?: "",
                plan = existing?.plan ?: "",
                isLoading = false
            )
        }
    }

    fun submit(wish: String, outcome: String, obstacle: String, plan: String) {
        viewModelScope.launch {
            woopRepository.upsert(WoopEntity(taskId = taskId, wish = wish, outcome = outcome, obstacle = obstacle, plan = plan))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniWoopReflectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: MiniWoopViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var wish by remember(uiState.wish) { mutableStateOf(uiState.wish) }
    var outcome by remember(uiState.outcome) { mutableStateOf(uiState.outcome) }
    var obstacle by remember(uiState.obstacle) { mutableStateOf(uiState.obstacle) }
    var plan by remember(uiState.plan) { mutableStateOf(uiState.plan) }
    var progressSeconds by remember { mutableStateOf(0) }

    // 30-second suggested timer
    LaunchedEffect(Unit) {
        while (progressSeconds < 30) {
            kotlinx.coroutines.delay(1_000)
            progressSeconds++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reflect on this task") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Suggested 30-second reflection timer
            Column {
                Text("Suggested reflection time", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (progressSeconds / 30f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${minOf(progressSeconds, 30)}s / 30s", fontSize = 11.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("WOOP Reflection", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = wish,
                onValueChange = { wish = it },
                label = { Text("Wish — what do you want?") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = outcome,
                onValueChange = { outcome = it },
                label = { Text("Outcome — best result?") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = obstacle,
                onValueChange = { obstacle = it },
                label = { Text("Obstacle — what's in the way?") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = plan,
                onValueChange = { plan = it },
                label = { Text("Plan — if obstacle, then I will...") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.submit(wish, outcome, obstacle, plan)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = wish.isNotBlank()
            ) {
                Text("Save Reflection")
            }
        }
    }
}

