package com.neuroflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neuroflow.app.data.local.dao.GoalDao
import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.dao.TimeSessionDao
import com.neuroflow.app.data.local.dao.UlyssesContractDao
import com.neuroflow.app.data.local.dao.WoopDao
import com.neuroflow.app.data.local.entity.GoalEntity
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

@Database(
    entities = [TaskEntity::class, TimeSessionEntity::class, GoalEntity::class, WoopEntity::class, UlyssesContractEntity::class, UnlockCodeEntity::class, HyperFocusSessionEntity::class],
    version = 11,
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
}
