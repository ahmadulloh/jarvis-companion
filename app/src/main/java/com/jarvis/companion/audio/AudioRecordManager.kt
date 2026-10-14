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

class AudioRecordManager(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val onAudioChunk: (ByteArray) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // VAD threshold
    var vadEnabled: Boolean = false
    var vadThreshold: Double = 90.0

    // Software digital gain boost (1.5x) to compensate for low Bluetooth earbud mic gain
    var gainBoost: Float = 1.5f

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
        if (isRecording) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate / 10 * 2) // ~100ms slices

        try {
            // Priority 1: VOICE_COMMUNICATION is strictly required for Bluetooth SCO & echo cancellation
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            )

            var record: AudioRecord? = null
            for (src in sources) {
                try {
                    val candidate = AudioRecord(src, sampleRate, channelConfig, audioFormat, bufferSize)
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidate
                        Log.d("AudioRecordManager", "AudioRecord initialized with source $src")
                        break
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.w("AudioRecordManager", "Source $src init failed: ${e.message}")
                }
            }

            if (record == null) {
                Log.e("AudioRecordManager", "Failed to initialize AudioRecord with any source")
                return
            }
            audioRecord = record

            // Attach hardware acoustic effects to audio session
            try {
                val sessionId = record.audioSessionId
                if (AutomaticGainControl.isAvailable()) {
                    AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                    Log.d("AudioRecordManager", "Hardware AutomaticGainControl enabled")
                }
                if (NoiseSuppressor.isAvailable()) {
                    NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                    Log.d("AudioRecordManager", "Hardware NoiseSuppressor enabled")
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                    Log.d("AudioRecordManager", "Hardware AcousticEchoCanceler enabled")
                }
            } catch (e: Exception) {
                Log.w("AudioRecordManager", "AudioFX init: ${e.message}")
            }

            // Route to Bluetooth headset microphone if available
            bindBluetoothInputDevice(record)

            record.startRecording()
            isRecording = true

            recordingThread = Thread {
                val buffer = ByteArray(3200) // 100 ms at 16000 Hz, 16-bit mono
                while (isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        applyGain(buffer, readBytes)
                        val rms = calculateRms(buffer, readBytes)
                        if (rms < 32.0) {
                            // Squelch static floor hiss from cheap Bluetooth mic
                            buffer.fill(0, 0, readBytes)
                        }
                        val chunk = buffer.copyOf(readBytes)
                        onAudioChunk(chunk)
                    }
                }
            }.apply {
                name = "JarvisAudioRecordThread"
                priority = Thread.MAX_PRIORITY
                start()
            }
            Log.d("AudioRecordManager", "Recording started at $sampleRate Hz")
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Error starting AudioRecord: ${e.message}")
        }
    }

    private fun bindBluetoothInputDevice(record: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val btInput = getBluetoothInputDevice()
            if (btInput != null) {
                val prefSet = record.setPreferredDevice(btInput)
                Log.d("AudioRecordManager", "setPreferredDevice Bluetooth: $prefSet (${btInput.productName ?: "BT"})")
                return prefSet
            }
        }
        return false
    }

    fun updateRouting() {
        if (!isRecording) return
        Log.d("AudioRecordManager", "updateRouting called on SCO state change")
        audioRecord?.let { record ->
            if (bindBluetoothInputDevice(record)) {
                return
            }
        }
        // If setPreferredDevice did not bind directly, perform a smooth non-blocking restart
        Thread {
            try {
                Log.d("AudioRecordManager", "Restarting AudioRecord to catch new SCO route")
                stop()
                Thread.sleep(200)
                start()
            } catch (e: Exception) {
                Log.e("AudioRecordManager", "Restart error: ${e.message}")
            }
        }.start()
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
            Log.d("AudioRecordManager", "Recording stopped")
        } catch (e: Exception) {
            Log.e("AudioRecordManager", "Error stopping AudioRecord: ${e.message}")
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
