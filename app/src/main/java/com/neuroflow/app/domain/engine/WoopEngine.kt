package com.neuroflow.app.domain.engine

import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.WoopEntity

object WoopEngine {
    /** Returns true if the WOOP prompt should be shown for this task (never shown before). */
    fun shouldShowPrompt(woopData: WoopEntity?, woopPromptShown: Boolean): Boolean =
        woopData == null && !woopPromptShown

    /** Generates a default if-then plan string from the obstacle text. */
    fun generateIfThenPlan(obstacle: String): String =
        "If $obstacle, then I will "

    /** Returns the affective forecast insight message if ≥5 tasks were rated worse than expected. */
    fun dreadedTaskInsight(completedTasks: List<TaskEntity>): String? {
        val negativeCount = completedTasks.count { it.affectiveForecastError != null && it.affectiveForecastError < 0 }
        return if (negativeCount >= 5) {
            "You've rated $negativeCount tasks as worse than expected — but you finished them anyway. That's the real win."
        } else null
    }
}
