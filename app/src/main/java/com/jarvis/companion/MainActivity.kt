package com.jarvis.companion

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.jarvis.companion.service.JarvisForegroundService
import com.jarvis.companion.ui.EventType
import com.jarvis.companion.ui.LiveEvent
import com.jarvis.companion.ui.LiveEventAdapter
import com.jarvis.companion.util.PreferencesHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesHelper

    // Header & Tabs
    private lateinit var tvStatusBadge: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var tabLiveDisplay: View
    private lateinit var tabWebDisplay: View
    private lateinit var tabSettings: View

    // Tab 1: Live Display
    private lateinit var tvLiveStatus: TextView
    private lateinit var tvLiveSubStatus: TextView
    private lateinit var btnQuickMic: MaterialButton
    private lateinit var rvLiveFeed: RecyclerView
    private lateinit var tvEmptyFeed: TextView
    private lateinit var etCommandInput: EditText
    private lateinit var btnSendCommand: MaterialButton
    private lateinit var eventAdapter: LiveEventAdapter

    // Tab 2: Web Display
    private lateinit var webViewDashboard: WebView
    private lateinit var tvWebUrl: TextView
    private lateinit var btnReloadWeb: MaterialButton
    private var webViewLoaded = false

    // Tab 3: Settings
    private lateinit var etServerAddress: EditText
    private lateinit var etAuthToken: EditText
    private lateinit var cbAutoStart: CheckBox
    private lateinit var cbContinuous: CheckBox
    private lateinit var btnToggleService: MaterialButton

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                JarvisForegroundService.BROADCAST_STATE_CHANGE -> {
                    val state = intent.getStringExtra(JarvisForegroundService.EXTRA_STATE) ?: return
                    updateUiState(state)
                }
                JarvisForegroundService.BROADCAST_CHAT_LOG -> {
                    val speaker = intent.getStringExtra(JarvisForegroundService.EXTRA_SPEAKER) ?: ""
                    val text = intent.getStringExtra(JarvisForegroundService.EXTRA_TEXT) ?: ""
                    if (text.isNotBlank()) {
                        val type = if (speaker.equals("user", ignoreCase = true)) EventType.USER else EventType.JARVIS
                        val title = if (type == EventType.USER) "[Siz] 👤" else "[JARVIS] 🤖"
                        addFeedEvent(LiveEvent(type, title, text, currentTime()))
                    }
                }
                JarvisForegroundService.BROADCAST_ACTION_LOG -> {
                    val name = intent.getStringExtra(JarvisForegroundService.EXTRA_ACTION_NAME) ?: ""
                    val result = intent.getStringExtra(JarvisForegroundService.EXTRA_ACTION_RESULT) ?: ""
                    if (name.isNotBlank()) {
                        addFeedEvent(LiveEvent(EventType.ACTION, name, result, currentTime()))
                    }
                }
            }
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

        initViews()
        setupTabs()
        setupLiveFeed()
        setupWebDashboard()
        setupSettings()

        if (prefs.isServiceRunning) {
            updateUiState("RUNNING")
        } else {
            updateUiState("STOPPED")
        }
    }

    private fun initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tabLayout = findViewById(R.id.tabLayout)
        tabLiveDisplay = findViewById(R.id.tabLiveDisplay)
        tabWebDisplay = findViewById(R.id.tabWebDisplay)
        tabSettings = findViewById(R.id.tabSettings)

        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        tvLiveSubStatus = findViewById(R.id.tvLiveSubStatus)
        btnQuickMic = findViewById(R.id.btnQuickMic)
        rvLiveFeed = findViewById(R.id.rvLiveFeed)
        tvEmptyFeed = findViewById(R.id.tvEmptyFeed)
        etCommandInput = findViewById(R.id.etCommandInput)
        btnSendCommand = findViewById(R.id.btnSendCommand)

        webViewDashboard = findViewById(R.id.webViewDashboard)
        tvWebUrl = findViewById(R.id.tvWebUrl)
        btnReloadWeb = findViewById(R.id.btnReloadWeb)

        etServerAddress = findViewById(R.id.etServerAddress)
        etAuthToken = findViewById(R.id.etAuthToken)
        cbAutoStart = findViewById(R.id.cbAutoStart)
        cbContinuous = findViewById(R.id.cbContinuous)
        btnToggleService = findViewById(R.id.btnToggleService)
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        tabLiveDisplay.visibility = View.VISIBLE
                        tabWebDisplay.visibility = View.GONE
                        tabSettings.visibility = View.GONE
                    }
                    1 -> {
                        tabLiveDisplay.visibility = View.GONE
                        tabWebDisplay.visibility = View.VISIBLE
                        tabSettings.visibility = View.GONE
                        loadWebDashboardIfNeeded()
                    }
                    2 -> {
                        tabLiveDisplay.visibility = View.GONE
                        tabWebDisplay.visibility = View.GONE
                        tabSettings.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupLiveFeed() {
        eventAdapter = LiveEventAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvLiveFeed.layoutManager = layoutManager
        rvLiveFeed.adapter = eventAdapter

        addFeedEvent(LiveEvent(
            EventType.SYSTEM,
            "TIZIM",
            "JARVIS Ovozli Displeyi tayyor. Bluetooth quloqchin orqali gapiring yoki pastdan buyruq yozing.",
            currentTime()
        ))

        btnQuickMic.setOnClickListener {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_TOGGLE_MIC
            }
            startService(intent)
        }

        btnSendCommand.setOnClickListener {
            val text = etCommandInput.text.toString().trim()
            if (text.isNotEmpty()) {
                val intent = Intent(this, JarvisForegroundService::class.java).apply {
                    action = JarvisForegroundService.ACTION_SEND_TEXT
                    putExtra(JarvisForegroundService.EXTRA_COMMAND_TEXT, text)
                }
                startService(intent)
                etCommandInput.setText("")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebDashboard() {
        val settings: WebSettings = webViewDashboard.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webViewDashboard.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Injected auth token into webView session
                val token = prefs.authToken
                val js = "try { sessionStorage.setItem('jarvis_token', '$token'); } catch(e){}"
                webViewDashboard.evaluateJavascript(js, null)
            }
        }
        webViewDashboard.webChromeClient = WebChromeClient()

        btnReloadWeb.setOnClickListener {
            webViewLoaded = false
            loadWebDashboardIfNeeded()
        }
    }

    private fun loadWebDashboardIfNeeded() {
        val server = prefs.serverAddress.trim()
        val cleanHost = server
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")

        val targetUrl = "http://$cleanHost/"
        tvWebUrl.text = targetUrl

        if (!webViewLoaded) {
            webViewDashboard.loadUrl(targetUrl)
            webViewLoaded = true
        }
    }

    private fun setupSettings() {
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
    }

    private fun saveSettings() {
        prefs.serverAddress = etServerAddress.text.toString().trim()
        prefs.authToken = etAuthToken.text.toString().trim()
        prefs.autoStartOnBoot = cbAutoStart.isChecked
        prefs.continuousMode = cbContinuous.isChecked
    }

    private fun addFeedEvent(event: LiveEvent) {
        tvEmptyFeed.visibility = View.GONE
        eventAdapter.addEvent(event)
        rvLiveFeed.scrollToPosition(eventAdapter.itemCount - 1)
    }

    private fun currentTime(): String = timeFormat.format(Date())

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(JarvisForegroundService.BROADCAST_STATE_CHANGE)
            addAction(JarvisForegroundService.BROADCAST_CHAT_LOG)
            addAction(JarvisForegroundService.BROADCAST_ACTION_LOG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}
        saveSettings()
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
        when {
            state.startsWith("CONNECTED") -> {
                tvStatusBadge.text = "ONLINE"
                tvStatusBadge.setTextColor(Color.parseColor("#00E676"))
                tvLiveStatus.text = "🟢 Aloqa o‘rnatildi"
                tvLiveSubStatus.text = "Quloqchin mikrofoniga gapiring yoki matn yozing"
                btnToggleService.text = "XIZMATNI TO‘XTATISH"
                btnToggleService.setBackgroundColor(Color.parseColor("#FF5252"))
            }
            state.startsWith("DISCONNECTED") -> {
                tvStatusBadge.text = "QAYTA ULANMOQDA"
                tvStatusBadge.setTextColor(Color.parseColor("#FFB300"))
                tvLiveStatus.text = "🟠 Qayta ulanmoqda..."
                tvLiveSubStatus.text = "Kompyuter (Tailscale) bilan aloqa kutilmoqda..."
            }
            state.startsWith("SPEAKING") -> {
                tvStatusBadge.text = "GAPIRMOQDA"
                tvStatusBadge.setTextColor(Color.parseColor("#00E5FF"))
                tvLiveStatus.text = "🔊 JARVIS Gapirmoqda"
                tvLiveSubStatus.text = "Javob quloqchingizda yangramoqda..."
            }
            state.startsWith("LISTENING") -> {
                tvStatusBadge.text = "TINGLAMOQDA"
                tvStatusBadge.setTextColor(Color.parseColor("#00E676"))
                tvLiveStatus.text = "🎤 Tinglamoqda"
                tvLiveSubStatus.text = "Gapiring: «Jarvis...»"
            }
            state.startsWith("THINKING") -> {
                tvStatusBadge.text = "O‘YLAMOQDA"
                tvStatusBadge.setTextColor(Color.parseColor("#00E5FF"))
                tvLiveStatus.text = "🔵 Amal / Fikr bajarilmoqda"
                tvLiveSubStatus.text = state
            }
            state.startsWith("RUNNING") -> {
                tvStatusBadge.text = "FAOL"
                tvStatusBadge.setTextColor(Color.parseColor("#00E5FF"))
                tvLiveStatus.text = "🔵 JARVIS Fon Xizmati Faol"
                tvLiveSubStatus.text = "Kompyuterga ulanmoqda..."
                btnToggleService.text = "XIZMATNI TO‘XTATISH"
                btnToggleService.setBackgroundColor(Color.parseColor("#FF5252"))
            }
            state.startsWith("STOPPED") -> {
                tvStatusBadge.text = "OFFLINE"
                tvStatusBadge.setTextColor(Color.parseColor("#FF5252"))
                tvLiveStatus.text = "To‘xtatilgan"
                tvLiveSubStatus.text = "Xizmat o‘chirilgan"
                btnToggleService.text = "XIZMATNI ISHGA TUSHIRISH"
                btnToggleService.setBackgroundColor(Color.parseColor("#00E5FF"))
            }
        }
    }
}
