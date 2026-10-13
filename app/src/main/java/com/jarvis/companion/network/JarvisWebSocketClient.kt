package com.jarvis.companion.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class JarvisWebSocketClient(
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onControlReceived: (JSONObject) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isManuallyStopped = false
    private var currentUrl: String = ""

    fun connect(serverAddress: String, authToken: String) {
        isManuallyStopped = false

        val host = serverAddress.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")

        val scheme = if (serverAddress.startsWith("https://") || serverAddress.startsWith("wss://")) "wss" else "ws"
        currentUrl = "$scheme://$host/ws/jarvis-audio?token=$authToken"

        val request = Request.Builder()
            .url(currentUrl)
            .build()

        Log.d("JarvisWebSocketClient", "Connecting to $currentUrl...")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("JarvisWebSocketClient", "Connected successfully!")
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
                    Log.w("JarvisWebSocketClient", "Non-JSON message: $text")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("JarvisWebSocketClient", "Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("JarvisWebSocketClient", "Closed: $code / $reason")
                onDisconnected()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("JarvisWebSocketClient", "Failure: ${t.message}")
                onDisconnected()
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (isManuallyStopped) return
        Thread {
            try {
                Thread.sleep(4000)
                if (!isManuallyStopped && currentUrl.isNotEmpty()) {
                    Log.d("JarvisWebSocketClient", "Reconnecting...")
                    val request = Request.Builder().url(currentUrl).build()
                    webSocket = client.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(ws: WebSocket, response: Response) = onConnected()
                        override fun onMessage(ws: WebSocket, bytes: ByteString) = onAudioReceived(bytes.toByteArray())
                        override fun onMessage(ws: WebSocket, text: String) {
                            try { onControlReceived(JSONObject(text)) } catch (_: Exception) {}
                        }
                        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                            onDisconnected()
                            scheduleReconnect()
                        }
                        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                            onDisconnected()
                            scheduleReconnect()
                        }
                    })
                }
            } catch (_: Exception) {}
        }.start()
    }

    fun sendAudio(pcmChunk: ByteArray) {
        webSocket?.send(ByteString.of(*pcmChunk))
    }

    fun sendControl(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    fun sendWake() {
        val json = JSONObject().apply {
            put("type", "wake")
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

    fun disconnect() {
        isManuallyStopped = true
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        onDisconnected()
    }
}
