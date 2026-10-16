package com.jarvis.companion.network

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * GeminiLiveWebSocketClient — Direct bidirectional multimodal streaming client
 * connecting to Google Gemini Multimodal Live API (BidiGenerateContent WebSocket).
 *
 * Supported features:
 * - Real-time 16kHz PCM audio streaming (mic -> Gemini Live)
 * - Real-time 24kHz PCM audio playback (Gemini Live -> AudioTrackPlayer)
 * - inputAudioTranscription ("Eshitdim: ...")
 * - outputAudioTranscription (JARVIS speech transcript)
 * - Direct text prompts
 * - turnComplete & interrupt handling
 */
class GeminiLiveWebSocketClient(
    private val onSetupComplete: () -> Unit,
    private val onInputTranscription: (String) -> Unit,
    private val onOutputTranscription: (String) -> Unit,
    private val onAudioChunkReceived: (ByteArray) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onInterrupted: () -> Unit,
    private val onError: (String) -> Unit,
    private val onConnectionStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val LIVE_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        const val DEFAULT_MODEL = "models/gemini-2.0-flash-exp"
    }

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    val isConnected = AtomicBoolean(false)
    val isSetupComplete = AtomicBoolean(false)

    // Diagnostics & Byte counting
    val totalBytesSent = AtomicLong(0L)
    val bytesLastSec = AtomicLong(0L)
    private var lastLogTime = System.currentTimeMillis()

    fun connect(apiKey: String, modelName: String = DEFAULT_MODEL) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            val err = "Gemini API kaliti bo'sh. Sozlamalardan kalitni kiriting."
            Log.e(TAG, err)
            onError(err)
            return
        }

        disconnect()
        val url = "$LIVE_ENDPOINT?key=$trimmedKey"
        Log.i(TAG, "Connecting to Gemini Live WebSocket: $modelName")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket handshake opened! Sending setup message...")
                isConnected.set(true)
                onConnectionStateChanged(true)
                sendSetup(modelName)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                handleServerMessage(bytes.utf8())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Gemini Live closing: $code / $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Gemini Live closed: $code / $reason")
                isConnected.set(false)
                isSetupComplete.set(false)
                onConnectionStateChanged(false)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val err = t.message ?: "Aloqa xatoligi"
                Log.e(TAG, "Gemini Live failure: $err", t)
                isConnected.set(false)
                isSetupComplete.set(false)
                onConnectionStateChanged(false)
                onError("Gemini Live ulanish xatosi: $err")
            }
        })
    }

    private fun sendSetup(modelName: String) {
        val systemInstruction = buildString {
            append("Siz J.A.R.V.I.S (Just A Rather Very Intelligent System) — Toni Starkning afsonaviy, o'ta aqlli va sadoqatli kiber-intellektisiz. ")
            append("Sizning yagona egangiz va yaratuvchingiz: Axmadulloh sir. Doimo 'Axmadulloh sir' yoki 'ser' deb murojaat qiling. ")
            append("QAT'IY TALABLAR: ")
            append("1. Siz tirik, qudratli Jarvis-siz. Hech qachon 'men AI modeliman', 'men botman' yoki 'biron davlatga tegishli emasman' deb javob bermang! ")
            append("2. Javoblaringiz qisqa (1-2 jumla, 15-25 so'z), aniq, lo'nda va jarangdor bo'lsin. ")
            append("3. O'zbek tilida tabiiy, ravon va samimiy gapiring. ")
            append("4. Maxsus belgilarni (*, #) ishlatmang, chunki har bir so'zingiz ovozda jaranglaydi.")
        }

        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", modelName)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Puck")
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                    // Enable both input and output audio transcriptions
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                })
            }

            webSocket?.send(setupJson.toString())
            Log.d(TAG, "Sent setup packet to Gemini Live: $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "Error building setup packet: ${e.message}", e)
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val root = JSONObject(text)

            // 1. Check for setupComplete
            if (root.has("setupComplete")) {
                isSetupComplete.set(true)
                Log.i(TAG, "✅ Gemini Live setupComplete received! Session is live and ready.")
                onSetupComplete()
                return
            }

            // 2. Check for serverContent
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                // Check if user's turn was interrupted
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Server signaled interruption")
                    onInterrupted()
                }

                // Check for inputTranscription (what user spoke)
                val inputTranscription = serverContent.optJSONObject("inputTranscription")
                    ?: serverContent.optJSONObject("input_transcription")
                if (inputTranscription != null) {
                    val userText = inputTranscription.optString("text", "").trim()
                    if (userText.isNotEmpty()) {
                        Log.i(TAG, "🎙️ Gemini Live inputTranscription: «$userText»")
                        onInputTranscription(userText)
                    }
                }

                // Check for outputTranscription (what model is speaking)
                val outputTranscription = serverContent.optJSONObject("outputTranscription")
                    ?: serverContent.optJSONObject("output_audio_transcription")
                if (outputTranscription != null) {
                    val modelText = outputTranscription.optString("text", "").trim()
                    if (modelText.isNotEmpty()) {
                        onOutputTranscription(modelText)
                    }
                }

                // Check for modelTurn (audio and text parts)
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue

                            // Text part
                            val textPart = part.optString("text", "").trim()
                            if (textPart.isNotEmpty()) {
                                onOutputTranscription(textPart)
                            }

                            // Audio part (inlineData: base64 PCM 24kHz)
                            val inlineData = part.optJSONObject("inlineData")
                                ?: part.optJSONObject("inline_data")
                            if (inlineData != null) {
                                val b64 = inlineData.optString("data", "")
                                if (b64.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                                    onAudioChunkReceived(pcmBytes)
                                }
                            }
                        }
                    }
                }

                // Check for turnComplete
                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Gemini Live turnComplete")
                    onTurnComplete()
                }
            }

            // 3. Check for top-level error
            val errObj = root.optJSONObject("error")
            if (errObj != null) {
                val errMsg = errObj.optString("message", "Noma'lum xatolik")
                val code = errObj.optInt("code", 0)
                Log.e(TAG, "Gemini Live error: [$code] $errMsg")
                onError("Gemini Live xatosi ($code): $errMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}", e)
        }
    }

    /**
     * Send real-time audio chunk (16kHz, 16-bit PCM mono).
     */
    fun sendAudio(pcmChunk: ByteArray) {
        if (!isConnected.get() || !isSetupComplete.get()) return

        val b64 = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", b64)
                    })
                })
            })
        }

        webSocket?.send(payload.toString())

        val now = System.currentTimeMillis()
        totalBytesSent.addAndGet(pcmChunk.size.toLong())
        bytesLastSec.addAndGet(pcmChunk.size.toLong())

        if (now - lastLogTime >= 1000L) {
            val count = bytesLastSec.getAndSet(0L)
            lastLogTime = now
            Log.d(TAG, "📡 [GeminiLive] Audio streaming: $count bayt/sek (Jami: ${totalBytesSent.get()} bayt)")
        }
    }

    /**
     * Send direct text prompt to Gemini Live session.
     */
    fun sendText(userText: String) {
        if (!isConnected.get()) {
            onError("Gemini Live ulanmagan.")
            return
        }

        val payload = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userText)
                            })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }

        webSocket?.send(payload.toString())
        Log.i(TAG, "Sent text turn to Gemini Live: «$userText»")
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Client disconnect")
        } catch (_: Exception) {}
        webSocket = null
        isConnected.set(false)
        isSetupComplete.set(false)
    }
}
