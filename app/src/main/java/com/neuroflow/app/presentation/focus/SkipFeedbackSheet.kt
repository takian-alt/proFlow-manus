package com.neuroflow.app.presentation.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SkipFeedbackSheet(
    onReasonSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "skipped" to "I skipped it",
        "partially_done" to "I partially did it",
        "bad_time" to "Bad time",
        "too_big" to "Too big",
        "wrong_priority" to "Wrong priority"
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("What got in the way?", style = MaterialTheme.typography.titleLarge)
            Text(
                "This helps NeuroFlow choose a better time or smaller block next time.",
                style = MaterialTheme.typography.bodySmall
            )
            options.forEach { (reason, label) ->
                Button(
                    onClick = { onReasonSelected(reason) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
