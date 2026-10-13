package com.jarvis.companion.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_companion_prefs", Context.MODE_PRIVATE)

    var serverAddress: String
        get() = prefs.getString("server_address", "100.112.86.116:8000") ?: "100.112.86.116:8000"
        set(value) = prefs.edit().putString("server_address", value.trim()).apply()

    var authToken: String
        get() = prefs.getString("auth_token", "jarvis-phone-companion-token") ?: "jarvis-phone-companion-token"
        set(value) = prefs.edit().putString("auth_token", value.trim()).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean("auto_start_boot", true)
        set(value) = prefs.edit().putBoolean("auto_start_boot", value).apply()

    var continuousMode: Boolean
        get() = prefs.getBoolean("continuous_mode", true)
        set(value) = prefs.edit().putBoolean("continuous_mode", value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean("service_running", false)
        set(value) = prefs.edit().putBoolean("service_running", value).apply()
}
