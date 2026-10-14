package com.jarvis.companion.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

data class TelegramMessage(
    val sender: String,
    val text: String,
    val timestamp: Long
)

class JarvisNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotification"
        private val recentMessages = CopyOnWriteArrayList<TelegramMessage>()

        fun getRecentMessages(limit: Int = 5): List<TelegramMessage> {
            return recentMessages.takeLast(limit.coerceIn(1, 20)).reversed()
        }

        fun clearMessages() {
            recentMessages.clear()
        }

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, JarvisNotificationListener::class.java).flattenToString()
            return enabledListeners.contains(myComponent)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName?.lowercase() ?: ""
        if (!pkg.contains("telegram") && !pkg.contains("challegram")) {
            return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) 
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() 
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() 
            ?: extras.getString(Notification.EXTRA_BIG_TEXT) 
            ?: ""

        if (title.isBlank() || text.isBlank()) return

        // Skip generic service notifications
        if (text.contains("Telegram is running", ignoreCase = true) ||
            text.contains("Checking for messages", ignoreCase = true)) {
            return
        }

        val now = System.currentTimeMillis()
        val msg = TelegramMessage(sender = title.trim(), text = text.trim(), timestamp = now)

        // Avoid exact immediate duplicate
        val last = recentMessages.lastOrNull()
        if (last != null && last.sender == msg.sender && last.text == msg.text && (now - last.timestamp < 3000)) {
            return
        }

        recentMessages.add(msg)
        if (recentMessages.size > 50) {
            recentMessages.removeAt(0)
        }
        Log.d(TAG, "New Telegram message from ${msg.sender}: ${msg.text}")
    }
}
