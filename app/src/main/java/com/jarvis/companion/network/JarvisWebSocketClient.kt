package com.jarvis.companion.network

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class JarvisWebSocketClient(
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onControlReceived: (JSONObject) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "JarvisWebSocketClient"
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var webSocket: WebSocket? = null
    private val isManuallyStopped = AtomicBoolean(false)
    private var currentUrl: String = ""
    private var reconnectAttempts = 0

    fun connect(serverAddress: String, authToken: String) {
        isManuallyStopped.set(false)
        reconnectAttempts = 0

        ioExecutor.execute {
            try {
                val raw = serverAddress.trim()
                if (raw.isBlank()) {
                    val err = "Kompyuter manzili kiritilmagan. Sozlamalardan IP va portni kiriting."
                    Log.w(TAG, err)
                    onError(err)
                    onDisconnected()
                    return@execute
                }

                val host = raw
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .removePrefix("ws://")
                    .removePrefix("wss://")
                    .trimEnd('/')

                val scheme = if (raw.startsWith("https://") || raw.startsWith("wss://")) "wss" else "ws"
                val httpEquivalent = if (scheme == "wss") "https" else "http"
                val testHttpUrl = "$httpEquivalent://$host/ws/jarvis-audio?token=$authToken".toHttpUrlOrNull()

                if (testHttpUrl == null) {
                    val err = "Noto'g'ri server manzili formati: '$raw'. Masalan: 192.168.1.5:8000"
                    Log.e(TAG, err)
                    onError(err)
                    onDisconnected()
                    return@execute
                }

                currentUrl = "$scheme://$host/ws/jarvis-audio?token=$authToken"
                Log.i(TAG, "Connecting to PC Bridge: $currentUrl...")

                val request = Request.Builder()
                    .url(currentUrl)
                    .addHeader("Authorization", "Bearer $authToken")
                    .build()

                disconnectInternal()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "✅ Connected to Jarvis PC server successfully!")
                        reconnectAttempts = 0
                        onConnected()
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        onAudioReceived(bytes.toByteArray())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = JSONObject(text)
                            onControlReceived(json)
                        } catch (e: Exception) {
                            Log.w(TAG, "Non-JSON message: $text")
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "Closing: $code / $reason")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "Closed: $code / $reason")
                        onDisconnected()
                        if (!isManuallyStopped.get()) {
                            scheduleReconnect()
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val errMsg = "Kompyuterga ulanishda uzilish: ${t.localizedMessage ?: "Aloqa uzildi"}."
                        Log.w(TAG, "WebSocket failure: $errMsg")
                        onDisconnected()
                        if (!isManuallyStopped.get()) {
                            scheduleReconnect()
                        }
                    }
                })
            } catch (t: Throwable) {
                val err = "Kompyuterga ulanishda xatolik: ${t.message ?: t.javaClass.simpleName}"
                Log.e(TAG, err, t)
                onError(err)
                onDisconnected()
            }
        }
    }

    private fun scheduleReconnect() {
        if (isManuallyStopped.get()) return

        reconnectAttempts++
        // Exponential backoff: 2s, 4s, 8s, 16s, capped at 20s
        val delayMs = (1000L * (1 shl (reconnectAttempts.coerceAtMost(4)))).coerceIn(2000L, 20000L)

        if (reconnectAttempts == 6) {
            Log.w(TAG, "Multiple reconnect attempts ($reconnectAttempts). Server may be offline or changing network.")
        }

        ioExecutor.execute {
            try {
                Thread.sleep(delayMs)
                if (!isManuallyStopped.get() && currentUrl.isNotEmpty()) {
                    Log.d(TAG, "Reconnecting attempt $reconnectAttempts (delay ${delayMs}ms)...")
                    val request = Request.Builder()
                        .url(currentUrl)
                        .addHeader("Authorization", "Bearer ${currentUrl.substringAfter("token=")}")
                        .build()
                    webSocket = client.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(ws: WebSocket, response: Response) {
                            Log.i(TAG, "✅ Reconnected to PC server!")
                            reconnectAttempts = 0
                            onConnected()
                        }
                        override fun onMessage(ws: WebSocket, bytes: ByteString) = onAudioReceived(bytes.toByteArray())
                        override fun onMessage(ws: WebSocket, text: String) {
                            try { onControlReceived(JSONObject(text)) } catch (_: Exception) {}
                        }
                        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                            onDisconnected()
                            if (!isManuallyStopped.get()) scheduleReconnect()
                        }
                        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                            onDisconnected()
                            if (!isManuallyStopped.get()) scheduleReconnect()
                        }
                    })
                }
            } catch (_: Exception) {}
        }
    }

    fun sendAudio(pcmChunk: ByteArray) {
        try {
            webSocket?.send(pcmChunk.toByteString())
        } catch (_: Exception) {}
    }

    fun sendControl(json: JSONObject) {
        try {
            webSocket?.send(json.toString())
        } catch (_: Exception) {}
    }

    fun sendWake() {
        val json = JSONObject().apply {
            put("type", "wake")
        }
        sendControl(json)
    }

    fun sendInterrupt() {
        val json = JSONObject().apply {
            put("type", "interrupt")
        }
        sendControl(json)
    }

    fun sendText(text: String) {
        val json = JSONObject().apply {
            put("type", "text")
            put("text", text)
        }
        sendControl(json)
    }

    private fun disconnectInternal() {
        try {
            webSocket?.close(1000, "Reconnecting or changing mode")
        } catch (_: Exception) {}
        webSocket = null
    }

    fun disconnect() {
        isManuallyStopped.set(true)
        disconnectInternal()
        onDisconnected()
    }
}
