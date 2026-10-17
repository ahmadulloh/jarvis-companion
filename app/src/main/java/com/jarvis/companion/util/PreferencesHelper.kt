package com.jarvis.companion.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_companion_prefs", Context.MODE_PRIVATE)

    var serverAddress: String
        get() = prefs.getString("server_address", "77.83.206.138:8000") ?: "77.83.206.138:8000"
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
        get() = prefs.getString("audio_route", "PHONE") ?: "PHONE"
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
        get() = prefs.getString("connection_mode", "PC_BRIDGE") ?: "PC_BRIDGE"
        set(value) = prefs.edit().putString("connection_mode", value.trim()).apply()

    // Encryption helper for sensitive API keys
    private fun encryptKey(plain: String): String {
        if (plain.isBlank()) return ""
        try {
            val keyBytes = "JarvisSecretKey2026SecureSeed".toByteArray()
            val inBytes = plain.toByteArray(Charsets.UTF_8)
            val out = ByteArray(inBytes.size)
            for (i in inBytes.indices) {
                out[i] = (inBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return "ENC:" + android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            return plain
        }
    }

    private fun decryptKey(encoded: String): String {
        if (encoded.isBlank()) return ""
        if (!encoded.startsWith("ENC:")) return encoded
        try {
            val b64 = encoded.removePrefix("ENC:")
            val inBytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            val keyBytes = "JarvisSecretKey2026SecureSeed".toByteArray()
            val out = ByteArray(inBytes.size)
            for (i in inBytes.indices) {
                out[i] = (inBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return String(out, Charsets.UTF_8)
        } catch (_: Exception) {
            return ""
        }
    }

    // Direct Google Gemini API Key for phone-only AI (Encrypted storage)
    var geminiApiKey: String
        get() {
            val enc = prefs.getString("gemini_api_key_enc", null)
            if (enc != null) return decryptKey(enc)
            val legacy = prefs.getString("gemini_api_key", "") ?: ""
            if (legacy.isNotBlank()) {
                geminiApiKey = legacy
            }
            return legacy
        }
        set(value) {
            prefs.edit().putString("gemini_api_key_enc", encryptKey(value.trim())).remove("gemini_api_key").apply()
        }

    // Show Text Input Bar (default true = input bar always visible above keyboard)
    var showTextInputBar: Boolean
        get() = prefs.getBoolean("show_text_input_bar", true)
        set(value) = prefs.edit().putBoolean("show_text_input_bar", value).apply()

    // TTS Voice Engine: "UZBEKVOICE_AI", "ELEVENLABS", "DEVICE_TTS"
    var ttsEngine: String
        get() = prefs.getString("tts_engine", "DEVICE_TTS") ?: "DEVICE_TTS"
        set(value) = prefs.edit().putString("tts_engine", value.trim()).apply()


    // UzbekVoice.ai Credentials (Encrypted storage)
    var uzbekVoiceApiKey: String
        get() {
            val enc = prefs.getString("uzbek_voice_api_key_enc", null)
            val key = if (enc != null) decryptKey(enc) else (prefs.getString("uzbek_voice_api_key", "") ?: "")
            return key.ifBlank { "393c4279-0b87-4935-a8ce-8391e859f81c:bbc076fd-1dae-48fd-a0fd-29a86cc16fa1" }
        }
        set(value) {
            prefs.edit().putString("uzbek_voice_api_key_enc", encryptKey(value.trim())).remove("uzbek_voice_api_key").apply()
        }

    var uzbekVoiceModel: String
        get() = prefs.getString("uzbek_voice_model", "sevinch") ?: "sevinch"
        set(value) = prefs.edit().putString("uzbek_voice_model", value.trim()).apply()

    // ElevenLabs Credentials (Encrypted storage)
    var elevenLabsApiKey: String
        get() {
            val enc = prefs.getString("eleven_labs_api_key_enc", null)
            val key = if (enc != null) decryptKey(enc) else (prefs.getString("eleven_labs_api_key", "") ?: "")
            return key.ifBlank { "sk_cae53f7e2debb8bd6a99700edfd811ce69b9b4dbbca7995b" }
        }
        set(value) {
            prefs.edit().putString("eleven_labs_api_key_enc", encryptKey(value.trim())).remove("eleven_labs_api_key").apply()
        }

    var elevenLabsVoiceId: String
        get() = prefs.getString("eleven_labs_voice_id", "21m00Tcm4TlvDq8ikWAM") ?: "21m00Tcm4TlvDq8ikWAM"
        set(value) = prefs.edit().putString("eleven_labs_voice_id", value.trim()).apply()

    var lastWelcomeTimestamp: Long
        get() = prefs.getLong("last_welcome_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_welcome_timestamp", value).apply()
}
