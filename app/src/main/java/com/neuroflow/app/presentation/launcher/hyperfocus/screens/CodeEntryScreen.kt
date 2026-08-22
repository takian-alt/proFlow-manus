package com.neuroflow.app.presentation.launcher.hyperfocus.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusViewModel
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.UnlockResult
import kotlin.math.roundToInt

@Composable
fun CodeEntryScreen(viewModel: HyperFocusViewModel, navController: NavController, blockedPackage: String = "") {
    val prefs by viewModel.hyperFocusPrefs.collectAsState()
    val lockoutSecondsRemaining by viewModel.lockoutSecondsRemaining.collectAsState()
    val submitResult by viewModel.submitCodeResult.collectAsState()

    val chars = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    // Shake animation offset
    val shakeOffset = remember { Animatable(0f) }

    val isLocked = lockoutSecondsRemaining != null
    val allFilled = chars.all { it.isNotEmpty() }
    val attemptsRemaining = (3 - prefs.wrongCodeAttempts).coerceAtLeast(0)

    // React to submit result
    LaunchedEffect(submitResult) {
        when (submitResult) {
            is UnlockResult.Success -> {
                viewModel.clearSubmitCodeResult()
                // Launch the blocked app now that unlock is active, then close this activity
                navController.navigate("launch_app") {
                    popUpTo("blocking_overlay") { inclusive = true }
                }
            }
            is UnlockResult.InvalidCode -> {
                viewModel.clearSubmitCodeResult()
                // Shake the input boxes
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 400
                        0f at 0
                        -16f at 50
                        16f at 100
                        -12f at 150
                        12f at 200
                        -8f at 250
                        8f at 300
                        0f at 400
                    }
                )
                // Clear the entered chars
                for (i in chars.indices) chars[i] = ""
                focusRequesters[0].requestFocus()
            }
            is UnlockResult.Lockout, null -> { /* nothing */ }
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
            // 1. Title
            Text(
                text = "Enter your unlock code",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 2. Code boxes or lockout countdown
            if (isLocked) {
                val secs = lockoutSecondsRemaining!!
                Text(
                    text = "Try again in ${secs / 60}:${String.format("%02d", secs % 60)}",
                    color = Color(0xFFFF5252),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                ) {
                    chars.forEachIndexed { index, value ->
                        BasicTextField(
                            value = value,
                            onValueChange = { newVal ->
                                val char = newVal.uppercase().lastOrNull()?.toString() ?: ""
                                chars[index] = char
                                if (char.isNotEmpty() && index < 5) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                            },
                            modifier = Modifier
                                .size(width = 48.dp, height = 56.dp)
                                .border(1.dp, Color.White)
                                .focusRequester(focusRequesters[index])
                                .onKeyEvent { event ->
                                    if (event.key == Key.Backspace && chars[index].isEmpty() && index > 0) {
                                        focusRequesters[index - 1].requestFocus()
                                        chars[index - 1] = ""
                                        true
                                    } else false
                                },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii
                            ),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.Center) {
                                    innerTextField()
                                }
                            },
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Attempts remaining
                Text(
                    text = "$attemptsRemaining of 3 attempts remaining",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Confirm button
            Button(
                onClick = {
                    viewModel.submitCode(chars.joinToString(""))
                    // Navigation happens in LaunchedEffect(submitResult) above
                },
                enabled = allFilled && !isLocked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Help text
            Text(
                text = "Don't have a code? Complete a task to earn one.",
                color = Color.Gray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Request focus on first box when screen appears
    LaunchedEffect(Unit) {
        if (!isLocked) {
            focusRequesters[0].requestFocus()
        }
    }
}
