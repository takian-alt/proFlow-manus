package com.neuroflow.app.presentation.launcher.hyperfocus.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.neuroflow.app.BuildConfig
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
import javax.inject.Inject

@AndroidEntryPoint
class AppBlockingService : AccessibilityService() {

    @Inject lateinit var hyperFocusDataStore: HyperFocusDataStore
    @Inject lateinit var hyperFocusManager: HyperFocusManager

    private val heartbeatScope = CoroutineScope(Dispatchers.IO)
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0

    companion object {
        private const val TAG = "AppBlockingService"
        private const val BLOCK_DEBOUNCE_MS = 1000L
    }

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

        checkAndBlockApp(pkg)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppBlockingService interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AppBlockingService connected")

        serviceInfo?.let { info ->
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }

        registerReceiver(
            unlockExpiredReceiver,
            IntentFilter(UnlockTimerService.ACTION_UNLOCK_EXPIRED),
            RECEIVER_NOT_EXPORTED
        )

        registerReceiver(
            packageTamperReceiver,
            IntentFilter().apply {
                addDataScheme("package")
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_RESTARTED)
            },
            RECEIVER_NOT_EXPORTED
        )

        // Heartbeat
        heartbeatScope.launch {
            while (true) {
                delay(30_000L)
                hyperFocusManager.updateHeartbeat()  // now suspend, safe to call here
            }
        }

        // Polling backup — handles kicking user out when timer expires
        // Uses rootInActiveWindow as primary (no permission needed), UsageStats as secondary
        heartbeatScope.launch {
            while (true) {
                delay(500L)
                val prefs = hyperFocusDataStore.current()
                if (prefs.isActive) {
                    val currentApp = rootInActiveWindow?.packageName?.toString()
                        ?: getForegroundAppFromUsageStats()
                    if (currentApp != null) {
                        checkAndBlockApp(currentApp)
                    }
                }
            }
        }
    }

    private fun checkAndBlockApp(pkg: String) {
        if (pkg == BuildConfig.APPLICATION_ID) return

        heartbeatScope.launch {
            val prefs = hyperFocusDataStore.current()
            if (!prefs.isActive) return@launch
            if (pkg !in prefs.blockedPackages) return@launch
            if (RewardEngine.isUnlockActive(prefs)) return@launch

            val now = System.currentTimeMillis()
            if (pkg == lastBlockedPackage && (now - lastBlockTime) < BLOCK_DEBOUNCE_MS) return@launch

            lastBlockedPackage = pkg
            lastBlockTime = now
            Log.d(TAG, "Blocking app: $pkg")

            startActivity(Intent(this@AppBlockingService, HyperFocusActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("blocked_package", pkg)
            })
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
