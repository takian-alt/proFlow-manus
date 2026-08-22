# ProGuard rules for proFlow Launcher
# Theserules prevent R8 full-mode from removing critical launcher components

# ===== StatusBarManager reflection methods =====
# Used in LauncherGestureHandler for notification shade expansion
-keep class android.app.StatusBarManager {
    public void expandNotificationsPanel();
    public void collapsePanels();
}

# ===== LauncherApps and related classes =====
# Core launcher API - must be preserved for app queries, shortcuts, and work profiles
-keep class android.content.pm.LauncherApps { *; }
-keep class android.content.pm.LauncherActivityInfo { *; }
-keep class android.content.pm.ShortcutInfo { *; }
-keep class android.os.UserHandle { *; }

# Keep all LauncherApps subclasses and callbacks
-keep class * extends android.content.pm.LauncherApps { *; }
-keep class * implements android.content.pm.LauncherApps$Callback { *; }

# ===== NotificationListenerService =====
# Required for NotificationBadgeService
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class android.service.notification.StatusBarNotification { *; }
-keep class android.service.notification.NotificationListenerService { *; }

# ===== Icon Pack Support =====
# Resource identifier calls for themed icons - R8 may remove these thinking they're unused
-keepclassmembers class * {
    public int getIdentifier(java.lang.String, java.lang.String, java.lang.String);
}

# Keep Resources class methods used for icon pack loading
-keep class android.content.res.Resources {
    public android.graphics.drawable.Drawable getDrawable(int);
    public android.graphics.drawable.Drawable getDrawableForDensity(int, int);
}

# ===== AppWidgetHost =====
# Required for widget support (Phase 1 scaffolding)
-keep class android.appwidget.AppWidgetHost { *; }
-keep class android.appwidget.AppWidgetHostView { *; }
-keep class android.appwidget.AppWidgetProviderInfo { *; }
-keep class * extends android.appwidget.AppWidgetHost { *; }

# ===== BiometricPrompt =====
# Required for biometric app lock
-keep class androidx.biometric.BiometricPrompt { *; }
-keep class androidx.biometric.BiometricManager { *; }

# ===== DataStore =====
# Prevent serialization issues with launcher preferences
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ===== Hilt/Dagger generated code =====
# Ensure launcher module DI works correctly
-keep class com.neuroflow.app.presentation.launcher.** { *; }
-keep class com.neuroflow.app.di.LauncherModule { *; }

# ===== Kotlin coroutines =====
# Prevent issues with StateFlow and Flow in LauncherViewModel
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ===== Compose =====
# Prevent removal of composable functions
-keep class com.neuroflow.app.presentation.launcher.components.** { *; }
-keep class com.neuroflow.app.presentation.launcher.drawer.** { *; }
-keep class com.neuroflow.app.presentation.launcher.folder.** { *; }
-keep class com.neuroflow.app.presentation.launcher.stats.** { *; }
-keep class com.neuroflow.app.presentation.launcher.settings.** { *; }

# ===== Coil image loading =====
# Icon loading via Coil with LruCache
-keep class coil.** { *; }
-dontwarn coil.**

# ===== Reflection-based settings access =====
# Used for navigation mode detection and settings queries
-keep class android.provider.Settings { *; }
-keep class android.provider.Settings$Secure { *; }
-keep class android.provider.Settings$System { *; }

# ===== RoleManager =====
# Used for default launcher detection on API 29+
-keep class android.app.role.RoleManager { *; }

# ===== WindowManager and display metrics =====
# Used for gesture exclusion zones and foldable detection
-keep class android.view.WindowManager { *; }
-keep class android.util.DisplayMetrics { *; }
-keep class androidx.window.** { *; }

# ===== General optimization settings =====
# Allow optimization but preserve critical paths
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

