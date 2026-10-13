package com.jarvis.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.jarvis.companion.MainActivity
import com.jarvis.companion.R
import com.jarvis.companion.audio.AudioRecordManager
import com.jarvis.companion.audio.AudioTrackPlayer
import com.jarvis.companion.audio.BluetoothScoManager
import com.jarvis.companion.network.JarvisWebSocketClient
import com.jarvis.companion.util.PreferencesHelper

class JarvisForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_foreground_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.jarvis.companion.ACTION_START"
        const val ACTION_STOP = "com.jarvis.companion.ACTION_STOP"
        const val ACTION_TOGGLE_MIC = "com.jarvis.companion.ACTION_TOGGLE_MIC"
        const val ACTION_WAKE = "com.jarvis.companion.ACTION_WAKE"
        const val BROADCAST_STATE_CHANGE = "com.jarvis.companion.STATE_CHANGE"
        const val EXTRA_STATE = "extra_state"

        const val BROADCAST_CHAT_LOG = "com.jarvis.companion.CHAT_LOG"
        const val EXTRA_SPEAKER = "extra_speaker"
        const val EXTRA_TEXT = "extra_text"

        const val BROADCAST_ACTION_LOG = "com.jarvis.companion.ACTION_LOG"
        const val EXTRA_ACTION_NAME = "extra_action_name"
        const val EXTRA_ACTION_RESULT = "extra_action_result"

        const val ACTION_SEND_TEXT = "com.jarvis.companion.ACTION_SEND_TEXT"
        const val EXTRA_COMMAND_TEXT = "extra_command_text"
    }

    private lateinit var prefs: PreferencesHelper
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSessionCompat? = null

    private lateinit var scoManager: BluetoothScoManager
    private lateinit var audioRecorder: AudioRecordManager
    private lateinit var audioPlayer: AudioTrackPlayer
    private lateinit var wsClient: JarvisWebSocketClient

    private var isMicStreaming = false
    private var isConnected = false
    private var isSpeaking = false

    private var mediaPlayer: MediaPlayer? = null
    private var currentMediaTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesHelper(this)

        acquireLocks()
        createNotificationChannel()
        setupMediaSession()

        audioPlayer = AudioTrackPlayer(sampleRate = 24000)

        audioRecorder = AudioRecordManager(this, sampleRate = 16000) { pcmChunk ->
            if (isMicStreaming && isConnected) {
                wsClient.sendAudio(pcmChunk)
            }
        }
        audioRecorder.vadEnabled = false

        scoManager = BluetoothScoManager(this) { scoConnected ->
            Log.d("JarvisService", "Bluetooth SCO state: $scoConnected")
            updateNotification()
        }

        wsClient = JarvisWebSocketClient(
            onConnected = {
                isConnected = true
                Log.d("JarvisService", "Connected to Jarvis PC server")
                updateNotification()
                broadcastState("CONNECTED")
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
                    "media_play" -> {
                        val streamUrlRaw = json.optString("stream_url", "")
                        val directUrlRaw = json.optString("direct_url", "")
                        val title = json.optString("title", "Musiqa")
                        currentMediaTitle = title

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

                        if (finalUrl.isNotEmpty()) {
                            playMediaStream(finalUrl, directUrlRaw, title)
                        }
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
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                prefs.isServiceRunning = true
                scoManager.start()
                audioPlayer.start()
                wsClient.connect(prefs.serverAddress, prefs.authToken)

                // Always start continuous microphone recording
                isMicStreaming = true
                audioRecorder.start()
                broadcastState("MIC_ON")
            }
            ACTION_TOGGLE_MIC -> {
                toggleMicStreaming()
            }
            ACTION_WAKE -> {
                wsClient.sendWake()
                playBeep()
                if (!isMicStreaming) {
                    toggleMicStreaming()
                }
            }
            ACTION_SEND_TEXT -> {
                val cmd = intent?.getStringExtra(EXTRA_COMMAND_TEXT) ?: ""
                if (cmd.isNotBlank()) {
                    wsClient.sendText(cmd)
                    broadcastChatLog("user", cmd)
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun toggleMicStreaming() {
        isMicStreaming = !isMicStreaming
        playBeep()
        if (isMicStreaming) {
            audioRecorder.start()
            wsClient.sendWake()
            broadcastState("MIC_ON")
        } else {
            if (!prefs.continuousMode) {
                audioRecorder.stop()
            }
            broadcastState("MIC_OFF")
        }
        updateNotification()
    }

    private fun playBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
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
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY)
                    .build()
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        toggleMicStreaming()
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onPlay() { toggleMicStreaming() }
                override fun onPause() { toggleMicStreaming() }
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
                    broadcastActionLog("musiqa", "Quloqchinda ijro etilmoqda: $title")
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
        }
        sendBroadcast(intent)
    }

    private fun broadcastChatLog(speaker: String, text: String) {
        val intent = Intent(BROADCAST_CHAT_LOG).apply {
            putExtra(EXTRA_SPEAKER, speaker)
            putExtra(EXTRA_TEXT, text)
        }
        sendBroadcast(intent)
    }

    private fun broadcastActionLog(name: String, result: String) {
        val intent = Intent(BROADCAST_ACTION_LOG).apply {
            putExtra(EXTRA_ACTION_NAME, name)
            putExtra(EXTRA_ACTION_RESULT, result)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        prefs.isServiceRunning = false
        stopMediaStream()
        audioRecorder.stop()
        audioPlayer.stop()
        scoManager.stop()
        wsClient.disconnect()

        mediaSession?.release()
        mediaSession = null

        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()

        broadcastState("STOPPED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
