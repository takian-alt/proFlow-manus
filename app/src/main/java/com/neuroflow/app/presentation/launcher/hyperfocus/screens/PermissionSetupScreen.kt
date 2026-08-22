package com.neuroflow.app.presentation.launcher.hyperfocus.screens

import android.app.AppOpsManager
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PermissionSetupScreen(onBothGranted: () -> Unit) {
    val context = LocalContext.current

    fun isAccessibilityEnabled(): Boolean {
        return com.neuroflow.app.presentation.launcher.hyperfocus.util.AccessibilityUtil
            .isAppBlockingServiceEnabled(context)
    }

    fun isUsageStatsGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    var accessibilityGranted by remember { mutableStateOf(isAccessibilityEnabled()) }
    var usageStatsGranted by remember { mutableStateOf(isUsageStatsGranted()) }

    // Poll every second for both permissions
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityGranted = isAccessibilityEnabled()
            usageStatsGranted = isUsageStatsGranted()
            if (accessibilityGranted && usageStatsGranted) {
                onBothGranted()
                break
            }
            delay(1000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0A0A0A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Two Permissions Required",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Hyper Focus needs these to detect and block distracting apps.",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission 1 — Accessibility
            PermissionRow(
                label = "Accessibility Service",
                description = "Detects when a blocked app is opened",
                granted = accessibilityGranted
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!accessibilityGranted) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Accessibility →")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Find \"${context.applicationInfo.loadLabel(context.packageManager)}\" and enable it.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Permission 2 — Usage Stats
            PermissionRow(
                label = "Usage Access",
                description = "Monitors which app is in the foreground",
                granted = usageStatsGranted
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!usageStatsGranted) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Usage Access →")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Find \"${context.applicationInfo.loadLabel(context.packageManager)}\" and enable it.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (accessibilityGranted && usageStatsGranted) {
                Text(
                    text = "✓ All permissions granted!",
                    color = Color(0xFF69F0AE),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Waiting for permissions...",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, description: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.Check else Icons.Default.Circle,
            contentDescription = null,
            tint = if (granted) Color(0xFF69F0AE) else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color.Gray, fontSize = 12.sp)
        }
    }
}
