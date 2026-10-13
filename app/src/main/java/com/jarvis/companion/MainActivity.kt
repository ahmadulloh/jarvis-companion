package com.jarvis.companion

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.jarvis.companion.service.JarvisForegroundService
import com.jarvis.companion.util.PreferencesHelper

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesHelper
    private lateinit var etServerAddress: EditText
    private lateinit var etAuthToken: EditText
    private lateinit var cbAutoStart: CheckBox
    private lateinit var cbContinuous: CheckBox
    private lateinit var btnToggleService: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvSubStatus: TextView

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(JarvisForegroundService.EXTRA_STATE) ?: return
            updateUiState(state)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            startJarvisService()
        } else {
            Toast.makeText(this, "Mikrofon ruxsati zarur!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesHelper(this)

        etServerAddress = findViewById(R.id.etServerAddress)
        etAuthToken = findViewById(R.id.etAuthToken)
        cbAutoStart = findViewById(R.id.cbAutoStart)
        cbContinuous = findViewById(R.id.cbContinuous)
        btnToggleService = findViewById(R.id.btnToggleService)
        tvStatus = findViewById(R.id.tvStatus)
        tvSubStatus = findViewById(R.id.tvSubStatus)

        // Load saved values
        etServerAddress.setText(prefs.serverAddress)
        etAuthToken.setText(prefs.authToken)
        cbAutoStart.isChecked = prefs.autoStartOnBoot
        cbContinuous.isChecked = prefs.continuousMode

        btnToggleService.setOnClickListener {
            saveSettings()
            if (prefs.isServiceRunning) {
                stopJarvisService()
            } else {
                checkPermissionsAndStart()
            }
        }

        if (prefs.isServiceRunning) {
            updateUiState("RUNNING")
        } else {
            updateUiState("STOPPED")
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(JarvisForegroundService.BROADCAST_STATE_CHANGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
        if (prefs.isServiceRunning) {
            updateUiState("RUNNING")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
        saveSettings()
    }

    private fun saveSettings() {
        prefs.serverAddress = etServerAddress.text.toString().trim()
        prefs.authToken = etAuthToken.text.toString().trim()
        prefs.autoStartOnBoot = cbAutoStart.isChecked
        prefs.continuousMode = cbContinuous.isChecked
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            startJarvisService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startJarvisService() {
        saveSettings()
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUiState("RUNNING")
        Toast.makeText(this, "JARVIS xizmati ishga tushdi!", Toast.LENGTH_SHORT).show()
    }

    private fun stopJarvisService() {
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_STOP
        }
        startService(intent)
        updateUiState("STOPPED")
        Toast.makeText(this, "JARVIS xizmati to‘xtatildi", Toast.LENGTH_SHORT).show()
    }

    private fun updateUiState(state: String) {
        when (state) {
            "CONNECTED" -> {
                tvStatus.text = "🟢 Kompyuterga Ulangan"
                tvStatus.setTextColor(getColor(R.color.jarvis_cyan))
                tvSubStatus.text = "Quloqchin orqali gaplashishga tayyor"
                btnToggleService.text = "XIZMATNI TO‘XTATISH"
                btnToggleService.setBackgroundColor(getColor(R.color.jarvis_red))
            }
            "DISCONNECTED" -> {
                tvStatus.text = "🟠 Kompyuterga ulanmoqda..."
                tvStatus.setTextColor(getColor(R.color.jarvis_text_dim))
                tvSubStatus.text = "Qayta ulanishga urinmoqda..."
                btnToggleService.text = "XIZMATNI TO‘XTATISH"
                btnToggleService.setBackgroundColor(getColor(R.color.jarvis_red))
            }
            "MIC_ON" -> {
                tvStatus.text = "🎤 Mikrofon Ochiq (Tinglamoqda)"
                tvStatus.setTextColor(getColor(R.color.jarvis_accent))
                tvSubStatus.text = "Ovozingiz kompyuterga uzatilmoqda..."
            }
            "MIC_OFF" -> {
                tvStatus.text = "🟢 Aloqada (Kutish rejimida)"
                tvStatus.setTextColor(getColor(R.color.jarvis_cyan))
                tvSubStatus.text = "Gapirish uchun quloqchin tugmasini bosing"
            }
            "SPEAKING" -> {
                tvStatus.text = "🔊 JARVIS Gapirmoqda"
                tvStatus.setTextColor(getColor(R.color.jarvis_cyan))
                tvSubStatus.text = "Javob quloqchingizda yangramoqda..."
            }
            "RUNNING" -> {
                tvStatus.text = "🔵 Fonda Ishlamoqda"
                tvStatus.setTextColor(getColor(R.color.jarvis_cyan))
                tvSubStatus.text = "Ilovani yopishingiz mumkin, xizmat faol"
                btnToggleService.text = "XIZMATNI TO‘XTATISH"
                btnToggleService.setBackgroundColor(getColor(R.color.jarvis_red))
            }
            "STOPPED" -> {
                tvStatus.text = "To‘xtatilgan"
                tvStatus.setTextColor(getColor(R.color.jarvis_text))
                tvSubStatus.text = "Xizmat o‘chirilgan"
                btnToggleService.text = "XIZMATNI ISHGA TUSHIRISH"
                btnToggleService.setBackgroundColor(getColor(R.color.jarvis_cyan))
            }
        }
    }
}
