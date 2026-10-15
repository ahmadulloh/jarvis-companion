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
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.content.res.ColorStateList
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jarvis.companion.audio.AudioRoute
import com.jarvis.companion.domain.DomainAdapter
import com.jarvis.companion.domain.JarvisDomain
import com.jarvis.companion.domain.JarvisDomainRegistry
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
    private lateinit var tvHeaderDomainBadge: TextView
    private lateinit var btnThemeToggle: ImageButton
    private lateinit var btnOpenSettings: ImageButton
    private lateinit var layoutProfileAvatar: View
    private lateinit var ivUserProfile: ImageView
    private var isNightMode = false // Default Day mode matching screenshot!

    // Active Device Route
    private var activeRoute: AudioRoute = AudioRoute.EARBUDS

    // Active Domain (31 Soha)
    private var currentDomain: JarvisDomain = JarvisDomainRegistry.ASOSIY

    // AI Orb & Status
    private lateinit var jarvisOrb: JarvisOrbView
    private lateinit var tvOrbStatus: TextView
    private lateinit var tvOrbSubtitle: TextView

    // Dynamic Domain Action Dock
    private lateinit var chipDomainAction1: TextView
    private lateinit var chipDomainAction2: TextView
    private lateinit var chipDomainAction3: TextView
    private lateinit var chipDomainAction4: TextView
    private lateinit var chipInterrupt: TextView

    // AI Kotib (Call Secretary) Card
    private lateinit var cardAiSecretary: View
    private lateinit var tvSecretaryTitle: TextView
    private lateinit var tvSecretaryToggle: TextView
    private lateinit var tvSecretarySubtitle: TextView
    private lateinit var chipReasonDars: TextView
    private lateinit var chipReasonMashina: TextView
    private lateinit var chipReasonBand: TextView
    private lateinit var chipReasonNamoz: TextView
    private lateinit var chipReasonCustom: TextView
    private lateinit var tvSecretaryPreview: TextView

    // Quran Lesson Card
    private lateinit var cardQuronLesson: View
    private lateinit var tvQuronTitle: TextView
    private lateinit var tvQuronInfo: TextView
    private lateinit var tvQuronLessonName: TextView
    private lateinit var pbQuronProgress: ProgressBar
    private lateinit var btnStartQuron: MaterialButton
    private lateinit var btnOpenQuronWeb: MaterialButton
    private lateinit var btnCloseQuronCard: TextView
    private var quronWebUrl: String = ""

    // HUD & Input
    private lateinit var cardLiveHud: View
    private lateinit var rvLiveFeed: RecyclerView
    private lateinit var etCommandInput: EditText
    private lateinit var btnSendCommand: ImageButton
    private lateinit var eventAdapter: LiveEventAdapter
    private lateinit var webViewDashboard: WebView
    private lateinit var containerWebView: View
    private lateinit var tvWebTitle: TextView
    private lateinit var btnOpenExternalBrowser: TextView
    private lateinit var btnCloseWebView: TextView

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
                        val type = if (speaker.equals("JARVIS", ignoreCase = true)) EventType.JARVIS else EventType.USER
                        addFeedEvent(LiveEvent(type, speaker, text, currentTime()))

                        // Automatic Voice Domain Detection
                        if (type == EventType.USER) {
                            val detectedDomain = JarvisDomainRegistry.findDomainByVoice(text)
                            if (detectedDomain != null && detectedDomain.id != currentDomain.id) {
                                runOnUiThread {
                                    applyDomain(detectedDomain, speakAnnouncement = true)
                                }
                            }
                        }
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
                    val rName = intent.getStringExtra(JarvisForegroundService.EXTRA_ROUTE) ?: ""
                    try {
                        activeRoute = AudioRoute.valueOf(rName)
                    } catch (_: Exception) {}
                }
                JarvisForegroundService.BROADCAST_QURON_UPDATE -> {
                    val url = intent.getStringExtra("url") ?: ""
                    val sura = intent.getStringExtra("sura") ?: ""
                    val oyat = intent.getIntExtra("oyat", 0)
                    if (url.isNotEmpty()) {
                        quronWebUrl = url
                        cardQuronLesson.visibility = View.VISIBLE
                        openQuronLesson(url)
                    }
                    if (sura.isNotEmpty()) {
                        cardQuronLesson.visibility = View.VISIBLE
                        tvQuronLessonName.text = "Bugungi dars: $sura surasi, $oyat-oyat"
                        tvQuronInfo.text = "$sura surasi, $oyat-oyat tilovati va mashqi davom etmoqda."
                    }
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val recordAudioGranted = results[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            startJarvisService()
        } else {
            Toast.makeText(this, "JARVIS ishlashi uchun mikrofon ruxsati zarur", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesHelper(this)

        initViews()
        initAutoTheme()
        setupOrbInteractions()
        setupQuickActions()
        setupSecretaryCard()
        setupQuronCard()
        setupFeedAndInput()
        setupProfileAndAuth()
        setupSettingsDialog()

        checkPermissionsAndStart()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        layoutProfileAvatar = findViewById(R.id.layoutProfileAvatar)
        ivUserProfile = findViewById(R.id.ivUserProfile)

        jarvisOrb = findViewById(R.id.jarvisOrb)
        tvOrbStatus = findViewById(R.id.tvOrbStatus)
        tvOrbSubtitle = findViewById(R.id.tvOrbSubtitle)

        tvHeaderDomainBadge = findViewById(R.id.tvHeaderDomainBadge)
        chipDomainAction1 = findViewById(R.id.chipDomainAction1)
        chipDomainAction2 = findViewById(R.id.chipDomainAction2)
        chipDomainAction3 = findViewById(R.id.chipDomainAction3)
        chipDomainAction4 = findViewById(R.id.chipDomainAction4)
        chipInterrupt = findViewById(R.id.chipInterrupt)

        cardAiSecretary = findViewById(R.id.cardAiSecretary)
        tvSecretaryTitle = findViewById(R.id.tvSecretaryTitle)
        tvSecretaryToggle = findViewById(R.id.tvSecretaryToggle)
        tvSecretarySubtitle = findViewById(R.id.tvSecretarySubtitle)
        chipReasonDars = findViewById(R.id.chipReasonDars)
        chipReasonMashina = findViewById(R.id.chipReasonMashina)
        chipReasonBand = findViewById(R.id.chipReasonBand)
        chipReasonNamoz = findViewById(R.id.chipReasonNamoz)
        chipReasonCustom = findViewById(R.id.chipReasonCustom)
        tvSecretaryPreview = findViewById(R.id.tvSecretaryPreview)

        cardQuronLesson = findViewById(R.id.cardQuronLesson)
        tvQuronTitle = findViewById(R.id.tvQuronTitle)
        tvQuronInfo = findViewById(R.id.tvQuronInfo)
        tvQuronLessonName = findViewById(R.id.tvQuronLessonName)
        pbQuronProgress = findViewById(R.id.pbQuronProgress)
        btnStartQuron = findViewById(R.id.btnStartQuron)
        btnOpenQuronWeb = findViewById(R.id.btnOpenQuronWeb)
        btnCloseQuronCard = findViewById(R.id.btnCloseQuronCard)

        cardLiveHud = findViewById(R.id.cardLiveHud)
        rvLiveFeed = findViewById(R.id.rvLiveFeed)
        etCommandInput = findViewById(R.id.etCommandInput)
        btnSendCommand = findViewById(R.id.btnSendCommand)
        webViewDashboard = findViewById(R.id.webViewDashboard)
        containerWebView = findViewById(R.id.containerWebView)
        tvWebTitle = findViewById(R.id.tvWebTitle)
        btnOpenExternalBrowser = findViewById(R.id.btnOpenExternalBrowser)
        btnCloseWebView = findViewById(R.id.btnCloseWebView)

        btnCloseWebView.setOnClickListener {
            containerWebView.visibility = View.GONE
            webViewDashboard.loadUrl("about:blank")
        }

        activeRoute = try {
            AudioRoute.valueOf(prefs.audioRoute)
        } catch (_: Exception) {
            AudioRoute.EARBUDS
        }

        currentDomain = JarvisDomainRegistry.getDomainById(prefs.activeDomain)
        applyDomain(currentDomain, speakAnnouncement = false)

        tvHeaderDomainBadge.setOnClickListener {
            btnOpenSettings.performClick()
        }
    }

    /**
     * Automatic Day / Night Theme detection based on hour (07:00 - 19:00 = Day, else Night).
     */
    private fun initAutoTheme() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        isNightMode = hour !in 7..19
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
            cardAiSecretary.setBackgroundResource(R.drawable.bg_ios_card)
            cardQuronLesson.setBackgroundResource(R.drawable.bg_ios_card)
            cardLiveHud.setBackgroundResource(R.drawable.bg_ios_card)
        } else {
            rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.day_bg))
            tvHeaderTitle.setTextColor(ContextCompat.getColor(this, R.color.day_text_primary))
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
            tvOrbStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_blue))
            tvOrbSubtitle.setTextColor(ContextCompat.getColor(this, R.color.day_text_secondary))
            cardAiSecretary.setBackgroundResource(R.drawable.bg_day_card)
            cardQuronLesson.setBackgroundResource(R.drawable.bg_day_card)
            cardLiveHud.setBackgroundResource(R.drawable.bg_day_card)
        }
        if (::containerWebView.isInitialized) {
            containerWebView.setBackgroundColor(ContextCompat.getColor(this, if (night) R.color.night_bg else R.color.day_bg))
            tvWebTitle.setTextColor(ContextCompat.getColor(this, if (night) R.color.night_text_primary else R.color.day_text_primary))
        }
        if (::cardAiSecretary.isInitialized) {
            updateSecretaryUi()
        }
    }

    private fun setupOrbInteractions() {
        jarvisOrb.setOnClickListener {
            // Wake Jarvis or interrupt speaking
            try {
                val intent = Intent(this, JarvisForegroundService::class.java).apply {
                    action = JarvisForegroundService.ACTION_WAKE
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "setupOrbInteractions error: ${e.message}")
            }
        }
    }

    private fun updateOrbState(state: String) {
        when (state) {
            "SPEAKING" -> {
                jarvisOrb.state = OrbState.SPEAKING
                tvOrbStatus.text = "JARVIS GAPIRMOQDA"
                tvOrbStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_cyan))
                tvStatusBadge.text = "● GAPIRMOQDA"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_cyan))
            }
            "LISTENING" -> {
                jarvisOrb.state = OrbState.LISTENING
                tvOrbStatus.text = "JARVIS ESHITMOQDA..."
                tvOrbStatus.setTextColor(Color.parseColor("#10B981"))
                tvStatusBadge.text = "● ESHITMOQDA"
                tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
            }
            "THINKING" -> {
                jarvisOrb.state = OrbState.THINKING
                tvOrbStatus.text = "JARVIS O'YLAMOQDA..."
                tvOrbStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_amber))
                tvStatusBadge.text = "● O'YLAMOQDA"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_amber))
            }
            else -> {
                jarvisOrb.state = OrbState.IDLE
                tvOrbStatus.text = "JARVIS TAYYOR"
                tvOrbStatus.setTextColor(if (isNightMode) ContextCompat.getColor(this, R.color.jarvis_cyan) else ContextCompat.getColor(this, R.color.jarvis_blue))
                tvStatusBadge.text = "● ALOQADA"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.jarvis_accent))
            }
        }
    }

    private fun setupQuickActions() {
        chipInterrupt.setOnClickListener {
            sendJarvisCommand("To'xtat")
            try {
                val intent = Intent(this, JarvisForegroundService::class.java).apply {
                    action = JarvisForegroundService.ACTION_INTERRUPT
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "chipInterrupt error: ${e.message}")
            }
        }
    }

    private fun applyDomain(domain: JarvisDomain, speakAnnouncement: Boolean = false) {
        currentDomain = domain
        prefs.activeDomain = domain.id

        // 1. Morph Neural Core Colors & Title
        jarvisOrb.setDomainColors(domain.primaryColor, domain.glowColor, domain.titleUz)

        // 2. Update Header Badge
        tvHeaderDomainBadge.text = "${domain.icon} ${domain.titleUz.uppercase()}"
        tvHeaderDomainBadge.backgroundTintList = ColorStateList.valueOf(domain.primaryColor)
        tvHeaderDomainBadge.setTextColor(Color.parseColor("#0F172A"))

        // 3. Update Status Subtitle
        tvOrbStatus.text = "${domain.titleUz.uppercase()} REJIMI"
        tvOrbStatus.setTextColor(domain.primaryColor)
        tvOrbSubtitle.text = domain.motto

        // 4. Update the 4 Dynamic Domain Action Pills
        if (domain.actions.size >= 4) {
            chipDomainAction1.text = "${domain.actions[0].icon} ${domain.actions[0].title}"
            chipDomainAction1.setOnClickListener { sendJarvisCommand(domain.actions[0].promptCommand) }

            chipDomainAction2.text = "${domain.actions[1].icon} ${domain.actions[1].title}"
            chipDomainAction2.setOnClickListener { sendJarvisCommand(domain.actions[1].promptCommand) }

            chipDomainAction3.text = "${domain.actions[2].icon} ${domain.actions[2].title}"
            chipDomainAction3.setOnClickListener { sendJarvisCommand(domain.actions[2].promptCommand) }

            chipDomainAction4.text = "${domain.actions[3].icon} ${domain.actions[3].title}"
            chipDomainAction4.setOnClickListener { sendJarvisCommand(domain.actions[3].promptCommand) }
        }

        // 5. Quran card visibility (Din mode)
        if (domain.id == "DIN") {
            cardQuronLesson.visibility = View.VISIBLE
        }

        // 6. Notify Backend about domain switch
        if (prefs.isServiceRunning) {
            try {
                val intent = Intent(this, JarvisForegroundService::class.java).apply {
                    action = JarvisForegroundService.ACTION_SEND_TEXT
                    putExtra(JarvisForegroundService.EXTRA_COMMAND_TEXT, "DOMAIN_SWITCH:${domain.id}")
                }
                startService(intent)
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to notify service: ${e.message}")
            }
        }

        if (speakAnnouncement) {
            addFeedEvent(LiveEvent(
                EventType.SYSTEM,
                "JARVIS",
                "${domain.titleUz} rejimi faollashtirildi. ${domain.motto}",
                currentTime()
            ))
            sendJarvisCommand("${domain.titleUz} rejimi faol. ${domain.motto}")
        }
    }

    private fun setupSecretaryCard() {
        tvSecretaryToggle.setOnClickListener {
            val newVal = !prefs.aiSecretaryEnabled
            prefs.aiSecretaryEnabled = newVal
            updateSecretaryUi()
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_SET_SECRETARY
                putExtra("enabled", newVal)
            }
            startService(intent)
            Toast.makeText(this, if (newVal) "AI Kotib yoqildi" else "AI Kotib o'chirildi", Toast.LENGTH_SHORT).show()
        }

        chipReasonDars.setOnClickListener { selectSecretaryReason("Darsdaman") }
        chipReasonMashina.setOnClickListener { selectSecretaryReason("Mashinadaman") }
        chipReasonBand.setOnClickListener { selectSecretaryReason("Bandman / Majlis") }
        chipReasonNamoz.setOnClickListener { selectSecretaryReason("Namozdaman") }
        chipReasonCustom.setOnClickListener { promptCustomReasonDialog() }

        updateSecretaryUi()
    }

    private fun selectSecretaryReason(reason: String) {
        prefs.aiSecretaryReason = reason
        updateSecretaryUi()
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_SET_SECRETARY
            putExtra("enabled", prefs.aiSecretaryEnabled)
            putExtra("reason", reason)
        }
        startService(intent)
        Toast.makeText(this, "Bandlik sababi: $reason", Toast.LENGTH_SHORT).show()
    }

    private fun updateSecretaryUi() {
        val enabled = prefs.aiSecretaryEnabled
        val reason = prefs.aiSecretaryReason

        if (enabled) {
            tvSecretaryToggle.text = "YOQILGAN"
            tvSecretaryToggle.setTextColor(ContextCompat.getColor(this, R.color.jarvis_accent))
            tvSecretarySubtitle.text = "Siz band bo'lganingizda o'rningizga odob bilan javob beradi."
        } else {
            tvSecretaryToggle.text = "O'CHIRILGAN"
            tvSecretaryToggle.setTextColor(ContextCompat.getColor(this, R.color.jarvis_red))
            tvSecretarySubtitle.text = "AI Kotib hozir faol emas (oddiy qo'ng'iroq)."
        }

        val activeBg = R.drawable.bg_ios_pill_active
        val normalBg = R.drawable.bg_ios_action_chip
        val activeText = if (isNightMode) ContextCompat.getColor(this, R.color.night_pill_active_text) else Color.WHITE
        val normalText = if (isNightMode) ContextCompat.getColor(this, R.color.night_text_primary) else ContextCompat.getColor(this, R.color.day_text_primary)

        val chips = listOf(
            chipReasonDars to "Darsdaman",
            chipReasonMashina to "Mashinadaman",
            chipReasonBand to "Bandman / Majlis",
            chipReasonNamoz to "Namozdaman"
        )

        var matchedPreset = false
        for ((chip, rName) in chips) {
            if (reason.equals(rName, ignoreCase = true)) {
                chip.setBackgroundResource(activeBg)
                chip.setTextColor(activeText)
                matchedPreset = true
            } else {
                chip.setBackgroundResource(normalBg)
                chip.setTextColor(normalText)
            }
        }

        if (!matchedPreset) {
            chipReasonCustom.setBackgroundResource(activeBg)
            chipReasonCustom.setTextColor(activeText)
            chipReasonCustom.text = "✍️ $reason"
        } else {
            chipReasonCustom.setBackgroundResource(normalBg)
            chipReasonCustom.setTextColor(normalText)
            chipReasonCustom.text = "✍️ Boshqa..."
        }

        val r = reason.lowercase()
        val reasonFormatted = when {
            r.contains("dars") -> "darsdalar"
            r.contains("mashina") || r.contains("hayda") -> "mashina haydamoqdalar"
            r.contains("namoz") -> "namozdalar"
            r.contains("majlis") || r.contains("band") -> "muhim majlisda bandlar"
            r.contains("dam") -> "dam olmoqdalar"
            else -> "$reason sababli hozir bandlar"
        }

        tvSecretaryPreview.text = "💬 Jarvis aytadi: «Assalomu alaykum. Axmadulloh sir hozir $reasonFormatted, shuning uchun go'shakni ko'tara olmadilar. Xabaringizni eshitaman...»"
    }

    private fun promptCustomReasonDialog() {
        val et = EditText(this).apply {
            hint = "Masalan: Sportdaman, Uydaman"
            setText(prefs.aiSecretaryReason)
            setPadding(40, 30, 40, 30)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("AI Kotib uchun sabab kiriting")
            .setView(et)
            .setPositiveButton("Saqlash") { _, _ ->
                val txt = et.text.toString().trim()
                if (txt.isNotEmpty()) {
                    selectSecretaryReason(txt)
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    /**
     * Quran Lesson Card setup.
     * User requested: "Quron ustoz ai qachon so'rsam paydo bo'lsin" -> GONE by default!
     */
    private fun setupQuronCard() {
        cardQuronLesson.visibility = if (prefs.quronCardVisible) View.VISIBLE else View.GONE

        btnCloseQuronCard.setOnClickListener {
            cardQuronLesson.visibility = View.GONE
            prefs.quronCardVisible = false
        }

        btnStartQuron.setOnClickListener {
            sendJarvisCommand("Qur'on darsini boshlaymiz")
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

    private fun resolveLessonUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        try {
            val uri = Uri.parse(rawUrl)
            val configuredServer = prefs.serverAddress.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("ws://")
                .removePrefix("wss://")

            val configuredHost = if (configuredServer.contains(":")) {
                configuredServer.substringBefore(":")
            } else {
                configuredServer
            }

            if (configuredHost.isNotBlank()) {
                val originalPort = uri.port
                val newPortPart = if (originalPort != -1) ":$originalPort" else ""
                val scheme = uri.scheme ?: "http"
                val path = uri.path ?: ""
                val query = if (uri.query != null) "?${uri.query}" else ""
                val fragment = if (uri.fragment != null) "#${uri.fragment}" else ""
                return "$scheme://$configuredHost$newPortPart$path$query$fragment"
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Error resolving lesson URL: ${e.message}")
        }
        return rawUrl
    }

    private fun openQuronLesson(url: String) {
        val resolvedUrl = resolveLessonUrl(url)
        quronWebUrl = resolvedUrl
        runOnUiThread {
            btnOpenExternalBrowser.setOnClickListener {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Brauzer ochishda xatolik", Toast.LENGTH_SHORT).show()
                }
            }
            openWebUrl(resolvedUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openWebUrl(url: String) {
        if (url.isBlank()) return
        containerWebView.visibility = View.VISIBLE
        tvWebTitle.text = "📖 Qur'on Ustozi AI"
        webViewDashboard.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            databaseEnabled = true
        }
        webViewDashboard.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        webViewDashboard.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    try {
                        request?.grant(request.resources)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "WebRTC permission grant error: ${e.message}")
                    }
                }
            }
        }
        webViewDashboard.loadUrl(url)
    }

    private fun setupFeedAndInput() {
        eventAdapter = LiveEventAdapter()
        rvLiveFeed.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvLiveFeed.adapter = eventAdapter

        addFeedEvent(LiveEvent(
            EventType.SYSTEM,
            "TIZIM",
            "JARVIS Mark 57 Companion tayyor. Xizmatingizdaman!",
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
        val detected = JarvisDomainRegistry.findDomainByVoice(cmd)
        if (detected != null && detected.id != currentDomain.id) {
            applyDomain(detected, speakAnnouncement = true)
        }

        try {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_SEND_TEXT
                putExtra(JarvisForegroundService.EXTRA_COMMAND_TEXT, cmd)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to send command to service: ${e.message}")
        }
        addFeedEvent(LiveEvent(EventType.USER, "Siz", cmd, currentTime()))
    }

    /**
     * User Profile & Authentication Dialog (Google / Manual Registration).
     */
    private fun setupProfileAndAuth() {
        layoutProfileAvatar.setOnClickListener {
            showAuthProfileDialog()
        }
    }

    private fun showAuthProfileDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_auth_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etName = dialog.findViewById<EditText>(R.id.etProfileName)
        val etEmail = dialog.findViewById<EditText>(R.id.etProfileEmail)
        val btnGoogle = dialog.findViewById<MaterialButton>(R.id.btnGoogleSignIn)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnProfileSave)
        val btnLogout = dialog.findViewById<MaterialButton>(R.id.btnProfileLogout)

        etName.setText(prefs.userName)
        etEmail.setText(prefs.userEmail)

        btnGoogle.setOnClickListener {
            // Google Sign-In simulation / Account link
            prefs.userName = "Axmadulloh sir"
            prefs.userEmail = "axmadulloh.dev@gmail.com"
            prefs.isLoggedIn = true
            dialog.dismiss()
            Toast.makeText(this, "Google orqali muvaffaqiyatli ulandi: ${prefs.userName}", Toast.LENGTH_LONG).show()
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            if (name.isNotEmpty()) prefs.userName = name
            if (email.isNotEmpty()) prefs.userEmail = email
            prefs.isLoggedIn = true
            dialog.dismiss()
            Toast.makeText(this, "Profil ma'lumotlari saqlandi!", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            prefs.userName = "Foydalanuvchi"
            prefs.userEmail = ""
            prefs.isLoggedIn = false
            dialog.dismiss()
            Toast.makeText(this, "Hisobdan chiqildi", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    /**
     * Settings Dialog with 3-Way Device Route Control, AI Persona, and Quran options.
     */
    private fun setupSettingsDialog() {
        btnOpenSettings.setOnClickListener {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_settings)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val btnPc = dialog.findViewById<TextView>(R.id.btnDialogRoutePc)
            val btnPhone = dialog.findViewById<TextView>(R.id.btnDialogRoutePhone)
            val btnEarbuds = dialog.findViewById<TextView>(R.id.btnDialogRouteEarbuds)

            val tvActiveLabel = dialog.findViewById<TextView>(R.id.tvSettingsActiveDomainLabel)
            val rvDomains = dialog.findViewById<RecyclerView>(R.id.rvSettingsDomains)

            tvActiveLabel.text = "${currentDomain.icon} ${currentDomain.titleUz}"
            tvActiveLabel.setTextColor(currentDomain.primaryColor)

            rvDomains.layoutManager = LinearLayoutManager(this)
            val domainAdapter = DomainAdapter(JarvisDomainRegistry.getAllDomains(), currentDomain.id) { selected ->
                applyDomain(selected, speakAnnouncement = true)
                tvActiveLabel.text = "${selected.icon} ${selected.titleUz}"
                tvActiveLabel.setTextColor(selected.primaryColor)
                dialog.dismiss()
                Toast.makeText(this, "${selected.titleUz} rejimi faollashtirildi!", Toast.LENGTH_SHORT).show()
            }
            rvDomains.adapter = domainAdapter

            val etServer = dialog.findViewById<EditText>(R.id.etDialogServer)
            val etToken = dialog.findViewById<EditText>(R.id.etDialogToken)
            val cbBoot = dialog.findViewById<CheckBox>(R.id.cbDialogAutoStart)
            val cbQuron = dialog.findViewById<CheckBox>(R.id.cbDialogQuronAlways)
            val btnNotif = dialog.findViewById<MaterialButton>(R.id.btnGrantNotificationAccess)
            val btnSave = dialog.findViewById<MaterialButton>(R.id.btnDialogSave)

            var selectedRoute = activeRoute

            fun updateDialogRouteUi() {
                val activeBg = R.drawable.bg_ios_pill_active
                val actText = Color.BLACK
                val inactText = Color.GRAY

                btnPc.background = null; btnPhone.background = null; btnEarbuds.background = null
                btnPc.setTextColor(inactText); btnPhone.setTextColor(inactText); btnEarbuds.setTextColor(inactText)

                when (selectedRoute) {
                    AudioRoute.PC -> { btnPc.setBackgroundResource(activeBg); btnPc.setTextColor(actText) }
                    AudioRoute.PHONE -> { btnPhone.setBackgroundResource(activeBg); btnPhone.setTextColor(actText) }
                    AudioRoute.EARBUDS -> { btnEarbuds.setBackgroundResource(activeBg); btnEarbuds.setTextColor(actText) }
                }
            }

            updateDialogRouteUi()

            btnPc.setOnClickListener { selectedRoute = AudioRoute.PC; updateDialogRouteUi() }
            btnPhone.setOnClickListener { selectedRoute = AudioRoute.PHONE; updateDialogRouteUi() }
            btnEarbuds.setOnClickListener { selectedRoute = AudioRoute.EARBUDS; updateDialogRouteUi() }

            etServer.setText(prefs.serverAddress)
            etToken.setText(prefs.authToken)
            cbBoot.isChecked = prefs.autoStartOnBoot
            cbQuron.isChecked = prefs.quronCardVisible

            btnNotif.setOnClickListener {
                promptNotificationAccess()
            }

            btnSave.setOnClickListener {
                activeRoute = selectedRoute
                prefs.audioRoute = selectedRoute.name

                prefs.serverAddress = etServer.text.toString().trim()
                prefs.authToken = etToken.text.toString().trim()
                prefs.autoStartOnBoot = cbBoot.isChecked
                prefs.quronCardVisible = cbQuron.isChecked
                cardQuronLesson.visibility = if (cbQuron.isChecked) View.VISIBLE else View.GONE

                val routeIntent = Intent(this, JarvisForegroundService::class.java).apply {
                    action = JarvisForegroundService.ACTION_SET_ROUTE
                    putExtra(JarvisForegroundService.EXTRA_ROUTE, selectedRoute.name)
                }
                startService(routeIntent)

                dialog.dismiss()
                startJarvisService()
                Toast.makeText(this, "Sozlamalar saqlandi: ${selectedRoute.name}", Toast.LENGTH_SHORT).show()
            }

            dialog.show()
        }
    }

    private fun promptNotificationAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Telegram uchun 'Jarvis Companion'ni yoqing", Toast.LENGTH_LONG).show()
    }

    private fun addFeedEvent(event: LiveEvent) {
        rvLiveFeed.post {
            try {
                eventAdapter.addEvent(event)
                rvLiveFeed.scrollToPosition(eventAdapter.itemCount - 1)
            } catch (e: Exception) {
                Log.e("MainActivity", "addFeedEvent error: ${e.message}")
            }
        }
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
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
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
        try {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "startJarvisService error: ${e.message}")
        }
    }

    override fun onBackPressed() {
        if (::containerWebView.isInitialized && containerWebView.visibility == View.VISIBLE) {
            if (webViewDashboard.canGoBack()) {
                webViewDashboard.goBack()
            } else {
                containerWebView.visibility = View.GONE
                webViewDashboard.loadUrl("about:blank")
            }
            return
        }
        super.onBackPressed()
    }
}
