package com.neuroflow.app.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DrawerGoalsSection(
    yearlyGoals: List<String>,
    weeklyGoals: List<String>,
    onEditYearly: () -> Unit,
    onEditWeekly: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        GoalGroup(
            title = "This Year",
            goals = yearlyGoals,
            onEdit = onEditYearly
        )
        Spacer(modifier = Modifier.height(12.dp))
        GoalGroup(
            title = "This Week",
            goals = weeklyGoals,
            onEdit = onEditWeekly
        )
    }
}

@Composable
private fun GoalGroup(
    title: String,
    goals: List<String>,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit $title goals",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    val filled = goals.filter { it.isNotBlank() }
    if (filled.isEmpty()) {
        Text(
            text = "Tap edit to add goals",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    } else {
        filled.forEach { goal ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = goal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
