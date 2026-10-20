package com.jarvis.companion

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class JarvisApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global Crash Guard — Prevents background threads from crashing the companion app
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("JarvisApp", "Caught unhandled exception in thread '${thread.name}': ${throwable.message}", throwable)
            try {
                val prefs = getSharedPreferences("jarvis_crash_log", MODE_PRIVATE)
                prefs.edit().putString(
                    "last_crash",
                    "${throwable.javaClass.simpleName}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
                ).apply()
            } catch (_: Exception) {}

            val isMain = (thread == Looper.getMainLooper().thread)
            if (!isMain) {
                // Background worker thread error (AudioRecord, WebSocket, Bluetooth) - recover silently!
                Log.w("JarvisApp", "Silently recovered from background thread error: ${thread.name}")
                return@setDefaultUncaughtExceptionHandler
            }

            // Guard against main-thread Audio/Bluetooth/Headset/Security exceptions
            val stack = Log.getStackTraceString(throwable)
            val isAudioOrHeadsetCrash = throwable is SecurityException ||
                    throwable is IllegalStateException && stack.contains("service", ignoreCase = true) ||
                    stack.contains("audio", ignoreCase = true) ||
                    stack.contains("bluetooth", ignoreCase = true) ||
                    stack.contains("headset", ignoreCase = true) ||
                    stack.contains("MediaButton", ignoreCase = true) ||
                    stack.contains("ForegroundServiceStartNotAllowedException")

            if (isAudioOrHeadsetCrash) {
                Log.w("JarvisApp", "Silently recovered from main-thread audio/headset error: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }

            // On other main thread errors, try to show toast before fallback
            try {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@JarvisApp, "JARVIS qayta tiklandi", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
