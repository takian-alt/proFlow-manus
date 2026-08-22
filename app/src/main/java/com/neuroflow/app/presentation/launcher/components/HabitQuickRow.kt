package com.neuroflow.app.presentation.launcher.components

/**
 * HabitQuickRow - Horizontal row of recurring tasks due today.
 *
 * Usage example:
 * ```kotlin
 * @Composable
 * fun HomeScreen(viewModel: LauncherViewModel = hiltViewModel()) {
 *     val habitTasks by viewModel.habitTasks.collectAsStateWithLifecycle()
 *
 *     Column(modifier = Modifier.fillMaxSize()) {
 *         HabitQuickRow(
 *             habitTasks = habitTasks,
 *             onCompleteHabit = { task -> viewModel.completeHabit(task) }
 *         )
 *         // Other home screen components...
 *     }
 * }
 * ```
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neuroflow.app.data.local.entity.TaskEntity
import kotlinx.coroutines.delay

/**
 * Habit Quick Row - displays up to 3 recurring tasks due today as tappable chips.
 *
 * Displays:
 * - Task title (max 12 chars)
 * - Habit streak count
 * - Completion indicator
 *
 * Behavior:
 * - Renders with zero height when no habits due today
 * - Shows completion state message when all habits completed
 * - Updates UI within 1 second of completion
 *
 * @param habitTasks List of recurring tasks due today (max 3, pre-sorted by score)
 * @param onCompleteHabit Callback when a habit chip is tapped
 * @param modifier Modifier for the row container
 */
@Composable
fun HabitQuickRow(
    habitTasks: List<TaskEntity>,
    onCompleteHabit: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track completed habit IDs for inline completion state
    var completedHabitIds by remember { mutableStateOf(setOf<String>()) }

    // Reset completed state when habit list changes
    LaunchedEffect(habitTasks.map { it.id }) {
        completedHabitIds = emptySet()
    }

    // Filter out completed habits
    val activeHabits = habitTasks.filter { it.id !in completedHabitIds }

    // Render with zero height when no habits
    if (habitTasks.isEmpty()) {
        Spacer(modifier = modifier.height(0.dp))
        return
    }

    AnimatedVisibility(
        visible = habitTasks.isNotEmpty(),
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section header
            Text(
                text = "Today's Habits",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            // Habit chips or completion message
            if (activeHabits.isEmpty()) {
                // All habits completed
                CompletionStateMessage()
            } else {
                // Active habit chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = activeHabits,
                        key = { it.id }
                    ) { habit ->
                        HabitChip(
                            habit = habit,
                            onComplete = {
                                // Mark completed inline
                                completedHabitIds = completedHabitIds + habit.id
                                // Call repository to complete and recur
                                onCompleteHabit(habit)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Completion state message shown when all habits are completed.
 */
@Composable
private fun CompletionStateMessage() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "All habits completed! 🎉",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Habit chip displaying task title, streak count, and completion indicator.
 */
@Composable
private fun HabitChip(
    habit: TaskEntity,
    onComplete: () -> Unit
) {
    var isCompleting by remember { mutableStateOf(false) }

    // Simulate completion animation
    LaunchedEffect(isCompleting) {
        if (isCompleting) {
            delay(300) // Brief delay for visual feedback
            onComplete()
        }
    }

    FilterChip(
        selected = false,
        onClick = {
            if (!isCompleting) {
                isCompleting = true
            }
        },
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Task title (max 12 chars)
                Text(
                    text = habit.title.take(12),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Habit streak count
                if (habit.habitStreak > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "${habit.habitStreak}🔥",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        leadingIcon = if (isCompleting) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
