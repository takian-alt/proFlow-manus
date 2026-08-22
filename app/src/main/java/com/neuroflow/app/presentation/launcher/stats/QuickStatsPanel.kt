package com.neuroflow.app.presentation.launcher.stats

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.domain.engine.AnalyticsEngine
import com.neuroflow.app.domain.model.Quadrant
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel
import com.neuroflow.app.presentation.launcher.hyperfocus.screens.RewardSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * QuickStatsPanel - Slide-in productivity summary panel.
 *
 * Displays:
 * - Tasks completed today
 * - Focus minutes today
 * - Current daily streak
 * - Quadrant breakdown of completed tasks for current day
 * - Seinfeld chain for current month
 *
 * Slides in from left edge (right in RTL) with spring animation.
 * Dismisses via right swipe or tap outside.
 *
 * Requirements: 22.1, 22.2, 22.3, 22.4, 22.5, 22.6
 *
 * @param visible Whether the panel is visible
 * @param summary AnalyticsSummary from AnalyticsEngine.buildSummary()
 * @param onDismiss Callback when panel is dismissed
 * @param onOpenFullAnalytics Callback to open full analytics screen
 * @param modifier Modifier for the panel container
 */
@Composable
fun QuickStatsPanel(
    visible: Boolean,
    summary: AnalyticsEngine.AnalyticsSummary?,
    onDismiss: () -> Unit,
    onOpenFullAnalytics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Panel width: 85% of screen width, max 400dp
    val panelWidth = minOf(screenWidth * 0.85f, 400.dp)

    // Animate panel offset based on visibility and layout direction
    val targetOffset = if (visible) 0f else {
        // Slide out to the start edge (left in LTR, right in RTL)
        if (layoutDirection == LayoutDirection.Ltr) {
            -panelWidth.value
        } else {
            panelWidth.value
        }
    }

    val offsetX by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "QuickStatsPanel offset"
    )

    // Dismiss on tap outside
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                )
        )
    }

    // Panel content
    Surface(
        modifier = modifier
            .width(panelWidth)
            .fillMaxHeight()
            .graphicsLayer {
                translationX = offsetX
            }
            .pointerInput(Unit) {
                // Detect swipe to dismiss
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    // Swipe right in LTR or left in RTL to dismiss
                    val dismissThreshold = 50f
                    val shouldDismiss = if (layoutDirection == LayoutDirection.Ltr) {
                        dragAmount > dismissThreshold
                    } else {
                        dragAmount < -dismissThreshold
                    }
                    if (shouldDismiss) {
                        onDismiss()
                    }
                }
            },
        shape = RoundedCornerShape(
            topEnd = if (layoutDirection == LayoutDirection.Ltr) 16.dp else 0.dp,
            bottomEnd = if (layoutDirection == LayoutDirection.Ltr) 16.dp else 0.dp,
            topStart = if (layoutDirection == LayoutDirection.Rtl) 16.dp else 0.dp,
            bottomStart = if (layoutDirection == LayoutDirection.Rtl) 16.dp else 0.dp
        ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        if (summary != null) {
            QuickStatsPanelContent(
                summary = summary,
                onDismiss = onDismiss,
                onOpenFullAnalytics = onOpenFullAnalytics
            )
        } else {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * QuickStatsPanel content - scrollable stats display.
 *
 * Requirements: 6.4
 */
@Composable
private fun QuickStatsPanelContent(
    summary: AnalyticsEngine.AnalyticsSummary,
    onDismiss: () -> Unit,
    onOpenFullAnalytics: () -> Unit,
    hyperFocusViewModel: HyperFocusViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()

    val hyperFocusPrefs by hyperFocusViewModel.hyperFocusPrefs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Stats",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }

        // Today's stats
        TodayStatsCard(summary)

        // Quadrant breakdown
        QuadrantBreakdownCard(summary)

        // Seinfeld chain
        SeinfeldChainCard(summary)

        // Open full analytics button
        Button(
            onClick = onOpenFullAnalytics,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Full Analytics")
        }

        // Reward section — shown only when Hyper Focus is active
        if (hyperFocusPrefs.isActive) {
            RewardSection(viewModel = hyperFocusViewModel)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Today's stats card - tasks completed, focus minutes, streak.
 */
@Composable
private fun TodayStatsCard(summary: AnalyticsEngine.AnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    value = "${summary.completedToday}",
                    label = "Tasks"
                )
                StatColumn(
                    value = formatMinutes(summary.focusMinutesToday),
                    label = "Focus"
                )
                StatColumn(
                    value = "${summary.currentStreak} 🔥",
                    label = "Streak"
                )
            }
        }
    }
}

/**
 * Quadrant breakdown card - completed tasks by quadrant for today.
 */
@Composable
private fun QuadrantBreakdownCard(summary: AnalyticsEngine.AnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Today's Quadrant Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            QuadrantRow(
                quadrant = Quadrant.DO_FIRST,
                count = summary.tasksByQuadrant[Quadrant.DO_FIRST] ?: 0,
                label = "Do First",
                color = MaterialTheme.colorScheme.error
            )
            QuadrantRow(
                quadrant = Quadrant.SCHEDULE,
                count = summary.tasksByQuadrant[Quadrant.SCHEDULE] ?: 0,
                label = "Schedule",
                color = MaterialTheme.colorScheme.primary
            )
            QuadrantRow(
                quadrant = Quadrant.DELEGATE,
                count = summary.tasksByQuadrant[Quadrant.DELEGATE] ?: 0,
                label = "Delegate",
                color = MaterialTheme.colorScheme.tertiary
            )
            QuadrantRow(
                quadrant = Quadrant.ELIMINATE,
                count = summary.tasksByQuadrant[Quadrant.ELIMINATE] ?: 0,
                label = "Eliminate",
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

/**
 * Quadrant row - single quadrant with count.
 */
@Composable
private fun QuadrantRow(
    quadrant: Quadrant,
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Seinfeld chain card - monthly streak calendar.
 */
@Composable
private fun SeinfeldChainCard(summary: AnalyticsEngine.AnalyticsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "This Month",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Generate Seinfeld chain for current month
            SeinfeldChain(currentStreak = summary.currentStreak)
        }
    }
}

/**
 * Seinfeld chain - monthly calendar with filled/empty circles.
 *
 * Displays the current month with circles for each day:
 * - Filled circle: day with completed tasks (part of current streak)
 * - Empty circle: day without completed tasks or future day
 *
 * @param currentStreak Current daily streak count
 */
@Composable
private fun SeinfeldChain(currentStreak: Int) {
    val calendar = Calendar.getInstance()
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Calculate which days are part of the streak
    // Streak days are the last N days up to today
    val streakStartDay = maxOf(1, currentDay - currentStreak + 1)

    // Display days in a grid (7 columns for week days)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Week day headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Calculate first day of month offset
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday

        // Display days in rows
        var dayCounter = 1
        var weekOffset = firstDayOfWeek

        while (dayCounter <= daysInMonth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 0 until 7) {
                    if (weekOffset > 0) {
                        // Empty space before first day
                        Spacer(modifier = Modifier.width(32.dp))
                        weekOffset--
                    } else if (dayCounter <= daysInMonth) {
                        val day = dayCounter
                        val isFilled = day in streakStartDay..currentDay
                        val isFuture = day > currentDay

                        DayCircle(
                            day = day,
                            isFilled = isFilled,
                            isFuture = isFuture
                        )
                        dayCounter++
                    } else {
                        // Empty space after last day
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                }
            }
        }
    }
}

/**
 * Day circle - single day in Seinfeld chain.
 */
@Composable
private fun DayCircle(
    day: Int,
    isFilled: Boolean,
    isFuture: Boolean
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFilled -> MaterialTheme.colorScheme.primary
                    isFuture -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$day",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isFilled -> MaterialTheme.colorScheme.onPrimary
                isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            },
            fontSize = 11.sp
        )
    }
}

/**
 * Stat column - value and label.
 */
@Composable
private fun StatColumn(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Format minutes to human-readable string.
 */
private fun formatMinutes(minutes: Float): String {
    val hours = (minutes / 60).toInt()
    val mins = (minutes % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}
