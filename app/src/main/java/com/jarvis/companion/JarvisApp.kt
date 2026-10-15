package com.jarvis.companion

import android.app.Application
import android.util.Log

class JarvisApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global Uncaught Exception Handler — prevents abrupt crash/exits
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("JarvisApp", "FATAL CRASH in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val prefs = getSharedPreferences("jarvis_crash_log", MODE_PRIVATE)
                prefs.edit().putString(
                    "last_crash",
                    "${throwable.javaClass.name}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
                ).apply()
            } catch (_: Exception) {}

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
