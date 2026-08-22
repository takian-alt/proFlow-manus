package com.neuroflow.app.presentation.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

data class OnboardingData(
    val identityLabel: String = "",
    val peakEnergyPeriod: String = "morning",
    val topGoal: String = "",
    val firstTask: String = "",
    val wakeUpHour: Int = 7
)

@Composable
fun OnboardingScreen(
    onComplete: (OnboardingData) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(OnboardingData()) }
    val totalSteps = 5

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                repeat(totalSteps) { index ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (index <= currentStep) NeuroFlowColors.Purple else Color.LightGray,
                        modifier = Modifier.size(if (index == currentStep) 12.dp else 8.dp)
                    ) {}
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (currentStep) {
                    0 -> IdentityStep(data.identityLabel) { data = data.copy(identityLabel = it) }
                    1 -> EnergyStep(data.peakEnergyPeriod) { data = data.copy(peakEnergyPeriod = it) }
                    2 -> GoalStep(data.topGoal) { data = data.copy(topGoal = it) }
                    3 -> FirstTaskStep(data.firstTask) { data = data.copy(firstTask = it) }
                    4 -> WakeTimeStep(data.wakeUpHour) { data = data.copy(wakeUpHour = it) }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    TextButton(onClick = { currentStep-- }) {
                        Text("Back")
                    }
                } else {
                    TextButton(onClick = { onComplete(data) }) {
                        Text("Skip")
                    }
                }

                Button(
                    onClick = {
                        if (currentStep < totalSteps - 1) {
                            currentStep++
                        } else {
                            onComplete(data)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(if (currentStep == totalSteps - 1) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun IdentityStep(value: String, onValueChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "What kind of person do you want to become?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("I am a...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeuroFlowColors.Purple,
                focusedLabelColor = NeuroFlowColors.Purple
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Deep Worker", "Consistent Learner", "Creative Builder", "Focused Professional").forEach { label ->
                SuggestionChip(
                    onClick = { onValueChange(label) },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
private fun EnergyStep(selected: String, onSelect: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "When do you feel most focused?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        listOf(
            Triple("🌅 Morning", "morning", "6 AM – 12 PM"),
            Triple("☀ Afternoon", "afternoon", "12 PM – 5 PM"),
            Triple("🌙 Evening", "evening", "5 PM – 10 PM")
        ).forEach { (label, value, subtitle) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == value) NeuroFlowColors.PurpleLight else MaterialTheme.colorScheme.surface
                ),
                border = if (selected == value) CardDefaults.outlinedCardBorder() else null,
                onClick = { onSelect(value) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GoalStep(value: String, onValueChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "What's your most important goal right now?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("My top goal") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeuroFlowColors.Purple,
                focusedLabelColor = NeuroFlowColors.Purple
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Optional — you can add more goals later",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FirstTaskStep(value: String, onValueChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "What's one thing you've been putting off?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("My first task") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeuroFlowColors.Purple,
                focusedLabelColor = NeuroFlowColors.Purple
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This will be auto-assigned to your DO FIRST quadrant",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WakeTimeStep(hour: Int, onHourChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "When do you start your day?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "${formatHour(hour)}",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp),
            fontWeight = FontWeight.Bold,
            color = NeuroFlowColors.Purple
        )
        Spacer(modifier = Modifier.height(16.dp))
        Slider(
            value = hour.toFloat(),
            onValueChange = { onHourChange(it.toInt()) },
            valueRange = 4f..12f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = NeuroFlowColors.Purple,
                activeTrackColor = NeuroFlowColors.Purple
            )
        )
        Text(
            "This sets your morning plan notification time",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12:00 AM"
    hour < 12 -> "$hour:00 AM"
    hour == 12 -> "12:00 PM"
    else -> "${hour - 12}:00 PM"
}
