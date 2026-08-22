package com.neuroflow.app.domain.model

enum class Quadrant {
    DO_FIRST,
    SCHEDULE,
    DELEGATE,
    ELIMINATE
}

enum class Priority {
    HIGH,
    MEDIUM,
    LOW
}

enum class TaskStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED
}

enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM
}

enum class EnergyLevel {
    HIGH,
    MEDIUM,
    LOW
}

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

// Circadian rhythm task type — used to match task to optimal time of day
// ANALYTICAL: deep work, problem solving, writing (best in peak hours)
// CREATIVE: brainstorming, design, ideation (best mid-morning or late afternoon)
// ADMIN: emails, scheduling, routine tasks(best in low-energy slots)
// PHYSICAL: exercise, errands, calls (flexible)
enum class TaskType {
    ANALYTICAL,
    CREATIVE,
    ADMIN,
    PHYSICAL
}

enum class HyperFocusState {
    INACTIVE,
    ACTIVE,
    MICRO_UNLOCKED,
    PARTIAL_UNLOCKED,
    EARNED_UNLOCKED,
    // All tasks done — user must add ≥3 tasks for tomorrow before apps unlock
    FULL_REWARD_PENDING,
    FULLY_UNLOCKED,
    PLANNING
}

enum class HyperFocusSessionMode {
    TASK_BASED,
    TIME_BASED
}

enum class RewardTier(val unlockMinutes: Int, val taskThreshold: Int) {
    NONE(0, 0),
    MICRO(2, 1),
    PARTIAL(10, 3),
    EARNED(30, 5),
    FULL(Int.MAX_VALUE, Int.MAX_VALUE)
}
