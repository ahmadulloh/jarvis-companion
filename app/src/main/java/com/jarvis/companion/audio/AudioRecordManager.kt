package com.jarvis.companion.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlin.math.sqrt

/**
 * AudioRecordManager — Low-latency 16kHz PCM Audio Recorder
 * with hardware AEC/NS/AGC, dynamic gain boost, Bluetooth SCO routing,
 * real-time RMS monitoring, and automated fallback when earbuds are disconnected.
 */
class AudioRecordManager(
    private val context: Context,
    val sampleRate: Int = 16000,
    private val onAudioChunk: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecordManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    var isRecording = false
        private set
    private var recordingThread: Thread? = null

    // Callbacks for live UI reaction & fallback
    var onRmsListener: ((rms: Double, normalized: Float) -> Unit)? = null
    var onRouteFallback: ((message: String) -> Unit)? = null

    // Software digital gain boost (2.5x = ~8dB) for Bluetooth SCO earbuds
    var gainBoost: Float = 2.5f

    var currentRoute: AudioRoute = AudioRoute.EARBUDS
        private set

    fun isBluetoothHeadsetConnected(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                if (devices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }) return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                return inputDevices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            }
            return audioManager.isBluetoothScoAvailableOffCall || audioManager.isBluetoothA2dpOn
        } catch (_: Exception) {
            return false
        }
    }

    private fun getBluetoothInputDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return inputDevices.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) {
            Log.d(TAG, "AudioRecord already recording, skipping start")
            return
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate / 10 * 2) // ~100ms slices

        try {
            // Check earbud connectivity before starting
            if (currentRoute == AudioRoute.EARBUDS && !isBluetoothHeadsetConnected()) {
                Log.w(TAG, "⚠️ Quloqchin tanlangan, lekin ulanmagan! Avtomatik telefon mikrofoniga o'tilmoqda.")
                currentRoute = AudioRoute.PHONE
                gainBoost = 1.2f
                onRouteFallback?.invoke("Quloqchin ulanmagan, telefon mikrofoniga o'tildi")
            }

            // Priority: VOICE_COMMUNICATION for SCO & hardware echo canceler, fallback to MIC
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )

            var record: AudioRecord? = null
            var chosenSource = -1
            for (src in sources) {
                try {
                    val candidate = AudioRecord(src, sampleRate, channelConfig, audioFormat, bufferSize)
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidate
                        chosenSource = src
                        Log.i(TAG, "✅ AudioRecord STATE_INITIALIZED: source=$src, sampleRate=$sampleRate Hz, channel=MONO, format=PCM_16BIT, bufferSize=$bufferSize, sessionId=${candidate.audioSessionId}")
                        break
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord candidate source=$src failed: ${e.message}")
                }
            }

            if (record == null) {
                Log.e(TAG, "❌ AudioRecord FAILED to initialize with any source! sampleRate=$sampleRate, bufferSize=$bufferSize")
                return
            }
            audioRecord = record

            // Attach hardware acoustic effects to audio session
            try {
                val sessionId = record.audioSessionId
                if (AutomaticGainControl.isAvailable()) {
                    AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                    Log.d(TAG, "Hardware AutomaticGainControl enabled")
                }
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    Log.d(TAG, "Hardware NoiseSuppressor enabled")
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    Log.d(TAG, "Hardware AcousticEchoCanceler enabled")
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioFX init: ${e.message}")
            }

            // Route to Bluetooth headset microphone if available
            bindBluetoothInputDevice(record)

            record.startRecording()
            isRecording = true

            recordingThread = Thread {
                val buffer = ByteArray(3200) // 100 ms at 16000 Hz, 16-bit mono
                var secBytes = 0
                var lastLogTime = System.currentTimeMillis()
                var totalBytesRead = 0L

                while (isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val rms = calculateRms(buffer, readBytes)
                        // Normalize RMS (typical speech: 200 - 4000)
                        val norm = ((rms - 40.0) / 4500.0).toFloat().coerceIn(0f, 1f)
                        onRmsListener?.invoke(rms, norm)

                        secBytes += readBytes
                        totalBytesRead += readBytes

                        val now = System.currentTimeMillis()
                        if (now - lastLogTime >= 1000L) {
                            Log.i(TAG, "🎙️ [AudioRecord] RMS: ${String.format("%.1f", rms)} | Oqim: $secBytes bayt/sek (Jami: $totalBytesRead bayt)")
                            secBytes = 0
                            lastLogTime = now
                        }

                        if (currentRoute == AudioRoute.PC) {
                            // In PC mode, PC uses its own microphone - phone companion mic is silent
                            continue
                        }

                        applyGain(buffer, readBytes)
                        val chunk = buffer.copyOf(readBytes)
                        onAudioChunk(chunk)
                    }
                }
            }.apply {
                name = "JarvisAudioRecordThread"
                priority = Thread.MAX_PRIORITY
                start()
            }
            Log.i(TAG, "🚀 Audio recording successfully started at $sampleRate Hz (Route: $currentRoute)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
        }
    }

    fun setAudioRoute(route: AudioRoute) {
        if (route == AudioRoute.EARBUDS && !isBluetoothHeadsetConnected()) {
            Log.w(TAG, "⚠️ Quloqchin rejimi tanlangan, lekin quloqchin ulanmagan! Avtomatik telefon mikrofoniga o'tilmoqda.")
            currentRoute = AudioRoute.PHONE
            gainBoost = 1.2f
            onRouteFallback?.invoke("Quloqchin ulanmagan, telefon mikrofoniga o'tildi")
        } else {
            currentRoute = route
            gainBoost = if (route == AudioRoute.EARBUDS) 2.5f else 1.2f
        }
        updateRouting()
    }

    private fun bindBluetoothInputDevice(record: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val btInput = getBluetoothInputDevice()
            if (btInput != null) {
                val prefSet = record.setPreferredDevice(btInput)
                Log.d(TAG, "setPreferredDevice Bluetooth: $prefSet (${btInput.productName ?: "BT"})")
                return prefSet
            }
        }
        return false
    }

    fun updateRouting() {
        if (!isRecording) return
        Log.d(TAG, "updateRouting called for route $currentRoute")
        val record = audioRecord ?: return
        if (currentRoute == AudioRoute.EARBUDS) {
            bindBluetoothInputDevice(record)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            record.preferredDevice = null
        }
    }

    private fun applyGain(buffer: ByteArray, bytesRead: Int) {
        if (gainBoost <= 1.0f) return
        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortVal = sample.toShort()
            val boosted = (shortVal * gainBoost).toInt().coerceIn(-32768, 32767)
            buffer[i] = (boosted and 0xFF).toByte()
            buffer[i + 1] = ((boosted shr 8) and 0xFF).toByte()
        }
    }

    fun stop() {
        isRecording = false
        try {
            recordingThread?.interrupt()
            recordingThread = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.i(TAG, "🛑 AudioRecord stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }

    private fun calculateRms(pcm: ByteArray, bytesCount: Int = pcm.size): Double {
        var sum = 0.0
        val sampleCount = bytesCount / 2
        for (i in 0 until sampleCount) {
            val sample = (pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)
            val shortVal = sample.toShort()
            sum += shortVal * shortVal
        }
        return if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
    }
}
