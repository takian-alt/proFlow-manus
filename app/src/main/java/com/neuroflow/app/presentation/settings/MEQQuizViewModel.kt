package com.neuroflow.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuroflow.app.data.local.UserPreferences
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.domain.engine.MEQChronotypeDetector
import com.neuroflow.app.domain.engine.PeakEnergyEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * State for an individual quiz question
 */
data class MEQQuizQuestion(
    val number: Int,
    val text: String,
    val options: List<String>,
    val selectedAnswer: Int = -1  // -1 means no selection
)

/**
 * State for the entire quiz
 */
data class MEQQuizState(
    val currentQuestionIndex: Int = 0,
    val questions: List<MEQQuizQuestion> = emptyList(),
    val isComplete: Boolean = false,
    val detectedChronotype: String? = null,  // Enum name like "DEFINITE_MORNING"
    val detectedMEQScore: Int? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MEQQuizViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _quizState = MutableStateFlow(MEQQuizState())
    val quizState: StateFlow<MEQQuizState> = _quizState.asStateFlow()

    private val _preferences = MutableStateFlow<UserPreferences?>(null)
    val preferences = _preferences.asStateFlow()

    init {
        // Load preferences and initialize quiz
        viewModelScope.launch {
            preferencesDataStore.preferencesFlow.collect { prefs ->
                _preferences.value = prefs
                // Initialize quiz on first load
                if (_quizState.value.questions.isEmpty()) {
                    initializeQuiz(prefs)
                }
            }
        }
    }

    private fun initializeQuiz(prefs: UserPreferences) {
        val questions = createMEQQuestions()
        
        // Check if there's existing progress to restore
        val restoredState = if (prefs.quizProgress.isNotEmpty() && prefs.quizProgress != "{}") {
            restoreProgress(questions, prefs.quizProgress)
        } else {
            MEQQuizState(questions = questions)
        }
        
        _quizState.value = restoredState
    }

    private fun createMEQQuestions(): List<MEQQuizQuestion> {
        return listOf(
            MEQQuizQuestion(1, "What time would you choose to wake up if you were entirely free to plan your day?", 
                listOf("5:00-6:30 AM", "6:30-7:45 AM", "7:45-9:45 AM", "9:45-11:00 AM")),
            MEQQuizQuestion(2, "How easy do you find getting up in the morning (with no alarm)?",
                listOf("Not at all easy", "Slightly easy", "Fairly easy", "Very easy")),
            MEQQuizQuestion(3, "How alert do you feel during the first 30 minutes after waking?",
                listOf("Not at all alert", "Slightly alert", "Fairly alert", "Very alert")),
            MEQQuizQuestion(4, "How is your appetite during the first 30 minutes after waking?",
                listOf("Very poor", "Fairly poor", "Fairly good", "Very good")),
            MEQQuizQuestion(5, "How do you feel physically during the first 30 minutes after waking?",
                listOf("Very tired", "Fairly tired", "Fairly fresh", "Very fresh")),
            MEQQuizQuestion(6, "What time of day do you feel your peak of good mood and energy?",
                listOf("5:00-7:30 AM", "7:30-10:00 AM", "10:00 AM-5:00 PM", "5:00-10:00 PM")),
            MEQQuizQuestion(7, "At what time in the evening do you feel tired enough to go to bed?",
                listOf("8:00-9:00 PM", "9:00-10:15 PM", "10:15-12:30 AM", "12:30-1:45 AM")),
            MEQQuizQuestion(8, "How would you describe your usual sleeping pattern?",
                listOf("Sleep well during early night, wake too early", "Sleep well throughout", "Take a long time to fall asleep", "Wake several times during night")),
            MEQQuizQuestion(9, "Do you own an alarm clock?",
                listOf("No, never use one", "Yes, but seldom need it", "Yes, sometimes need it", "Yes, always need it")),
            MEQQuizQuestion(10, "How would you describe yourself?",
                listOf("Definitely a morning person", "More a morning than evening person", "More an evening than morning person", "Definitely an evening person")),
            MEQQuizQuestion(11, "If you had to do 2 hours of physically hard work, which time would you choose?",
                listOf("7:00-9:00 AM", "9:00-11:00 AM", "3:00-5:00 PM", "7:00-9:00 PM")),
            MEQQuizQuestion(12, "At what time in the day do you think you reach your peak of mental ability?",
                listOf("5:00-7:30 AM", "7:30-10:00 AM", "10:00 AM-5:00 PM", "5:00-10:00 PM")),
            MEQQuizQuestion(13, "When you have decided to get up at a specific time, how dependent are you on an alarm clock?",
                listOf("Not dependent", "Slightly dependent", "Fairly dependent", "Very dependent")),
            MEQQuizQuestion(14, "How easy do you find making decisions the first half-hour after waking?",
                listOf("Very difficult", "Fairly difficult", "Fairly easy", "Very easy")),
            MEQQuizQuestion(15, "How easy is it for you to get up in the morning when you have no plans that day?",
                listOf("Very difficult", "Fairly difficult", "Fairly easy", "Very easy")),
            MEQQuizQuestion(16, "Suppose you intend to be in bed by 10:30 PM. How often do you actually get to bed around that time?",
                listOf("Never", "Rarely", "Sometimes", "Always")),
            MEQQuizQuestion(17, "If you went to bed at 11:00 PM, how tired would you be?",
                listOf("Very tired", "Fairly tired", "Not very tired", "Not at all tired")),
            MEQQuizQuestion(18, "For some reason you have gone to bed at 2:00 AM. How would you manage?",
                listOf("Would find it difficult", "Would rather not", "Wouldn't be too bad", "Would manage quite well")),
            MEQQuizQuestion(19, "How often do you feel energized/alert during the late afternoon?",
                listOf("Rarely/never", "Sometimes", "Often", "Very often"))
        )
    }

    private fun restoreProgress(
        questions: List<MEQQuizQuestion>,
        progressJson: String
    ): MEQQuizState {
        return try {
            val json = JSONObject(progressJson)
            val currentIndex = json.optInt("currentIndex", 0)
            val answers = json.optJSONObject("answers") ?: JSONObject()
            
            val restoredQuestions = questions.mapIndexed { idx, q ->
                val answerIdx = answers.optInt(idx.toString(), -1)
                q.copy(selectedAnswer = answerIdx)
            }
            
            MEQQuizState(
                currentQuestionIndex = currentIndex,
                questions = restoredQuestions
            )
        } catch (e: Exception) {
            MEQQuizState(questions = questions)
        }
    }

    fun selectAnswer(answerIndex: Int) {
        val currentState = _quizState.value
        if (currentState.questions.isEmpty()) return
        
        val updatedQuestions = currentState.questions.toMutableList()
        updatedQuestions[currentState.currentQuestionIndex] = 
            updatedQuestions[currentState.currentQuestionIndex].copy(selectedAnswer = answerIndex)
        
        viewModelScope.launch {
            // Save progress to preferences
            saveProgress(currentState.currentQuestionIndex, updatedQuestions)
        }
        
        _quizState.value = currentState.copy(questions = updatedQuestions)
    }

    fun nextQuestion() {
        val currentState = _quizState.value
        if (currentState.currentQuestionIndex < currentState.questions.size - 1) {
            val nextIndex = currentState.currentQuestionIndex + 1
            _quizState.value = currentState.copy(currentQuestionIndex = nextIndex)
            viewModelScope.launch {
                saveProgress(nextIndex, _quizState.value.questions)
            }
        } else {
            // Quiz completed
            completeQuiz()
        }
    }

    fun previousQuestion() {
        val currentState = _quizState.value
        if (currentState.currentQuestionIndex > 0) {
            val previousIndex = currentState.currentQuestionIndex - 1
            _quizState.value = currentState.copy(currentQuestionIndex = previousIndex)
            viewModelScope.launch {
                saveProgress(previousIndex, _quizState.value.questions)
            }
        }
    }

    fun skipQuestion() {
        nextQuestion()
    }

    private fun mapSelectedAnswersToMeqScores(questions: List<MEQQuizQuestion>): List<Int> {
        // 4-option per-question mapping to MEQ-like morningness score (higher = more morning type).
        val scoringTables = listOf(
            intArrayOf(4, 3, 2, 1), // Q1
            intArrayOf(1, 2, 3, 4), // Q2
            intArrayOf(1, 2, 3, 4), // Q3
            intArrayOf(1, 2, 3, 4), // Q4
            intArrayOf(1, 2, 3, 4), // Q5
            intArrayOf(4, 3, 2, 1), // Q6
            intArrayOf(4, 3, 2, 1), // Q7
            intArrayOf(4, 3, 1, 2), // Q8
            intArrayOf(4, 3, 2, 1), // Q9
            intArrayOf(4, 3, 2, 1), // Q10
            intArrayOf(4, 3, 2, 1), // Q11
            intArrayOf(4, 3, 2, 1), // Q12
            intArrayOf(4, 3, 2, 1), // Q13
            intArrayOf(1, 2, 3, 4), // Q14
            intArrayOf(1, 2, 3, 4), // Q15
            intArrayOf(1, 2, 3, 4), // Q16
            intArrayOf(4, 3, 2, 1), // Q17
            intArrayOf(4, 3, 2, 1), // Q18
            intArrayOf(4, 3, 2, 1)  // Q19
        )

        return questions.mapIndexed { idx, q ->
            val selected = q.selectedAnswer
            if (selected !in 0..3) {
                0
            } else {
                scoringTables.getOrNull(idx)?.getOrNull(selected) ?: 0
            }
        }
    }

    private fun completeQuiz() {
        val currentState = _quizState.value
        val answerScores = mapSelectedAnswersToMeqScores(currentState.questions)
        
        // Calculate MEQ score and chronotype, then feed it to PeakEnergyEngine.
        val meqResult = MEQChronotypeDetector.detect(answerScores)
        val chronotype = meqResult.chronotype
        val meqScore = meqResult.totalScore
        val prefs = _preferences.value
        val wakeUpHour = prefs?.wakeUpHour ?: 7
        val peakResult = PeakEnergyEngine.detect(
            meqResult = meqResult,
            wakeUpHour = wakeUpHour,
            sleepHour = prefs?.sleepHour,
            sleepPressurePoints = prefs?.sleepPressurePoints ?: 0
        )
        val primaryWindowDurationMinutes = peakResult.circadianProfile.windows
            .firstOrNull()
            ?.durationMinutes
            ?.coerceAtLeast(30)
            ?: 60
        val peakWindowEndMinuteOfDay = (peakResult.peakMinuteOfDay + primaryWindowDurationMinutes) % (24 * 60)
        val peakWindowEndHourOfDay = peakWindowEndMinuteOfDay / 60
        
        _quizState.value = currentState.copy(
            isComplete = true,
            detectedMEQScore = meqScore,
            detectedChronotype = chronotype.name
        )
        
        // Save quiz result to preferences
        viewModelScope.launch {
            preferencesDataStore.updatePreferences { prefs ->
                prefs.copy(
                    quizChronotype = chronotype.name,
                    detectedPeakStart = peakResult.peakHourOfDay,
                    detectedPeakEnd = peakWindowEndHourOfDay,
                    detectedPeakMinuteOfDay = peakResult.peakMinuteOfDay,
                    peakDetectionConfidence = peakResult.confidence,
                    effectivePeakStart = peakResult.peakHourOfDay,
                    effectivePeakEnd = peakWindowEndHourOfDay,
                    effectivePeakMinuteOfDay = peakResult.peakMinuteOfDay,
                    quizProgress = "{}"  // Clear progress on completion
                )
            }
        }
    }

    private suspend fun saveProgress(currentIndex: Int, questions: List<MEQQuizQuestion>) {
        val progressJson = JSONObject().apply {
            put("currentIndex", currentIndex)
            val answers = JSONObject()
            questions.forEachIndexed { idx, q ->
                answers.put(idx.toString(), q.selectedAnswer)
            }
            put("answers", answers)
        }.toString()
        
        preferencesDataStore.updatePreferences { prefs ->
            prefs.copy(quizProgress = progressJson)
        }
    }

    fun resetQuiz() {
        viewModelScope.launch {
            val questions = createMEQQuestions()
            _quizState.value = MEQQuizState(questions = questions)
            preferencesDataStore.updatePreferences { prefs ->
                prefs.copy(quizProgress = "{}")
            }
        }
    }
}
