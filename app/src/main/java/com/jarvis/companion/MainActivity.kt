package com.jarvis.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
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
import android.widget.TextView
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
    private var vibrator: Vibrator? = null

    // Root & Theme
    private lateinit var rootContainer: View
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvHeaderDomainBadge: TextView
    private lateinit var btnThemeToggle: ImageButton
    private lateinit var btnOpenSettings: ImageButton
    private lateinit var layoutProfileAvatar: View
    private lateinit var ivUserProfile: ImageView
    private var isNightMode = true // Default dark cyberpunk theme

    // Active Device Route
    private var activeRoute: AudioRoute = AudioRoute.EARBUDS

    // Active Domain (31 Soha)
    private var currentDomain: JarvisDomain = JarvisDomainRegistry.ASOSIY

    // AI Orb & Status
    private lateinit var jarvisOrb: JarvisOrbView
    private lateinit var tvOrbStatus: TextView
    private lateinit var tvOrbSubtitle: TextView

    // =========================================================================
    // DYNAMIC WORKSPACE CONTAINERS
    // =========================================================================
    private lateinit var workspaceAsosiy: View
    private lateinit var workspaceTalaba: View
    private lateinit var workspaceHaydovchi: View
    private lateinit var workspaceDin: View
    private lateinit var workspaceDokon: View
    private lateinit var workspaceDasturchi: View
    private lateinit var workspaceMoliya: View
    private lateinit var workspaceGeneric: View

    // 1. Asosiy Views
    private lateinit var chipDomainAction1: TextView
    private lateinit var chipDomainAction2: TextView
    private lateinit var chipDomainAction3: TextView
    private lateinit var chipDomainAction4: TextView
    private lateinit var chipInterrupt: TextView

    // AI Kotib (Call Secretary)
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

    // 2. Talaba Views
    private lateinit var btnTalabaRecordLecture: View
    private lateinit var btnTalabaSchedule: View
    private lateinit var btnTalabaQuiz: View
    private lateinit var btnTalabaSummary: View
    private lateinit var btnTalabaExam: View

    // 3. Haydovchi Views
    private lateinit var btnHaydovchiTraffic: View
    private lateinit var btnHaydovchiFuel: View
    private lateinit var btnHaydovchiCall: View
    private lateinit var btnHaydovchiMaintenance: View

    // 4. Din Views & Digital Tasbeh
    private lateinit var btnDinStartQuran: View
    private lateinit var tvTasbehPhrase: TextView
    private lateinit var tvTasbehCount: TextView
    private lateinit var btnTasbehTap: TextView
    private lateinit var btnTasbehReset: TextView
    private lateinit var btnDinNamoz: View
    private lateinit var btnDinDua: View
    private var tasbehCount = 0
    private var tasbehPhase = 0
    private val tasbehPhrases = listOf("Subhanalloh", "Alhamdulillah", "Allohu Akbar")

    // 5. Dokon Views
    private lateinit var btnDokonSale: View
    private lateinit var btnDokonStock: View
    private lateinit var btnDokonDebt: View
    private lateinit var btnDokonRevenue: View

    // 6. Dasturchi Views
    private lateinit var btnDevDebug: View
    private lateinit var btnDevGit: View
    private lateinit var btnDevServer: View
    private lateinit var btnDevTerminal: View

    // 7. Moliya Views
    private lateinit var btnFinanceExpense: View
    private lateinit var btnFinanceBudget: View
    private lateinit var btnFinanceDebt: View
    private lateinit var btnFinanceReport: View

    // 8. Generic Views
    private lateinit var btnGenericAction1: View
    private lateinit var btnGenericAction2: View
    private lateinit var btnGenericAction3: View
    private lateinit var btnGenericAction4: View
    private lateinit var tvGenericIcon1: TextView
    private lateinit var tvGenericIcon2: TextView
    private lateinit var tvGenericIcon3: TextView
    private lateinit var tvGenericIcon4: TextView
    private lateinit var tvGenericTitle1: TextView
    private lateinit var tvGenericTitle2: TextView
    private lateinit var tvGenericTitle3: TextView
    private lateinit var tvGenericTitle4: TextView

    // HUD & Input
    private lateinit var cardLiveHud: View
    private lateinit var tvLiveHudTitle: TextView
    private lateinit var rvLiveFeed: RecyclerView
    private lateinit var etCommandInput: EditText
    private lateinit var btnSendCommand: ImageButton
    private lateinit var eventAdapter: LiveEventAdapter

    // In-App WebView
    private lateinit var webViewDashboard: WebView
    private lateinit var containerWebView: View
    private lateinit var tvWebTitle: TextView
    private lateinit var btnOpenExternalBrowser: TextView
    private lateinit var btnCloseWebView: TextView
    private var quronWebUrl: String = ""

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
                    if (url.isNotEmpty()) {
                        quronWebUrl = url
                        runOnUiThread {
                            // Ensure Din domain is active
                            if (currentDomain.id != "DIN") {
                                applyDomain(JarvisDomainRegistry.DIN, speakAnnouncement = false)
                            }
                            openQuronLesson(url)
                        }
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
        initVibrator()

        initViews()
        initAutoTheme()
        setupOrbInteractions()
        setupWorkspaces()
        setupSecretaryCard()
        setupFeedAndInput()
        setupProfileAndAuth()
        setupSettingsDialog()

        checkPermissionsAndStart()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrateBrief(millis: Long = 35) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(millis)
            }
        } catch (_: Exception) {}
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

        // Workspaces
        workspaceAsosiy = findViewById(R.id.workspaceAsosiy)
        workspaceTalaba = findViewById(R.id.workspaceTalaba)
        workspaceHaydovchi = findViewById(R.id.workspaceHaydovchi)
        workspaceDin = findViewById(R.id.workspaceDin)
        workspaceDokon = findViewById(R.id.workspaceDokon)
        workspaceDasturchi = findViewById(R.id.workspaceDasturchi)
        workspaceMoliya = findViewById(R.id.workspaceMoliya)
        workspaceGeneric = findViewById(R.id.workspaceGeneric)

        // Asosiy
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

        // Talaba
        btnTalabaRecordLecture = findViewById(R.id.btnTalabaRecordLecture)
        btnTalabaSchedule = findViewById(R.id.btnTalabaSchedule)
        btnTalabaQuiz = findViewById(R.id.btnTalabaQuiz)
        btnTalabaSummary = findViewById(R.id.btnTalabaSummary)
        btnTalabaExam = findViewById(R.id.btnTalabaExam)

        // Haydovchi
        btnHaydovchiTraffic = findViewById(R.id.btnHaydovchiTraffic)
        btnHaydovchiFuel = findViewById(R.id.btnHaydovchiFuel)
        btnHaydovchiCall = findViewById(R.id.btnHaydovchiCall)
        btnHaydovchiMaintenance = findViewById(R.id.btnHaydovchiMaintenance)

        // Din & Tasbeh
        btnDinStartQuran = findViewById(R.id.btnDinStartQuran)
        tvTasbehPhrase = findViewById(R.id.tvTasbehPhrase)
        tvTasbehCount = findViewById(R.id.tvTasbehCount)
        btnTasbehTap = findViewById(R.id.btnTasbehTap)
        btnTasbehReset = findViewById(R.id.btnTasbehReset)
        btnDinNamoz = findViewById(R.id.btnDinNamoz)
        btnDinDua = findViewById(R.id.btnDinDua)

        // Dokon
        btnDokonSale = findViewById(R.id.btnDokonSale)
        btnDokonStock = findViewById(R.id.btnDokonStock)
        btnDokonDebt = findViewById(R.id.btnDokonDebt)
        btnDokonRevenue = findViewById(R.id.btnDokonRevenue)

        // Dasturchi
        btnDevDebug = findViewById(R.id.btnDevDebug)
        btnDevGit = findViewById(R.id.btnDevGit)
        btnDevServer = findViewById(R.id.btnDevServer)
        btnDevTerminal = findViewById(R.id.btnDevTerminal)

        // Moliya
        btnFinanceExpense = findViewById(R.id.btnFinanceExpense)
        btnFinanceBudget = findViewById(R.id.btnFinanceBudget)
        btnFinanceDebt = findViewById(R.id.btnFinanceDebt)
        btnFinanceReport = findViewById(R.id.btnFinanceReport)

        // Generic
        btnGenericAction1 = findViewById(R.id.btnGenericAction1)
        btnGenericAction2 = findViewById(R.id.btnGenericAction2)
        btnGenericAction3 = findViewById(R.id.btnGenericAction3)
        btnGenericAction4 = findViewById(R.id.btnGenericAction4)
        tvGenericIcon1 = findViewById(R.id.tvGenericIcon1)
        tvGenericIcon2 = findViewById(R.id.tvGenericIcon2)
        tvGenericIcon3 = findViewById(R.id.tvGenericIcon3)
        tvGenericIcon4 = findViewById(R.id.tvGenericIcon4)
        tvGenericTitle1 = findViewById(R.id.tvGenericTitle1)
        tvGenericTitle2 = findViewById(R.id.tvGenericTitle2)
        tvGenericTitle3 = findViewById(R.id.tvGenericTitle3)
        tvGenericTitle4 = findViewById(R.id.tvGenericTitle4)

        // HUD & Input
        cardLiveHud = findViewById(R.id.cardLiveHud)
        tvLiveHudTitle = findViewById(R.id.tvLiveHudTitle)
        rvLiveFeed = findViewById(R.id.rvLiveFeed)
        etCommandInput = findViewById(R.id.etCommandInput)
        btnSendCommand = findViewById(R.id.btnSendCommand)

        // In-App WebView
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

    private fun setupWorkspaces() {
        // Talaba Actions
        btnTalabaRecordLecture.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Ma'ruza yozishni boshla va asosiy konspekt qilib ber")
        }
        btnTalabaSchedule.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Bugungi dars jadvali va deadline'larimni ayt")
        }
        btnTalabaQuiz.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Oxirgi mavzu bo'yicha 5 ta test tuz")
        }
        btnTalabaSummary.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Ushbu darsning qisqa xulosasini tayyorla")
        }
        btnTalabaExam.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Imtihongacha kunlik o'qish rejamni ayt")
        }

        // Haydovchi Actions
        btnHaydovchiTraffic.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Hozirgi yo'nalish bo'yicha tirbandlik va marshrut qanday?")
        }
        btnHaydovchiFuel.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Yoqilg'i quyish xarajatini qayd et")
        }
        btnHaydovchiCall.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Menga eng so'nggi qo'ng'iroq qilgan odamga qaytar")
        }
        btnHaydovchiMaintenance.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Moy almashtirish va sug'urta muddatini eslat")
        }

        // Din & Tasbeh Actions
        btnDinStartQuran.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Qur'on ustozi darsini boshlaymiz")
            if (quronWebUrl.isNotEmpty()) {
                openQuronLesson(quronWebUrl)
            }
        }
        btnDinNamoz.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Bugungi Toshkent namoz vaqtlarini ayt")
        }
        btnDinDua.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Ertalabki va kechki ma'sur duolarni ayt")
        }

        btnTasbehTap.setOnClickListener {
            tasbehCount++
            vibrateBrief(35)
            if (tasbehCount >= 33) {
                tasbehCount = 0
                tasbehPhase = (tasbehPhase + 1) % tasbehPhrases.size
                vibrateBrief(80)
            }
            tvTasbehPhrase.text = tasbehPhrases[tasbehPhase]
            tvTasbehCount.text = "$tasbehCount / 33"
        }
        btnTasbehReset.setOnClickListener {
            tasbehCount = 0
            tasbehPhase = 0
            tvTasbehPhrase.text = tasbehPhrases[0]
            tvTasbehCount.text = "0 / 33"
            vibrateBrief(50)
        }

        // Dokon Actions
        btnDokonSale.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("5 ta non sotildi, omborni yangila")
        }
        btnDokonStock.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Ombor qoldig'ini va tugayotgan tovarlarni ayt")
        }
        btnDokonDebt.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Qarzdorlar ro'yxatini chiqar")
        }
        btnDokonRevenue.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Bugungi tushum va sof foyda hisobotini ber")
        }

        // Dasturchi Actions
        btnDevDebug.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Bu kod xatosini tahlil qil va tuzatib ber")
        }
        btnDevGit.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Git status va oxirgi commitlarni tekshir")
        }
        btnDevServer.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Server holatini tekshiruvchi buyruq ber")
        }
        btnDevTerminal.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Terminal holatini tekshir")
        }

        // Moliya Actions
        btnFinanceExpense.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Taksiga 20 ming to'ladim, xarajatga yoz")
        }
        btnFinanceBudget.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Oylik byudjetim qancha qoldi?")
        }
        btnFinanceDebt.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Qarz eslatmalarini ayt")
        }
        btnFinanceReport.setOnClickListener {
            vibrateBrief()
            sendJarvisCommand("Haftalik xarajatlarim hisobotini ber")
        }
    }

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
            cardLiveHud.setBackgroundResource(R.drawable.bg_ios_card)
            tvLiveHudTitle.setTextColor(Color.WHITE)
        } else {
            rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.day_bg))
            tvHeaderTitle.setTextColor(ContextCompat.getColor(this, R.color.day_text_primary))
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
            tvOrbStatus.setTextColor(ContextCompat.getColor(this, R.color.jarvis_blue))
            tvOrbSubtitle.setTextColor(ContextCompat.getColor(this, R.color.day_text_secondary))
            cardAiSecretary.setBackgroundResource(R.drawable.bg_day_card)
            cardLiveHud.setBackgroundResource(R.drawable.bg_day_card)
            tvLiveHudTitle.setTextColor(ContextCompat.getColor(this, R.color.day_text_primary))
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
            vibrateBrief(30)
            wakeJarvisVoice()
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

        // 4. Switch Dynamic Workspace Layouts (Entire UI transforms!)
        workspaceAsosiy.visibility = View.GONE
        workspaceTalaba.visibility = View.GONE
        workspaceHaydovchi.visibility = View.GONE
        workspaceDin.visibility = View.GONE
        workspaceDokon.visibility = View.GONE
        workspaceDasturchi.visibility = View.GONE
        workspaceMoliya.visibility = View.GONE
        workspaceGeneric.visibility = View.GONE

        when (domain.id) {
            "TALABA" -> workspaceTalaba.visibility = View.VISIBLE
            "HAYDOVCHI" -> workspaceHaydovchi.visibility = View.VISIBLE
            "DIN" -> workspaceDin.visibility = View.VISIBLE
            "DOKON" -> workspaceDokon.visibility = View.VISIBLE
            "DASTURCHI" -> workspaceDasturchi.visibility = View.VISIBLE
            "MOLIYA" -> workspaceMoliya.visibility = View.VISIBLE
            "ASOSIY" -> workspaceAsosiy.visibility = View.VISIBLE
            else -> {
                workspaceGeneric.visibility = View.VISIBLE
                setupGenericWorkspace(domain)
            }
        }

        // 5. Update Quick Action Pills in Asosiy
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

    private fun setupGenericWorkspace(domain: JarvisDomain) {
        val actions = domain.actions
        if (actions.isNotEmpty()) {
            tvGenericIcon1.text = actions[0].icon
            tvGenericTitle1.text = actions[0].title
            btnGenericAction1.setOnClickListener { sendJarvisCommand(actions[0].promptCommand) }
        }
        if (actions.size > 1) {
            tvGenericIcon2.text = actions[1].icon
            tvGenericTitle2.text = actions[1].title
            btnGenericAction2.setOnClickListener { sendJarvisCommand(actions[1].promptCommand) }
        }
        if (actions.size > 2) {
            tvGenericIcon3.text = actions[2].icon
            tvGenericTitle3.text = actions[2].title
            btnGenericAction3.setOnClickListener { sendJarvisCommand(actions[2].promptCommand) }
        }
        if (actions.size > 3) {
            tvGenericIcon4.text = actions[3].icon
            tvGenericTitle4.text = actions[3].title
            btnGenericAction4.setOnClickListener { sendJarvisCommand(actions[3].promptCommand) }
        }
    }

    private fun setupSecretaryCard() {
        updateSecretaryUi()

        tvSecretaryToggle.setOnClickListener {
            prefs.aiSecretaryEnabled = !prefs.aiSecretaryEnabled
            updateSecretaryUi()
            notifyServiceSecretaryChanged()
        }

        chipReasonDars.setOnClickListener { selectSecretaryReason("Darsdaman") }
        chipReasonMashina.setOnClickListener { selectSecretaryReason("Mashinadaman") }
        chipReasonBand.setOnClickListener { selectSecretaryReason("Bandman / Majlis") }
        chipReasonNamoz.setOnClickListener { selectSecretaryReason("Namozdaman") }
        chipReasonCustom.setOnClickListener { promptCustomReasonDialog() }
    }

    private fun updateSecretaryUi() {
        val enabled = prefs.aiSecretaryEnabled
        tvSecretaryToggle.text = if (enabled) "YOQILGAN" else "O'CHIRILGAN"
        tvSecretaryToggle.setTextColor(
            if (enabled) ContextCompat.getColor(this, R.color.jarvis_accent)
            else ContextCompat.getColor(this, R.color.jarvis_red)
        )
        selectSecretaryReason(prefs.aiSecretaryReason)
    }

    private fun selectSecretaryReason(reason: String) {
        prefs.aiSecretaryReason = reason

        val activeBg = R.drawable.bg_ios_pill_active
        val normalBg = R.drawable.bg_ios_action_chip
        val activeText = Color.parseColor("#070A12")
        val normalText = Color.WHITE

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
                    notifyServiceSecretaryChanged()
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun notifyServiceSecretaryChanged() {
        try {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_SET_SECRETARY
                putExtra("enabled", prefs.aiSecretaryEnabled)
                putExtra("reason", prefs.aiSecretaryReason)
            }
            startService(intent)
        } catch (_: Exception) {}
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
            "JARVIS Mark 57 tayyor. Xizmatingizdaman, Axmadulloh sir!",
            currentTime()
        ))

        // Dynamic Send / Mic Icon Switcher
        etCommandInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                btnSendCommand.setImageResource(if (hasText) R.drawable.ic_send else R.drawable.ic_mic)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etCommandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val txt = etCommandInput.text.toString().trim()
                if (txt.isNotEmpty()) {
                    sendJarvisCommand(txt)
                    etCommandInput.setText("")
                }
                true
            } else false
        }

        // Tap or Hold Mic / Send Button
        btnSendCommand.setOnClickListener {
            val txt = etCommandInput.text.toString().trim()
            if (txt.isNotEmpty()) {
                sendJarvisCommand(txt)
                etCommandInput.setText("")
            } else {
                vibrateBrief(35)
                wakeJarvisVoice()
            }
        }

        btnSendCommand.setOnLongClickListener {
            val txt = etCommandInput.text.toString().trim()
            if (txt.isEmpty()) {
                vibrateBrief(60)
                wakeJarvisVoice()
                Toast.makeText(this, "🎤 Jarvis eshitmoqda...", Toast.LENGTH_SHORT).show()
                true
            } else false
        }
    }

    private fun wakeJarvisVoice() {
        try {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_WAKE
            }
            startService(intent)
            updateOrbState("LISTENING")
        } catch (e: Exception) {
            Log.e("MainActivity", "wakeJarvisVoice error: ${e.message}")
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

        val tvDialogTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val etUserName = dialog.findViewById<EditText>(R.id.etUserName)
        val etUserEmail = dialog.findViewById<EditText>(R.id.etUserEmail)
        val btnGoogleLogin = dialog.findViewById<View>(R.id.btnGoogleLogin)
        val btnSaveProfile = dialog.findViewById<MaterialButton>(R.id.btnSaveProfile)
        val btnCloseAuthDialog = dialog.findViewById<ImageButton>(R.id.btnCloseAuthDialog)

        etUserName.setText(prefs.userName)
        etUserEmail.setText(prefs.userEmail)

        btnGoogleLogin.setOnClickListener {
            prefs.userName = "Axmadulloh"
            prefs.userEmail = "axmadulloh@gmail.com"
            prefs.userAuthType = "GOOGLE"
            etUserName.setText("Axmadulloh")
            etUserEmail.setText("axmadulloh@gmail.com")
            Toast.makeText(this, "Google hisobi ulandi: Axmadulloh", Toast.LENGTH_SHORT).show()
        }

        btnSaveProfile.setOnClickListener {
            val name = etUserName.text.toString().trim()
            val email = etUserEmail.text.toString().trim()
            if (name.isNotEmpty()) {
                prefs.userName = name
                prefs.userEmail = email
                Toast.makeText(this, "Profil saqlandi!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Iltimos, ismingizni kiriting", Toast.LENGTH_SHORT).show()
            }
        }

        btnCloseAuthDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupSettingsDialog() {
        btnOpenSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_settings)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etServer = dialog.findViewById<EditText>(R.id.etServerAddress)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnSaveSettings)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseSettings)
        val rvDomains = dialog.findViewById<RecyclerView>(R.id.rvDomains)

        val routeComputer = dialog.findViewById<TextView>(R.id.chipRouteComputer)
        val routePhone = dialog.findViewById<TextView>(R.id.chipRoutePhone)
        val routeEarbuds = dialog.findViewById<TextView>(R.id.chipRouteEarbuds)

        val cbMic = dialog.findViewById<CheckBox>(R.id.cbAutoStartMic)
        val cbSecretary = dialog.findViewById<CheckBox>(R.id.cbAiSecretaryDefault)

        etServer.setText(prefs.serverAddress)
        cbMic.isChecked = prefs.autoStartMic
        cbSecretary.isChecked = prefs.aiSecretaryEnabled

        fun updateRouteUi(route: AudioRoute) {
            val activeBg = R.drawable.bg_ios_pill_active
            val normalBg = R.drawable.bg_ios_action_chip
            val activeText = Color.parseColor("#070A12")
            val normalText = Color.WHITE

            routeComputer.setBackgroundResource(if (route == AudioRoute.COMPUTER) activeBg else normalBg)
            routeComputer.setTextColor(if (route == AudioRoute.COMPUTER) activeText else normalText)

            routePhone.setBackgroundResource(if (route == AudioRoute.PHONE) activeBg else normalBg)
            routePhone.setTextColor(if (route == AudioRoute.PHONE) activeText else normalText)

            routeEarbuds.setBackgroundResource(if (route == AudioRoute.EARBUDS) activeBg else normalBg)
            routeEarbuds.setTextColor(if (route == AudioRoute.EARBUDS) activeText else normalText)
        }

        updateRouteUi(activeRoute)

        routeComputer.setOnClickListener {
            activeRoute = AudioRoute.COMPUTER
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }
        routePhone.setOnClickListener {
            activeRoute = AudioRoute.PHONE
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }
        routeEarbuds.setOnClickListener {
            activeRoute = AudioRoute.EARBUDS
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }

        // 31 Domains Adapter
        val domainAdapter = DomainAdapter(
            domains = JarvisDomainRegistry.allDomains,
            selectedDomainId = currentDomain.id,
            onDomainSelected = { domain ->
                applyDomain(domain, speakAnnouncement = true)
                dialog.dismiss()
            }
        )
        rvDomains.layoutManager = LinearLayoutManager(this)
        rvDomains.adapter = domainAdapter

        btnSave.setOnClickListener {
            prefs.serverAddress = etServer.text.toString().trim()
            prefs.autoStartMic = cbMic.isChecked
            prefs.aiSecretaryEnabled = cbSecretary.isChecked
            Toast.makeText(this, "Sozlamalar saqlandi", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyAudioRoute(route: AudioRoute) {
        prefs.audioRoute = route.name
        try {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_SET_ROUTE
                putExtra(JarvisForegroundService.EXTRA_ROUTE, route.name)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "applyAudioRoute error: ${e.message}")
        }
    }

    private fun addFeedEvent(event: LiveEvent) {
        // Filter out internal technical routing and raw status noise
        if (event.type == EventType.ACTION) {
            val title = event.title.lowercase()
            if (title.contains("audio_route") || title.contains("phone_") || title.contains("mic_")) {
                return
            }
        }
        runOnUiThread {
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
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA
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
