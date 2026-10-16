package com.jarvis.companion.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class AudioTrackPlayer(
    private val sampleRate: Int = 24000
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    fun start() {
        if (isPlaying) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate * 2) // 1 second buffer

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        try {
            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
                isPlaying = true
                Log.d("AudioTrackPlayer", "AudioTrack started at $sampleRate Hz")
            } else {
                Log.e("AudioTrackPlayer", "AudioTrack uninitialized: state=${audioTrack?.state}")
                isPlaying = false
            }
        } catch (e: Throwable) {
            Log.e("AudioTrackPlayer", "Error creating AudioTrack: ${e.message}")
            isPlaying = false
        }
    }

    var currentRoute: AudioRoute = AudioRoute.EARBUDS
    private var discardUntil = 0L

    fun write(pcmChunk: ByteArray) {
        if (currentRoute == AudioRoute.PC) {
            return
        }
        if (System.currentTimeMillis() < discardUntil) {
            return
        }
        try {
            if (!isPlaying || audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                start()
            }
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.write(pcmChunk, 0, pcmChunk.size)
            }
        } catch (e: Throwable) {
            Log.e("AudioTrackPlayer", "Error writing audio: ${e.message}")
        }
    }

    fun flush() {
        discardUntil = System.currentTimeMillis() + 800L
        try {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.play()
            }
            Log.d("AudioTrackPlayer", "AudioTrack flushed (interrupted)")
        } catch (e: Throwable) {
            Log.e("AudioTrackPlayer", "Error flushing AudioTrack: ${e.message}")
        }
    }

    fun stop() {
        isPlaying = false
        try {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.stop()
                audioTrack?.release()
            }
            audioTrack = null
            Log.d("AudioTrackPlayer", "AudioTrack stopped")
        } catch (e: Throwable) {
            Log.e("AudioTrackPlayer", "Error stopping AudioTrack: ${e.message}")
        }
    }
}
