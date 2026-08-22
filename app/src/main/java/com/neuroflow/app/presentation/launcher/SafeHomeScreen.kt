package com.neuroflow.app.presentation.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neuroflow.app.presentation.launcher.components.DateTimeDisplay

/**
 * SafeHomeScreen - Minimal fallback composable for crash recovery.
 *
 * Renders only DateTimeDisplay and a placeholder for DockRow when the main
 * HomeScreen composable crashes. This ensures the launcher remains functional
 * as a home screen even when Compose-level exceptions occur.
 *
 * Requirements: 14.10
 *
 * @param modifier Modifier for the safe home screen container
 */
@Composable
fun SafeHomeScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top section - DateTimeDisplay
        DateTimeDisplay(
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        // Bottom section - DockRow placeholder
        // In a real crash scenario, we'd want to render a minimal dock
        // For now, just reserve the space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        )
    }
}
