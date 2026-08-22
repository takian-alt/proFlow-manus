package com.neuroflow.app.presentation.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

data class FocusFeedback(
    val scheduleRating: Float,
    val durationRating: Float,
    val energyRating: Float
)

@Composable
fun AffordanceRatingSheet(
    onSubmit: (FocusFeedback) -> Unit,
    onSkip: () -> Unit
) {
    var scheduleRating by remember { mutableFloatStateOf(0f) }
    var durationRating by remember { mutableFloatStateOf(0f) }
    var energyRating by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onSkip,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Calibrate your next plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Quick feedback helps NeuroFlow improve timing, duration, and energy predictions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            RatingSlider("Was this a good time?", scheduleRating) { scheduleRating = it }
            RatingSlider("Was the duration realistic?", durationRating) { durationRating = it }
            RatingSlider("Was your energy as expected?", energyRating) { energyRating = it }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
                Button(
                    onClick = { onSubmit(FocusFeedback(scheduleRating, durationRating, energyRating)) },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple)
                ) { Text("Submit") }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RatingSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -2f..2f,
            colors = SliderDefaults.colors(
                thumbColor = NeuroFlowColors.Purple,
                activeTrackColor = NeuroFlowColors.Purple
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Worse", style = MaterialTheme.typography.labelSmall)
            Text("Better", style = MaterialTheme.typography.labelSmall)
        }
    }
}
