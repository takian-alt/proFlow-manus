package com.neuroflow.app.di

import android.content.Context
import android.content.pm.LauncherApps
import androidx.room.Room
import androidx.work.WorkManager
import com.neuroflow.app.data.local.MIGRATION_1_2
import com.neuroflow.app.data.local.MIGRATION_2_3
import com.neuroflow.app.data.local.MIGRATION_3_4
import com.neuroflow.app.data.local.MIGRATION_4_5
import com.neuroflow.app.data.local.MIGRATION_5_6
import com.neuroflow.app.data.local.MIGRATION_6_7
import com.neuroflow.app.data.local.MIGRATION_7_8
import com.neuroflow.app.data.local.MIGRATION_8_9
import com.neuroflow.app.data.local.MIGRATION_9_10
import com.neuroflow.app.data.local.MIGRATION_10_11
import com.neuroflow.app.data.local.MIGRATION_11_12
import com.neuroflow.app.data.local.MIGRATION_12_13
import com.neuroflow.app.data.local.MIGRATION_13_14
import com.neuroflow.app.data.local.MIGRATION_14_15
import com.neuroflow.app.data.local.MIGRATION_15_16
import com.neuroflow.app.data.local.MIGRATION_16_17
import com.neuroflow.app.data.local.MIGRATION_17_18
import com.neuroflow.app.data.local.MIGRATION_18_19
import com.neuroflow.app.data.local.MIGRATION_19_20
import com.neuroflow.app.data.local.MIGRATION_20_21
import com.neuroflow.app.data.local.MIGRATION_21_22
import com.neuroflow.app.data.local.NeuroFlowDatabase
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.GoalDao
import com.neuroflow.app.data.local.dao.SleepLogDao
import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.dao.EnergyPredictionDao
import com.neuroflow.app.data.local.dao.AutoScheduleTelemetryDao
import com.neuroflow.app.data.local.dao.ScheduleAdjustmentDao
import com.neuroflow.app.data.local.dao.TaskFeedbackDao
import com.neuroflow.app.data.local.dao.UlyssesContractDao
import com.neuroflow.app.data.local.dao.WoopDao
import com.neuroflow.app.data.repository.UlyssesContractRepository
import com.neuroflow.app.data.repository.WoopRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NeuroFlowDatabase {
        return Room.databaseBuilder(
            context,
            NeuroFlowDatabase::class.java,
            "neuroflow_database"
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22
            )
            .build()
    }

    @Provides
    fun provideTaskDao(database: NeuroFlowDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideTimeSessionDao(database: NeuroFlowDatabase): TimeSessionDao = database.timeSessionDao()

    @Provides
    fun provideGoalDao(database: NeuroFlowDatabase): GoalDao = database.goalDao()

    @Provides
    fun provideWoopDao(database: NeuroFlowDatabase): WoopDao = database.woopDao()

    @Provides
    fun provideUlyssesContractDao(database: NeuroFlowDatabase): UlyssesContractDao = database.ulyssesContractDao()

    @Provides
    fun provideSleepLogDao(database: NeuroFlowDatabase): SleepLogDao = database.sleepLogDao()

    @Provides
    fun provideAutoScheduleTelemetryDao(database: NeuroFlowDatabase): AutoScheduleTelemetryDao = database.autoScheduleTelemetryDao()

    @Provides
    fun provideScheduleAdjustmentDao(database: NeuroFlowDatabase): ScheduleAdjustmentDao = database.scheduleAdjustmentDao()

    @Provides
    fun provideTaskFeedbackDao(database: NeuroFlowDatabase): TaskFeedbackDao = database.taskFeedbackDao()

    @Provides
    @Singleton
    fun provideWoopRepository(dao: WoopDao): WoopRepository = WoopRepository(dao)

    @Provides
    @Singleton
    fun provideUlyssesContractRepository(dao: UlyssesContractDao): UlyssesContractRepository = UlyssesContractRepository(dao)

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context
    ): UserPreferencesDataStore = UserPreferencesDataStore(context)

    @Provides
    @Singleton
    fun provideLauncherApps(@ApplicationContext context: Context): LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
