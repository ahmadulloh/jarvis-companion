package com.jarvis.companion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.jarvis.companion.service.JarvisForegroundService

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        // Toggle mic / wake via Service
                        val serviceIntent = Intent(context, JarvisForegroundService::class.java).apply {
                            action = JarvisForegroundService.ACTION_TOGGLE_MIC
                        }
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
