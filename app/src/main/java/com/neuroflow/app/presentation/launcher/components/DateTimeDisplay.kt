package com.neuroflow.app.presentation.launcher.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.presentation.launcher.domain.ClockStyle
import com.neuroflow.app.presentation.launcher.theme.LocalLauncherTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Display current date and time with minute-boundary updates.
 *
 * Updates once per minute aligned to the minute boundary using:
 * delay(60_000 - (System.currentTimeMillis() % 60_000))
 *
 * Supports Digital and Minimal clock styles from LauncherTheme.
 *
 * @param modifier Modifier for the display container
 */
@Composable
fun DateTimeDisplay(
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Update time at minute boundaries
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            // Calculate delay to next minute boundary
            val delayMs = 60_000 - (currentTime % 60_000)
            delay(delayMs)
        }
    }

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply {
            timeInMillis = currentTime
        }
    }

    when (theme.clockStyle) {
        ClockStyle.DIGITAL -> DigitalClock(calendar, modifier)
        ClockStyle.MINIMAL -> MinimalClock(calendar, modifier)
    }
}

/**
 * Digital clock style: full date and time display.
 */
@Composable
private fun DigitalClock(
    calendar: Calendar,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    val timeText = remember(calendar) { timeFormat.format(calendar.time) }
    val dateText = remember(calendar) { dateFormat.format(calendar.time) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag("date_time_display"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 64.sp,
                fontWeight = FontWeight.Light
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Minimal clock style: time only, compact display.
 */
@Composable
private fun MinimalClock(
    calendar: Calendar,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val timeText = remember(calendar) { timeFormat.format(calendar.time) }

    Text(
        text = timeText,
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = 48.sp,
            fontWeight = FontWeight.Light
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(16.dp)
            .testTag("date_time_display")
    )
}
