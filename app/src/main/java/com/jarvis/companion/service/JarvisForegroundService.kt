package com.jarvis.companion.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.companion.MainActivity
import com.jarvis.companion.R
import com.jarvis.companion.audio.AudioRecordManager
import com.jarvis.companion.audio.AudioRoute
import com.jarvis.companion.audio.AudioTrackPlayer
import com.jarvis.companion.audio.BluetoothScoManager
import com.jarvis.companion.network.GeminiLiveWebSocketClient
import com.jarvis.companion.network.JarvisWebSocketClient
import com.jarvis.companion.util.CallSecretaryManager
import com.jarvis.companion.util.PhoneActionManager
import com.jarvis.companion.util.PreferencesHelper

class JarvisForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.jarvis.companion.ACTION_START"
        const val ACTION_STOP = "com.jarvis.companion.ACTION_STOP"
        const val ACTION_TOGGLE_MIC = "com.jarvis.companion.ACTION_TOGGLE_MIC"
        const val ACTION_WAKE = "com.jarvis.companion.ACTION_WAKE"
        const val ACTION_EARBUD_TAP = "com.jarvis.companion.ACTION_EARBUD_TAP"
        const val BROADCAST_STATE_CHANGE = "com.jarvis.companion.STATE_CHANGE"
        const val EXTRA_STATE = "extra_state"

        const val BROADCAST_RMS = "com.jarvis.companion.BROADCAST_RMS"
        const val EXTRA_RMS = "extra_rms"

        const val BROADCAST_INPUT_TRANSCRIPT = "com.jarvis.companion.INPUT_TRANSCRIPT"
        const val EXTRA_TRANSCRIPT = "extra_transcript"

        const val BROADCAST_CHAT_LOG = "com.jarvis.companion.CHAT_LOG"
        const val EXTRA_SPEAKER = "extra_speaker"
        const val EXTRA_TEXT = "extra_text"

        const val BROADCAST_ACTION_LOG = "com.jarvis.companion.ACTION_LOG"
        const val EXTRA_ACTION_NAME = "extra_action_name"
        const val EXTRA_ACTION_RESULT = "extra_action_result"

        const val ACTION_SEND_TEXT = "com.jarvis.companion.ACTION_SEND_TEXT"
        const val EXTRA_COMMAND_TEXT = "extra_command_text"

        const val ACTION_SET_ROUTE = "com.jarvis.companion.ACTION_SET_ROUTE"
        const val EXTRA_ROUTE = "extra_route"
        const val BROADCAST_ROUTE_CHANGE = "com.jarvis.companion.ROUTE_CHANGE"
        const val EXTRA_ROUTE_NAME = "extra_route_name"

        const val BROADCAST_QURON_UPDATE = "com.jarvis.companion.QURON_UPDATE"
        const val EXTRA_QURON_URL = "extra_quron_url"
        const val EXTRA_QURON_SURA = "extra_quron_sura"
        const val EXTRA_QURON_OYAT = "extra_quron_oyat"

        const val ACTION_INTERRUPT = "com.jarvis.companion.ACTION_INTERRUPT"
        const val ACTION_SET_SECRETARY = "com.jarvis.companion.ACTION_SET_SECRETARY"

        const val BROADCAST_PC_CONNECTION_FAILED = "com.jarvis.companion.PC_CONNECTION_FAILED"
        const val EXTRA_PC_ERROR = "extra_pc_error"

        const val BROADCAST_CAPTION = "com.jarvis.companion.CAPTION"
        const val EXTRA_CAPTION = "extra_caption"

        const val BROADCAST_TURN_COMPLETE = "com.jarvis.companion.TURN_COMPLETE"

        const val BROADCAST_DOMAIN_CHANGE = "com.jarvis.companion.DOMAIN_CHANGE"
        const val EXTRA_DOMAIN_ID = "extra_domain_id"

        const val BROADCAST_MEDIA_PLAY = "com.jarvis.companion.MEDIA_PLAY"
        const val EXTRA_MEDIA_URL = "extra_media_url"
        const val EXTRA_MEDIA_TITLE = "extra_media_title"

        const val BROADCAST_CONNECTION_GREETING = "com.jarvis.companion.CONNECTION_GREETING"
        private const val TAG = "JarvisService"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: PreferencesHelper
    private lateinit var phoneActionManager: PhoneActionManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSessionCompat? = null

    private lateinit var scoManager: BluetoothScoManager
    private lateinit var audioRecorder: AudioRecordManager
    private lateinit var audioPlayer: AudioTrackPlayer
    private lateinit var wsClient: JarvisWebSocketClient
    private lateinit var geminiLiveClient: GeminiLiveWebSocketClient
    private lateinit var callSecretary: CallSecretaryManager

    private var currentAudioRoute: AudioRoute = AudioRoute.PHONE
    private var isMicStreaming = false
    private var isConnected = false
    private var isSpeaking = false

    private var mediaPlayer: MediaPlayer? = null
    private var currentMediaTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesHelper(this)
        phoneActionManager = PhoneActionManager(this)
        currentAudioRoute = try {
            AudioRoute.valueOf(prefs.audioRoute)
        } catch (_: Exception) {
            AudioRoute.PHONE
        }

        acquireLocks()
        createNotificationChannel()
        setupMediaSession()

        audioPlayer = AudioTrackPlayer(sampleRate = 24000).apply {
            onAudioRms = { speechRms ->
                broadcastRms(speechRms)
            }
        }

        geminiLiveClient = GeminiLiveWebSocketClient(
            onSetupComplete = {
                Log.d("JarvisService", "Gemini Live setupComplete")
                broadcastState("CONNECTED")
                updateNotification()
            },
            onInputTranscription = { transcript ->
                Log.d("JarvisService", "Gemini Live Input: $transcript")
                broadcastInputTranscript(transcript)
            },
            onOutputTranscription = { text ->
                Log.d("JarvisService", "Gemini Live Output: $text")
                broadcastCaption(text)
                broadcastState("SPEAKING")
            },
            onAudioChunkReceived = { pcmChunk ->
                audioPlayer.write(pcmChunk)
            },
            onTurnComplete = {
                Log.d("JarvisService", "Gemini Live turnComplete")
                if (isMicStreaming) broadcastState("LISTENING")
                broadcastTurnComplete()
            },
            onInterrupted = {
                Log.d("JarvisService", "Gemini Live interrupted")
                audioPlayer.flush()
                if (isMicStreaming) broadcastState("LISTENING")
                broadcastTurnComplete()
            },
            onError = { err ->
                Log.e("JarvisService", "Gemini Live error: $err")
                broadcastActionLog("gemini_live", "Xatolik: $err")
                if (prefs.connectionMode == "GEMINI_DIRECT") {
                    broadcastState("DISCONNECTED")
                    updateNotification()
                }
            },
            onConnectionStateChanged = { connected ->
                Log.d("JarvisService", "Gemini Live state changed: connected=$connected")
                if (prefs.connectionMode == "GEMINI_DIRECT") {
                    isConnected = connected
                    broadcastState(if (connected) "CONNECTED" else "DISCONNECTED")
                    updateNotification()
                }
            }
        )

        audioRecorder = AudioRecordManager(this, sampleRate = 16000) { pcmChunk ->
            if (isMicStreaming) {
                // Echo Gate: if AudioTrack is physically outputting sound, suppress mic to avoid echo.
                // If audioTrack has stopped playing, auto-release isSpeaking so user can reply immediately!
                val activelyPlaying = audioPlayer.isActivelyPlaying()
                if (activelyPlaying) {
                    return@AudioRecordManager
                }
                if (isSpeaking && !activelyPlaying) {
                    isSpeaking = false
                    broadcastState("LISTENING")
                }
                if (prefs.connectionMode == "GEMINI_DIRECT") {
                    geminiLiveClient.sendAudio(pcmChunk)
                } else {
                    if (isConnected) {
                        wsClient.sendAudio(pcmChunk)
                    }
                }

            }
        }
        audioRecorder.onRmsListener = { rms, norm ->
            broadcastRms(norm)
        }
        audioRecorder.onRouteFallback = { message ->
            mainHandler.post {
                setAudioRoute(AudioRoute.PHONE)
                broadcastActionLog("audio_route", "⚠️ $message")
            }
        }

        var lastScoState: Boolean? = null
        scoManager = BluetoothScoManager(this) { scoConnected ->
            if (lastScoState == scoConnected) return@BluetoothScoManager
            lastScoState = scoConnected
            Log.d("JarvisService", "Bluetooth SCO state: $scoConnected")
            updateNotification()
            val status = if (scoConnected) "🎧 Quloqchin ulandi" else "📱 Telefon mikrofoni"
            broadcastActionLog("audio_route", status)
            if (isMicStreaming) {
                audioRecorder.updateRouting()
            }
        }

        wsClient = JarvisWebSocketClient(
            onConnected = {
                isConnected = true
                Log.d("JarvisService", "Connected to Jarvis PC server")
                updateNotification()
                broadcastState("CONNECTED")
                broadcastConnectionGreeting()
            },
            onDisconnected = {
                isConnected = false
                Log.d("JarvisService", "Disconnected from Jarvis PC server")
                updateNotification()
                broadcastState("DISCONNECTED")
            },
            onAudioReceived = { pcmChunk ->
                audioPlayer.write(pcmChunk)
            },
            onControlReceived = { json ->
                val type = json.optString("type")
                when (type) {
                    "speaking" -> {
                        isSpeaking = json.optBoolean("state", false)
                        val vol = if (isSpeaking) 0.15f else 1.0f
                        try {
                            mediaPlayer?.setVolume(vol, vol)
                        } catch (_: Exception) {}

                        if (isSpeaking) {
                            broadcastState("SPEAKING")
                        } else {
                            broadcastState("LISTENING")
                        }
                        updateNotification()
                    }
                    "interrupt" -> {
                        audioPlayer.flush()
                    }
                    "media_play", "play" -> {
                        val streamUrlRaw = json.optString("stream_url", json.optString("url", ""))
                        val directUrlRaw = json.optString("direct_url", json.optString("fallback_url", ""))
                        val title = json.optString("title", "Musiqa")
                        currentMediaTitle = title

                        val isYouTube = streamUrlRaw.contains("youtube.com") || streamUrlRaw.contains("youtu.be") ||
                                directUrlRaw.contains("youtube.com") || directUrlRaw.contains("youtu.be")

                        if (isYouTube) {
                            val ytUrl = if (streamUrlRaw.contains("youtube") || streamUrlRaw.contains("youtu.be")) streamUrlRaw else directUrlRaw
                            // 1. Broadcast to MainActivity for in-app video rendering if app is open
                            broadcastMediaPlay(ytUrl, title)

                            // 2. Safely launch intent without background activity restriction blocking
                            try {
                                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl))
                                phoneActionManager.launchIntentSafely(ytIntent, "YouTube")
                                broadcastActionLog("musiqa", "YouTube ochildi: $title")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error opening YouTube: ${e.message}")
                            }
                        } else {
                            val host = prefs.serverAddress.trim()
                                .removePrefix("http://")
                                .removePrefix("https://")
                                .removePrefix("ws://")
                                .removePrefix("wss://")

                            val finalUrl = when {
                                streamUrlRaw.startsWith("http://") || streamUrlRaw.startsWith("https://") -> streamUrlRaw
                                streamUrlRaw.isNotEmpty() -> "http://$host$streamUrlRaw"
                                directUrlRaw.isNotEmpty() -> directUrlRaw
                                else -> ""
                            }

                            val primaryUrl = if (directUrlRaw.startsWith("http://") || directUrlRaw.startsWith("https://")) directUrlRaw else finalUrl
                            val fallbackUrl = if (primaryUrl == directUrlRaw) finalUrl else directUrlRaw

                            if (primaryUrl.isNotEmpty()) {
                                playMediaStream(primaryUrl, fallbackUrl, title)
                            }
                        }
                    }
                    "switch_domain" -> {
                        val domainId = json.optString("domain", json.optString("query", "ASOSIY"))
                        broadcastDomainChange(domainId)
                    }
                    "media_mute" -> {
                        val muted = json.optBoolean("muted", false)
                        val vol = if (muted) 0.0f else (if (isSpeaking) 0.15f else 1.0f)
                        mediaPlayer?.setVolume(vol, vol)
                        broadcastActionLog("musiqa", if (muted) "Ovoz o'chirildi" else "Ovoz yoqildi")
                    }
                    "media_pause" -> {
                        val paused = json.optBoolean("paused", true)
                        if (paused) {
                            mediaPlayer?.pause()
                            broadcastActionLog("musiqa", "Pauza qilindi")
                        } else {
                            mediaPlayer?.start()
                            broadcastActionLog("musiqa", "Ijro davom etmoqda")
                        }
                    }
                    "media_stop" -> {
                        stopMediaStream()
                        broadcastActionLog("musiqa", "To'xtatildi")
                    }
                    "media_seek" -> {
                        val sec = json.optDouble("seconds", 0.0)
                        val rel = json.optBoolean("relative", true)
                        mediaPlayer?.let { mp ->
                            val cur = mp.currentPosition
                            val target = if (rel) (cur + (sec * 1000).toInt()) else (sec * 1000).toInt()
                            mp.seekTo(target.coerceAtLeast(0))
                        }
                    }
                    "log" -> {
                        val speaker = json.optString("speaker", "")
                        val text = json.optString("text", "")
                        if (text.isNotEmpty()) {
                            broadcastChatLog(speaker, text)
                        }
                    }
                    "action" -> {
                        val name = json.optString("name", "")
                        val result = json.optString("result", "")
                        if (name.isNotEmpty()) {
                            broadcastActionLog(name, result)
                        }
                    }
                    "status" -> {
                        val state = json.optString("state", "")
                        if (state.isNotEmpty()) {
                            broadcastState(state)
                        }
                    }
                    "phone_action" -> {
                        val id = json.optString("id", "")
                        val act = json.optString("action", "")
                        val query = json.optString("query", "")
                        val message = json.optString("message", "")
                        val time = json.optString("time", "")
                        val limit = json.optInt("limit", 5)

                        val resText = when (act) {
                            "open_app" -> phoneActionManager.openApp(query, json.optString("package"))
                            "call" -> phoneActionManager.makeCall(query)
                            "search_contact" -> phoneActionManager.searchContactDetailed(query)
                            "sms" -> phoneActionManager.sendSms(query, message)
                            "telegram_send", "send_telegram" -> {
                                val recipient = json.optString("recipient", query)
                                phoneActionManager.sendTelegramMessage(recipient, message)
                            }
                            "switch_domain" -> {
                                val dId = if (query.isNotEmpty()) query else json.optString("domain", "ASOSIY")
                                broadcastDomainChange(dId)
                                "Axmadulloh sir, $dId rejimiga o'tildi."
                            }
                            "reminder" -> phoneActionManager.setReminder(query, time)
                            "telegram_messages" -> phoneActionManager.getTelegramMessages(limit)
                            else -> "Noma'lum amal: $act"
                        }

                        wsClient.sendControl(org.json.JSONObject().apply {
                            put("type", "phone_action_result")
                            put("id", id)
                            put("result", resText)
                        })
                        broadcastActionLog("phone_$act", resText)
                    }
                    "set_route" -> {
                        val rStr = json.optString("route", "").uppercase()
                        try {
                            val route = AudioRoute.valueOf(rStr)
                            setAudioRoute(route)
                        } catch (_: Exception) {}
                    }
                    "quron_lesson" -> {
                        val url = json.optString("url", "")
                        val sura = json.optString("sura", "")
                        val oyat = json.optInt("oyat", 0)
                        broadcastQuronUpdate(url, sura, oyat)
                    }
                    "set_secretary_reason" -> {
                        val reason = json.optString("reason", "Darsdaman")
                        val enabled = json.optBoolean("enabled", true)
                        prefs.aiSecretaryReason = reason
                        prefs.aiSecretaryEnabled = enabled
                        broadcastActionLog("ai_secretary", "Sabab o'rnatildi: $reason")
                    }
                    "trigger_secretary_call" -> {
                        val target = json.optString("target", "")
                        val reason = json.optString("reason", prefs.aiSecretaryReason)
                        callSecretary.triggerSecretaryManually(target, reason)
                    }
                }
            },
            onError = { errorMsg ->
                Log.e("JarvisService", "PC connection error: $errorMsg")
                broadcastActionLog("pc_bridge", errorMsg)
                broadcastPcConnectionFailed(errorMsg)
            }
        )

        callSecretary = CallSecretaryManager(
            this,
            phoneActionManager,
            wsClient,
            prefs
        ) { title, msg ->
            broadcastActionLog(title, msg)
        }

        callSecretary.onCallStateListener = { state, inCall ->
            if (inCall) {
                Log.d("JarvisService", "Qo'ng'iroq faol: mikrofon va audio to'xtatildi")
                try {
                    audioRecorder.stop()
                    audioPlayer.flush()
                    mediaPlayer?.pause()
                    if (isConnected) {
                        wsClient.sendControl(org.json.JSONObject().apply {
                            put("type", "phone_call_state")
                            put("in_call", true)
                        })
                    }
                    broadcastState("MIC_OFF")
                    broadcastActionLog("phone_call", "Qo'ng'iroq boshlandi: Jarvis mikrofoni to'xtatildi")
                } catch (e: Exception) {
                    Log.e("JarvisService", "Error pausing recorder for call: ${e.message}")
                }
            } else {
                Log.d("JarvisService", "Qo'ng'iroq yakunlandi: mikrofon qayta yoqilmoqda")
                mainHandler.postDelayed({
                    try {
                        if (isMicStreaming) {
                            audioRecorder.start()
                            broadcastState("MIC_ON")
                        } else {
                            broadcastState("IDLE")
                        }
                        if (isConnected) {
                            wsClient.sendControl(org.json.JSONObject().apply {
                                put("type", "phone_call_state")
                                put("in_call", false)
                            })
                        }
                        broadcastActionLog("phone_call", "Qo'ng'iroq yakunlandi: Jarvis mikrofoni qayta yoqildi")
                    } catch (e: Exception) {
                        Log.e("JarvisService", "Error restarting recorder after call: ${e.message}")
                    }
                }, 1200)
            }
        }
        callSecretary.startMonitoring()
    }

    fun setAudioRoute(route: AudioRoute) {
        currentAudioRoute = route
        prefs.audioRoute = route.name
        scoManager.setAudioRoute(route)
        audioRecorder.setAudioRoute(route)
        audioPlayer.currentRoute = route

        val label = when (route) {
            AudioRoute.PC -> "💻 Kompyuter rejimi"
            AudioRoute.PHONE -> "📱 Telefon dinamigi"
            AudioRoute.EARBUDS -> "🎧 Bluetooth quloqchin"
        }
        broadcastActionLog("audio_route", label)
        broadcastRouteChange(route.name)
        updateNotification()

        if (isConnected) {
            wsClient.sendControl(org.json.JSONObject().apply {
                put("type", "route_change")
                put("route", route.name.lowercase())
            })
        }
    }

    private fun broadcastRouteChange(routeName: String) {
        val intent = Intent(BROADCAST_ROUTE_CHANGE).apply {
            putExtra(EXTRA_ROUTE_NAME, routeName)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastQuronUpdate(url: String, sura: String, oyat: Int) {
        val intent = Intent(BROADCAST_QURON_UPDATE).apply {
            putExtra(EXTRA_QURON_URL, url)
            putExtra(EXTRA_QURON_SURA, sura)
            putExtra(EXTRA_QURON_OYAT, oyat)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private var isForegroundStarted = false
    private var lastFgsType: Int = -1

    private fun startForegroundSafely() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                if (!isForegroundStarted || lastFgsType != fgsType) {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                    lastFgsType = fgsType
                    isForegroundStarted = true
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgsType = 0
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (!isForegroundStarted || lastFgsType != fgsType) {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                    lastFgsType = fgsType
                    isForegroundStarted = true
                }
            } else {
                if (!isForegroundStarted) {
                    startForeground(NOTIFICATION_ID, notification)
                    isForegroundStarted = true
                }
            }
        } catch (e: Throwable) {
            Log.w("JarvisService", "Typed startForeground failed: ${e.message}, falling back to basic startForeground")
            try {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
                isForegroundStarted = true
            } catch (e2: Throwable) {
                Log.e("JarvisService", "Fatal fallback startForeground failed: ${e2.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ALWAYS satisfy Android 8+ foreground service requirements immediately
        startForegroundSafely()

        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                prefs.isServiceRunning = true
                try { scoManager.start() } catch (e: Exception) { Log.e("JarvisService", "scoManager.start error: ${e.message}") }
                try { audioPlayer.start() } catch (e: Exception) { Log.e("JarvisService", "audioPlayer.start error: ${e.message}") }

                if (prefs.connectionMode == "GEMINI_DIRECT") {
                    try { wsClient.disconnect() } catch (_: Exception) {}
                    val key = prefs.geminiApiKey
                    if (key.isNotBlank()) {
                        Log.i("JarvisService", "Starting Gemini Live direct mode...")
                        geminiLiveClient.connect(key)
                    } else {
                        Log.w("JarvisService", "Gemini API key is missing!")
                        broadcastActionLog("gemini_live", "Gemini API kaliti kiritilmagan. Sozlamalardan kiriting.")
                    }
                } else {
                    try { geminiLiveClient.disconnect() } catch (_: Exception) {}
                    try { wsClient.connect(prefs.serverAddress, prefs.authToken) } catch (e: Exception) { Log.e("JarvisService", "wsClient.connect error: ${e.message}") }
                }

                // Only start audio recording if RECORD_AUDIO permission is granted
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    isMicStreaming = true
                    try {
                        audioRecorder.start()
                    } catch (e: Exception) {
                        Log.e("JarvisService", "audioRecorder.start error: ${e.message}")
                    }
                    setAudioRoute(currentAudioRoute)
                    broadcastState("MIC_ON")
                } else {
                    isMicStreaming = false
                    Log.w("JarvisService", "RECORD_AUDIO not granted yet, mic recording paused")
                    broadcastState("PERMISSION_DENIED")
                }
            }
            ACTION_SET_ROUTE -> {
                val rName = intent?.getStringExtra(EXTRA_ROUTE) ?: AudioRoute.EARBUDS.name
                val r = try { AudioRoute.valueOf(rName) } catch (_: Exception) { AudioRoute.EARBUDS }
                setAudioRoute(r)
            }
            ACTION_TOGGLE_MIC -> {
                toggleMicStreaming()
            }
            ACTION_WAKE -> {
                if (prefs.connectionMode == "GEMINI_DIRECT") {
                    if (!geminiLiveClient.isConnected.get()) {
                        geminiLiveClient.connect(prefs.geminiApiKey)
                    }
                } else {
                    try { wsClient.sendWake() } catch (_: Exception) {}
                }
                playBeep()
                if (!isMicStreaming) {
                    toggleMicStreaming()
                }
            }
            ACTION_EARBUD_TAP -> {
                handleEarbudTap()
            }
            ACTION_SEND_TEXT -> {
                val cmd = intent?.getStringExtra(EXTRA_COMMAND_TEXT) ?: ""
                if (cmd.isNotBlank()) {
                    broadcastChatLog("user", cmd)
                    if (prefs.connectionMode == "GEMINI_DIRECT") {
                        if (!geminiLiveClient.isConnected.get()) {
                            geminiLiveClient.connect(prefs.geminiApiKey)
                        }
                        geminiLiveClient.sendText(cmd)
                    } else {
                        try { wsClient.sendText(cmd) } catch (_: Exception) {}
                    }
                }
            }
            ACTION_INTERRUPT -> {
                try {
                    audioPlayer.flush()
                    if (prefs.connectionMode != "GEMINI_DIRECT") {
                        wsClient.sendControl(org.json.JSONObject().apply {
                            put("type", "interrupt")
                        })
                    }
                } catch (_: Exception) {}
            }
            ACTION_SET_SECRETARY -> {
                val enabled = intent?.getBooleanExtra("enabled", prefs.aiSecretaryEnabled) ?: prefs.aiSecretaryEnabled
                val reason = intent?.getStringExtra("reason") ?: prefs.aiSecretaryReason
                prefs.aiSecretaryEnabled = enabled
                prefs.aiSecretaryReason = reason
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private var lastTapTime = 0L
    private val tapHandler = Handler(Looper.getMainLooper())
    private var pendingTapRunnable: Runnable? = null

    private fun handleEarbudTap(keyCode: Int = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
        // 1. If JARVIS is speaking, ANY touch/button on the earbud interrupts immediately!
        if (isSpeaking) {
            Log.d("JarvisService", "Earbud tapped while speaking -> Immediate Interrupt!")
            pendingTapRunnable?.let { tapHandler.removeCallbacks(it) }
            pendingTapRunnable = null
            lastTapTime = 0L
            onEarbudDoubleTap()
            return
        }

        // 2. i12 TWS and other Bluetooth earbuds send KEYCODE_MEDIA_NEXT / PREVIOUS on hardware double-tap
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                Log.d("JarvisService", "Hardware double-tap keycode ($keyCode) -> Interrupted/Listening")
                pendingTapRunnable?.let { tapHandler.removeCallbacks(it) }
                pendingTapRunnable = null
                lastTapTime = 0L
                onEarbudDoubleTap()
                return
            }
        }

        // 3. Single vs double tap detection on generic click events
        val now = SystemClock.elapsedRealtime()
        val diff = now - lastTapTime

        if (diff in 50..500) {
            // DOUBLE TAP! (2 marta urildi)
            pendingTapRunnable?.let { tapHandler.removeCallbacks(it) }
            pendingTapRunnable = null
            lastTapTime = 0L
            onEarbudDoubleTap()
        } else {
            // SINGLE TAP (1 marta bosildi) — 400ms kutib, 2-marta bosilmasa bajariladi
            lastTapTime = now
            pendingTapRunnable?.let { tapHandler.removeCallbacks(it) }
            val r = Runnable {
                pendingTapRunnable = null
                onEarbudSingleTap()
            }
            pendingTapRunnable = r
            tapHandler.postDelayed(r, 400)
        }
    }

    private fun onEarbudSingleTap() {
        Log.d("JarvisService", "Earbud Single Tap: Labbay, eshitaman")
        playBeep(highPitch = true)

        broadcastChatLog("jarvis", "Labbay, Axmadulloh sir, eshitaman!")
        broadcastState("LISTENING")

        if (prefs.connectionMode == "GEMINI_DIRECT") {
            if (!geminiLiveClient.isConnected.get()) {
                geminiLiveClient.connect(prefs.geminiApiKey)
            }
            geminiLiveClient.sendText("Labbay, Axmadulloh sir, eshitaman deb qisqa javob qaytar")
        } else {
            wsClient.sendWake()
            wsClient.sendText("Labbay, Axmadulloh sir, eshitaman deb qisqa javob qaytar")
        }

        if (!isMicStreaming) {
            isMicStreaming = true
            audioRecorder.start()
        }
    }

    private fun onEarbudDoubleTap() {
        Log.d("JarvisService", "Earbud Double Tap: Interrupted — listening...")
        playDoubleBeep()

        // Flush and stop audio output immediately
        audioPlayer.flush()
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}

        if (prefs.connectionMode == "GEMINI_DIRECT") {
            // AudioTrack flushed
        } else {
            wsClient.sendInterrupt()
        }

        broadcastChatLog("jarvis", "⚡ Interrupted — listening...")
        broadcastState("LISTENING")

        if (!isMicStreaming) {
            isMicStreaming = true
            audioRecorder.start()
        }
    }

    private fun toggleMicStreaming() {
        isMicStreaming = !isMicStreaming
        playBeep()
        if (isMicStreaming) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startForegroundSafely()
                audioRecorder.start()
                if (prefs.connectionMode == "GEMINI_DIRECT") {
                    if (!geminiLiveClient.isConnected.get()) {
                        geminiLiveClient.connect(prefs.geminiApiKey)
                    }
                } else {
                    wsClient.sendWake()
                }
                broadcastState("MIC_ON")
            } else {
                isMicStreaming = false
                broadcastState("PERMISSION_DENIED")
            }
        } else {
            audioRecorder.stop()
            broadcastState("MIC_OFF")
        }
        updateNotification()
    }

    private fun playBeep(highPitch: Boolean = false) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75)
            val toneType = if (highPitch) ToneGenerator.TONE_PROP_PROMPT else ToneGenerator.TONE_PROP_BEEP
            tone.startTone(toneType, 120)
        } catch (_: Exception) {}
    }

    private fun playDoubleBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
        } catch (_: Exception) {}
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisCompanion::ServiceWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours lock
        }

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "JarvisCompanion::WifiLock").apply {
            acquire()
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "JarvisMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
                    )
                    .build()
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        Log.d("JarvisService", "MediaButtonEvent keyCode=${keyEvent.keyCode}")
                        handleEarbudTap(keyEvent.keyCode)
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onPlay() { handleEarbudTap(KeyEvent.KEYCODE_MEDIA_PLAY) }
                override fun onPause() { handleEarbudTap(KeyEvent.KEYCODE_MEDIA_PAUSE) }
                override fun onSkipToNext() { onEarbudDoubleTap() }
                override fun onSkipToPrevious() { onEarbudDoubleTap() }
                override fun onStop() { onEarbudDoubleTap() }
            })
            isActive = true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Aloqa Xizmati",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Uydagi kompyuter bilan ovozli ko‘prik fon xizmati"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val toggleMicIntent = Intent(this, JarvisForegroundService::class.java).apply {
            action = ACTION_TOGGLE_MIC
        }.let {
            PendingIntent.getService(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val wakeIntent = Intent(this, JarvisForegroundService::class.java).apply {
            action = ACTION_WAKE
        }.let {
            PendingIntent.getService(this, 2, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val stopIntent = Intent(this, JarvisForegroundService::class.java).apply {
            action = ACTION_STOP
        }.let {
            PendingIntent.getService(this, 3, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val statusText = when {
            prefs.connectionMode == "GEMINI_DIRECT" -> {
                when {
                    !geminiLiveClient.isConnected.get() -> "Gemini Live-ga ulanmoqda..."
                    isSpeaking -> "🔊 JARVIS gapirmoqda"
                    isMicStreaming -> "🎤 Sizni eshitmoqda (Gemini Live)"
                    else -> "🟢 Aloqada (Kutish rejimida)"
                }
            }
            !isConnected -> "Kompyuterga ulanmoqda..."
            isSpeaking -> "🔊 JARVIS gapirmoqda"
            currentMediaTitle.isNotEmpty() -> "🎵 $currentMediaTitle"
            isMicStreaming -> "🎤 Sizni eshitmoqda (Mikrofon ochiq)"
            else -> "🟢 Aloqada (Kutish rejimida)"
        }

        val micActionTitle = if (isMicStreaming) "🔇 Jim turish" else "🎤 Gapirish"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Aloqada")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_mic, micActionTitle, toggleMicIntent)
            .addAction(R.drawable.ic_jarvis, "⚡ Uyg‘otish", wakeIntent)
            .addAction(R.drawable.ic_stop, "⏹️ To‘xtatish", stopIntent)
            .build()
    }

    private fun playMediaStream(url: String, fallbackUrl: String, title: String) {
        try {
            stopMediaStream()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    mp.start()
                    val vol = if (isSpeaking) 0.15f else 1.0f
                    mp.setVolume(vol, vol)
                    val dev = if (currentAudioRoute == AudioRoute.EARBUDS) "Quloqchinda" else "Telefonda"
                    broadcastActionLog("musiqa", "$dev ijro etilmoqda: $title")
                    updateNotification()
                }
                setOnCompletionListener {
                    currentMediaTitle = ""
                    broadcastActionLog("musiqa", "Ijro yakunlandi: $title")
                    updateNotification()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("JarvisService", "Media error: $what, extra: $extra")
                    if (fallbackUrl.isNotEmpty() && url != fallbackUrl) {
                        Log.d("JarvisService", "Retrying with fallback direct URL: $fallbackUrl")
                        playMediaStream(fallbackUrl, "", title)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("JarvisService", "Failed to start media stream: ${e.message}")
        }
    }

    private fun stopMediaStream() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentMediaTitle = ""
            updateNotification()
        } catch (_: Exception) {}
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun broadcastState(state: String) {
        val intent = Intent(BROADCAST_STATE_CHANGE).apply {
            putExtra(EXTRA_STATE, state)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastRms(rmsNorm: Float) {
        val intent = Intent(BROADCAST_RMS).apply {
            putExtra(EXTRA_RMS, rmsNorm)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastInputTranscript(transcript: String) {
        val intent = Intent(BROADCAST_INPUT_TRANSCRIPT).apply {
            putExtra(EXTRA_TRANSCRIPT, transcript)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastChatLog(speaker: String, text: String) {
        val intent = Intent(BROADCAST_CHAT_LOG).apply {
            putExtra(EXTRA_SPEAKER, speaker)
            putExtra(EXTRA_TEXT, text)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastActionLog(name: String, result: String) {
        val intent = Intent(BROADCAST_ACTION_LOG).apply {
            putExtra(EXTRA_ACTION_NAME, name)
            putExtra(EXTRA_ACTION_RESULT, result)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastCaption(caption: String) {
        val intent = Intent(BROADCAST_CAPTION).apply {
            putExtra(EXTRA_CAPTION, caption)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastTurnComplete() {
        val intent = Intent(BROADCAST_TURN_COMPLETE).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastDomainChange(domainId: String) {
        val intent = Intent(BROADCAST_DOMAIN_CHANGE).apply {
            putExtra(EXTRA_DOMAIN_ID, domainId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastMediaPlay(url: String, title: String) {
        val intent = Intent(BROADCAST_MEDIA_PLAY).apply {
            putExtra(EXTRA_MEDIA_URL, url)
            putExtra(EXTRA_MEDIA_TITLE, title)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastConnectionGreeting() {
        val intent = Intent(BROADCAST_CONNECTION_GREETING).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastPcConnectionFailed(errorMsg: String) {
        val intent = Intent(BROADCAST_PC_CONNECTION_FAILED).apply {
            putExtra(EXTRA_PC_ERROR, errorMsg)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }


    override fun onDestroy() {
        isForegroundStarted = false
        prefs.isServiceRunning = false
        stopMediaStream()
        audioRecorder.stop()
        audioPlayer.stop()
        scoManager.stop()
        wsClient.disconnect()
        try { geminiLiveClient.disconnect() } catch (_: Exception) {}
        try { callSecretary.release() } catch (_: Exception) {}

        mediaSession?.release()
        mediaSession = null

        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()

        broadcastState("STOPPED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
