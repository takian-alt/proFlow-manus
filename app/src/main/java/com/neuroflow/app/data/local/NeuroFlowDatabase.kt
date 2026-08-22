package com.neuroflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neuroflow.app.data.local.dao.EnergyPredictionDao
import com.neuroflow.app.data.local.dao.AutoScheduleTelemetryDao
import com.neuroflow.app.data.local.dao.GoalDao
import com.neuroflow.app.data.local.dao.SleepLogDao
import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.dao.TimeSessionDao
import com.neuroflow.app.data.local.dao.UlyssesContractDao
import com.neuroflow.app.data.local.dao.WoopDao
import com.neuroflow.app.data.local.entity.EnergyPredictionEntity
import com.neuroflow.app.data.local.entity.AutoScheduleTelemetryEntity
import com.neuroflow.app.data.local.entity.GoalEntity
import com.neuroflow.app.data.local.entity.SleepLogEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity
import com.neuroflow.app.data.local.entity.UlyssesContractEntity
import com.neuroflow.app.data.local.entity.WoopEntity
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusSessionDao
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusSessionEntity
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeDao
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceIntervalDays INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN valueScore INTEGER NOT NULL DEFAULT 50")
        db.execSQL("ALTER TABLE tasks ADD COLUMN effortScore INTEGER NOT NULL DEFAULT 50")
        db.execSQL("ALTER TABLE tasks ADD COLUMN waitingFor TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE time_sessions ADD COLUMN pausedAt INTEGER")
        db.execSQL("ALTER TABLE time_sessions ADD COLUMN totalPausedMs INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN taskType TEXT NOT NULL DEFAULT 'ANALYTICAL'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN enjoymentScore INTEGER NOT NULL DEFAULT 50")
        db.execSQL("ALTER TABLE tasks ADD COLUMN isPublicCommitment INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN isAnxietyTask INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN goalRiskLevel INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN dependsOnTaskIds TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN habitDate INTEGER")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN affectiveForecastError REAL")
        db.execSQL("ALTER TABLE tasks ADD COLUMN woopPromptShown INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS woop_data (
                taskId TEXT NOT NULL PRIMARY KEY,
                wish TEXT NOT NULL DEFAULT '',
                outcome TEXT NOT NULL DEFAULT '',
                obstacle TEXT NOT NULL DEFAULT '',
                plan TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS ulysses_contracts (
                id TEXT NOT NULL PRIMARY KEY,
                taskId TEXT NOT NULL,
                deadlineAt INTEGER NOT NULL,
                consequence TEXT NOT NULL,
                outcome TEXT,
                createdAt INTEGER NOT NULL
            )
        """)
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN distractionScore REAL NOT NULL DEFAULT -1")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS unlock_codes (
                id TEXT NOT NULL PRIMARY KEY,
                encryptedCode TEXT NOT NULL,
                tier TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                isUsed INTEGER NOT NULL DEFAULT 0,
                usedAt INTEGER,
                unlockedUntil INTEGER
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS hyperfocus_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                startedAt INTEGER NOT NULL,
                state TEXT NOT NULL,
                blockedPackages TEXT NOT NULL,
                dailyTaskTarget INTEGER NOT NULL,
                tasksCompletedAtStart INTEGER NOT NULL,
                currentTier TEXT NOT NULL,
                fullyUnlockedAt INTEGER,
                endedAt INTEGER
            )
        """)
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE unlock_codes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_logs (
                id TEXT NOT NULL PRIMARY KEY,
                startAt INTEGER NOT NULL,
                endAt INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                source TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sleep_logs_startAt ON sleep_logs(startAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sleep_logs_endAt ON sleep_logs(endAt)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE time_sessions ADD COLUMN pauseResumeCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE time_sessions ADD COLUMN appSwitchCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE time_sessions ADD COLUMN interruptionBurstCount INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Phase 2 telemetry: energy prediction audit trail and backtesting data
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS energy_predictions (
                id TEXT NOT NULL PRIMARY KEY,
                predictedAtMillis INTEGER NOT NULL,
                predictedAtDayOfWeek INTEGER NOT NULL,
                predictedAtHourOfDay INTEGER NOT NULL,
                peakDetectionAgeMillis INTEGER NOT NULL,
                sleepLogAgeMillis INTEGER NOT NULL,
                sessionDataAgeMillis INTEGER NOT NULL,
                baselineRawEnergy REAL NOT NULL,
                peakScore REAL NOT NULL,
                fatiguePenalty REAL NOT NULL,
                sleepPressurePoints INTEGER NOT NULL,
                fatiguePercent INTEGER NOT NULL,
                momentAdjustment REAL NOT NULL,
                momentConfidence REAL NOT NULL,
                momentSupportScore REAL NOT NULL,
                momentPressureScore REAL NOT NULL,
                adjustedRawEnergy REAL NOT NULL,
                usableEnergy INTEGER NOT NULL,
                chronotype TEXT NOT NULL,
                wakeUpHour INTEGER NOT NULL,
                peakMinuteOfDay INTEGER NOT NULL,
                peakConfidence REAL NOT NULL,
                recentFocusMinutes REAL NOT NULL,
                recentInterruptionCount INTEGER NOT NULL,
                recentAppSwitchCount INTEGER NOT NULL,
                activeTaskCount INTEGER NOT NULL,
                notificationCount INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN isAutoScheduled INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add lastAutoScheduledAt column for replanning cooldown mechanism
        db.execSQL("ALTER TABLE tasks ADD COLUMN lastAutoScheduledAt INTEGER")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add explicit scheduling category — nullable TEXT so existing tasks default to null
        // (category is inferred at runtime from tags/taskType when null)
        db.execSQL("ALTER TABLE tasks ADD COLUMN schedulingCategory TEXT")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN earliestStartDate INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN earliestStartTime INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN preferredWeekdaysMask INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN avoidStartTime INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN avoidEndTime INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN isHardDeadline INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE tasks ADD COLUMN canSplit INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE tasks ADD COLUMN maxSessionLengthMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN minimumFocusBlockMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS auto_schedule_telemetry (
                id TEXT NOT NULL PRIMARY KEY,
                taskId TEXT NOT NULL,
                generatedAtMillis INTEGER NOT NULL,
                horizonDays INTEGER NOT NULL,
                wasApplied INTEGER NOT NULL,
                selectedSlotDate INTEGER,
                selectedSlotTime INTEGER,
                candidateSlotStartMillisJson TEXT NOT NULL,
                rejectedCandidateSlotStartMillisJson TEXT NOT NULL,
                rejectionReason TEXT,
                assignmentReason TEXT NOT NULL,
                fitScore REAL NOT NULL,
                energyMatch REAL NOT NULL,
                tagFit REAL NOT NULL,
                deadlineUrgency REAL NOT NULL,
                confidence REAL NOT NULL,
                energyScore REAL NOT NULL,
                deadlinePressure REAL NOT NULL,
                estimatedDurationMinutes INTEGER NOT NULL,
                userAdjustment TEXT,
                outcome TEXT,
                userFeedbackAtMillis INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_schedule_telemetry_taskId ON auto_schedule_telemetry(taskId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_schedule_telemetry_generatedAtMillis ON auto_schedule_telemetry(generatedAtMillis)")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE auto_schedule_telemetry ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'PENDING'")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN deadlineType TEXT NOT NULL DEFAULT 'SOFT'")
        db.execSQL("ALTER TABLE tasks ADD COLUMN startByDate INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN avoidWeekdaysMask INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN doBeforeTaskIds TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE tasks ADD COLUMN doAfterTaskIds TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [TaskEntity::class, TimeSessionEntity::class, GoalEntity::class, WoopEntity::class, UlyssesContractEntity::class, UnlockCodeEntity::class, HyperFocusSessionEntity::class, SleepLogEntity::class, EnergyPredictionEntity::class, AutoScheduleTelemetryEntity::class],
    version = 20,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NeuroFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun timeSessionDao(): TimeSessionDao
    abstract fun goalDao(): GoalDao
    abstract fun woopDao(): WoopDao
    abstract fun ulyssesContractDao(): UlyssesContractDao
    abstract fun unlockCodeDao(): UnlockCodeDao
    abstract fun hyperFocusSessionDao(): HyperFocusSessionDao
    abstract fun sleepLogDao(): SleepLogDao
    abstract fun energyPredictionDao(): EnergyPredictionDao
    abstract fun autoScheduleTelemetryDao(): AutoScheduleTelemetryDao
}
