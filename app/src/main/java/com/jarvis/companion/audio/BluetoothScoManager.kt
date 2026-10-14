package com.jarvis.companion.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class BluetoothScoManager(
    private val context: Context,
    private val onScoStateChanged: (Boolean) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isScoStarted = false
    private var isReceiverRegistered = false
    private var isDeviceCallbackRegistered = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isReconnecting = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d("BluetoothScoManager", "Audio devices added, re-checking headset")
            routeBluetoothAudio()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d("BluetoothScoManager", "Audio devices removed, re-checking headset")
            routeBluetoothAudio()
        }
    }

    private var lastReportedState: Boolean? = null
    private fun notifyStateChanged(state: Boolean) {
        if (lastReportedState == state) return
        lastReportedState = state
        onScoStateChanged(state)
    }

    fun isBluetoothAudioRouted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val comm = audioManager.communicationDevice
            if (comm != null && (comm.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || comm.type == AudioDeviceInfo.TYPE_BLE_HEADSET)) {
                return true
            }
        }
        return audioManager.isBluetoothScoOn
    }

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        Log.d("BluetoothScoManager", "Bluetooth SCO Connected")
                        audioManager.isBluetoothScoOn = true
                        isReconnecting = false
                        notifyStateChanged(true)
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        Log.d("BluetoothScoManager", "Bluetooth SCO Disconnected")
                        audioManager.isBluetoothScoOn = false
                        if (!isBluetoothAudioRouted()) {
                            notifyStateChanged(false)
                            scheduleScoReconnect(1500L)
                        }
                    }
                }
            }
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isScoStarted) {
                if (isBluetoothHeadsetConnected()) {
                    if (!isBluetoothAudioRouted() && !isReconnecting) {
                        Log.d("BluetoothScoManager", "Watchdog: Audio route lost but headset connected, reconnecting...")
                        routeBluetoothAudio()
                    }
                }
                handler.postDelayed(this, 5000L)
            }
        }
    }

    private fun scheduleScoReconnect(delayMs: Long) {
        if (!isScoStarted) return
        handler.postDelayed({
            if (isScoStarted && isBluetoothHeadsetConnected() && !isBluetoothAudioRouted()) {
                Log.d("BluetoothScoManager", "Auto-reconnecting Bluetooth audio...")
                routeBluetoothAudio()
            }
        }, delayMs)
    }

    fun start() {
        if (isScoStarted) return
        try {
            requestAudioFocus()
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.registerAudioDeviceCallback(deviceCallback, null)
                isDeviceCallbackRegistered = true
            }

            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            context.registerReceiver(scoReceiver, filter)
            isReceiverRegistered = true

            routeBluetoothAudio()
            isScoStarted = true
            handler.postDelayed(watchdogRunnable, 3000L)
            Log.d("BluetoothScoManager", "Bluetooth SCO manager started with watchdog")
        } catch (e: Exception) {
            Log.e("BluetoothScoManager", "Failed to start SCO: ${e.message}")
        }
    }

    fun isBluetoothHeadsetConnected(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                if (devices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }) return true
            }
            val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return inputDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun routeBluetoothAudio() {
        if (isBluetoothAudioRouted()) {
            Log.d("BluetoothScoManager", "Already routed to Bluetooth, skipping")
            return
        }
        try {
            isReconnecting = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val btDevice = devices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btDevice != null) {
                    val commDeviceSet = audioManager.setCommunicationDevice(btDevice)
                    Log.d("BluetoothScoManager", "setCommunicationDevice: $commDeviceSet (${btDevice.productName})")
                    if (commDeviceSet) {
                        isReconnecting = false
                        notifyStateChanged(true)
                        return
                    }
                }
            }

            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d("BluetoothScoManager", "startBluetoothSco requested (legacy)")
            } catch (e: Exception) {
                Log.w("BluetoothScoManager", "startBluetoothSco: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w("BluetoothScoManager", "routeBluetoothAudio error: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            Log.w("BluetoothScoManager", "requestAudioFocus error: ${e.message}")
        }
    }

    fun stop() {
        if (!isScoStarted) return
        try {
            handler.removeCallbacksAndMessages(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isDeviceCallbackRegistered) {
                audioManager.unregisterAudioDeviceCallback(deviceCallback)
                isDeviceCallbackRegistered = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                audioFocusRequest = null
            }

            if (isReceiverRegistered) {
                context.unregisterReceiver(scoReceiver)
                isReceiverRegistered = false
            }
            isScoStarted = false
            onScoStateChanged(false)
            Log.d("BluetoothScoManager", "Bluetooth SCO Stopped")
        } catch (e: Exception) {
            Log.e("BluetoothScoManager", "Failed to stop SCO: ${e.message}")
        }
    }
}
