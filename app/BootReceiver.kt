package com.focusguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = PrefsManager(context)
            if (prefs.isScheduleActive) {
                ContextCompat.startForegroundService(
                    context, Intent(context, BlockerService::class.java)
                )
            }
        }
    }
}
