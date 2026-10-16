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

    var audioRoute: String
        get() = prefs.getString("audio_route", "EARBUDS") ?: "EARBUDS"
        set(value) = prefs.edit().putString("audio_route", value).apply()

    var aiSecretaryEnabled: Boolean
        get() = prefs.getBoolean("ai_secretary_enabled", true)
        set(value) = prefs.edit().putBoolean("ai_secretary_enabled", value).apply()

    var aiSecretaryReason: String
        get() = prefs.getString("ai_secretary_reason", "Darsdaman") ?: "Darsdaman"
        set(value) = prefs.edit().putString("ai_secretary_reason", value.trim()).apply()

    var userName: String
        get() = prefs.getString("user_name", "Axmadulloh sir") ?: "Axmadulloh sir"
        set(value) = prefs.edit().putString("user_name", value.trim()).apply()

    var userEmail: String
        get() = prefs.getString("user_email", "axmadulloh@gmail.com") ?: "axmadulloh@gmail.com"
        set(value) = prefs.edit().putString("user_email", value.trim()).apply()

    var userAvatarUri: String
        get() = prefs.getString("user_avatar_uri", "") ?: ""
        set(value) = prefs.edit().putString("user_avatar_uri", value.trim()).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean("is_logged_in", true)
        set(value) = prefs.edit().putBoolean("is_logged_in", value).apply()

    var avatarMode: String
        get() = prefs.getString("avatar_mode", "FEMALE_AI") ?: "FEMALE_AI"
        set(value) = prefs.edit().putString("avatar_mode", value).apply()

    var quronCardVisible: Boolean
        get() = prefs.getBoolean("quron_card_visible", false)
        set(value) = prefs.edit().putBoolean("quron_card_visible", value).apply()

    var activeDomain: String
        get() = prefs.getString("active_domain", "ASOSIY") ?: "ASOSIY"
        set(value) = prefs.edit().putString("active_domain", value.trim()).apply()

    // Hybrid Connection Mode: "GEMINI_DIRECT" or "PC_BRIDGE"
    var connectionMode: String
        get() = prefs.getString("connection_mode", "GEMINI_DIRECT") ?: "GEMINI_DIRECT"
        set(value) = prefs.edit().putString("connection_mode", value.trim()).apply()

    // Direct Google Gemini API Key for phone-only AI
    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value.trim()).apply()

    // Show Text Input Bar (default false = clean, futuristic voice-first mode)
    var showTextInputBar: Boolean
        get() = prefs.getBoolean("show_text_input_bar", false)
        set(value) = prefs.edit().putBoolean("show_text_input_bar", value).apply()
}
