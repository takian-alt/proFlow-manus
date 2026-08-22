package com.neuroflow.app.presentation.launcher.hyperfocus

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neuroflow.app.presentation.launcher.hyperfocus.screens.BlockingOverlayScreen
import com.neuroflow.app.presentation.launcher.hyperfocus.screens.CodeEntryScreen
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class HyperFocusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        // Block back gesture on Android 13+ (predictive back)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY
            ) {
                // Consume back gesture — do nothing
            }
        }

        setContent {
            val blockedPackage = intent.getStringExtra("blocked_package") ?: ""
            val viewModel: HyperFocusViewModel = hiltViewModel()
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "blocking_overlay"
            ) {
                composable("blocking_overlay") {
                    BlockingOverlayScreen(blockedPackage, viewModel, navController)
                }
                composable("code_entry") {
                    CodeEntryScreen(viewModel, navController, blockedPackage)
                }
                composable("launch_app") {
                    // Transparent screen — immediately launches the blocked app and finishes
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    LaunchedEffect(Unit) {
                        launchApp(blockedPackage)
                        finish()
                    }
                }
            }
        }
    }

    fun launchApp(packageName: String) {
        if (packageName.isBlank()) {
            finish()
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)
        }
        finish()
    }
}
