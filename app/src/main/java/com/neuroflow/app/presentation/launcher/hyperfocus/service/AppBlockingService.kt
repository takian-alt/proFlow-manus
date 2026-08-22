package com.neuroflow.app.presentation.launcher.hyperfocus.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.neuroflow.app.BuildConfig
import com.neuroflow.app.domain.model.HyperFocusSessionMode
import com.neuroflow.app.kiosk.DeviceOwnerKioskManager
import com.neuroflow.app.presentation.launcher.hyperfocus.HyperFocusActivity
import com.neuroflow.app.presentation.launcher.hyperfocus.data.HyperFocusDataStore
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.HyperFocusManager
import com.neuroflow.app.presentation.launcher.hyperfocus.domain.RewardEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockingService : AccessibilityService() {

    @Inject lateinit var hyperFocusDataStore: HyperFocusDataStore
    @Inject lateinit var hyperFocusManager: HyperFocusManager

    private val heartbeatScope = CoroutineScope(Dispatchers.IO)
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0
    @Volatile private var lastSeenForegroundPackage: String? = null

    companion object {
        private const val TAG = "AppBlockingService"
        private const val BLOCK_DEBOUNCE_MS = 1000L
        private const val HEARTBEAT_INTERVAL_MS = 2_000L
        private const val POLLING_INTERVAL_MS = 300L

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.huawei.systemmanager"
        )

        private val PERMISSION_CONTROLLER_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )

        private val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.miui.packageinstaller"
        )

        private val APP_PROTECTION_TOKENS = setOf(
            BuildConfig.APPLICATION_ID.lowercase(Locale.ROOT),
            "proflow",
            "neuroflow"
        )

        private val APP_MANAGEMENT_KEYWORDS = setOf(
            "app info",
            "app details",
            "manage app",
            "uninstall",
            "remove app",
            "delete app"
        )

        private val DEVICE_ADMIN_KEYWORDS = setOf(
            "device admin",
            "device administrator",
            "device admin apps",
            "admin app",
            "deviceadminadd",
            "add device admin"
        )
    }

    private data class ProtectedSurfaceDecision(
        val shouldBlock: Boolean,
        val tamperReason: String?
    )

    // When the unlock timer expires, reset debounce so the polling immediately re-blocks
    private val unlockExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UnlockTimerService.ACTION_UNLOCK_EXPIRED) {
                Log.d(TAG, "Unlock expired — resetting debounce to re-block current app")
                lastBlockedPackage = null
                lastBlockTime = 0
            }
        }
    }

    private val packageTamperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val packageName = intent.data?.schemeSpecificPart ?: return

            heartbeatScope.launch {
                val prefs = hyperFocusDataStore.current()
                if (!prefs.isActive) return@launch

                if (packageName == BuildConfig.APPLICATION_ID) {
                    Log.w(TAG, "Tamper: own package event detected ($action).")
                    hyperFocusManager.reportTamper("App package event: $action")
                }

                if (packageName in prefs.blockedPackages) {
                    Log.w(TAG, "Tamper: blocked package event detected ($action) for $packageName")
                    hyperFocusManager.reportTamper("Blocked app package event: $action for $packageName")
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        // Prefer rootInActiveWindow package — more reliable than event.packageName
        // event.packageName can be system UI or launcher on some ROMs
        val pkg = rootInActiveWindow?.packageName?.toString()
            ?: event.packageName?.toString()
            ?: return

        lastSeenForegroundPackage = pkg

        val className = event.className?.toString()
        val eventSnapshot = buildEventSnapshot(event, className)
        checkAndBlockApp(pkg, className, eventSnapshot)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppBlockingService interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AppBlockingService connected")

        runCatching {
            DeviceOwnerKioskManager.enableHybridProtection(this)
        }.onFailure { error ->
            Log.w(TAG, "Failed to re-arm kiosk/keepalive protections from accessibility service", error)
        }

        serviceInfo?.let { info ->
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        registerReceiverCompat(
            unlockExpiredReceiver,
            IntentFilter(UnlockTimerService.ACTION_UNLOCK_EXPIRED)
        )

        registerReceiverCompat(
            packageTamperReceiver,
            IntentFilter().apply {
                addDataScheme("package")
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_RESTARTED)
            }
        )

        // Heartbeat
        heartbeatScope.launch {
            while (true) {
                hyperFocusManager.updateHeartbeat()  // now suspend, safe to call here
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        // Polling backup — handles kicking user out when timer expires
        // Uses rootInActiveWindow as primary (no permission needed), UsageStats as secondary
        heartbeatScope.launch {
            while (true) {
                delay(POLLING_INTERVAL_MS)
                val prefs = hyperFocusDataStore.current()
                if (prefs.isActive) {
                    val currentApp = rootInActiveWindow?.packageName?.toString()
                        ?: getForegroundAppFromUsageStats()
                        ?: lastSeenForegroundPackage
                    if (currentApp != null) {
                        checkAndBlockApp(
                            currentApp,
                            className = rootInActiveWindow?.className?.toString(),
                            eventSnapshot = getLiveWindowSnapshot()
                        )
                    }
                }
            }
        }
    }

    private fun checkAndBlockApp(pkg: String, className: String?, eventSnapshot: String?) {
        if (pkg == BuildConfig.APPLICATION_ID) return

        heartbeatScope.launch {
            val prefs = hyperFocusDataStore.current()
            if (!prefs.isActive) return@launch

            if (prefs.sessionMode == HyperFocusSessionMode.TIME_BASED) {
                val endsAt = prefs.sessionEndsAtMillis
                if (endsAt != null && endsAt <= System.currentTimeMillis()) {
                    hyperFocusManager.deactivate()
                    return@launch
                }
            }

            val protectedSurfaceDecision = evaluateProtectedSurface(pkg, className, eventSnapshot)
            val isTamperSensitive = protectedSurfaceDecision?.shouldBlock == true
            val isUserBlockedApp = pkg in prefs.blockedPackages
            if (!isUserBlockedApp && !isTamperSensitive) return@launch
            if (RewardEngine.isUnlockActive(prefs)) return@launch

            if (isTamperSensitive && !isUserBlockedApp) {
                hyperFocusManager.reportTamper(
                    protectedSurfaceDecision?.tamperReason ?: "Protected settings opened: $pkg"
                )
            }

            val now = System.currentTimeMillis()
            if (pkg == lastBlockedPackage && (now - lastBlockTime) < BLOCK_DEBOUNCE_MS) return@launch

            lastBlockedPackage = pkg
            lastBlockTime = now
            Log.d(TAG, "Blocking app: $pkg")

            val launchIntent = Intent(this@AppBlockingService, HyperFocusActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("blocked_package", pkg)
            }

            val launched = runCatching {
                startActivity(launchIntent)
            }.onFailure { error ->
                Log.w(TAG, "Failed to launch blocking overlay for $pkg", error)
            }.isSuccess

            if (!launched) {
                if (DeviceOwnerKioskManager.isStrictKioskEnforcementEnabled(this@AppBlockingService)) {
                    DeviceOwnerKioskManager.bringLauncherToFront(this@AppBlockingService)
                }
                // Android 12+ can deny background launches; force HOME so user exits blocked app.
                runCatching {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }.onFailure { error ->
                    Log.w(TAG, "Fallback HOME action failed after overlay launch denial", error)
                }
            }
        }
    }

    private fun evaluateProtectedSurface(
        packageName: String,
        className: String?,
        eventSnapshot: String?
    ): ProtectedSurfaceDecision? {
        val loweredClassName = className?.lowercase(Locale.ROOT).orEmpty()

        val inSettingsSurface = packageName in SETTINGS_PACKAGES || packageName in PERMISSION_CONTROLLER_PACKAGES
        val inInstallerSurface = packageName in PACKAGE_INSTALLER_PACKAGES

        if (!inSettingsSurface && !inInstallerSurface) return null

        val snapshot = ((eventSnapshot ?: "") + " " + getWindowTextSnapshot())
            .lowercase(Locale.ROOT)

        val looksLikeDeviceAdmin =
            loweredClassName.contains("deviceadmin") || DEVICE_ADMIN_KEYWORDS.any { snapshot.contains(it) }
        if (looksLikeDeviceAdmin) {
            return if (DeviceOwnerKioskManager.isAdminActive(this)) {
                ProtectedSurfaceDecision(
                    shouldBlock = true,
                    tamperReason = "Protected device-admin settings opened: $packageName"
                )
            } else {
                // Allow opening device-admin activation pages while admin is still off.
                ProtectedSurfaceDecision(shouldBlock = false, tamperReason = null)
            }
        }

        val looksLikeAppManagement =
            loweredClassName.contains("installedappdetails") ||
                loweredClassName.contains("appinfo") ||
                loweredClassName.contains("uninstall") ||
                loweredClassName.contains("manageapplications") ||
                loweredClassName.contains("applicationsdetails") ||
                loweredClassName.contains("settings\$appinfo") ||
                APP_MANAGEMENT_KEYWORDS.any { snapshot.contains(it) }
        val isTargetingThisApp =
            APP_PROTECTION_TOKENS.any { snapshot.contains(it) } ||
                loweredClassName.contains("installedappdetails") ||
                loweredClassName.contains("uninstall")

        if (looksLikeAppManagement && isTargetingThisApp) {
            return ProtectedSurfaceDecision(
                shouldBlock = true,
                tamperReason = "Protected app-management screen opened: $packageName"
            )
        }

        return null
    }

    private fun buildEventSnapshot(event: AccessibilityEvent, className: String?): String {
        val textPart = event.text?.joinToString(" ") { it?.toString().orEmpty() }.orEmpty()
        val contentDescription = event.contentDescription?.toString().orEmpty()
        return "$className $textPart $contentDescription"
    }

    private fun getWindowTextSnapshot(maxNodes: Int = 120): String {
        val root = rootInActiveWindow ?: return ""
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val builder = StringBuilder()
        queue.add(root)

        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited += 1

            node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                builder.append(it).append(' ')
            }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
                builder.append(it).append(' ')
            }
            node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let {
                builder.append(it).append(' ')
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.addLast(it) }
            }

        }
        return builder.toString()
    }

    private fun getLiveWindowSnapshot(): String {
        return getWindowTextSnapshot()
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun getForegroundAppFromUsageStats(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            usm?.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, now - 2000L, now)
                ?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AppBlockingService destroyed")
        runCatching { unregisterReceiver(unlockExpiredReceiver) }
        runCatching { unregisterReceiver(packageTamperReceiver) }
        heartbeatScope.cancel()
    }
}
