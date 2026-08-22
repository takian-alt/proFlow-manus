package com.neuroflow.app.presentation.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuroflow.app.domain.engine.WoopEngine
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WoopPromptSheet(
    onSubmit: (wish: String, outcome: String, obstacle: String, plan: String) -> Unit,
    onSkip: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var wish by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("") }
    var obstacle by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    var planEdited by remember { mutableStateOf(false) }

    // Auto-populate plan from obstacle unless user has manually edited it
    LaunchedEffect(obstacle) {
        if (!planEdited) {
            plan = WoopEngine.generateIfThenPlan(obstacle)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "WOOP It 🎯",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Wish · Outcome · Obstacle · Plan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = wish,
                onValueChange = { wish = it },
                label = { Text("Wish") },
                placeholder = { Text("What do you wish to achieve?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuroFlowColors.Purple,
                    focusedLabelColor = NeuroFlowColors.Purple
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = outcome,
                onValueChange = { outcome = it },
                label = { Text("Outcome") },
                placeholder = { Text("What's the best outcome?") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuroFlowColors.Purple,
                    focusedLabelColor = NeuroFlowColors.Purple
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = obstacle,
                onValueChange = { obstacle = it },
                label = { Text("Obstacle") },
                placeholder = { Text("What might get in the way?") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuroFlowColors.Purple,
                    focusedLabelColor = NeuroFlowColors.Purple
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = plan,
                onValueChange = {
                    plan = it
                    planEdited = true
                },
                label = { Text("Plan (if-then)") },
                placeholder = { Text("If obstacle, then I will…") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuroFlowColors.Purple,
                    focusedLabelColor = NeuroFlowColors.Purple
                )
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip")
                }
                Button(
                    onClick = { onSubmit(wish, outcome, obstacle, plan) },
                    modifier = Modifier.weight(1f),
                    enabled = wish.isNotBlank() && obstacle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple)
                ) {
                    Text("Submit")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
