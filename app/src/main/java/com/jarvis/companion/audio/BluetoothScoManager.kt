package com.jarvis.companion.audio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

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
            try {
                Log.d("BluetoothScoManager", "Audio devices added, re-checking headset")
                if (currentRoute == AudioRoute.EARBUDS) {
                    if (isBluetoothHeadsetConnected()) {
                        routeBluetoothAudio()
                    } else if (isWiredHeadsetConnected()) {
                        Log.d("BluetoothScoManager", "Wired headset connected")
                        notifyStateChanged(true)
                    }
                }
            } catch (e: Throwable) {
                Log.w("BluetoothScoManager", "onAudioDevicesAdded error: ${e.message}")
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            try {
                Log.d("BluetoothScoManager", "Audio devices removed, re-checking headset")
                if (currentRoute == AudioRoute.EARBUDS) {
                    if (isBluetoothHeadsetConnected()) {
                        routeBluetoothAudio()
                    } else if (isWiredHeadsetConnected()) {
                        notifyStateChanged(true)
                    } else {
                        notifyStateChanged(false)
                    }
                }
            } catch (e: Throwable) {
                Log.w("BluetoothScoManager", "onAudioDevicesRemoved error: ${e.message}")
            }
        }
    }

    private var lastReportedState: Boolean? = null
    private fun notifyStateChanged(state: Boolean) {
        try {
            if (lastReportedState == state) return
            lastReportedState = state
            onScoStateChanged(state)
        } catch (e: Throwable) {
            Log.w("BluetoothScoManager", "notifyStateChanged error: ${e.message}")
        }
    }

    fun isBluetoothAudioRouted(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val comm = audioManager.communicationDevice
                if (comm != null && (comm.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || comm.type == AudioDeviceInfo.TYPE_BLE_HEADSET)) {
                    return true
                }
            }
            return audioManager.isBluetoothScoOn
        } catch (_: Throwable) {
            return false
        }
    }

    var currentRoute: AudioRoute = AudioRoute.EARBUDS

    fun setAudioRoute(route: AudioRoute) {
        currentRoute = route
        Log.d("BluetoothScoManager", "setAudioRoute -> $route")
        when (route) {
            AudioRoute.EARBUDS -> {
                try {
                    audioManager.isSpeakerphoneOn = false
                } catch (_: Throwable) {}
                if (isBluetoothHeadsetConnected()) {
                    routeBluetoothAudio()
                } else if (isWiredHeadsetConnected()) {
                    notifyStateChanged(true)
                }
            }
            AudioRoute.PHONE -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val devices = audioManager.availableCommunicationDevices
                        val speaker = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        if (speaker != null) {
                            audioManager.setCommunicationDevice(speaker)
                        } else {
                            audioManager.clearCommunicationDevice()
                        }
                    }
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                } catch (e: Throwable) {
                    Log.w("BluetoothScoManager", "setAudioRoute PHONE error: ${e.message}")
                }
                notifyStateChanged(false)
            }
            AudioRoute.PC -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        audioManager.clearCommunicationDevice()
                    }
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                } catch (e: Throwable) {
                    Log.w("BluetoothScoManager", "setAudioRoute PC error: ${e.message}")
                }
                notifyStateChanged(false)
            }
        }
    }

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
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
                            if (!isBluetoothAudioRouted() && !isWiredHeadsetConnected()) {
                                notifyStateChanged(false)
                                if (currentRoute == AudioRoute.EARBUDS && isBluetoothHeadsetConnected()) {
                                    scheduleScoReconnect(2000L)
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w("BluetoothScoManager", "scoReceiver error: ${e.message}")
            }
        }
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            try {
                if (isScoStarted && currentRoute == AudioRoute.EARBUDS) {
                    if (isBluetoothHeadsetConnected()) {
                        if (!isBluetoothAudioRouted() && !isReconnecting) {
                            Log.d("BluetoothScoManager", "Watchdog: BT route lost but headset connected, reconnecting...")
                            routeBluetoothAudio()
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w("BluetoothScoManager", "watchdogRunnable error: ${e.message}")
            }
            if (isScoStarted) {
                handler.postDelayed(this, 6000L)
            }
        }
    }

    private fun scheduleScoReconnect(delayMs: Long) {
        if (!isScoStarted || currentRoute != AudioRoute.EARBUDS) return
        handler.postDelayed({
            try {
                if (isScoStarted && currentRoute == AudioRoute.EARBUDS && isBluetoothHeadsetConnected() && !isBluetoothAudioRouted()) {
                    Log.d("BluetoothScoManager", "Auto-reconnecting Bluetooth audio...")
                    routeBluetoothAudio()
                }
            } catch (e: Throwable) {
                Log.w("BluetoothScoManager", "scheduleScoReconnect error: ${e.message}")
            }
        }, delayMs)
    }

    fun start() {
        if (isScoStarted) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    audioManager.registerAudioDeviceCallback(deviceCallback, null)
                    isDeviceCallbackRegistered = true
                } catch (e: Throwable) {
                    Log.w("BluetoothScoManager", "registerAudioDeviceCallback: ${e.message}")
                }
            }

            try {
                val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(scoReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(scoReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Throwable) {
                Log.w("BluetoothScoManager", "registerReceiver scoReceiver: ${e.message}")
            }

            if (currentRoute == AudioRoute.EARBUDS) {
                if (isBluetoothHeadsetConnected()) {
                    requestAudioFocus()
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    routeBluetoothAudio()
                } else if (isWiredHeadsetConnected()) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    notifyStateChanged(true)
                } else {
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
            } else {
                audioManager.mode = AudioManager.MODE_NORMAL
            }

            isScoStarted = true
            handler.postDelayed(watchdogRunnable, 3000L)
            Log.d("BluetoothScoManager", "Bluetooth SCO manager started with watchdog")
        } catch (e: Throwable) {
            Log.e("BluetoothScoManager", "Failed to start SCO: ${e.message}")
        }
    }

    fun isBluetoothHeadsetConnected(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
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
            @Suppress("DEPRECATION")
            return audioManager.isBluetoothScoAvailableOffCall || audioManager.isBluetoothA2dpOn
        } catch (_: Throwable) {
            return false
        }
    }

    fun isWiredHeadsetConnected(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
                return devices.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
            }
            @Suppress("DEPRECATION")
            return audioManager.isWiredHeadsetOn
        } catch (_: Throwable) {
            return false
        }
    }

    fun isAnyHeadsetConnected(): Boolean {
        return isBluetoothHeadsetConnected() || isWiredHeadsetConnected()
    }

    private fun routeBluetoothAudio() {
        try {
            if (isBluetoothAudioRouted()) {
                Log.d("BluetoothScoManager", "Already routed to Bluetooth, skipping")
                return
            }
            if (!isBluetoothHeadsetConnected()) {
                Log.d("BluetoothScoManager", "No Bluetooth headset connected, skipping SCO routing")
                return
            }
            isReconnecting = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val devices = audioManager.availableCommunicationDevices
                    val btDevice = devices.find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    if (btDevice != null) {
                        val commDeviceSet = audioManager.setCommunicationDevice(btDevice)
                        Log.d("BluetoothScoManager", "setCommunicationDevice: $commDeviceSet")
                        if (commDeviceSet) {
                            isReconnecting = false
                            notifyStateChanged(true)
                            return
                        }
                    }
                }
            }

            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d("BluetoothScoManager", "startBluetoothSco requested (legacy)")
            } catch (e: Throwable) {
                Log.w("BluetoothScoManager", "startBluetoothSco: ${e.message}")
            }
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
            Log.w("BluetoothScoManager", "requestAudioFocus error: ${e.message}")
        }
    }

    fun stop() {
        if (!isScoStarted) return
        try {
            handler.removeCallbacksAndMessages(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isDeviceCallbackRegistered) {
                try {
                    audioManager.unregisterAudioDeviceCallback(deviceCallback)
                } catch (_: Throwable) {}
                isDeviceCallbackRegistered = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice()
                } catch (_: Throwable) {}
            }
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            } catch (_: Throwable) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                try {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
                } catch (_: Throwable) {}
                audioFocusRequest = null
            }

            if (isReceiverRegistered) {
                try {
                    context.unregisterReceiver(scoReceiver)
                } catch (_: Throwable) {}
                isReceiverRegistered = false
            }
            isScoStarted = false
            notifyStateChanged(false)
            Log.d("BluetoothScoManager", "Bluetooth SCO Stopped")
        } catch (e: Throwable) {
            Log.e("BluetoothScoManager", "Failed to stop SCO: ${e.message}")
        }
    }
}
