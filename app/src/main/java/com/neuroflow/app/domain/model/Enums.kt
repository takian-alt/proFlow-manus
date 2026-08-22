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
