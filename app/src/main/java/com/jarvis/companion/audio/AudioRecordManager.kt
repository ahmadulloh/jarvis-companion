package com.jarvis.companion.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlin.math.sqrt

class AudioRecordManager(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val onAudioChunk: (ByteArray) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // VAD threshold: Bluetooth earphone mic gain is low, so threshold is sensitive
    var vadEnabled: Boolean = false
    var vadThreshold: Double = 90.0

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate / 10 * 2) // ~100ms slices

        try {
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
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

            // Route to Bluetooth headset microphone if available (SCO or BLE Headset only, never A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val btDevice = am.availableCommunicationDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) {
                    val prefSet = audioRecord?.setPreferredDevice(btDevice)
                    Log.d("AudioRecordManager", "setPreferredDevice: $prefSet (${btDevice.productName})")
                }
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                val buffer = ByteArray(3200) // 100 ms at 16000 Hz, 16-bit mono
                while (isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
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

    private fun calculateRms(pcm: ByteArray): Double {
        var sum = 0.0
        val sampleCount = pcm.size / 2
        for (i in 0 until sampleCount) {
            val sample = (pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)
            val shortVal = sample.toShort()
            sum += shortVal * shortVal
        }
        return if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
    }
}
