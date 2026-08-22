package com.neuroflow.app.domain.engine

/**
 * Baseline chronotype detector using the Morningness-Eveningness Questionnaire (MEQ).
 *
 * This is meant to establish the user's initial energy profile before any task-history
 * based adaptation is introduced.
 */
object MEQChronotypeDetector {

    const val QUESTION_COUNT = 19
    const val MIN_TOTAL_SCORE = 16
    const val MAX_TOTAL_SCORE = 86

    enum class Chronotype {
        DEFINITE_MORNING,
        MODERATE_MORNING,
        INTERMEDIATE,
        MODERATE_EVENING,
        DEFINITE_EVENING
    }

    data class Result(
        val totalScore: Int,
        val chronotype: Chronotype,
        val baselinePeakStartHour: Int,
        val baselinePeakEndHour: Int,
        val answeredQuestions: Int,
        val confidence: Float
    )

    /**
     * Accepts the 19 MEQ question scores exactly as provided in the questionnaire.
     *
     * Each item should be the points for the selected answer, already mapped to the
     * question's scoring rules.
     */
    fun detect(answerScores: List<Int>): Result {
        require(answerScores.size == QUESTION_COUNT) {
            "MEQ requires exactly $QUESTION_COUNT answers."
        }

        val totalScore = answerScores.sum().coerceIn(MIN_TOTAL_SCORE, MAX_TOTAL_SCORE)
        val chronotype = classify(totalScore)
        val (peakStart, peakEnd) = baselinePeakWindow(chronotype)
        val confidence = (answerScores.count { it > 0 }.toFloat() / QUESTION_COUNT).coerceIn(0f, 1f)

        return Result(
            totalScore = totalScore,
            chronotype = chronotype,
            baselinePeakStartHour = peakStart,
            baselinePeakEndHour = peakEnd,
            answeredQuestions = answerScores.count { it > 0 },
            confidence = confidence
        )
    }

    fun classify(totalScore: Int): Chronotype {
        return when (totalScore) {
            in 70..MAX_TOTAL_SCORE -> Chronotype.DEFINITE_MORNING
            in 59..69 -> Chronotype.MODERATE_MORNING
            in 42..58 -> Chronotype.INTERMEDIATE
            in 31..41 -> Chronotype.MODERATE_EVENING
            else -> Chronotype.DEFINITE_EVENING
        }
    }

    fun baselinePeakWindow(chronotype: Chronotype): Pair<Int, Int> {
        return when (chronotype) {
            Chronotype.DEFINITE_MORNING -> 6 to 11
            Chronotype.MODERATE_MORNING -> 7 to 12
            Chronotype.INTERMEDIATE -> 9 to 14
            Chronotype.MODERATE_EVENING -> 13 to 18
            Chronotype.DEFINITE_EVENING -> 15 to 21
        }
    }

    fun label(chronotype: Chronotype): String {
        return when (chronotype) {
            Chronotype.DEFINITE_MORNING -> "Definite Morning Type"
            Chronotype.MODERATE_MORNING -> "Moderate Morning Type"
            Chronotype.INTERMEDIATE -> "Intermediate Type"
            Chronotype.MODERATE_EVENING -> "Moderate Evening Type"
            Chronotype.DEFINITE_EVENING -> "Definite Evening Type"
        }
    }
}
