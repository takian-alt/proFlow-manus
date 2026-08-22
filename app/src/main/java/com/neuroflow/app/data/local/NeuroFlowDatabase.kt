package com.neuroflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neuroflow.app.data.local.dao.GoalDao
import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.dao.TimeSessionDao
import com.neuroflow.app.data.local.entity.GoalEntity
import com.neuroflow.app.data.local.entity.TaskEntity
import com.neuroflow.app.data.local.entity.TimeSessionEntity

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

@Database(
    entities = [TaskEntity::class, TimeSessionEntity::class, GoalEntity::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NeuroFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun timeSessionDao(): TimeSessionDao
    abstract fun goalDao(): GoalDao
}
