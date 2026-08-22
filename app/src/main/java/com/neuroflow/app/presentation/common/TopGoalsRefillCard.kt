package com.neuroflow.app.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

enum class GoalPeriod { WEEKLY, YEARLY }

@Composable
fun TopGoalsRefillCard(
    period: GoalPeriod,
    existingGoals: List<String> = emptyList(),
    onConfirm: (List<String>) -> Unit,
    onSkip: () -> Unit
) {
    val label = if (period == GoalPeriod.YEARLY) "year" else "week"
    // Use existingGoals as key so fields populate correctly even if prefs load after first frame
    val goals = remember(existingGoals) {
        mutableStateListOf(
            existingGoals.getOrElse(0) { "" },
            existingGoals.getOrElse(1) { "" },
            existingGoals.getOrElse(2) { "" }
        )
    }
    // FocusRequesters are stable — safe to create once
    val focusRequesters = remember { List(3) { FocusRequester() } }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "What are your top 3 goals for this $label?",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "These will be pinned in your drawer as a daily reminder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            goals.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { goals[index] = it },
                    label = { Text("Goal ${index + 1}") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[index]),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (index < 2) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            // Safe: move focus via FocusManager, not direct requestFocus
                            focusManager.moveFocus(FocusDirection.Down)
                        },
                        onDone = {
                            focusManager.clearFocus()
                            if (goals.any { it.isNotBlank() }) onConfirm(goals.map { it.trim() })
                        }
                    )
                )
                if (index < 2) Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onSkip) {
                    Text("Skip for now")
                }
                Button(
                    onClick = { onConfirm(goals.map { it.trim() }) },
                    enabled = goals.any { it.isNotBlank() }
                ) {
                    Text("Save goals")
                }
            }
        }
    }
}
