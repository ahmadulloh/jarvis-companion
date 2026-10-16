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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.jarvis.companion.audio.AudioRoute
import com.jarvis.companion.domain.DomainAdapter
import com.jarvis.companion.domain.DomainGridAdapter
import com.jarvis.companion.domain.JarvisDomain
import com.jarvis.companion.domain.JarvisDomainRegistry
import com.jarvis.companion.network.GeminiApiClient
import com.jarvis.companion.service.JarvisForegroundService
import com.jarvis.companion.ui.JarvisParticleState
import com.jarvis.companion.ui.JarvisParticleView
import com.jarvis.companion.util.PhoneActionManager
import com.jarvis.companion.util.PreferencesHelper
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesHelper
    private lateinit var phoneActionManager: PhoneActionManager
    private var vibrator: Vibrator? = null

    // ── Living Particle Intelligence Views ─────────────────────────────────
    private lateinit var webViewLivingParticle: WebView
    private lateinit var jarvisParticleView: JarvisParticleView
    private lateinit var rootContainer: View
    private lateinit var topBar: View
    private lateinit var tvBrandTitle: TextView
    private lateinit var chipDomainSelector: LinearLayout
    private lateinit var viewChipDot: View
    private lateinit var tvChipTitle: TextView
    private lateinit var cardCaptionContainer: View
    private lateinit var tvJarvisCaption: TextView
    private var captionDismissRunnable: Runnable? = null
    private val captionHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Optional bottom text input
    private lateinit var bottomBarContainer: View
    private lateinit var etCommandInput: EditText
    private lateinit var btnSendCommand: ImageButton

    // In-App WebView
    private lateinit var containerWebView: View
    private lateinit var webViewDashboard: WebView
    private lateinit var tvWebTitle: TextView
    private lateinit var btnOpenExternalBrowser: TextView
    private lateinit var btnCloseWebView: TextView
    private var quronWebUrl: String = ""

    // Active Domain & Route
    private var currentDomain: JarvisDomain = JarvisDomainRegistry.ASOSIY
    private var activeRoute: AudioRoute = AudioRoute.EARBUDS

    // Mobile Speech & TTS
    private var speechRecognizer: SpeechRecognizer? = null
    private var isVoiceListening = false
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Broadcast Receiver for Foreground Service
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
                        showCaption(text)
                        if (!speaker.equals("JARVIS", ignoreCase = true)) {
                            val detectedDomain = JarvisDomainRegistry.findDomainByVoice(text)
                            if (detectedDomain != null && detectedDomain.id != currentDomain.id) {
                                runOnUiThread {
                                    applyDomain(detectedDomain, speakAnnouncement = true)
                                }
                            }
                        }
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true

        setContentView(R.layout.activity_main)

        prefs = PreferencesHelper(this)
        phoneActionManager = PhoneActionManager(this)
        initVibrator()
        initViews()
        initSpeechRecognizer()
        initTextToSpeech()

        // Faol sohani yuklash
        val savedDomain = JarvisDomainRegistry.getDomainById(prefs.activeDomain)
        applyDomain(savedDomain, speakAnnouncement = false)

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

    private fun vibrateBrief(millis: Long = 30) {
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
        topBar = findViewById(R.id.topBar)
        jarvisParticleView = findViewById(R.id.jarvisParticleView)
        webViewLivingParticle = findViewById(R.id.webViewLivingParticle)
        setupLivingParticleWebView()

        tvBrandTitle = findViewById(R.id.tvBrandTitle)
        chipDomainSelector = findViewById(R.id.chipDomainSelector)
        viewChipDot = findViewById(R.id.viewChipDot)
        tvChipTitle = findViewById(R.id.tvChipTitle)
        cardCaptionContainer = findViewById(R.id.cardCaptionContainer)
        tvJarvisCaption = findViewById(R.id.tvJarvisCaption)

        cardCaptionContainer.setOnClickListener {
            vibrateBrief(15)
            showCaption("")
        }

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val density = resources.displayMetrics.density
            val topPadding = if (statusBarHeight > 0) {
                statusBarHeight + (10 * density).toInt()
            } else {
                (36 * density).toInt()
            }
            v.setPadding(v.paddingLeft, topPadding, v.paddingRight, (12 * density).toInt())
            insets
        }

        bottomBarContainer = findViewById(R.id.bottomBarContainer)
        etCommandInput = findViewById(R.id.etCommandInput)
        btnSendCommand = findViewById(R.id.btnSendCommand)

        containerWebView = findViewById(R.id.containerWebView)
        webViewDashboard = findViewById(R.id.webViewDashboard)
        tvWebTitle = findViewById(R.id.tvWebTitle)
        btnOpenExternalBrowser = findViewById(R.id.btnOpenExternalBrowser)
        btnCloseWebView = findViewById(R.id.btnCloseWebView)

        // Matn kiritish satri (Settings'da yoqilgan bo'lsa)
        bottomBarContainer.visibility = if (prefs.showTextInputBar) View.VISIBLE else View.GONE

        // Ekran va Zarralar bilan interaktiv aloqa
        jarvisParticleView.setOnClickListener {
            vibrateBrief()
            if (isVoiceListening) {
                stopMobileVoiceRecognition()
            } else {
                startMobileVoiceRecognition()
            }
        }

        // Yuqori o'ngdagi Chip bosilganda pastdan Sheet ochiladi
        chipDomainSelector.setOnClickListener {
            vibrateBrief()
            showDomainBottomSheet()
        }

        // Brand nomi bosilganda yoki uzoq bosilganda Sozlamalar ochiladi
        tvBrandTitle.setOnClickListener {
            vibrateBrief()
            showDomainBottomSheet()
        }
        tvBrandTitle.setOnLongClickListener {
            vibrateBrief()
            openSettingsDialog()
            true
        }

        // Matnli buyruq yuborish
        btnSendCommand.setOnClickListener {
            val txt = etCommandInput.text.toString().trim()
            if (txt.isNotEmpty()) {
                vibrateBrief()
                etCommandInput.setText("")
                sendJarvisCommand(txt)
            }
        }

        // WebView nazorati
        btnCloseWebView.setOnClickListener {
            containerWebView.visibility = View.GONE
            webViewDashboard.loadUrl("about:blank")
        }
        btnOpenExternalBrowser.setOnClickListener {
            if (quronWebUrl.isNotBlank()) {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(quronWebUrl))
                startActivity(intent)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLivingParticleWebView() {
        webViewLivingParticle.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webViewLivingParticle.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webViewLivingParticle.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webViewLivingParticle.evaluateJavascript("window.jarvisSetModeById('${currentDomain.id}');", null)
            }
        }
        webViewLivingParticle.webChromeClient = WebChromeClient()
        webViewLivingParticle.addJavascriptInterface(JarvisBridge(), "AndroidBridge")
        webViewLivingParticle.loadUrl("file:///android_asset/jarvis_ui.html")
    }

    inner class JarvisBridge {
        @android.webkit.JavascriptInterface
        fun onCanvasTap() {
            runOnUiThread {
                vibrateBrief(20)
                if (isVoiceListening) {
                    stopMobileVoiceRecognition()
                } else {
                    startMobileVoiceRecognition()
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onExecuteCommand(cmd: String, emoji: String) {
            runOnUiThread {
                vibrateBrief(20)
                sendJarvisCommand(cmd, emoji)
            }
        }

        @android.webkit.JavascriptInterface
        fun onDomainSelected(domainId: String, name: String, emoji: String) {
            runOnUiThread {
                vibrateBrief(25)
                val domain = JarvisDomainRegistry.getDomainById(domainId)
                applyDomain(domain, speakAnnouncement = true)
            }
        }

        @android.webkit.JavascriptInterface
        fun onSetReminderClicked(defaultReminderText: String) {
            runOnUiThread {
                vibrateBrief(20)
                showSetReminderDialog(defaultReminderText)
            }
        }

        @android.webkit.JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                vibrateBrief(20)
                openSettingsDialog()
            }
        }
    }

    private fun showSetReminderDialog(defaultReminderText: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_reminder_quick, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etTitle = view.findViewById<EditText>(R.id.etReminderTitle)
        val etTime = view.findViewById<EditText>(R.id.etReminderTime)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveReminder)
        val btnClose = view.findViewById<TextView>(R.id.btnCancelReminder)

        etTitle?.setText(defaultReminderText)
        btnClose?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            val title = etTitle?.text?.toString()?.trim() ?: defaultReminderText
            val time = etTime?.text?.toString()?.trim() ?: ""
            if (title.isNotEmpty()) {
                val res = phoneActionManager.setReminder(title, time)
                deliverJarvisResponseToUi(res, "⏰")
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun deliverJarvisResponseToUi(replyText: String, shapeEmoji: String = currentDomain.icon) {
        runOnUiThread {
            showCaption(replyText)
            val clean = replyText.replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").trim()
            val js = "window.jarvisDeliverResponse('$clean', '$shapeEmoji');"
            try {
                webViewLivingParticle.evaluateJavascript(js, null)
            } catch (_: Exception) {}
            jarvisParticleView.showResult(shapeEmoji, replyText)
            speakJarvis(replyText)
        }
    }

    // ── Matnli Status / Subtitle ko'rsatish (Floating Glass Capsule & Auto Fade) ──
    fun showCaption(text: String, durationMs: Long = 5000L) {
        runOnUiThread {
            captionDismissRunnable?.let { captionHandler.removeCallbacks(it) }
            val clean = text.trim()
            if (clean.isBlank()) {
                cardCaptionContainer.animate()
                    .alpha(0f)
                    .translationY(16f)
                    .setDuration(250)
                    .withEndAction { cardCaptionContainer.visibility = View.GONE }
                    .start()
            } else {
                tvJarvisCaption.text = clean
                cardCaptionContainer.visibility = View.VISIBLE
                cardCaptionContainer.translationY = 16f
                cardCaptionContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .start()

                // 5 soniyadan so'ng avtomatik mayin yo'qoladi (ekran yana toza galaktikaga aylanadi)
                val runnable = Runnable {
                    cardCaptionContainer.animate()
                        .alpha(0f)
                        .translationY(16f)
                        .setDuration(350)
                        .withEndAction { cardCaptionContainer.visibility = View.GONE }
                        .start()
                }
                captionDismissRunnable = runnable
                captionHandler.postDelayed(runnable, durationMs)
            }
        }
    }

    private fun getShortDomainName(domain: JarvisDomain): String {
        return when (domain.id.uppercase()) {
            "ASOSIY" -> "Asosiy"
            "HAYDOVCHI" -> "Taxi"
            "TALABA" -> "O'quvchi"
            "O'QITUVCHI", "OQITUVCHI" -> "O'qituvchi"
            "DIN" -> "Din"
            "DASTURCHI" -> "Dasturchi"
            "DOKON", "DO'KON" -> "Do'kon"
            "MOLIYA" -> "Moliya"
            "UY" -> "Aqlli Uy"
            "SALOMATLIK" -> "Salomatlik"
            "TIL" -> "Til"
            "ONLAYN_SAVDO" -> "Savdo"
            "KAFE" -> "Kafe"
            "SALON" -> "Salon"
            "AVTOUSTA" -> "Avtousta"
            "USTA" -> "Usta"
            "MULK" -> "Mulk"
            "FRILANSER" -> "Frilanser"
            "MENEJER" -> "Menejer"
            "HR" -> "HR"
            "YURIST" -> "Yurist"
            "IT_ADMIN" -> "IT-Admin"
            "KONTENT" -> "Kontent"
            "FOTO_VIDEO" -> "Media"
            "MUSIQA" -> "Musiqa"
            "GAMER" -> "Geymer"
            "FERMER" -> "Fermer"
            "OILA" -> "Oila"
            "KEKSA" -> "Keksa"
            "SPORT" -> "Sport"
            "SAYOHAT" -> "Sayohat"
            "HAYVON" -> "Hayvon"
            else -> domain.titleUz.split(" ", "/", "·").firstOrNull()?.trim() ?: domain.titleUz
        }
    }

    private fun buildDomainAnnouncement(domain: JarvisDomain): String {
        return when (domain.id.uppercase()) {
            "ASOSIY" -> "Asosiy neyron rejim faol, Axmadulloh sir! Qo'ng'iroqlar, xabarlar, eslatmalar va universal kiber-intellektga tayyorman."
            "HAYDOVCHI" -> "Haydovchi rejimi faol, Axmadulloh sir! To'liq qo'llarsiz boshqaruv, tirbandliklar, marshrut, yoqilg'i va xarajatlar hisobini yurita olaman."
            "TALABA" -> "Talaba rejimi faol, Axmadulloh sir! Dars jadvali, konspektlar, imtihon tayyorgarligi va audio ma'ruzalarni tahlil qilishga tayyorman."
            "O'QITUVCHI", "OQITUVCHI" -> "O'qituvchi rejimi faol, Axmadulloh sir! Dars ishlanmalari, test savollari va o'quvchilar baholarini nazorat qila olaman."
            "DIN" -> "Din va Qur'on rejimi faol, Axmadulloh sir! Namoz vaqtlari, Qur'on tilovati, tajvid darslari va ma'sur duolarni eslatib turaman."
            "DASTURCHI" -> "Dasturchi rejimi faol, Axmadulloh sir! Kod yozish, xatolarni tuzatish, arxitektura va API hujjatlarini tahlil qila olaman."
            "DOKON", "DO'KON" -> "Do'kon rejimi faol, Axmadulloh sir! Mahsulotlar qoldig'i, kassa hisobi va kunlik sotuvlarni nazorat qilaman."
            "MOLIYA" -> "Moliya rejimi faol, Axmadulloh sir! Daromad, xarajatlar, byudjet va moliyaviy hisobotlarni rejalashtiramiz."
            "UY" -> "Aqlli uy rejimi faol, Axmadulloh sir! Chiroqlar, harorat va xavfsizlik tizimlarini ovozda boshqara olaman."
            "SALOMATLIK" -> "Salomatlik rejimi faol, Axmadulloh sir! Suv ichish, dori qabul qilish va sog'lom turmush tarzingizni nazorat qilaman."
            "TIL" -> "Til o'rganish rejimi faol, Axmadulloh sir! Lug'at, grammatika va suhbat amaliyotida yordam beraman."
            "ONLAYN_SAVDO" -> "Onlayn savdo rejimi faol, Axmadulloh sir! Buyurtmalar, mijozlar bilan aloqa va yetkazib berishni boshqaramiz."
            "KAFE" -> "Kafe va restoran rejimi faol, Axmadulloh sir! Menyu boshqaruvi, buyurtmalar va mahsulotlar sarfini hisoblayman."
            "SALON" -> "Go'zallik saloni rejimi faol, Axmadulloh sir! Mijozlar qabuli, navbat va xizmatlar jadvalini yurita olaman."
            "AVTOUSTA" -> "Avtousta rejimi faol, Axmadulloh sir! Diagnostika, ehtiyot qismlar va texnik xizmat ko'rsatishni nazorat qilamiz."
            "USTA" -> "Usta rejimi faol, Axmadulloh sir! O'lchamlar, materiallar hisob-kitobi va buyurtmalar ro'yxatini yuritishda yordam beraman."
            "MULK" -> "Ko'chmas mulk rejimi faol, Axmadulloh sir! Obyektlar bazasi, ijara va mijozlar shartnomalarini nazorat qilaman."
            "FRILANSER" -> "Frilanser rejimi faol, Axmadulloh sir! Loyihalar muddatlari, soatbay hisob va mijozlar takliflarini yuritaman."
            "MENEJER" -> "Menejer rejimi faol, Axmadulloh sir! Jamoa vazifalari, reja va loyiha monitoringini amalga oshiramiz."
            "HR" -> "HR rejimi faol, Axmadulloh sir! Nomzodlar suhbati, rezyume saralash va xodimlar hisobini yurita olaman."
            "YURIST" -> "Yurist rejimi faol, Axmadulloh sir! Qonunchilik, shartnomalar tahlili va huquqiy maslahatlarni taqdim etaman."
            "IT_ADMIN" -> "IT-Administrator rejimi faol, Axmadulloh sir! Serverlar holati, tarmoq xavfsizligi va zaxira nusxalarni kuzataman."
            "KONTENT" -> "Kontent-meyker rejimi faol, Axmadulloh sir! Post g'oyalari, ssenariylar va media-rejalarni tuzib beraman."
            "FOTO_VIDEO" -> "Media rejimi faol, Axmadulloh sir! Kadrlash, ranglar tuzatish va video montaj g'oyalarida yordam beraman."
            "MUSIQA" -> "Musiqa rejimi faol, Axmadulloh sir! Akkordlar, matn yozish va tovush muhandisligida yordam beraman."
            "GAMER" -> "Geymer rejimi faol, Axmadulloh sir! O'yin strategiyalari, sozlamalar va tezkor maslahatlar beraman."
            "FERMER" -> "Fermer xo'jaligi rejimi faol, Axmadulloh sir! Sug'orish jadvali, ekinlar holati va hosil hisobini yurita olaman."
            "OILA" -> "Oila rejimi faol, Axmadulloh sir! Umumiy xaridlar, oilaviy bayramlar va rejalarni nazorat qilaman."
            "KEKSA" -> "Keksa parvarishi rejimi faol, Axmadulloh sir! Dori ichish vaqtlari, shifokor qabullari va salomatlikni nazorat qilaman."
            "SPORT" -> "Sport va fitnes rejimi faol, Axmadulloh sir! Mashg'ulotlar, taymer va kaloriyalar sarfini kuzatib boramiz."
            "SAYOHAT" -> "Sayohat rejimi faol, Axmadulloh sir! Chiptalar, mehmonxonalar va shahar yo'riqnomalarini tayyorlayman."
            "HAYVON" -> "Uy hayvonlari rejimi faol, Axmadulloh sir! Jonivorlar parvarishi, emlash va ovqatlantirish jadvalini nazorat qilaman."
            else -> "${domain.titleUz} rejimi faol, Axmadulloh sir! ${domain.description}"
        }
    }

    // ── Soha almashtirish (Theme, Zarralar rangi, Top chip) ────────────────
    private fun applyDomain(domain: JarvisDomain, speakAnnouncement: Boolean = false) {
        currentDomain = domain
        prefs.activeDomain = domain.id

        // Webview va Native Zarralar tizimini yangi soha rangiga o'tkazish
        try {
            webViewLivingParticle.evaluateJavascript("window.jarvisSetModeById('${domain.id}');", null)
        } catch (_: Exception) {}
        jarvisParticleView.setMode(domain)

        // Yuqori o'ngdagi Chip dizaynini yangilash (ixcham va qisqa nom)
        val density = resources.displayMetrics.density
        val shortName = getShortDomainName(domain)
        tvChipTitle.text = shortName
        tvChipTitle.setTextColor(domain.primaryColor)

        val chipBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * density
            setColor(Color.WHITE)
            setStroke((1.5f * density).toInt(), domain.primaryColor)
        }
        chipDomainSelector.background = chipBg

        val dotBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(domain.primaryColor)
        }
        viewChipDot.background = dotBg

        if (speakAnnouncement) {
            val announcement = buildDomainAnnouncement(domain)
            deliverJarvisResponseToUi(announcement, domain.icon)
        } else {
            showCaption("")
        }
    }

    // ── Bottom Sheet (31 ta Soha Tanlash) ──────────────────────────────────
    private fun showDomainBottomSheet() {
        vibrateBrief(20)
        val sheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_domain_picker, null)
        sheetDialog.setContentView(sheetView)

        val rv = sheetView.findViewById<RecyclerView>(R.id.rvDomainsGrid)
        val btnExpand = sheetView.findViewById<TextView>(R.id.btnExpandAllDomains)
        val etSearch = sheetView.findViewById<EditText>(R.id.etDomainSearch)
        val btnSettings = sheetView.findViewById<ImageButton>(R.id.btnSheetSettings)

        btnSettings?.setOnClickListener {
            sheetDialog.dismiss()
            openSettingsDialog()
        }

        val allDomains = JarvisDomainRegistry.getAllDomains()

        // 5 ta asosiy soha: Asosiy, Taxi, O'quvchi, O'qituvchi, Usta
        val top5 = listOfNotNull(
            JarvisDomainRegistry.ASOSIY,
            JarvisDomainRegistry.HAYDOVCHI,
            JarvisDomainRegistry.TALABA,
            allDomains.find { it.id.equals("OQITUVCHI", true) || it.titleUz.contains("O'qituvchi", true) } ?: allDomains.getOrNull(3),
            allDomains.find { it.id.equals("USTA", true) || it.titleUz.contains("Usta", true) } ?: allDomains.getOrNull(4)
        )

        val adapter = DomainGridAdapter(top5, currentDomain.id) { selected ->
            applyDomain(selected, speakAnnouncement = true)
            sheetDialog.dismiss()
        }

        rv?.layoutManager = GridLayoutManager(this, 3)
        rv?.adapter = adapter

        btnExpand?.setOnClickListener {
            btnExpand.visibility = View.GONE
            etSearch?.visibility = View.VISIBLE
            etSearch?.requestFocus()
            adapter.updateData(allDomains, currentDomain.id)
        }

        etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                val filtered = if (q.isEmpty()) {
                    allDomains
                } else {
                    allDomains.filter {
                        it.titleUz.contains(q, ignoreCase = true) ||
                                it.description.contains(q, ignoreCase = true) ||
                                it.keywords.any { kw -> kw.contains(q, ignoreCase = true) }
                    }
                }
                adapter.updateData(filtered, currentDomain.id)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        sheetDialog.show()
    }

    // ── Ovozli Qabul qilish (Speech Recognition) ───────────────────────────
    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isVoiceListening = true
                        try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('listen');", null) } catch (_: Exception) {}
                        jarvisParticleView.setState(JarvisParticleState.LISTENING)
                        showCaption("Eshitmoqdaman, Axmadulloh sir...")
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        try { webViewLivingParticle.evaluateJavascript("window.jarvisSetMicLevel($norm);", null) } catch (_: Exception) {}
                        jarvisParticleView.setMicLevel(norm)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isVoiceListening = false
                        try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('scan');", null) } catch (_: Exception) {}
                        jarvisParticleView.setMicLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.SEARCHING)
                        showCaption("Izlayapman…")
                    }
                    override fun onError(error: Int) {
                        isVoiceListening = false
                        try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('idle');", null) } catch (_: Exception) {}
                        jarvisParticleView.setMicLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.IDLE)
                        Log.d("MainActivity", "SpeechRecognizer onError code: $error")
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            showCaption("")
                        } else {
                            showCaption("Axmadulloh sir, buyruqni bosing yoki yozing")
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        isVoiceListening = false
                        jarvisParticleView.setMicLevel(0f)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spoken = matches[0]
                            sendJarvisCommand(spoken)
                        } else {
                            jarvisParticleView.setState(JarvisParticleState.IDLE)
                            showCaption("")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            showCaption("«${matches[0]}»")
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "SpeechRecognizer error: ${e.message}")
        }
    }

    private fun startMobileVoiceRecognition() {
        if (isVoiceListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }

        try {
            if (speechRecognizer == null) {
                initSpeechRecognizer()
            }
            if (speechRecognizer == null || !SpeechRecognizer.isRecognitionAvailable(this)) {
                // Agar qurilmada offline ovoz xizmati bo'lmasa, sinov buyrug'i bajariladi
                val demoCmd = if (currentDomain.actions.isNotEmpty()) currentDomain.actions[0].promptCommand else "Onamga telefon qil"
                showCaption("«$demoCmd»")
                sendJarvisCommand(demoCmd)
                return
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "uz-UZ")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
            isVoiceListening = true
            jarvisParticleView.setState(JarvisParticleState.LISTENING)
            showCaption("Eshitmoqdaman, Axmadulloh sir...")
        } catch (e: Exception) {
            Log.e("MainActivity", "startMobileVoiceRecognition error: ${e.message}")
            isVoiceListening = false
            jarvisParticleView.setState(JarvisParticleState.IDLE)
        }
    }

    private fun stopMobileVoiceRecognition() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isVoiceListening = false
        jarvisParticleView.setMicLevel(0f)
    }

    // ── Ovozli Sintez (TextToSpeech) ───────────────────────────────────────
    private fun prepareTextForSpeech(text: String): String {
        return text
            .replace(Regex("\\*\\*|\\*|#|_|`"), "")
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace("«", "").replace("»", "")
            .replace("\"", "").replace("'", "")
            // Normalize Uzbek vowels with apostrophes so TTS reads them fluently as single words without stuttering
            .replace("o‘", "o").replace("o’", "o").replace("o'", "o").replace("o`", "o")
            .replace("O‘", "O").replace("O’", "O").replace("O'", "O").replace("O`", "O")
            .replace("g‘", "g").replace("g’", "g").replace("g'", "g").replace("g`", "g")
            .replace("G‘", "G").replace("G’", "G").replace("G'", "G").replace("G`", "G")
            .replace("·", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ── Ovozli Sintez (TextToSpeech) ───────────────────────────────────────
    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                var selectedVoice = false
                try {
                    val uzVoice = tts?.voices?.find { it.locale.language.startsWith("uz", ignoreCase = true) }
                    if (uzVoice != null) {
                        tts?.voice = uzVoice
                        tts?.setLanguage(uzVoice.locale)
                        selectedVoice = true
                        Log.d("MainActivity", "Using native Uzbek TTS voice: ${uzVoice.name}")
                    }
                } catch (_: Exception) {}

                if (!selectedVoice) {
                    val trResult = tts?.setLanguage(Locale("tr"))
                    if (trResult != TextToSpeech.LANG_MISSING_DATA && trResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                        selectedVoice = true
                        Log.d("MainActivity", "Using Turkish TTS engine for fluent Uzbek Latin pronunciation")
                    }
                }

                if (!selectedVoice) {
                    val enResult = tts?.setLanguage(Locale.US)
                    if (enResult == TextToSpeech.LANG_MISSING_DATA || enResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                }

                try {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(attrs)
                } catch (_: Exception) {}

                // Deeper, masculine, resonant JARVIS pitch and steady articulation pace
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(0.95f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            jarvisParticleView.setState(JarvisParticleState.SPEAKING)
                            jarvisParticleView.setSpeechLevel(0.85f)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            jarvisParticleView.setSpeechLevel(0f)
                            jarvisParticleView.setState(JarvisParticleState.RELEASE)
                            showCaption("")
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            jarvisParticleView.setSpeechLevel(0f)
                            jarvisParticleView.setState(JarvisParticleState.IDLE)
                            showCaption("")
                        }
                    }
                })
            } else {
                Log.e("MainActivity", "TTS initialization failed status: $status")
            }
        }
    }

    private fun speakJarvis(text: String) {
        showCaption(text)
        if (!isTtsReady || tts == null) {
            return
        }

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol == 0 && maxVol > 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75f).toInt(), 0)
                }
            }

            val spokenText = prepareTextForSpeech(text)
            val utteranceId = "JARVIS_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e("MainActivity", "speakJarvis error: ${e.message}")
        }
    }

    // ── Buyruqni Bajarish (Gemini AI yoki PC Bridge) ────────────────────────
    private fun sendJarvisCommand(cmd: String, shapeEmoji: String = currentDomain.icon) {
        val detected = JarvisDomainRegistry.findDomainByVoice(cmd)
        if (detected != null && detected.id != currentDomain.id) {
            applyDomain(detected, speakAnnouncement = false)
        }

        showCaption("«$cmd»")
        try {
            webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('scan');", null)
        } catch (_: Exception) {}
        jarvisParticleView.setState(JarvisParticleState.SEARCHING)

        val lower = cmd.lowercase()
        // 1. Eslatmalar (Reminders / Alarms in all domains)
        if (lower.contains("eslat") || lower.contains("budilnik") || lower.contains("taymer") || lower.contains("reminder")) {
            val res = phoneActionManager.setReminder(cmd, "")
            deliverJarvisResponseToUi(res, "⏰")
            return
        }

        // 2. Qo'ng'iroqlar
        if (lower.startsWith("onamga telefon") || lower.contains("telefon qil") || lower.contains("qo'ng'iroq qil")) {
            val target = if (lower.contains("onam")) "onam" else cmd.replace(Regex("(?i)telefon qil|qo'ng'iroq qil"), "").trim()
            val res = phoneActionManager.makeCall(target)
            deliverJarvisResponseToUi(res, "📞")
            return
        }

        // 3. SMS xabarlar
        if (lower.contains("sms yoz") || lower.contains("sms yubor")) {
            val res = phoneActionManager.sendSms(cmd, "JARVIS xabari")
            deliverJarvisResponseToUi(res, "✉️")
            return
        }

        if (prefs.connectionMode == "GEMINI_DIRECT") {
            executeGeminiPrompt(cmd, shapeEmoji)
        } else {
            var sentToService = false
            try {
                val intent = Intent(this, JarvisForegroundService::class.java).apply {
                    action = JarvisForegroundService.ACTION_SEND_TEXT
                    putExtra(JarvisForegroundService.EXTRA_COMMAND_TEXT, cmd)
                }
                startService(intent)
                sentToService = true
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send command to service: ${e.message}")
            }
            if (!sentToService) {
                executeGeminiPrompt(cmd, shapeEmoji)
            }
        }
    }

    private fun executeGeminiPrompt(prompt: String, shapeEmoji: String = currentDomain.icon) {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            val guidance = "Axmadulloh sir, to'g'ridan-to'g'ri ishlash uchun Sozlamalardan Google Gemini API kalitini kiriting yoki kompyuterni ulang."
            deliverJarvisResponseToUi(guidance, "⚙️")
            return
        }

        try {
            webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('scan');", null)
        } catch (_: Exception) {}
        jarvisParticleView.setState(JarvisParticleState.SEARCHING)
        showCaption("Izlayapman…")

        lifecycleScope.launch {
            val result = GeminiApiClient.generateReply(
                apiKey = apiKey,
                userPrompt = prompt,
                activeDomain = "${currentDomain.id} (${currentDomain.titleUz})",
                userName = prefs.userName
            )
            result.onSuccess { reply ->
                deliverJarvisResponseToUi(reply, shapeEmoji)
            }.onFailure { err ->
                val errorMsg = "Xatolik: ${err.message ?: "Aloqa uzildi"}"
                deliverJarvisResponseToUi("Kechirasiz ser, so'rovni bajarishda xatolik yuz berdi.", "⚠️")
            }
        }
    }

    fun updateOrbState(state: String) {
        runOnUiThread {
            when (state.uppercase()) {
                "IDLE" -> {
                    jarvisParticleView.setState(JarvisParticleState.IDLE)
                    try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('idle');", null) } catch (_: Exception) {}
                }
                "LISTENING" -> {
                    jarvisParticleView.setState(JarvisParticleState.LISTENING)
                    try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('listen');", null) } catch (_: Exception) {}
                }
                "THINKING", "SEARCHING" -> {
                    jarvisParticleView.setState(JarvisParticleState.SEARCHING)
                    try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('scan');", null) } catch (_: Exception) {}
                }
                "SPEAKING" -> {
                    jarvisParticleView.setState(JarvisParticleState.SPEAKING)
                    try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('form');", null) } catch (_: Exception) {}
                }
                "RELEASE" -> {
                    jarvisParticleView.setState(JarvisParticleState.RELEASE)
                    try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('release');", null) } catch (_: Exception) {}
                }
                else -> jarvisParticleView.setState(JarvisParticleState.IDLE)
            }
        }
    }

    // ── Sozlamalar Dialogi ─────────────────────────────────────────────────
    private fun openSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_settings)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val btnClose = dialog.findViewById<TextView>(R.id.btnDialogClose)
        btnClose?.setOnClickListener { dialog.dismiss() }

        val btnPc = dialog.findViewById<TextView>(R.id.btnDialogRoutePc)
        val btnPhone = dialog.findViewById<TextView>(R.id.btnDialogRoutePhone)
        val btnEarbuds = dialog.findViewById<TextView>(R.id.btnDialogRouteEarbuds)

        val tvActiveLabel = dialog.findViewById<TextView>(R.id.tvSettingsActiveDomainLabel)
        val layoutActiveDomainRow = dialog.findViewById<View>(R.id.layoutSettingsActiveDomainRow)

        val shortName = getShortDomainName(currentDomain)
        tvActiveLabel?.text = "${currentDomain.icon} $shortName"
        tvActiveLabel?.setTextColor(currentDomain.primaryColor)

        layoutActiveDomainRow?.setOnClickListener {
            dialog.dismiss()
            showDomainBottomSheet()
        }

        val btnModeGemini = dialog.findViewById<TextView>(R.id.btnModeGemini)
        val btnModePcBridge = dialog.findViewById<TextView>(R.id.btnModePcBridge)
        val layoutGemini = dialog.findViewById<View>(R.id.layoutGeminiSettings)
        val layoutPc = dialog.findViewById<View>(R.id.layoutPcSettings)
        val etGeminiKey = dialog.findViewById<EditText>(R.id.etDialogGeminiKey)
        val cbShowTextInput = dialog.findViewById<CheckBox>(R.id.cbDialogShowTextInput)

        var selectedMode = prefs.connectionMode

        fun updateModeUi(mode: String) {
            val isGemini = mode == "GEMINI_DIRECT"
            if (isGemini) {
                btnModeGemini?.setBackgroundResource(R.drawable.bg_settings_pill_active)
                btnModeGemini?.setTextColor(Color.WHITE)
                btnModePcBridge?.setBackgroundColor(Color.TRANSPARENT)
                btnModePcBridge?.setTextColor(Color.parseColor("#64748B"))
            } else {
                btnModePcBridge?.setBackgroundResource(R.drawable.bg_settings_pill_active)
                btnModePcBridge?.setTextColor(Color.WHITE)
                btnModeGemini?.setBackgroundColor(Color.TRANSPARENT)
                btnModeGemini?.setTextColor(Color.parseColor("#64748B"))
            }

            layoutGemini?.visibility = if (isGemini) View.VISIBLE else View.GONE
            layoutPc?.visibility = if (!isGemini) View.VISIBLE else View.GONE
        }

        updateModeUi(selectedMode)

        btnModeGemini?.setOnClickListener {
            selectedMode = "GEMINI_DIRECT"
            updateModeUi(selectedMode)
        }

        btnModePcBridge?.setOnClickListener {
            selectedMode = "PC_BRIDGE"
            updateModeUi(selectedMode)
        }

        etGeminiKey?.setText(prefs.geminiApiKey)
        cbShowTextInput?.isChecked = prefs.showTextInputBar

        val etServer = dialog.findViewById<EditText>(R.id.etDialogServer)
        val etToken = dialog.findViewById<EditText>(R.id.etDialogToken)
        val cbBoot = dialog.findViewById<CheckBox>(R.id.cbDialogAutoStart)
        val cbQuron = dialog.findViewById<CheckBox>(R.id.cbDialogQuronAlways)
        val btnGrantNotif = dialog.findViewById<MaterialButton>(R.id.btnGrantNotificationAccess)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnDialogSave)

        etServer?.setText(prefs.serverAddress)
        etToken?.setText(prefs.authToken)
        cbBoot?.isChecked = prefs.autoStartOnBoot
        cbQuron?.isChecked = prefs.quronCardVisible

        btnGrantNotif?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Sozlamalarni ochishda xatolik", Toast.LENGTH_SHORT).show()
            }
        }

        fun updateRouteUi(route: AudioRoute) {
            btnPc?.setBackgroundColor(Color.TRANSPARENT)
            btnPc?.setTextColor(Color.parseColor("#64748B"))
            btnPhone?.setBackgroundColor(Color.TRANSPARENT)
            btnPhone?.setTextColor(Color.parseColor("#64748B"))
            btnEarbuds?.setBackgroundColor(Color.TRANSPARENT)
            btnEarbuds?.setTextColor(Color.parseColor("#64748B"))

            when (route) {
                AudioRoute.PC -> {
                    btnPc?.setBackgroundResource(R.drawable.bg_settings_pill_active)
                    btnPc?.setTextColor(Color.WHITE)
                }
                AudioRoute.PHONE -> {
                    btnPhone?.setBackgroundResource(R.drawable.bg_settings_pill_active)
                    btnPhone?.setTextColor(Color.WHITE)
                }
                AudioRoute.EARBUDS -> {
                    btnEarbuds?.setBackgroundResource(R.drawable.bg_settings_pill_active)
                    btnEarbuds?.setTextColor(Color.WHITE)
                }
            }
        }

        updateRouteUi(activeRoute)

        btnPc?.setOnClickListener {
            activeRoute = AudioRoute.PC
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }
        btnPhone?.setOnClickListener {
            activeRoute = AudioRoute.PHONE
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }
        btnEarbuds?.setOnClickListener {
            activeRoute = AudioRoute.EARBUDS
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }

        btnSave?.setOnClickListener {
            val newKey = etGeminiKey?.text?.toString()?.trim() ?: ""
            prefs.geminiApiKey = newKey
            prefs.connectionMode = selectedMode
            prefs.showTextInputBar = cbShowTextInput?.isChecked == true
            prefs.serverAddress = etServer?.text?.toString()?.trim() ?: prefs.serverAddress
            prefs.authToken = etToken?.text?.toString()?.trim() ?: prefs.authToken
            prefs.autoStartOnBoot = cbBoot?.isChecked == true
            prefs.quronCardVisible = cbQuron?.isChecked == true

            bottomBarContainer.visibility = if (prefs.showTextInputBar) View.VISIBLE else View.GONE
            applyAudioRoute(activeRoute)

            if (selectedMode == "PC_BRIDGE") {
                startJarvisService()
            }

            dialog.dismiss()
            Toast.makeText(this, "Sozlamalar saqlandi", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun applyAudioRoute(route: AudioRoute) {
        val intent = Intent(this, JarvisForegroundService::class.java).apply {
            action = JarvisForegroundService.ACTION_SET_ROUTE
            putExtra(JarvisForegroundService.EXTRA_ROUTE, route.name)
        }
        startService(intent)
    }

    // ── In-App WebView (Qur'on yoki Havolalar) ──────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun openQuronLesson(url: String) {
        containerWebView.visibility = View.VISIBLE
        tvWebTitle.text = "Qur'on Ustozi"

        webViewDashboard.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webViewDashboard.webViewClient = WebViewClient()
        webViewDashboard.webChromeClient = WebChromeClient()
        webViewDashboard.loadUrl(url)
    }

    // ── Ruxsatlar va Xizmatni Ishga Tushirish ──────────────────────────────
    private fun checkPermissionsAndStart() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            startJarvisService()
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
        stopMobileVoiceRecognition()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            webViewLivingParticle.removeJavascriptInterface("AndroidBridge")
            webViewLivingParticle.loadUrl("about:blank")
            webViewLivingParticle.destroy()
        } catch (_: Exception) {}
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
