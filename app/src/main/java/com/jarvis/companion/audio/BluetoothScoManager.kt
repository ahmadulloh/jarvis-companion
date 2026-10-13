package com.jarvis.companion.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log

class BluetoothScoManager(
    private val context: Context,
    private val onScoStateChanged: (Boolean) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isScoStarted = false
    private var isReceiverRegistered = false

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        Log.d("BluetoothScoManager", "Bluetooth SCO Connected")
                        audioManager.isBluetoothScoOn = true
                        onScoStateChanged(true)
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        Log.d("BluetoothScoManager", "Bluetooth SCO Disconnected")
                        audioManager.isBluetoothScoOn = false
                        onScoStateChanged(false)
                    }
                }
            }
        }
    }

    fun start() {
        if (isScoStarted) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(scoReceiver, filter)
            isReceiverRegistered = true

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.startBluetoothSco()
            isScoStarted = true
            Log.d("BluetoothScoManager", "Starting Bluetooth SCO...")
        } catch (e: Exception) {
            Log.e("BluetoothScoManager", "Failed to start SCO: ${e.message}")
        }
    }

    fun stop() {
        if (!isScoStarted) return
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            isScoStarted = false
            if (isReceiverRegistered) {
                context.unregisterReceiver(scoReceiver)
                isReceiverRegistered = false
            }
            onScoStateChanged(false)
            Log.d("BluetoothScoManager", "Bluetooth SCO Stopped")
        } catch (e: Exception) {
            Log.e("BluetoothScoManager", "Failed to stop SCO: ${e.message}")
        }
    }
}
