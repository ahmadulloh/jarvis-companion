package com.jarvis.companion.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.sqrt

class AudioRecordManager(
    private val sampleRate: Int = 16000,
    private val onAudioChunk: (ByteArray) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // VAD (Voice Activity Detection) threshold: silence RMS is usually < 400
    var vadEnabled: Boolean = false
    var vadThreshold: Double = 450.0

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate / 10 * 2) // ~100ms slices

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecordManager", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                val buffer = ByteArray(3200) // 100 ms at 16000 Hz, 16-bit mono
                while (isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        if (vadEnabled) {
                            val rms = calculateRms(chunk)
                            if (rms >= vadThreshold) {
                                onAudioChunk(chunk)
                            }
                        } else {
                            onAudioChunk(chunk)
                        }
                    }
                }
            }.apply {
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
