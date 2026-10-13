package com.jarvis.companion.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Modern Android 12+ (API 31+) route for Bluetooth headset
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val btDevice = devices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                if (btDevice != null) {
                    val success = audioManager.setCommunicationDevice(btDevice)
                    Log.d("BluetoothScoManager", "setCommunicationDevice: $success (${btDevice.productName})")
                    onScoStateChanged(true)
                }
            }

            // Legacy fallback for Android 11 and lower
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(scoReceiver, filter)
            isReceiverRegistered = true

            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            isScoStarted = true
            Log.d("BluetoothScoManager", "Starting Bluetooth SCO...")
        } catch (e: Exception) {
            Log.e("BluetoothScoManager", "Failed to start SCO: ${e.message}")
        }
    }

    fun stop() {
        if (!isScoStarted) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
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
