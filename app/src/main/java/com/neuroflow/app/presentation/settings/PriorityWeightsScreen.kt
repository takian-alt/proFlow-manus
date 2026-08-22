package com.neuroflow.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityWeightsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Priority Weights") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Adjust how each factor influences task scoring",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WeightSlider(
                label = "Quadrant (DO FIRST > others)",
                value = prefs.weightQuadrant,
                onValueChange = { viewModel.updatePreferences { p -> p.copy(weightQuadrant = it) } }
            )

            WeightSlider(
                label = "Deadline Urgency",
                value = prefs.weightDeadlineUrgency,
                onValueChange = { viewModel.updatePreferences { p -> p.copy(weightDeadlineUrgency = it) } }
            )

            WeightSlider(
                label = "Priority Level (High > Medium > Low)",
                value = prefs.weightPriorityLevel,
                onValueChange = { viewModel.updatePreferences { p -> p.copy(weightPriorityLevel = it) } }
            )

            WeightSlider(
                label = "Duration (shorter tasks boost)",
                value = prefs.weightDuration,
                onValueChange = { viewModel.updatePreferences { p -> p.copy(weightDuration = it) } }
            )

            WeightSlider(
                label = "Impact (higher impact boost)",
                value = prefs.weightImpact,
                onValueChange = { viewModel.updatePreferences { p -> p.copy(weightImpact = it) } }
            )

            WeightSlider(
                label = "Focus Mode (active task boost)",
                value = prefs.weightFocusMode,
                onValueChange = { viewModel.updatePreferences { p -> p.copy(weightFocusMode = it) } }
            )

            // Reset to defaults
            OutlinedButton(
                onClick = {
                    viewModel.updatePreferences { p ->
                        p.copy(
                            weightQuadrant = 1.0f,
                            weightDeadlineUrgency = 1.0f,
                            weightPriorityLevel = 1.0f,
                            weightDuration = 1.0f,
                            weightImpact = 1.0f,
                            weightFocusMode = 1.0f
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset to Defaults")
            }

            // Tip
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = NeuroFlowColors.PurpleLight)
            ) {
                Text(
                    text = "💡 Tip: Increase weights for factors that matter most to your workflow. Lower impact and focus mode weights if you want traditional urgency-based sorting.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp,
                    color = NeuroFlowColors.PurpleDark
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WeightSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NeuroFlowColors.Purple
                ) {
                    Text(
                        "${String.format("%.1f", value)}x",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = { onValueChange(it) },
                valueRange = 0f..2f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = NeuroFlowColors.Purple,
                    activeTrackColor = NeuroFlowColors.Purple
                )
            )
        }
    }
}
