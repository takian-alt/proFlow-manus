package com.neuroflow.app.presentation.launcher.di

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import com.neuroflow.app.data.local.NeuroFlowDatabase
import com.neuroflow.app.presentation.launcher.data.AppRepository
import com.neuroflow.app.presentation.launcher.data.IconPackManager
import com.neuroflow.app.presentation.launcher.data.LauncherBackupManager
import com.neuroflow.app.presentation.launcher.data.NotificationBadgeManager
import com.neuroflow.app.presentation.launcher.data.PinnedAppsDataStore
import com.neuroflow.app.presentation.launcher.domain.AppWidgetHostWrapper
import com.neuroflow.app.presentation.launcher.domain.LauncherSearchEngine
import com.neuroflow.app.presentation.launcher.domain.LauncherSearchEngineImpl
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStoreImpl
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusSessionDao
import com.neuroflow.app.presentation.launcher.hyperfocus.data.UnlockCodeDao
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManager
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManagerImpl
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.UnlockCodeRepository
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.UnlockCodeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing launcher-specific dependencies.
 *
 * Provides:
 * - LauncherApps system service
 * - PinnedAppsDataStore singleton
 * - AppWidgetHostWrapper with host ID 1337 (Phase 1 scaffolding)
 * - NotificationBadgeService singleton
 * - AppRepository, IconPackManager, LauncherBackupManager singletons
 * - LauncherSearchEngine interface binding
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LauncherModule {

    /**
     * Binds LauncherSearchEngine interface to implementation.
     */
    @Binds
    @Singleton
    abstract fun bindLauncherSearchEngine(
        impl: LauncherSearchEngineImpl
    ): LauncherSearchEngine

    /**
     * Binds UnlockCodeRepository interface to implementation.
     */
    @Binds
    @Singleton
    abstract fun bindUnlockCodeRepository(impl: UnlockCodeRepositoryImpl): UnlockCodeRepository

    /**
     * Binds HyperFocusManager interface to implementation.
     */
    @Binds
    @Singleton
    abstract fun bindHyperFocusManager(impl: HyperFocusManagerImpl): HyperFocusManager

    companion object {
        /**
         * Provides PackageManager for icon pack queries.
         * Note: LauncherApps is provided by AppModule to avoid duplicate bindings.
         */
        @Provides
        @Singleton
        fun providePackageManager(@ApplicationContext context: Context): PackageManager {
            return context.packageManager
        }

        /**
         * Provides PinnedAppsDataStore singleton.
         * Uses separate launcher_prefs file to avoid polluting main app's user_prefs.
         */
        @Provides
        @Singleton
        fun providePinnedAppsDataStore(@ApplicationContext context: Context): PinnedAppsDataStore {
            return PinnedAppsDataStore(context)
        }

        /**
         * Provides AppWidgetHostWrapper with host ID 1337.
         * Phase 1 scaffolding: Host is created but no widgets are bound yet.
         * This prevents future structural changes when widgets are added.
         */
        @Provides
        @Singleton
        fun provideAppWidgetHost(@ApplicationContext context: Context): AppWidgetHostWrapper {
            return AppWidgetHostWrapper(context, 1337)
        }

        /**
         * Provides NotificationBadgeManager singleton.
         * Manages badge count StateFlow that is updated by NotificationBadgeService.
         */
        @Provides
        @Singleton
        fun provideNotificationBadgeManager(): NotificationBadgeManager {
            return NotificationBadgeManager()
        }

        /**
         * Provides AppRepository singleton.
         * Manages app list with LauncherApps API and LruCache for icons.
         */
        @Provides
        @Singleton
        fun provideAppRepository(
            @ApplicationContext context: Context,
            launcherApps: LauncherApps,
            pinnedAppsDataStore: PinnedAppsDataStore
        ): AppRepository {
            return AppRepository(context, launcherApps, pinnedAppsDataStore)
        }

        /**
         * Provides IconPackManager singleton.
         * Handles icon pack queries and themed icon loading.
         */
        @Provides
        @Singleton
        fun provideIconPackManager(
            @ApplicationContext context: Context,
            packageManager: PackageManager
        ): IconPackManager {
            return IconPackManager(context, packageManager)
        }

        /**
         * Provides LauncherBackupManager singleton.
         * Handles backup/restore of launcher configuration.
         */
        @Provides
        @Singleton
        fun provideLauncherBackupManager(
            pinnedAppsDataStore: PinnedAppsDataStore,
            appRepository: AppRepository
        ): LauncherBackupManager {
            return LauncherBackupManager(pinnedAppsDataStore, appRepository)
        }

        /**
         * Provides HyperFocusDataStore singleton.
         */
        @Provides
        @Singleton
        fun provideHyperFocusDataStore(@ApplicationContext context: Context): HyperFocusDataStore =
            HyperFocusDataStoreImpl(context)

        /**
         * Provides UnlockCodeDao from NeuroFlowDatabase.
         */
        @Provides
        fun provideUnlockCodeDao(database: NeuroFlowDatabase): UnlockCodeDao = database.unlockCodeDao()

        /**
         * Provides HyperFocusSessionDao from NeuroFlowDatabase.
         */
        @Provides
        fun provideHyperFocusSessionDao(database: NeuroFlowDatabase): HyperFocusSessionDao = database.hyperFocusSessionDao()
    }
}
