package com.neuroflow.app.di

import android.content.Context
import android.content.pm.LauncherApps
import androidx.room.Room
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
import com.neuroflow.app.data.local.NeuroFlowDatabase
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.GoalDao
import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.dao.TimeSessionDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
}
