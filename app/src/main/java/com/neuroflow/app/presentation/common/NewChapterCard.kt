package com.neuroflow.app.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NewChapterCard(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var intentText by remember { mutableStateOf("") }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🌅 New Chapter", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A fresh start is a great time to set your intention for the week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = intentText,
                onValueChange = { intentText = it },
                label = { Text("What do you commit to this week?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Skip", fontSize = 16.sp)
                }
                Button(
                    onClick = { onConfirm(intentText.trim()) },
                    enabled = intentText.isNotBlank()
                ) {
                    Text("Confirm", fontSize = 16.sp)
                }
            }
        }
    }
}
