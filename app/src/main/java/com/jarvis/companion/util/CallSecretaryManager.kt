package com.jarvis.companion.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.jarvis.companion.network.JarvisWebSocketClient
import org.json.JSONObject
import java.util.Locale

class CallSecretaryManager(
    private val context: Context,
    private val phoneActionManager: PhoneActionManager,
    private val wsClient: JarvisWebSocketClient,
    private val prefs: PreferencesHelper,
    private val onLogCallback: (title: String, message: String) -> Unit
) {

    companion object {
        private const val TAG = "CallSecretaryManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var currentCallState = TelephonyManager.CALL_STATE_IDLE
    private var currentCallerName = ""
    private var currentCallerNumber = ""
    private var isSecretaryCallActive = false
    private var callStartTime = 0L

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val uzLocale = Locale("uz", "UZ")
                val res = tts?.setLanguage(uzLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale("ru", "RU")
                }
                isTtsReady = true
            }
        }
    }

    fun startMonitoring() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallStateChange(state, null)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChange(state, phoneNumber)
                    }
                }, PhoneStateListener.LISTEN_CALL_STATE)
            }
            Log.d(TAG, "Call monitoring started.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call monitoring: ${e.message}")
        }
    }

    fun formatReasonUzbek(reason: String): String {
        val r = reason.trim().lowercase()
        return when {
            r.contains("dars") -> "darsdalar"
            r.contains("mashina") || r.contains("hayda") -> "mashina haydamoqdalar"
            r.contains("namoz") -> "namozdalar"
            r.contains("majlis") || r.contains("ish") || r.contains("band") -> "muhim majlisda bandlar"
            r.contains("dam") || r.contains("uyqu") -> "dam olmoqdalar"
            r.contains("ovqat") -> "tushlik qilmoqdalar"
            r.isNotEmpty() -> "$reason sababli hozir bandlar"
            else -> "muhim ish bilan bandlar"
        }
    }

    var onCallStateListener: ((state: Int, inCall: Boolean) -> Unit)? = null

    private fun handleCallStateChange(state: Int, incomingNumber: String?) {
        if (state == currentCallState) return
        currentCallState = state

        val inCall = (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING)
        onCallStateListener?.invoke(state, inCall)

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                val num = incomingNumber ?: ""
                val contactName = if (num.isNotEmpty()) phoneActionManager.searchContactByNumber(num) else null
                currentCallerName = contactName ?: if (num.isNotEmpty()) num else "Noma'lum qo'ng'iroq"
                currentCallerNumber = num

                onLogCallback("Qo'ng'iroq", "Kiruvchi qo'ng'iroq: $currentCallerName")

                if (prefs.aiSecretaryEnabled) {
                    onLogCallback("AI Kotib", "AI Kotib yoqilgan: 3 soniyada avto-javob beriladi...")
                    mainHandler.postDelayed({
                        if (currentCallState == TelephonyManager.CALL_STATE_RINGING) {
                            answerCall()
                        }
                    }, 3500)
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                callStartTime = System.currentTimeMillis()
                if (prefs.aiSecretaryEnabled || isSecretaryCallActive) {
                    isSecretaryCallActive = true
                    startSecretaryConversation()
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (isSecretaryCallActive) {
                    finishSecretaryConversation()
                }
                resetCall()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun answerCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.acceptRingingCall()
            }
            isSecretaryCallActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call: ${e.message}")
        }
    }

    fun triggerSecretaryManually(targetName: String, customReason: String? = null) {
        currentCallerName = targetName
        isSecretaryCallActive = true
        if (customReason != null && customReason.isNotBlank()) {
            prefs.aiSecretaryReason = customReason
        }
        startSecretaryConversation()
    }

    private fun startSecretaryConversation() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.e(TAG, "Speakerphone toggle error: ${e.message}")
        }

        val reasonText = formatReasonUzbek(prefs.aiSecretaryReason)
        val greeting = "Assalomu alaykum. Men Axmadulloh sirning sun'iy intellekt yordamchisi Jarvisman. " +
                "Axmadulloh sir hozir $reasonText, shuning uchun go'shakni ko'tara olmadilar. " +
                "Sizni tinglayapman, qanday xabar qoldirasiz?"

        onLogCallback("AI Kotib", "Muloqot boshlandi: $greeting")

        mainHandler.postDelayed({
            speakText(greeting)
        }, 1200)

        // Send to Jarvis PC
        val json = JSONObject().apply {
            put("type", "call_secretary_started")
            put("caller", currentCallerName)
            put("number", currentCallerNumber)
            put("reason", prefs.aiSecretaryReason)
            put("reason_formatted", reasonText)
        }
        wsClient.sendControl(json)
    }

    private fun finishSecretaryConversation() {
        try {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        isSecretaryCallActive = false
        val durationSec = ((System.currentTimeMillis() - callStartTime) / 1000).coerceAtLeast(1)

        val summary = "Axmadulloh sir, $currentCallerName bilan qo'ng'iroq yakunlandi ($durationSec soniya). " +
                "Sabab: «${prefs.aiSecretaryReason}». Xabar muvaffaqiyatli qabul qilindi."

        onLogCallback("AI Kotib Hisoboti", summary)

        val json = JSONObject().apply {
            put("type", "call_secretary_finished")
            put("caller", currentCallerName)
            put("number", currentCallerNumber)
            put("reason", prefs.aiSecretaryReason)
            put("duration_seconds", durationSec)
            put("summary", summary)
        }
        wsClient.sendControl(json)
    }

    fun speakText(text: String) {
        if (!isTtsReady || text.isBlank()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_SECRETARY")
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error: ${e.message}")
        }
    }

    private fun resetCall() {
        currentCallState = TelephonyManager.CALL_STATE_IDLE
        isSecretaryCallActive = false
        currentCallerName = ""
        currentCallerNumber = ""
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
