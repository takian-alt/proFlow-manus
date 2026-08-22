package com.neuroflow.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.data.local.entity.SleepLogEntity
import com.neuroflow.app.domain.engine.SleepPressureDetector
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.rememberDatePickerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepLogsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val sleepLogs by viewModel.sleepLogs.collectAsStateWithLifecycle()
    val sleepLogInputError by viewModel.sleepLogInputError.collectAsStateWithLifecycle()

    var logDateMillis by rememberSaveable { mutableStateOf(todayUtcDateMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var logStartHour by remember { mutableStateOf(23) }
    var logStartMinute by remember { mutableStateOf(0) }
    var wakeHour by remember { mutableStateOf(7) }
    var wakeMinute by remember { mutableStateOf(0) }
    var seededFromSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshSleepPressureNow()
    }

    LaunchedEffect(prefs.sleepHour, prefs.wakeUpHour) {
        if (!seededFromSettings) {
            logStartHour = prefs.sleepHour
            wakeHour = prefs.wakeUpHour
            seededFromSettings = true
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = logDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    logDateMillis = datePickerState.selectedDateMillis ?: logDateMillis
                    showDatePicker = false
                    viewModel.clearSleepLogInputError()
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep Logs") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SleepLogsSection("Sleep Pressure") {
                val fatiguePercent = SleepPressureDetector.fatiguePercent(prefs.sleepPressurePoints)
                val fatigueZone = SleepPressureDetector.fatigueZone(prefs.sleepPressurePoints)
                val fatigueZoneLabel = SleepPressureDetector.fatigueZoneLabel(fatigueZone)
                val fatigueColor = fatigueZoneColor(fatigueZone)

                Text(
                    "Current pressure: ${prefs.sleepPressurePoints} pts",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fatiguePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = fatigueColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Fatigue: $fatiguePercent% ($fatigueZoneLabel)",
                    fontSize = 12.sp,
                    color = fatigueColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Recovery happens only from saved sleep logs.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SleepLogsSection("Add Sleep Log") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sleep date", fontSize = 14.sp)
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(formatDateOnly(logDateMillis))
                    }
                }
                SleepNumberRow("Start hour", logStartHour, 0, 23, formatHour = true) {
                    logStartHour = it
                    viewModel.clearSleepLogInputError()
                }
                SleepNumberRow("Start minute", logStartMinute, 0, 59) {
                    logStartMinute = it
                    viewModel.clearSleepLogInputError()
                }
                SleepNumberRow("Wake hour", wakeHour, 0, 23, formatHour = true) {
                    wakeHour = it
                    viewModel.clearSleepLogInputError()
                }
                SleepNumberRow("Wake minute", wakeMinute, 0, 59) {
                    wakeMinute = it
                    viewModel.clearSleepLogInputError()
                }
                Spacer(modifier = Modifier.height(8.dp))

                val previewStartAt = sleepLogStartMillis(logDateMillis, logStartHour, logStartMinute)
                val previewEndAt = sleepLogEndMillis(previewStartAt, wakeHour, wakeMinute)
                val previewDurationMinutes = ((previewEndAt - previewStartAt) / 60_000L).toInt()
                Text(
                    "Calculated sleep: $previewDurationMinutes minutes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Max allowed per log: 16 hours",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Start: ${formatDateTime(previewStartAt)}  |  Wake: ${formatDateTime(previewEndAt)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val startAt = sleepLogStartMillis(logDateMillis, logStartHour, logStartMinute)
                        val endAt = sleepLogEndMillis(startAt, wakeHour, wakeMinute)
                        viewModel.addSleepLog(startAt, endAt)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Sleep Log")
                }

                sleepLogInputError?.let { errorMessage ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            SleepLogsSection("Saved Logs") {
                if (sleepLogs.isEmpty()) {
                    Text(
                        "No sleep logs yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sleepLogs.take(30).forEach { log ->
                        SleepLogRow(
                            log = log,
                            onDelete = { viewModel.deleteSleepLog(log.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepLogsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SleepNumberRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    formatHour: Boolean = false,
    onValueChange: (Int) -> Unit
) {
    val displayValue = if (formatHour) hourLabel(value) else "$value"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > min) onValueChange(value - 1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, "Decrease", modifier = Modifier.size(18.dp))
            }
            Text(
                displayValue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
            IconButton(onClick = { if (value < max) onValueChange(value + 1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, "Increase", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SleepLogRow(
    log: SleepLogEntity,
    onDelete: () -> Unit
) {
    val recovery = SleepPressureDetector.recoveryForSleepMinutes(log.durationMinutes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatDateTime(log.startAt)} (${log.durationMinutes}m)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Recovery: $recovery pts",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete sleep log")
        }
    }
}

private fun hourLabel(hour: Int) = when {
    hour == 0 -> "12 am"
    hour < 12 -> "$hour am"
    hour == 12 -> "12 pm"
    else -> "${hour - 12} pm"
}

private fun sleepLogStartMillis(dateMillis: Long, startHour: Int, startMinute: Int): Long {
    val selectedDate = Instant.ofEpochMilli(dateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()

    return selectedDate
        .atTime(startHour.coerceIn(0, 23), startMinute.coerceIn(0, 59))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun sleepLogEndMillis(startAtMillis: Long, wakeHour: Int, wakeMinute: Int): Long {
    val zoneId = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startAtMillis).atZone(zoneId)
    var wake = start.toLocalDate()
        .atTime(wakeHour.coerceIn(0, 23), wakeMinute.coerceIn(0, 59))
        .atZone(zoneId)

    if (!wake.toInstant().isAfter(start.toInstant())) {
        wake = wake.plusDays(1)
    }

    return wake.toInstant().toEpochMilli()
}

private fun formatDateTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatDateOnly(millis: Long): String {
    val date = Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
}

private fun todayUtcDateMillis(): Long {
    return LocalDate.now(ZoneId.systemDefault())
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

private fun fatigueZoneColor(zone: SleepPressureDetector.FatigueZone) = when (zone) {
    SleepPressureDetector.FatigueZone.RESTED -> NeuroFlowColors.ScheduleText
    SleepPressureDetector.FatigueZone.MODERATE -> NeuroFlowColors.DelegateText
    SleepPressureDetector.FatigueZone.HIGH -> NeuroFlowColors.MapeBad
    SleepPressureDetector.FatigueZone.CRITICAL -> NeuroFlowColors.TrackingRed
}
