package com.jarvis.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jarvis.companion.audio.AudioRoute
import com.jarvis.companion.service.JarvisForegroundService
import com.jarvis.companion.service.JarvisNotificationListener
import com.jarvis.companion.ui.EventType
import com.jarvis.companion.ui.JarvisOrbView
import com.jarvis.companion.ui.LiveEvent
import com.jarvis.companion.ui.LiveEventAdapter
import com.jarvis.companion.ui.OrbState
import com.jarvis.companion.util.PreferencesHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesHelper

    // Root & Theme
    private lateinit var rootContainer: View
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var btnThemeToggle: ImageButton
    private lateinit var btnOpenSettings: ImageButton
    private var isNightMode = true

    // 3-Way Device Route
    private lateinit var layoutSegmentBar: View
    private lateinit var btnRoutePc: TextView
    private lateinit var btnRoutePhone: TextView
    private lateinit var btnRouteEarbuds: TextView
    private var activeRoute: AudioRoute = AudioRoute.EARBUDS

    // AI Orb & Status
    private lateinit var jarvisOrb: JarvisOrbView
    private lateinit var tvOrbStatus: TextView
    private lateinit var tvOrbSubtitle: TextView

    // Quick Action Chips
    private lateinit var chipCallMom: TextView
    private lateinit var chipReadTelegram: TextView
    private lateinit var chipSetReminder: TextView
    private lateinit var chipInterrupt: TextView

    // Quran Lesson Card
    private lateinit var cardQuronLesson: View
    private lateinit var tvQuronTitle: TextView
    private lateinit var tvQuronBadge: TextView
    private lateinit var tvQuronInfo: TextView
    private lateinit var btnStartQuron: MaterialButton
    private lateinit var btnOpenQuronWeb: MaterialButton
    private var quronWebUrl: String = ""

    // HUD & Input
    private lateinit var rvLiveFeed: RecyclerView
    private lateinit var etCommandInput: EditText
    private lateinit var btnSendCommand: ImageButton
    private lateinit var eventAdapter: LiveEventAdapter
    private lateinit var webViewDashboard: WebView

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                JarvisForegroundService.BROADCAST_STATE_CHANGE -> {
                    val state = intent.getStringExtra(JarvisForegroundService.EXTRA_STATE) ?: return
                    updateOrbState(state)
                }
                JarvisForegroundService.BROADCAST_CHAT_LOG -> {
                    val speaker = intent.getStringExtra(JarvisForegroundService.EXTRA_SPEAKER) ?: ""
                    val text = intent.getStringExtra(JarvisForegroundService.EXTRA_TEXT) ?: ""
                    if (text.isNotBlank()) {
                        val isUser = speaker.equals("user", ignoreCase = true)
                        val title = if (isUser) "Siz" else "JARVIS"
                        val type = if (isUser) EventType.USER else EventType.JARVIS
                        addFeedEvent(LiveEvent(type, title, text, currentTime()))
                        tvOrbSubtitle.text = text
                    }
                }
                JarvisForegroundService.BROADCAST_ACTION_LOG -> {
                    val name = intent.getStringExtra(JarvisForegroundService.EXTRA_ACTION_NAME) ?: ""
                    val result = intent.getStringExtra(JarvisForegroundService.EXTRA_ACTION_RESULT) ?: ""
                    if (name.isNotBlank()) {
                        addFeedEvent(LiveEvent(EventType.ACTION, name, result, currentTime()))
                    }
                }
                JarvisForegroundService.BROADCAST_ROUTE_CHANGE -> {
                    val rName = intent.getStringExtra(JarvisForegroundService.EXTRA_ROUTE_NAME) ?: return
                    try {
                        val r = AudioRoute.valueOf(rName)
                        activeRoute = r
                        updateRouteSegmentUi(r)
                    } catch (_: Exception) {}
                }
                JarvisForegroundService.BROADCAST_QURON_UPDATE -> {
                    val url = intent.getStringExtra(JarvisForegroundService.EXTRA_QURON_URL) ?: ""
                    val sura = intent.getStringExtra(JarvisForegroundService.EXTRA_QURON_SURA) ?: ""
                    val oyat = intent.getIntExtra(JarvisForegroundService.EXTRA_QURON_OYAT, 0)
                    if (url.isNotEmpty()) {
                        quronWebUrl = url
                        openQuronLesson(url)
                    }
                    tvQuronBadge.text = "FAOL"
                    tvQuronBadge.setTextColor(Color.parseColor("#10B981"))
                    if (sura.isNotEmpty()) {
                        tvQuronInfo.text = "$sura surasi, $oyat-oyat tilovati va mashqi davom etmoqda."
                    }
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startJarvisService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesHelper(this)

        initViews()
        initAutoTheme()
        setupRouteSelector()
        setupOrbInteractions()
        setupQuickActions()
        setupQuronCard()
        setupFeedAndInput()
        setupSettingsDialog()

        checkPermissionsAndStart()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        layoutSegmentBar = findViewById(R.id.layoutSegmentBar)
        btnRoutePc = findViewById(R.id.btnRoutePc)
        btnRoutePhone = findViewById(R.id.btnRoutePhone)
        btnRouteEarbuds = findViewById(R.id.btnRouteEarbuds)

        jarvisOrb = findViewById(R.id.jarvisOrb)
        tvOrbStatus = findViewById(R.id.tvOrbStatus)
        tvOrbSubtitle = findViewById(R.id.tvOrbSubtitle)

        chipCallMom = findViewById(R.id.chipCallMom)
        chipReadTelegram = findViewById(R.id.chipReadTelegram)
        chipSetReminder = findViewById(R.id.chipSetReminder)
        chipInterrupt = findViewById(R.id.chipInterrupt)

        cardQuronLesson = findViewById(R.id.cardQuronLesson)
        tvQuronTitle = findViewById(R.id.tvQuronTitle)
        tvQuronBadge = findViewById(R.id.tvQuronBadge)
        tvQuronInfo = findViewById(R.id.tvQuronInfo)
        btnStartQuron = findViewById(R.id.btnStartQuron)
        btnOpenQuronWeb = findViewById(R.id.btnOpenQuronWeb)

        rvLiveFeed = findViewById(R.id.rvLiveFeed)
        etCommandInput = findViewById(R.id.etCommandInput)
        btnSendCommand = findViewById(R.id.btnSendCommand)
        webViewDashboard = findViewById(R.id.webViewDashboard)
    }

    /**
     * Automatic Day / Night Theme detection based on hour (06:00 - 19:00 = Day, else Night).
     */
    private fun initAutoTheme() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        isNightMode = hour !in 6..18
        applyTheme(isNightMode)

        btnThemeToggle.setOnClickListener {
            isNightMode = !isNightMode
            applyTheme(isNightMode)
        }
    }

    private fun applyTheme(night: Boolean) {
        jarvisOrb.isNightMode = night
        if (night) {
            rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.night_bg))
            tvHeaderTitle.setTextColor(ContextCompat.getColor(this, R.color.night_text_primary))
            btnThemeToggle.setImageResource(R.drawable.ic_moon)
            tvOrbStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_cyan))
            tvOrbSubtitle.setTextColor(ContextCompat.getColor(this, R.color.night_text_secondary))
        } else {
            rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.day_bg))
            tvHeaderTitle.setTextColor(ContextCompat.getColor(this, R.color.day_text_primary))
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
            tvOrbStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_blue))
            tvOrbSubtitle.setTextColor(ContextCompat.getColor(this, R.color.day_text_secondary))
        }
        updateRouteSegmentUi(activeRoute)
    }

    /**
     * 3-Way Device Route Control (Kompyuter, Telefon, Quloqchin).
     */
    private fun setupRouteSelector() {
        activeRoute = try {
            AudioRoute.valueOf(prefs.audioRoute)
        } catch (_: Exception) {
            AudioRoute.EARBUDS
        }
        updateRouteSegmentUi(activeRoute)

        btnRoutePc.setOnClickListener {
            switchAudioRoute(AudioRoute.PC)
        }
        btnRoutePhone.setOnClickListener {
            switchAudioRoute(AudioRoute.PHONE)
        }
        btnRouteEarbuds.setOnClickListener {
            switchAudioRoute(AudioRoute.EARBUDS)
        }
    }

    private fun switchAudioRoute(route: AudioRoute) {
        activeRoute = route
        prefs.audioRoute = route.name
        updateRouteSegmentUi(route)

        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_SET_ROUTE
            putExtra(JarvisForegroundService.EXTRA_ROUTE, route.name)
        }
        startService(intent)

        val label = when (route) {
            AudioRoute.PC -> "💻 Kompyuterga o'tkazildi"
            AudioRoute.PHONE -> "📱 Telefondagi ovoz/mikrofon yoqildi"
            AudioRoute.EARBUDS -> "🎧 Bluetooth quloqchin faol"
        }
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun updateRouteSegmentUi(route: AudioRoute) {
        val activeTextCol = if (isNightMode) ContextCompat.getColor(this, R.color.night_pill_active_text) else Color.WHITE
        val inactiveTextCol = if (isNightMode) ContextCompat.getColor(this, R.color.night_text_secondary) else ContextCompat.getColor(this, R.color.day_text_secondary)

        btnRoutePc.background = null
        btnRoutePhone.background = null
        btnRouteEarbuds.background = null

        btnRoutePc.setTextColor(inactiveTextCol)
        btnRoutePhone.setTextColor(inactiveTextCol)
        btnRouteEarbuds.setTextColor(inactiveTextCol)

        when (route) {
            AudioRoute.PC -> {
                btnRoutePc.setBackgroundResource(R.drawable.bg_ios_pill_active)
                btnRoutePc.setTextColor(activeTextCol)
            }
            AudioRoute.PHONE -> {
                btnRoutePhone.setBackgroundResource(R.drawable.bg_ios_pill_active)
                btnRoutePhone.setTextColor(activeTextCol)
            }
            AudioRoute.EARBUDS -> {
                btnRouteEarbuds.setBackgroundResource(R.drawable.bg_ios_pill_active)
                btnRouteEarbuds.setTextColor(activeTextCol)
            }
        }
    }

    /**
     * AI Glowing Orb Interactions.
     */
    private fun setupOrbInteractions() {
        jarvisOrb.setOnClickListener {
            // Tapping orb wakes Jarvis or interrupts speaking immediately
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_WAKE
            }
            startService(intent)
        }
    }

    private fun updateOrbState(state: String) {
        when (state) {
            "SPEAKING" -> {
                jarvisOrb.state = OrbState.SPEAKING
                tvOrbStatus.text = "JARVIS GAPIRMOQDA"
                tvStatusBadge.text = "● GAPIRMOQDA"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_cyan))
            }
            "LISTENING" -> {
                jarvisOrb.state = OrbState.LISTENING
                tvOrbStatus.text = "ESHITMOQDA..."
                tvStatusBadge.text = "● ESHITMOQDA"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_accent))
            }
            "CONNECTED" -> {
                jarvisOrb.state = OrbState.IDLE
                tvOrbStatus.text = "JARVIS TAYYOR"
                tvStatusBadge.text = "● ON-LINE"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_accent))
            }
            "DISCONNECTED" -> {
                jarvisOrb.state = OrbState.IDLE
                tvOrbStatus.text = "OFFLINE"
                tvStatusBadge.text = "● OFFLINE"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_red))
            }
        }
    }

    /**
     * Quick Action Shortcuts (Calls, Telegram, Reminders).
     */
    private fun setupQuickActions() {
        chipCallMom.setOnClickListener {
            sendJarvisCommand("Onamga telefon qil")
        }
        chipReadTelegram.setOnClickListener {
            if (!JarvisNotificationListener.isNotificationAccessGranted(this)) {
                promptNotificationAccess()
            } else {
                sendJarvisCommand("Telegramga kirib shaxsiyga kelgan xabarlarni aytib ber")
            }
        }
        chipSetReminder.setOnClickListener {
            sendJarvisCommand("Eslatma qo'y")
        }
        chipInterrupt.setOnClickListener {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_EARBUD_TAP
            }
            startService(intent)
        }
    }

    private fun promptNotificationAccess() {
        Toast.makeText(this, "Telegram xabarlarini o'qish uchun bildirishnomaga ruxsat bering", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {}
    }

    /**
     * Quran Lesson Card setup.
     */
    private fun setupQuronCard() {
        btnStartQuron.setOnClickListener {
            sendJarvisCommand("Qur'on darsini boshlaymiz")
            tvQuronBadge.text = "BOSHLANMOQDA"
            if (quronWebUrl.isNotEmpty()) {
                openQuronLesson(quronWebUrl)
            }
        }
        btnOpenQuronWeb.setOnClickListener {
            if (quronWebUrl.isNotEmpty()) {
                openQuronLesson(quronWebUrl)
            } else {
                val server = prefs.serverAddress.trim()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .removePrefix("ws://")
                    .removePrefix("wss://")
                openQuronLesson("http://$server/")
            }
        }
    }

    private fun openQuronLesson(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Xatolik brauzerni ochishda: ${e.message}")
            openWebUrl(url)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openWebUrl(url: String) {
        webViewDashboard.visibility = View.VISIBLE
        val settings = webViewDashboard.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewDashboard.webViewClient = WebViewClient()
        webViewDashboard.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        webViewDashboard.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webViewDashboard.visibility == View.VISIBLE) {
            if (webViewDashboard.canGoBack()) {
                webViewDashboard.goBack()
            } else {
                webViewDashboard.visibility = View.GONE
            }
            return
        }
        super.onBackPressed()
    }

    private fun setupFeedAndInput() {
        eventAdapter = LiveEventAdapter()
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvLiveFeed.layoutManager = lm
        rvLiveFeed.adapter = eventAdapter

        addFeedEvent(LiveEvent(
            EventType.SYSTEM,
            "TIZIM",
            "JARVIS Mark 57 Companion tayyor. 3 ta qurilmadan birini tanlang.",
            currentTime()
        ))

        btnSendCommand.setOnClickListener {
            val txt = etCommandInput.text.toString().trim()
            if (txt.isNotEmpty()) {
                sendJarvisCommand(txt)
                etCommandInput.setText("")
            }
        }
    }

    private fun sendJarvisCommand(cmd: String) {
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_SEND_TEXT
            putExtra(JarvisForegroundService.EXTRA_COMMAND_TEXT, cmd)
        }
        startService(intent)
        addFeedEvent(LiveEvent(EventType.USER, "Siz", cmd, currentTime()))
    }

    private fun setupSettingsDialog() {
        btnOpenSettings.setOnClickListener {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_settings)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val etServer = dialog.findViewById<EditText>(R.id.etDialogServer)
            val etToken = dialog.findViewById<EditText>(R.id.etDialogToken)
            val cbBoot = dialog.findViewById<CheckBox>(R.id.cbDialogAutoStart)
            val btnNotif = dialog.findViewById<MaterialButton>(R.id.btnGrantNotificationAccess)
            val btnSave = dialog.findViewById<MaterialButton>(R.id.btnDialogSave)

            etServer.setText(prefs.serverAddress)
            etToken.setText(prefs.authToken)
            cbBoot.isChecked = prefs.autoStartOnBoot

            btnNotif.setOnClickListener {
                promptNotificationAccess()
            }

            btnSave.setOnClickListener {
                prefs.serverAddress = etServer.text.toString().trim()
                prefs.authToken = etToken.text.toString().trim()
                prefs.autoStartOnBoot = cbBoot.isChecked
                dialog.dismiss()
                startJarvisService()
                Toast.makeText(this, "Sozlamalar saqlandi!", Toast.LENGTH_SHORT).show()
            }

            dialog.show()
        }
    }

    private fun addFeedEvent(event: LiveEvent) {
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
            addAction(JarvisForegroundService.BROADCAST_ROUTE_CHANGE)
            addAction(JarvisForegroundService.BROADCAST_QURON_UPDATE)
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
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS
        )
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
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
