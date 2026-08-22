package com.neuroflow.app.di

import android.content.Context
import androidx.room.Room
import com.neuroflow.app.data.local.MIGRATION_1_2
import com.neuroflow.app.data.local.MIGRATION_2_3
import com.neuroflow.app.data.local.MIGRATION_3_4
import com.neuroflow.app.data.local.MIGRATION_4_5
import com.neuroflow.app.data.local.MIGRATION_5_6
import com.neuroflow.app.data.local.NeuroFlowDatabase
import com.neuroflow.app.data.local.UserPreferencesDataStore
import com.neuroflow.app.data.local.dao.GoalDao
import com.neuroflow.app.data.local.dao.TaskDao
import com.neuroflow.app.data.local.dao.TimeSessionDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    @Provides
    fun provideTaskDao(database: NeuroFlowDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideTimeSessionDao(database: NeuroFlowDatabase): TimeSessionDao = database.timeSessionDao()

    @Provides
    fun provideGoalDao(database: NeuroFlowDatabase): GoalDao = database.goalDao()

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context
    ): UserPreferencesDataStore = UserPreferencesDataStore(context)
}
