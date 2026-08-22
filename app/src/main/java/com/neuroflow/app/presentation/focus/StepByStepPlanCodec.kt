package com.neuroflow.app.presentation.focus

import java.util.Locale

data class StepByStepPlanStep(
    val text: String,
    val completed: Boolean
)

object StepByStepPlanCodec {

    fun parse(raw: String): List<StepByStepPlanStep> {
        return raw
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val normalized = line.lowercase(Locale.getDefault())
                when {
                    normalized.startsWith("[x] ") -> StepByStepPlanStep(
                        text = line.drop(4).trim(),
                        completed = true
                    )
                    normalized.startsWith("[ ] ") -> StepByStepPlanStep(
                        text = line.drop(4).trim(),
                        completed = false
                    )
                    normalized.startsWith("- [x] ") -> StepByStepPlanStep(
                        text = line.drop(6).trim(),
                        completed = true
                    )
                    normalized.startsWith("- [ ] ") -> StepByStepPlanStep(
                        text = line.drop(6).trim(),
                        completed = false
                    )
                    else -> StepByStepPlanStep(text = line, completed = false)
                }
            }
            .toList()
    }

    fun serialize(steps: List<StepByStepPlanStep>): String {
        return steps.joinToString(separator = "\n") { step ->
            if (step.completed) "[x] ${step.text}" else "[ ] ${step.text}"
        }
    }

    fun setStepCompleted(raw: String, index: Int, completed: Boolean): String {
        val steps = parse(raw)
        if (index !in steps.indices) return raw
        val updated = steps.toMutableList()
        updated[index] = updated[index].copy(completed = completed)
        return serialize(updated)
    }
}
