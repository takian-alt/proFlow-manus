package com.neuroflow.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuroflow.app.presentation.common.theme.NeuroFlowColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MEQQuizScreen(
    onNavigateBack: () -> Unit,
    viewModel: MEQQuizViewModel = hiltViewModel()
) {
    val quizState by viewModel.quizState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chronotype Quiz") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (quizState.isComplete) {
                QuizCompletionScreen(
                    chronotypeName = quizState.detectedChronotype ?: "Unknown",
                    meqScore = quizState.detectedMEQScore ?: 0,
                    onReset = { viewModel.resetQuiz() },
                    onBack = onNavigateBack
                )
            } else if (quizState.questions.isNotEmpty()) {
                // Progress indicator
                LinearProgressIndicator(
                    progress = { (quizState.currentQuestionIndex + 1).toFloat() / quizState.questions.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = NeuroFlowColors.Purple
                )
                
                Text(
                    "Question ${quizState.currentQuestionIndex + 1} of ${quizState.questions.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Current question
                val currentQuestion = quizState.questions[quizState.currentQuestionIndex]
                
                // Question text
                Text(
                    currentQuestion.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Answer options
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currentQuestion.options.forEachIndexed { idx, option ->
                        AnswerOptionCard(
                            text = option,
                            isSelected = currentQuestion.selectedAnswer == idx,
                            onClick = { viewModel.selectAnswer(idx) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Navigation buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.previousQuestion() },
                        enabled = quizState.currentQuestionIndex > 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Button(
                        onClick = { viewModel.nextQuestion() },
                        enabled = currentQuestion.selectedAnswer >= 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple)
                    ) {
                        Text(
                            if (quizState.currentQuestionIndex == quizState.questions.size - 1) 
                                "Complete" 
                            else 
                                "Next"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerOptionCard(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                NeuroFlowColors.Purple.copy(alpha = 0.2f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            CardDefaults.outlinedCardBorder() 
        else 
            null,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Selected",
                    tint = NeuroFlowColors.Purple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun QuizCompletionScreen(
    chronotypeName: String,
    meqScore: Int,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🎉",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            "Quiz Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Chronotype result
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NeuroFlowColors.Purple.copy(alpha = 0.1f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your Chronotype",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatChronotypeName(chronotypeName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeuroFlowColors.Purple,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "MEQ Score: $meqScore",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        Text(
            "Your energy levels and focus patterns will be optimized based on this result.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeuroFlowColors.Purple),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done")
        }
    }
}

private fun formatChronotypeName(chronotype: String): String {
    return when (chronotype) {
        "DEFINITE_MORNING" -> "Definite Morning ⏰"
        "MODERATE_MORNING" -> "Moderate Morning 🌤️"
        "INTERMEDIATE" -> "Intermediate ☀️"
        "MODERATE_EVENING" -> "Moderate Evening 🌙"
        "DEFINITE_EVENING" -> "Definite Evening 🌃"
        else -> chronotype
    }
}
