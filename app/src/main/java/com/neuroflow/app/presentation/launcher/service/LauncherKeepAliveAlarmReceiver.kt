package com.neuroflow.app.presentation.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class LauncherKeepAliveAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_KEEP_ALIVE) return
        LauncherKeepAliveService.start(context)
    }

    companion object {
        const val ACTION_KEEP_ALIVE = "com.neuroflow.app.ACTION_KEEP_ALIVE"

        fun startServiceIntent(context: Context): Intent {
            return Intent(context, LauncherKeepAliveService::class.java)
        }
    }
}