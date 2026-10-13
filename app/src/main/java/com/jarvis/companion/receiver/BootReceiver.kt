package com.jarvis.companion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jarvis.companion.service.JarvisForegroundService
import com.jarvis.companion.util.PreferencesHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = PreferencesHelper(context)
            if (prefs.autoStartOnBoot) {
                Log.d("BootReceiver", "Device booted. Starting JarvisForegroundService...")
                val serviceIntent = Intent(context, JarvisForegroundService::class.java).apply {
                    this.action = JarvisForegroundService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
