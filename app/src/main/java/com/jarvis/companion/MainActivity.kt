package com.jarvis.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.PermissionRequest
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
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
import androidx.core.view.children
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
import com.jarvis.companion.network.UzbekVoiceApiClient
import com.jarvis.companion.network.ElevenLabsApiClient
import com.jarvis.companion.service.JarvisForegroundService
import com.jarvis.companion.ui.JarvisParticleState
import com.jarvis.companion.ui.JarvisParticleView
import com.jarvis.companion.util.PhoneActionManager
import com.jarvis.companion.util.PreferencesHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.inputmethod.InputMethodManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.io.File
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

    // Center Info under the Orb (Subtle status, single-line transcript, 1-2 line auto-fading caption)
    private lateinit var layoutCenterInfo: LinearLayout
    private lateinit var tvMicStatus: TextView
    private lateinit var tvInputTranscript: TextView
    private lateinit var tvJarvisCaption: TextView
    private var captionDismissRunnable: Runnable? = null
    private val captionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var activeTurnJarvisCaption: String = ""
    private var activeTurnUserTranscript: String = ""

    // Full Chat History (Shown only in pull-up Bottom Sheet)
    data class ChatHistoryMessage(val speaker: String, val text: String, val timestamp: Long = System.currentTimeMillis())
    private val fullChatHistory = mutableListOf<ChatHistoryMessage>()

    // Always-Visible Bottom Text Input Bar (Image 3 Style)
    private lateinit var bottomControlGroup: LinearLayout
    private lateinit var bottomBarContainer: LinearLayout
    private lateinit var btnPlusAttachment: ImageButton
    private lateinit var layoutAttachmentPreview: LinearLayout
    private lateinit var ivAttachmentPreviewThumb: ImageView
    private lateinit var tvAttachmentPreviewName: TextView
    private lateinit var btnRemoveAttachment: TextView
    private lateinit var etCommandInput: EditText
    private lateinit var btnToggleMic: ImageButton
    private lateinit var btnSendCommand: ImageButton
    private lateinit var btnFloatingKeyboard: ImageButton

    // On-Screen Live Chat Conversation View (Image 3 Style)
    private lateinit var scrollOnScreenChat: android.widget.ScrollView
    private lateinit var containerChatBubbles: LinearLayout

    // Pending attachment state
    private var pendingImageBitmap: Bitmap? = null
    private var pendingDocBytes: ByteArray? = null
    private var pendingDocName: String? = null
    private var pendingDocMime: String? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            pendingImageBitmap = bitmap
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val bytes = baos.toByteArray()
            val fileName = "kamera_foto_${System.currentTimeMillis()}.jpg"
            pendingDocBytes = bytes
            pendingDocName = fileName
            pendingDocMime = "image/jpeg"
            showAttachmentPreview(bitmap, fileName, isImage = true)
        }
    }

    private val pickDocumentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleSelectedDocumentUri(uri)
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleSelectedDocumentUri(uri)
        }
    }

    private var highResPhotoFile: java.io.File? = null
    private var highResPhotoUri: Uri? = null

    private val cameraAiFullPictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && highResPhotoFile != null && highResPhotoFile!!.exists()) {
            try {
                val boundsOpts = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(highResPhotoFile!!.absolutePath, boundsOpts)

                var inSampleSize = 1
                val maxDim = 1600
                while (boundsOpts.outWidth / (inSampleSize * 2) >= maxDim || boundsOpts.outHeight / (inSampleSize * 2) >= maxDim) {
                    inSampleSize *= 2
                }

                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                val bitmap = android.graphics.BitmapFactory.decodeFile(highResPhotoFile!!.absolutePath, decodeOpts)
                if (bitmap != null) {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                    val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                    runOnUiThread {
                        webViewDashboard.evaluateJavascript("if (window.onNativePhotoCaptured) { window.onNativePhotoCaptured('$b64'); }", null)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "cameraAiFullPictureLauncher error: ${e.message}")
                runOnUiThread {
                    webViewDashboard.evaluateJavascript("if (window.onNativePhotoCancelled) { window.onNativePhotoCancelled(); }", null)
                }
            }
        } else {
            runOnUiThread {
                webViewDashboard.evaluateJavascript("if (window.onNativePhotoCancelled) { window.onNativePhotoCancelled(); }", null)
            }
        }
    }

    private fun launchCameraCapture() {
        try {
            val photoFile = java.io.File(cacheDir, "camera_ai_photo.jpg")
            if (photoFile.exists()) photoFile.delete()
            photoFile.createNewFile()
            highResPhotoFile = photoFile
            highResPhotoUri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            cameraAiFullPictureLauncher.launch(highResPhotoUri)
        } catch (e: Exception) {
            Log.e("MainActivity", "launchCameraCapture error, fallback to preview: ${e.message}")
            cameraAiPictureLauncher.launch(null)
        }
    }

    private val cameraAiPictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                runOnUiThread {
                    webViewDashboard.evaluateJavascript("if (window.onNativePhotoCaptured) { window.onNativePhotoCaptured('$b64'); }", null)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "cameraAiPictureLauncher error: ${e.message}")
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCameraCapture()
        } else {
            Toast.makeText(this, "Kamera ruxsati berilmadi", Toast.LENGTH_SHORT).show()
        }
    }

    // In-App WebView
    private lateinit var containerWebView: View
    private lateinit var webViewDashboard: WebView
    private lateinit var tvWebTitle: TextView
    private lateinit var btnOpenExternalBrowser: TextView
    private lateinit var btnCloseWebView: TextView
    private var quronWebUrl: String = ""
    private var isTvFullscreen = false
    private var isQuronActive = false
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Inline Media Player Views (Image 1 Style)
    private lateinit var cardInlineMedia: LinearLayout
    private lateinit var cardVideoFrame: androidx.cardview.widget.CardView
    private lateinit var layoutVideoContainer: FrameLayout
    private lateinit var webViewInlineVideo: WebView
    private lateinit var tvMediaVideoTitle: TextView
    private lateinit var btnCloseInlineMedia: TextView
    private lateinit var btnExpandInlineMedia: TextView
    private lateinit var ivMediaThumb: ImageView
    private lateinit var tvMediaSongTitle: TextView
    private lateinit var tvMediaArtist: TextView
    private lateinit var btnMediaLike: ImageView
    private lateinit var btnMediaPrev: ImageButton
    private lateinit var btnMediaPlayPause: ImageButton
    private lateinit var btnMediaNext: ImageButton
    private var isMediaPlaying = true
    private var isMediaLiked = false
    private var isVideoExpanded = false
    private var inlineMediaInitialLp: android.widget.RelativeLayout.LayoutParams? = null

    // Active Domain & Route
    private var currentDomain: JarvisDomain = JarvisDomainRegistry.ASOSIY
    private var activeRoute: AudioRoute = AudioRoute.PHONE

    // Mobile Speech & TTS
    private var speechRecognizer: SpeechRecognizer? = null
    private var isVoiceListening = false
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var activeSpeechJob: Job? = null

    // Umid (Erkak) & Umida (Ayol) Dynamic Voice Persona
    private var activeAssistantPersona = "kore" // "umid" (Erkak) | "umida" (Ayol)

    // AI Kotib ovozli tasdiq saqlovi
    private var pendingSecretaryTarget: String? = null
    private var pendingSecretaryMessage: String? = null

    // Broadcast Receiver for Foreground Service
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                JarvisForegroundService.BROADCAST_STATE_CHANGE -> {
                    val state = intent.getStringExtra(JarvisForegroundService.EXTRA_STATE) ?: return
                    updateOrbState(state)
                    updateMicStatusBadge(state)
                }
                JarvisForegroundService.BROADCAST_RMS -> {
                    val rmsNorm = intent.getFloatExtra(JarvisForegroundService.EXTRA_RMS, 0f)
                    jarvisParticleView.setMicLevel(rmsNorm)
                    try {
                        webViewLivingParticle.evaluateJavascript("window.jarvisSetMicLevel($rmsNorm);", null)
                    } catch (_: Exception) {}
                }
                JarvisForegroundService.BROADCAST_INPUT_TRANSCRIPT -> {
                    val transcript = intent.getStringExtra(JarvisForegroundService.EXTRA_TRANSCRIPT) ?: ""
                    if (transcript.isNotBlank()) {
                        showInputTranscript(transcript)
                    }
                }
                JarvisForegroundService.BROADCAST_CAPTION -> {
                    val caption = intent.getStringExtra(JarvisForegroundService.EXTRA_CAPTION) ?: ""
                    if (caption.isNotBlank()) {
                        showJarvisTurnCaption(caption)
                    }
                }
                JarvisForegroundService.BROADCAST_TURN_COMPLETE -> {
                    onTurnCompleted()
                }
                JarvisForegroundService.BROADCAST_PC_CONNECTION_FAILED -> {
                    val errorMsg = intent.getStringExtra(JarvisForegroundService.EXTRA_PC_ERROR) ?: "Tarmoq xatosi"
                    handlePcConnectionFailed(errorMsg)
                }
                JarvisForegroundService.BROADCAST_CHAT_LOG -> {
                    val speaker = intent.getStringExtra(JarvisForegroundService.EXTRA_SPEAKER) ?: ""
                    val text = intent.getStringExtra(JarvisForegroundService.EXTRA_TEXT) ?: ""
                    if (text.isNotBlank()) {
                        if (speaker.equals("user", ignoreCase = true)) {
                            showInputTranscript(text)
                        } else {
                            showJarvisTurnCaption(text)
                            onTurnCompleted()
                        }
                        if (!speaker.equals("JARVIS", ignoreCase = true)) {
                            val detectedDomain = JarvisDomainRegistry.findDomainByVoice(text)
                            if (detectedDomain != null && detectedDomain.id != currentDomain.id) {
                                runOnUiThread {
                                    applyDomain(detectedDomain, speakAnnouncement = false)
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
                    val url = intent.getStringExtra(JarvisForegroundService.EXTRA_QURON_URL)
                        ?: intent.getStringExtra("url")
                        ?: "https://jarvis.77.83.206.138.sslip.io/quron"
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
                JarvisForegroundService.BROADCAST_WORLD_TV_UPDATE -> {
                    val url = intent.getStringExtra(JarvisForegroundService.EXTRA_WORLD_TV_URL)
                        ?: intent.getStringExtra("url")
                        ?: "https://jarvis.77.83.206.138.sslip.io/tv"
                    if (url.isNotEmpty()) {
                        runOnUiThread {
                            openWorldTv(url)
                        }
                    }
                }
                JarvisForegroundService.BROADCAST_DOMAIN_CHANGE -> {
                    val dId = intent.getStringExtra(JarvisForegroundService.EXTRA_DOMAIN_ID) ?: "ASOSIY"
                    val target = JarvisDomainRegistry.getDomainById(dId)
                    runOnUiThread {
                        applyDomain(target, speakAnnouncement = false)
                    }
                }

                JarvisForegroundService.BROADCAST_MEDIA_PLAY -> {
                    val url = intent.getStringExtra(JarvisForegroundService.EXTRA_MEDIA_URL) ?: ""
                    val title = intent.getStringExtra(JarvisForegroundService.EXTRA_MEDIA_TITLE) ?: "YouTube"
                    runOnUiThread {
                        playInAppYouTube(url, title)
                    }
                }
                JarvisForegroundService.BROADCAST_MEDIA_PAUSE -> {
                    val paused = intent.getBooleanExtra(JarvisForegroundService.EXTRA_MEDIA_PAUSED, true)
                    runOnUiThread {
                        isMediaPlaying = !paused
                        btnMediaPlayPause.setImageResource(if (isMediaPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play)
                        val func = if (isMediaPlaying) "playVideo" else "pauseVideo"
                        webViewInlineVideo.evaluateJavascript("sendCmd('$func');", null)
                        showCaption(if (paused) "⏸ Pauza qilindi" else "▶ Ijro davom etmoqda")
                    }
                }
                JarvisForegroundService.BROADCAST_MEDIA_STOP -> {
                    runOnUiThread {
                        if (isVideoExpanded) {
                            toggleVideoFullscreen()
                        }
                        cardInlineMedia.visibility = View.GONE
                        webViewInlineVideo.loadUrl("about:blank")
                        isMediaPlaying = false
                        showCaption("⏹ Ijro to'xtatildi")
                    }
                }
                JarvisForegroundService.BROADCAST_MEDIA_NEXT -> {
                    runOnUiThread {
                        webViewInlineVideo.evaluateJavascript("sendCmd('seekTo', [999999, true]);", null)
                    }
                }
                JarvisForegroundService.BROADCAST_MEDIA_PREV -> {
                    runOnUiThread {
                        webViewInlineVideo.evaluateJavascript("sendCmd('seekTo', [0, true]);", null)
                    }
                }
                JarvisForegroundService.BROADCAST_CONNECTION_GREETING -> {
                    runOnUiThread {
                        triggerWelcomeGreetingIfNeeded()
                    }
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val recordAudioGranted = results[Manifest.permission.RECORD_AUDIO] == true
        val callPhoneGranted = results[Manifest.permission.CALL_PHONE] == true
        val readContactsGranted = results[Manifest.permission.READ_CONTACTS] == true
        val readPhoneStateGranted = results[Manifest.permission.READ_PHONE_STATE] == true

        if (recordAudioGranted) {
            startJarvisService()
        } else {
            Toast.makeText(this, "JARVIS ishlashi uchun mikrofon ruxsati zarur", Toast.LENGTH_LONG).show()
        }

        val missing = mutableListOf<String>()
        if (!callPhoneGranted) missing.add("Telefon qo'ng'iroqlari (CALL_PHONE)")
        if (!readContactsGranted) missing.add("Kontaktlar (READ_CONTACTS)")
        if (!readPhoneStateGranted) missing.add("Telefon holati (READ_PHONE_STATE)")

        if (missing.isNotEmpty()) {
            val warn = "Axmadulloh sir, to'liq ishlashi uchun ruxsatlar kerak: ${missing.joinToString(", ")}. Sozlamalardan ruxsat berishingiz mumkin."
            Toast.makeText(this, warn, Toast.LENGTH_LONG).show()
            speakJarvis("Diqqat, telefon qo'ng'iroqlari va kontaktlar ruxsati berilmadi. Iltimos, sozlamalardan ruxsat bering.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)


        prefs = PreferencesHelper(this)
        phoneActionManager = PhoneActionManager(this)
        activeRoute = try { AudioRoute.valueOf(prefs.audioRoute) } catch (_: Exception) { AudioRoute.PHONE }
        initVibrator()
        checkStartupCrashLog()
        initViews()
        initSpeechRecognizer()
        initTextToSpeech()
        loadDailySchedule()
        loadReminders()
        loadChatHistory()
        cleanOldTemporaryFiles()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val cardQuickMenu = findViewById<View>(R.id.cardJarvisQuickMenu)
                if (cardQuickMenu?.visibility == View.VISIBLE) {
                    cardQuickMenu.visibility = View.GONE
                    return
                }
                if (customView != null) {
                    exitCustomViewFullscreen()
                    return
                }
                if (isTvFullscreen) {
                    setTvFullscreen(false)
                    webViewDashboard.evaluateJavascript("if (window.toggleFullscreen && document.getElementById('videoContainer')?.classList.contains('fullscreen-expanded')) window.toggleFullscreen();", null)
                    return
                }
                if (isVideoExpanded) {
                    toggleVideoFullscreen()
                } else if (containerWebView.visibility == View.VISIBLE) {
                    val title = tvWebTitle.text?.toString() ?: ""
                    isQuronActive = false
                    containerWebView.visibility = View.GONE
                    webViewDashboard.loadUrl("about:blank")
                    when {
                        title.contains("Kamera", ignoreCase = true) -> showCaption("📷 Kamera yopildi")
                        title.contains("Qur'on", ignoreCase = true) -> showCaption("📖 Qur'on Ustozi yopildi")
                        else -> showCaption("⏹ Telekanal yopildi")
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Faol sohani yuklash
        val savedDomain = JarvisDomainRegistry.getDomainById(prefs.activeDomain)
        applyDomain(savedDomain, speakAnnouncement = false)

        checkPermissionsAndStart()

        window.decorView.postDelayed({
            triggerWelcomeGreetingIfNeeded()
        }, 1200)
    }

    private fun checkStartupCrashLog() {
        try {
            val crashPrefs = getSharedPreferences("jarvis_crash_log", Context.MODE_PRIVATE)
            val lastCrash = crashPrefs.getString("last_crash", null)
            if (!lastCrash.isNullOrBlank()) {
                Log.w("MainActivity", "Previous crash detected:\n$lastCrash")
                crashPrefs.edit().remove("last_crash").apply()
            }
        } catch (_: Exception) {}
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
        rootContainer.keepScreenOn = true
        topBar = findViewById(R.id.topBar)
        jarvisParticleView = findViewById(R.id.jarvisParticleView)
        webViewLivingParticle = findViewById(R.id.webViewLivingParticle)
        setupLivingParticleWebView()

        tvBrandTitle = findViewById(R.id.tvBrandTitle)
        chipDomainSelector = findViewById(R.id.chipDomainSelector)
        viewChipDot = findViewById(R.id.viewChipDot)
        tvChipTitle = findViewById(R.id.tvChipTitle)

        // Center Info under the central Orb
        layoutCenterInfo = findViewById(R.id.layoutCenterInfo)
        tvMicStatus = findViewById(R.id.tvMicStatus)
        tvInputTranscript = findViewById(R.id.tvInputTranscript)
        tvJarvisCaption = findViewById(R.id.tvJarvisCaption)

        tvMicStatus.setOnClickListener {
            toggleMicFromUi()
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

        // Bottom Bar & Controls (Image 3 Style)
        bottomControlGroup = findViewById(R.id.bottomControlGroup)
        bottomBarContainer = findViewById(R.id.bottomBarContainer)
        btnPlusAttachment = findViewById(R.id.btnPlusAttachment)
        layoutAttachmentPreview = findViewById(R.id.layoutAttachmentPreview)
        ivAttachmentPreviewThumb = findViewById(R.id.ivAttachmentPreviewThumb)
        tvAttachmentPreviewName = findViewById(R.id.tvAttachmentPreviewName)
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment)
        etCommandInput = findViewById(R.id.etCommandInput)
        btnToggleMic = findViewById(R.id.btnToggleMic)
        btnSendCommand = findViewById(R.id.btnSendCommand)
        btnFloatingKeyboard = findViewById(R.id.btnFloatingKeyboard)

        // On-Screen Live Chat Conversation View (Image 3 Style)
        scrollOnScreenChat = findViewById(R.id.scrollOnScreenChat)
        containerChatBubbles = findViewById(R.id.containerChatBubbles)

        btnPlusAttachment.setOnClickListener {
            vibrateBrief(15)
            showAttachmentSourceChooser()
        }

        btnRemoveAttachment.setOnClickListener {
            vibrateBrief(15)
            clearPendingAttachment()
        }

        // WindowInsets for bottomControlGroup: adjust for navigation bar and IME keyboard
        ViewCompat.setOnApplyWindowInsetsListener(bottomControlGroup) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottomMargin = if (imeHeight > 0) {
                imeHeight + (8 * resources.displayMetrics.density).toInt()
            } else {
                navHeight + (12 * resources.displayMetrics.density).toInt()
            }
            val lp = v.layoutParams as? android.widget.RelativeLayout.LayoutParams
            if (lp != null) {
                lp.bottomMargin = bottomMargin
                v.layoutParams = lp
            }
            insets
        }

        val showInput = prefs.showTextInputBar
        bottomControlGroup.visibility = if (showInput) View.VISIBLE else View.GONE
        btnFloatingKeyboard.visibility = if (showInput) View.GONE else View.VISIBLE

        btnFloatingKeyboard.setOnClickListener {
            vibrateBrief(15)
            bottomControlGroup.visibility = View.VISIBLE
            btnFloatingKeyboard.visibility = View.GONE
            etCommandInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etCommandInput, InputMethodManager.SHOW_IMPLICIT)
        }

        btnToggleMic.setOnClickListener {
            toggleMicFromUi()
        }

        containerWebView = findViewById(R.id.containerWebView)
        webViewDashboard = findViewById(R.id.webViewDashboard)
        tvWebTitle = findViewById(R.id.tvWebTitle)
        btnOpenExternalBrowser = findViewById(R.id.btnOpenExternalBrowser)
        btnCloseWebView = findViewById(R.id.btnCloseWebView)

        // Enter bosilganda yuborish
        etCommandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                handleSendAction()
                true
            } else {
                false
            }
        }

        etCommandInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showChatContainerSmoothly()
            }
        }

        etCommandInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    showChatContainerSmoothly()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Ekran va Zarralar bilan interaktiv aloqa — mikrofonni boshqaradi
        jarvisParticleView.setOnClickListener {
            toggleMicFromUi()
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

        // — — — Menu Button under Jarvis Brand Title (Opens Qur'on AI, Barcha TV, Eslatmalar)
        val btnJarvisMenuDots = findViewById<View>(R.id.btnJarvisMenuDots)
        val cardJarvisQuickMenu = findViewById<View>(R.id.cardJarvisQuickMenu)
        val btnCloseQuickMenu = findViewById<View>(R.id.btnCloseQuickMenu)
        val itemMenuQuronAi = findViewById<View>(R.id.itemMenuQuronAi)
        val itemMenuBarchaTv = findViewById<View>(R.id.itemMenuBarchaTv)
        val itemMenuEslatmalar = findViewById<View>(R.id.itemMenuEslatmalar)
        val itemMenuKunlikReja = findViewById<View>(R.id.itemMenuKunlikReja)
        val itemMenuKameraAI = findViewById<View>(R.id.itemMenuKameraAI)

        fun toggleQuickMenu() {
            vibrateBrief(20)
            if (cardJarvisQuickMenu?.visibility == View.VISIBLE) {
                cardJarvisQuickMenu.visibility = View.GONE
            } else {
                cardJarvisQuickMenu?.visibility = View.VISIBLE
            }
        }
        btnJarvisMenuDots?.setOnClickListener { toggleQuickMenu() }
        btnCloseQuickMenu?.setOnClickListener {
            vibrateBrief(15)
            cardJarvisQuickMenu?.visibility = View.GONE
        }
        itemMenuQuronAi?.setOnClickListener {
            vibrateBrief(25)
            cardJarvisQuickMenu?.visibility = View.GONE
            openQuronLesson("https://jarvis.77.83.206.138.sslip.io/quron")
        }
        itemMenuBarchaTv?.setOnClickListener {
            vibrateBrief(25)
            cardJarvisQuickMenu?.visibility = View.GONE
            openWorldTv("https://jarvis.77.83.206.138.sslip.io/tv")
        }
        itemMenuEslatmalar?.setOnClickListener {
            vibrateBrief(25)
            cardJarvisQuickMenu?.visibility = View.GONE
            showEslatmalarSheet()
        }
        itemMenuKunlikReja?.setOnClickListener {
            vibrateBrief(25)
            cardJarvisQuickMenu?.visibility = View.GONE
            showKunlikRejaSheet()
        }
        itemMenuKameraAI?.setOnClickListener {
            vibrateBrief(25)
            cardJarvisQuickMenu?.visibility = View.GONE
            openKameraAI()
        }

        // Matnli buyruq yoki biriktirilgan fayl yuborish
        btnSendCommand.setOnClickListener {
            handleSendAction()
        }

        // WebView nazorati
        btnCloseWebView.setOnClickListener {
            if (customView != null) {
                exitCustomViewFullscreen()
            }
            if (isTvFullscreen) {
                setTvFullscreen(false)
            }
            val title = tvWebTitle.text?.toString() ?: ""
            isQuronActive = false
            containerWebView.visibility = View.GONE
            webViewDashboard.loadUrl("about:blank")
            when {
                title.contains("Kamera", ignoreCase = true) -> showCaption("📷 Kamera yopildi")
                title.contains("Qur'on", ignoreCase = true) -> showCaption("📖 Qur'on Ustozi yopildi")
                else -> showCaption("⏹ Telekanal yopildi")
            }
        }
        btnOpenExternalBrowser.setOnClickListener {
            if (quronWebUrl.isNotBlank()) {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(quronWebUrl))
                startActivity(intent)
            }
        }

        // Inline Media Player nazorati (Image 1 Style)
        cardInlineMedia = findViewById(R.id.cardInlineMedia)
        cardVideoFrame = findViewById(R.id.cardVideoFrame)
        layoutVideoContainer = findViewById(R.id.layoutVideoContainer)
        webViewInlineVideo = findViewById(R.id.webViewInlineVideo)
        tvMediaVideoTitle = findViewById(R.id.tvMediaVideoTitle)
        btnCloseInlineMedia = findViewById(R.id.btnCloseInlineMedia)
        btnExpandInlineMedia = findViewById(R.id.btnExpandInlineMedia)
        ivMediaThumb = findViewById(R.id.ivMediaThumb)
        tvMediaSongTitle = findViewById(R.id.tvMediaSongTitle)
        tvMediaArtist = findViewById(R.id.tvMediaArtist)
        btnMediaLike = findViewById(R.id.btnMediaLike)
        btnMediaPrev = findViewById(R.id.btnMediaPrev)
        btnMediaPlayPause = findViewById(R.id.btnMediaPlayPause)
        btnMediaNext = findViewById(R.id.btnMediaNext)

        btnExpandInlineMedia.setOnClickListener {
            vibrateBrief(20)
            toggleVideoFullscreen()
        }

        btnCloseInlineMedia.setOnClickListener {
            vibrateBrief(20)
            if (isVideoExpanded) {
                toggleVideoFullscreen()
            }
            cardInlineMedia.visibility = View.GONE
            webViewInlineVideo.loadUrl("about:blank")
        }

        btnMediaPlayPause.setOnClickListener {
            vibrateBrief(25)
            isMediaPlaying = !isMediaPlaying
            btnMediaPlayPause.setImageResource(if (isMediaPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play)
            val func = if (isMediaPlaying) "playVideo" else "pauseVideo"
            webViewInlineVideo.evaluateJavascript("sendCmd('$func');", null)
        }

        btnMediaLike.setOnClickListener {
            vibrateBrief(25)
            isMediaLiked = !isMediaLiked
            btnMediaLike.setImageResource(if (isMediaLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        }

        btnMediaPrev.setOnClickListener {
            vibrateBrief(20)
            webViewInlineVideo.evaluateJavascript("sendCmd('seekTo', [0, true]);", null)
        }

        btnMediaNext.setOnClickListener {
            vibrateBrief(20)
            webViewInlineVideo.evaluateJavascript("sendCmd('seekTo', [999999, true]);", null)
        }
    }

    private fun toggleVideoFullscreen() {
        isVideoExpanded = !isVideoExpanded
        val density = resources.displayMetrics.density

        if (isVideoExpanded) {
            if (inlineMediaInitialLp == null) {
                inlineMediaInitialLp = android.widget.RelativeLayout.LayoutParams(cardInlineMedia.layoutParams as android.widget.RelativeLayout.LayoutParams)
            }
            val fullLp = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(0, 0, 0, 0)
            }
            cardInlineMedia.layoutParams = fullLp
            cardInlineMedia.setPadding(0, 0, 0, 0)
            cardVideoFrame.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            cardVideoFrame.radius = 0f
            btnExpandInlineMedia.text = "🗗"
            bottomControlGroup.visibility = View.GONE
            topBar.visibility = View.GONE
        } else {
            val restoredLp = inlineMediaInitialLp ?: android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(android.widget.RelativeLayout.ABOVE, R.id.bottomControlGroup)
                setMargins((14 * density).toInt(), 0, (14 * density).toInt(), (10 * density).toInt())
            }
            cardInlineMedia.layoutParams = restoredLp
            val pad = (8 * density).toInt()
            cardInlineMedia.setPadding(pad, pad, pad, pad)
            cardVideoFrame.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (195 * density).toInt()
            )
            cardVideoFrame.radius = 20 * density
            btnExpandInlineMedia.text = "⛶"
            bottomControlGroup.visibility = if (prefs.showTextInputBar) View.VISIBLE else View.GONE
            topBar.visibility = View.VISIBLE
        }
    }

    private fun handleSendAction() {
        val txt = etCommandInput.text.toString().trim()
        if (pendingDocBytes != null) {
            vibrateBrief()
            sendPendingAttachmentWithPrompt(txt)
        } else if (txt.isNotEmpty()) {
            vibrateBrief()
            etCommandInput.setText("")
            addUserChatBubble(txt, null, null)
            sendJarvisCommand(txt)
        }
    }

    private fun showAttachmentSourceChooser() {
        val items = arrayOf(
            "📸 Kameradan rasmga olish",
            "🖼️ Galereyadan rasm tanlash",
            "📄 Hujjat yuklash (PDF, Word, Excel, TXT)"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Fayl yoki rasm yuklash")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
                        } else {
                            takePictureLauncher.launch(null)
                        }
                    }
                    1 -> {
                        pickImageLauncher.launch("image/*")
                    }
                    2 -> {
                        pickDocumentLauncher.launch("*/*")
                    }
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun handleSelectedDocumentUri(uri: Uri) {
        try {
            var fileName = "hujjat"
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = it.getString(nameIndex) ?: "hujjat"
                    }
                }
            }

            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null && bytes.isNotEmpty()) {
                val lowerName = fileName.lowercase()
                if (mime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp")) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        pendingImageBitmap = bmp
                        pendingDocBytes = bytes
                        pendingDocName = fileName
                        pendingDocMime = mime
                        showAttachmentPreview(bmp, fileName, isImage = true)
                        return
                    }
                }

                pendingImageBitmap = null
                pendingDocBytes = bytes
                pendingDocName = fileName
                pendingDocMime = mime
                showAttachmentPreview(null, fileName, isImage = false)
            } else {
                Toast.makeText(this, "Fayl bo'sh", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "handleSelectedDocumentUri error: ${e.message}")
            Toast.makeText(this, "Faylni yuklab bo'lmadi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAttachmentPreview(bitmap: Bitmap?, fileName: String, isImage: Boolean) {
        layoutAttachmentPreview.visibility = View.VISIBLE
        tvAttachmentPreviewName.text = fileName
        if (isImage && bitmap != null) {
            ivAttachmentPreviewThumb.setImageBitmap(bitmap)
        } else {
            ivAttachmentPreviewThumb.setImageResource(R.drawable.ic_document)
        }
        etCommandInput.requestFocus()
    }

    private fun clearPendingAttachment() {
        pendingImageBitmap = null
        pendingDocBytes = null
        pendingDocName = null
        pendingDocMime = null
        layoutAttachmentPreview.visibility = View.GONE
    }

    private fun showChatContainerSmoothly() {
        runOnUiThread {
            if (scrollOnScreenChat.visibility != View.VISIBLE) {
                scrollOnScreenChat.alpha = 0f
                scrollOnScreenChat.visibility = View.VISIBLE
                scrollOnScreenChat.animate().alpha(1f).setDuration(250).start()
            } else if (scrollOnScreenChat.alpha < 1f) {
                scrollOnScreenChat.animate().alpha(1f).setDuration(200).start()
            }
        }
    }

    private fun hideChatContainerSmoothly() {
        runOnUiThread {
            if (scrollOnScreenChat.visibility == View.VISIBLE && etCommandInput.text.isNullOrBlank() && !etCommandInput.hasFocus()) {
                scrollOnScreenChat.animate().alpha(0f).setDuration(350).withEndAction {
                    scrollOnScreenChat.visibility = View.GONE
                }.start()
            }
        }
    }

    private fun formatMarkdownToSpanned(text: String): CharSequence {
        try {
            var html = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            // **bold**
            html = html.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            // *italic*
            html = html.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")
            // `code`
            html = html.replace(Regex("`([^`]+)`"), "<tt><font color='#0F5132'><b>$1</b></font></tt>")
            // Linebreaks
            html = html.replace("\n", "<br/>")
            return androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
        } catch (_: Exception) {
            return text
        }
    }

    private fun addUserChatBubble(userMessage: String, imageBitmap: Bitmap?, docName: String?) {
        showChatContainerSmoothly()
        val density = resources.displayMetrics.density
        val maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()

        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (5 * density).toInt()
                bottomMargin = (5 * density).toInt()
            }
            gravity = android.view.Gravity.END
            orientation = LinearLayout.HORIZONTAL
        }

        val bubble = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_chat_bubble_user)
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            elevation = (2 * density)
        }

        if (imageBitmap != null) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (210 * density).toInt(),
                    (145 * density).toInt()
                ).apply {
                    bottomMargin = (6 * density).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(imageBitmap)
                clipToOutline = true
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_media_thumb_rounded)
            }
            bubble.addView(iv)
        }

        if (docName != null) {
            val docRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (4 * density).toInt()
                }
            }
            val icDoc = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
                setImageResource(R.drawable.ic_document)
            }
            val tvName = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (6 * density).toInt()
                }
                this.text = docName
                textSize = 12.5f
                setTextColor(Color.parseColor("#0F5132"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            docRow.addView(icDoc)
            docRow.addView(tvName)
            bubble.addView(docRow)
        }

        if (userMessage.isNotBlank()) {
            val tvText = TextView(this).apply {
                this.text = formatMarkdownToSpanned(userMessage)
                textSize = 14.5f
                setTextColor(Color.parseColor("#0B2A18"))
                setLineSpacing(2.5f * density, 1f)
                this.maxWidth = maxWidth
            }
            bubble.addView(tvText)
        }

        row.addView(bubble)
        containerChatBubbles.addView(row)
        scrollOnScreenChat.post {
            scrollOnScreenChat.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun addJarvisChatBubble(replyMessage: String) {
        showChatContainerSmoothly()
        val density = resources.displayMetrics.density
        val maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()

        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (5 * density).toInt()
                bottomMargin = (5 * density).toInt()
            }
            gravity = android.view.Gravity.START
            orientation = LinearLayout.HORIZONTAL
        }

        val bubble = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_chat_bubble_jarvis)
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            elevation = (2 * density)
        }

        // Header: JARVIS badge
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
        }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt())
            setImageResource(R.drawable.ic_jarvis)
        }
        val tvHeader = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (6 * density).toInt()
            }
            this.text = "JARVIS"
            textSize = 11.5f
            setTextColor(Color.parseColor("#12A550"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        header.addView(icon)
        header.addView(tvHeader)
        bubble.addView(header)

        val tvText = TextView(this).apply {
            this.text = formatMarkdownToSpanned(replyMessage)
            textSize = 14.5f
            setTextColor(Color.parseColor("#1E293B"))
            setTextIsSelectable(true)
            setLineSpacing(2.5f * density, 1f)
            this.maxWidth = maxWidth
        }
        bubble.addView(tvText)

        row.addView(bubble)
        containerChatBubbles.addView(row)
        scrollOnScreenChat.post {
            scrollOnScreenChat.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendPendingAttachmentWithPrompt(userPrompt: String) {
        val bytes = pendingDocBytes ?: return
        val docName = pendingDocName ?: "hujjat.pdf"
        val mime = pendingDocMime ?: "application/octet-stream"
        val bmp = pendingImageBitmap

        val promptToSend = if (userPrompt.isNotBlank()) {
            userPrompt
        } else {
            "Ushbu hujjatni sinchiklab tahlil qilib, xulosangizni bering."
        }

        // Add user chat bubble immediately
        addUserChatBubble(promptToSend, bmp, if (bmp == null) docName else null)
        fullChatHistory.add(ChatHistoryMessage("user", "📎 $docName: $promptToSend"))

        clearPendingAttachment()
        etCommandInput.setText("")

        showJarvisTurnCaption("🔍 Hujjat tahlil qilinmoqda...")
        tvMicStatus.text = "⏳ Tahlil qilinmoqda..."
        jarvisParticleView.setState(JarvisParticleState.SEARCHING)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "${getHttpBaseUrl()}/api/analyze-document"
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        docName,
                        bytes.toRequestBody(mime.toMediaTypeOrNull())
                    )
                    .addFormDataPart("prompt", promptToSend)
                    .addFormDataPart("domain", currentDomain.id)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request).execute()
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)

                if (json.has("analysis")) {
                    val analysis = json.getString("analysis")
                    withContext(Dispatchers.Main) {
                        tvMicStatus.text = "● Aloqada"
                        showJarvisTurnCaption("✅ Hujjat tahlili tayyor")
                        addJarvisChatBubble(analysis)
                        fullChatHistory.add(ChatHistoryMessage("JARVIS", analysis))
                        val firstLines = analysis.lines().filter { it.isNotBlank() }.take(2).joinToString(". ")
                        speakJarvis("Hujjat tahlili tayyorlandi, Axmadulloh sir. ${firstLines.take(150)}. To'liq tahlilni Telegram botingizga ham yubordim.")
                    }
                } else {
                    val err = json.optString("error", "Tahlil xatosi")
                    withContext(Dispatchers.Main) {
                        tvMicStatus.text = "● Aloqada"
                        addJarvisChatBubble("❌ Kechirasiz, tahlil qilishda xatolik: $err")
                        showJarvisTurnCaption("❌ $err")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvMicStatus.text = "● Aloqada"
                    addJarvisChatBubble("❌ Tarmoq xatosi: ${e.message}")
                    showJarvisTurnCaption("❌ Xatolik: ${e.message?.take(50)}")
                }
            }
        }
    }

    private fun toggleMicFromUi() {
        vibrateBrief(25)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        try {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_TOGGLE_MIC
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "toggleMicFromUi error: ${e.message}")
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
                toggleMicFromUi()
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

    // ── Eslatmalar Sheet ──────────────────────────────────────────────────
    private val reminderList = mutableListOf<Pair<String, String>>()

    private fun loadReminders() {
        try {
            val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("reminders_json", null) ?: return
            val arr = org.json.JSONArray(jsonStr)
            reminderList.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                reminderList.add(Pair(obj.optString("title"), obj.optString("time")))
            }
        } catch (_: Exception) {}
    }

    private fun saveReminders() {
        try {
            val arr = org.json.JSONArray()
            for (item in reminderList) {
                val obj = org.json.JSONObject()
                obj.put("title", item.first)
                obj.put("time", item.second)
                arr.put(obj)
            }
            getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("reminders_json", arr.toString())
                .apply()
        } catch (_: Exception) {}
    }

    private fun showEslatmalarSheet() {
        val sheet = BottomSheetDialog(this)
        val sv = layoutInflater.inflate(R.layout.sheet_eslatmalar, null)
        sheet.setContentView(sv)
        val rvReminders = sv.findViewById<RecyclerView>(R.id.rvReminderList)
        val btnAdd = sv.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddReminderFromSheet)
        val tvEmpty = sv.findViewById<TextView>(R.id.tvRemindersEmpty)

        var adapter: ReminderListAdapter? = null
        fun refreshList() {
            tvEmpty?.visibility = if (reminderList.isEmpty()) View.VISIBLE else View.GONE
            adapter?.notifyDataSetChanged()
        }

        adapter = ReminderListAdapter(reminderList) { pos ->
            if (pos >= 0 && pos < reminderList.size) {
                reminderList.removeAt(pos)
                saveReminders()
                refreshList()
            }
        }
        rvReminders?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
        }
        refreshList()

        btnAdd?.setOnClickListener {
            sheet.dismiss()
            showAddReminderDialog { title, time ->
                reminderList.add(0, Pair(title, time))
                saveReminders()
                deliverJarvisResponseToUi("Eslatma qo'shildi: \"$title\" — $time", "⏰")
            }
        }
        sheet.show()
    }

    private fun showAddReminderDialog(onSaved: (String, String) -> Unit) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Yangi eslatma")
            .create()
        val v = layoutInflater.inflate(R.layout.dialog_reminder_quick, null)
        dialog.setView(v)
        val etTitle = v.findViewById<EditText>(R.id.etReminderTitle)
        val etTime = v.findViewById<EditText>(R.id.etReminderTime)
        val btnSave = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveReminder)
        val btnCancel = v.findViewById<TextView>(R.id.btnCancelReminder)
        btnSave?.setOnClickListener {
            val title = etTitle?.text?.toString()?.trim() ?: ""
            val time = etTime?.text?.toString()?.trim() ?: ""
            if (title.isNotEmpty()) {
                val t = time.ifEmpty { "Vaqt belgilanmagan" }
                onSaved(title, t)
                try { phoneActionManager.setReminder(title, t) } catch (_: Exception) {}
            }
            dialog.dismiss()
        }
        btnCancel?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    inner class ReminderListAdapter(
        private val items: MutableList<Pair<String, String>>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<ReminderListAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvReminderItemTitle)
            val tvTime: TextView = v.findViewById(R.id.tvReminderItemTime)
            val btnDelete: View = v.findViewById(R.id.btnReminderItemDelete)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
            return VH(v)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (title, time) = items[position]
            holder.tvTitle.text = title
            holder.tvTime.text = time
            holder.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
        }
        override fun getItemCount() = items.size
    }

    // ── Kunlik Reja Sheet ─────────────────────────────────────────────────
    private val dailyScheduleMap = mutableMapOf<String, MutableList<Triple<String, String, String>>>()
    private val daysUz = listOf("Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba", "Yakshanba")

    private fun getInitialSchedule(): MutableMap<String, MutableList<Triple<String, String, String>>> {
        return mutableMapOf(
            "Dushanba" to mutableListOf(
                Triple("09:00", "12:30", "Ish va xizmat vazifalari"),
                Triple("13:30", "16:20", "O'qish va dars mashg'ulotlari"),
                Triple("17:00", "18:30", "Sport va jismoniy mashqlar")
            ),
            "Seshanba" to mutableListOf(
                Triple("09:00", "12:30", "Ish va loyihalar tahlili"),
                Triple("14:00", "17:00", "O'qish va amaliyot"),
                Triple("18:00", "19:30", "Kitob mutolaasi")
            ),
            "Chorshanba" to mutableListOf(
                Triple("09:00", "12:30", "Ish va uchrashuvlar"),
                Triple("13:30", "16:30", "Dasturlash va mustaqil ish"),
                Triple("17:30", "19:00", "Sport mashg'uloti")
            ),
            "Payshanba" to mutableListOf(
                Triple("09:00", "13:00", "Ish va haftalik hisobotlar"),
                Triple("14:00", "17:00", "O'qish va seminar"),
                Triple("18:00", "20:00", "Shaxsiy rivojlanish")
            ),
            "Juma" to mutableListOf(
                Triple("09:00", "12:00", "Ish va rejalashtirish"),
                Triple("12:30", "13:45", "Juma namozi va hordiq"),
                Triple("14:30", "17:30", "O'qish va mustaqil topshiriqlar")
            ),
            "Shanba" to mutableListOf(
                Triple("10:00", "13:00", "Shaxsiy loyihalar va ilm"),
                Triple("15:00", "18:00", "Sport va oila bilan dam olish")
            ),
            "Yakshanba" to mutableListOf(
                Triple("10:00", "13:00", "Haftalik yakunlar va yangi reja"),
                Triple("14:00", "19:00", "Madaniy hordiq va oilaviy vaqt")
            )
        )
    }

    private fun cleanOldTemporaryFiles() {
        try {
            val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < threeDaysAgo) {
                    file.delete()
                }
            }
            fullChatHistory.removeAll { it.timestamp < threeDaysAgo }
            saveChatHistory()
        } catch (_: Exception) {}
    }

    private fun loadChatHistory() {
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("chat_history_3days_json", null) ?: return
        try {
            val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            val arr = org.json.JSONArray(jsonStr)
            fullChatHistory.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                if (ts >= threeDaysAgo) {
                    fullChatHistory.add(ChatHistoryMessage(
                        speaker = obj.optString("speaker", "user"),
                        text = obj.optString("text", ""),
                        timestamp = ts
                    ))
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveChatHistory() {
        try {
            val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            val arr = org.json.JSONArray()
            for (msg in fullChatHistory) {
                if (msg.timestamp >= threeDaysAgo) {
                    val obj = org.json.JSONObject()
                    obj.put("speaker", msg.speaker)
                    obj.put("text", msg.text)
                    obj.put("timestamp", msg.timestamp)
                    arr.put(obj)
                }
            }
            getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("chat_history_3days_json", arr.toString())
                .apply()
        } catch (_: Exception) {}
    }

    private fun getScheduleSummary(): String {
        if (dailyScheduleMap.isEmpty()) loadDailySchedule()
        val sb = StringBuilder()
        for ((day, slots) in dailyScheduleMap) {
            if (slots.isNotEmpty()) {
                val dayDesc = slots.joinToString("; ") { "${it.first}-${it.second} ${it.third}" }
                sb.append("$day: $dayDesc\n")
            }
        }
        return sb.toString().trim()
    }

    private fun loadDailySchedule() {
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("daily_schedule_json", null)
        if (jsonStr.isNullOrEmpty()) {
            dailyScheduleMap.clear()
            dailyScheduleMap.putAll(getInitialSchedule())
            saveDailySchedule()
            return
        }
        try {
            val root = org.json.JSONObject(jsonStr)
            dailyScheduleMap.clear()
            for (day in daysUz) {
                val list = mutableListOf<Triple<String, String, String>>()
                if (root.has(day)) {
                    val arr = root.getJSONArray(day)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(Triple(obj.optString("start"), obj.optString("end"), obj.optString("title")))
                    }
                }
                dailyScheduleMap[day] = list
            }
        } catch (_: Exception) {
            dailyScheduleMap.clear()
            dailyScheduleMap.putAll(getInitialSchedule())
        }
    }

    private fun saveDailySchedule() {
        try {
            val root = org.json.JSONObject()
            for ((day, list) in dailyScheduleMap) {
                val arr = org.json.JSONArray()
                for (item in list) {
                    val obj = org.json.JSONObject()
                    obj.put("start", item.first)
                    obj.put("end", item.second)
                    obj.put("title", item.third)
                    arr.put(obj)
                }
                root.put(day, arr)
            }
            getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("daily_schedule_json", root.toString())
                .apply()
        } catch (_: Exception) {}
    }

    private fun showKunlikRejaSheet(initialDay: String? = null) {
        val sheet = BottomSheetDialog(this)
        val sv = layoutInflater.inflate(R.layout.sheet_kunlik_reja, null)
        sheet.setContentView(sv)
        val llTabs = sv.findViewById<LinearLayout>(R.id.llDayTabs)
        val rvSlots = sv.findViewById<RecyclerView>(R.id.rvDaySlots)
        val btnAddSlot = sv.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddSlot)
        val tvSelDay = sv.findViewById<TextView>(R.id.tvSelectedDay)
        var selectedDay = if (initialDay != null && daysUz.contains(initialDay)) initialDay else daysUz[0]

        var adapter: DaySlotAdapter? = null
        fun renderDay(day: String) {
            selectedDay = day
            tvSelDay?.text = day
            val slots = dailyScheduleMap.getOrPut(day) { mutableListOf() }
            adapter = DaySlotAdapter(slots) { pos ->
                if (pos >= 0 && pos < slots.size) {
                    slots.removeAt(pos)
                    saveDailySchedule()
                    adapter?.notifyItemRemoved(pos)
                }
            }
            rvSlots?.layoutManager = LinearLayoutManager(this@MainActivity)
            rvSlots?.adapter = adapter
        }

        llTabs?.removeAllViews()
        daysUz.forEachIndexed { _, day ->
            val btn = TextView(this).apply {
                text = day.take(3)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (day == selectedDay) 0xFF00E676.toInt() else 0xFF64748B.toInt())
                setPadding(22, 14, 22, 14)
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_caption_glass)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = 8
                layoutParams = lp
                setOnClickListener {
                    (parent as? LinearLayout)?.children?.forEach { v ->
                        (v as? TextView)?.setTextColor(0xFF64748B.toInt())
                    }
                    setTextColor(0xFF00E676.toInt())
                    renderDay(day)
                }
            }
            llTabs?.addView(btn)
        }

        btnAddSlot?.setOnClickListener {
            showAddSlotDialog(selectedDay) { renderDay(selectedDay) }
        }
        renderDay(selectedDay)
        sheet.show()
    }

    private fun showAddSlotDialog(day: String, onAdded: () -> Unit) {
        val dialog = MaterialAlertDialogBuilder(this).setTitle("$day — Yangi reja").create()
        val v = layoutInflater.inflate(R.layout.dialog_add_slot, null)
        dialog.setView(v)
        val etStart = v.findViewById<EditText>(R.id.etSlotStart)
        val etEnd = v.findViewById<EditText>(R.id.etSlotEnd)
        val etTitle = v.findViewById<EditText>(R.id.etSlotTitle)
        val btnSave = v.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveSlot)
        val btnCancel = v.findViewById<TextView>(R.id.btnCancelSlot)
        btnSave?.setOnClickListener {
            val start = etStart?.text?.toString()?.trim() ?: ""
            val end = etEnd?.text?.toString()?.trim() ?: ""
            val title = etTitle?.text?.toString()?.trim() ?: ""
            if (title.isNotEmpty()) {
                val list = dailyScheduleMap.getOrPut(day) { mutableListOf() }
                list.add(Triple(start, end, title))
                list.sortBy { it.first }
                saveDailySchedule()
                onAdded()
            }
            dialog.dismiss()
        }
        btnCancel?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    inner class DaySlotAdapter(
        private val slots: MutableList<Triple<String, String, String>>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<DaySlotAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTime: TextView = v.findViewById(R.id.tvSlotTime)
            val tvTitle: TextView = v.findViewById(R.id.tvSlotTitle)
            val btnDelete: View? = v.findViewById(R.id.btnSlotDelete)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_day_slot, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val (start, end, title) = slots[pos]
            h.tvTime.text = if (end.isNotEmpty()) "$start – $end" else start
            h.tvTitle.text = title
            h.btnDelete?.setOnClickListener {
                onDelete(h.adapterPosition)
            }
        }
        override fun getItemCount() = slots.size
    }

    // ── Camera AI Vision Mode ─────────────────────────────────────────────
    private fun openKameraAI() {
        isQuronActive = false
        stopMobileVoiceRecognition()
        try { tts?.stop() } catch (_: Exception) {}

        containerWebView.visibility = View.VISIBLE
        tvWebTitle.text = "JARVIS Kamera AI"
        try { webViewDashboard.setBackgroundColor(0xFF040812.toInt()) } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        webViewDashboard.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
        }
        webViewDashboard.loadUrl("https://jarvis.77.83.206.138.sslip.io/camera")
        showCaption("📷 Kamera AI faol")
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
            if (isQuronActive) {
                Log.d("MainActivity", "isQuronActive = true, suppressing JARVIS speech response")
                return@runOnUiThread
            }
            showJarvisTurnCaption(replyText)
            fullChatHistory.add(ChatHistoryMessage("jarvis", replyText))
            saveChatHistory()
            if (scrollOnScreenChat.visibility == View.VISIBLE) {
                addJarvisChatBubble(replyText)
            }
            if (containerWebView.visibility == View.VISIBLE) {
                try {
                    val qReply = JSONObject.quote(replyText)
                    webViewDashboard.evaluateJavascript("if (window.onJarvisReply) window.onJarvisReply($qReply);", null)
                } catch (_: Exception) {}
            }
            onTurnCompleted()
            val clean = replyText.replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").trim()
            val js = "window.jarvisDeliverResponse('$clean', '$shapeEmoji');"
            try {
                webViewLivingParticle.evaluateJavascript(js, null)
            } catch (_: Exception) {}
            jarvisParticleView.showResult(shapeEmoji, replyText)
            speakJarvis(replyText)
        }
    }

    // ── Matnli Status / Subtitle ko'rsatish (Organic in-place update & 6s Auto Fade) ──
    fun showInputTranscript(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        activeTurnUserTranscript = clean

        // Fast voice media control
        val lower = clean.lowercase()
        if (cardInlineMedia.visibility == View.VISIBLE || isMediaPlaying) {
            if (lower == "pauza" || lower == "pauza qil" || lower == "to'xtatib tur" || lower == "to'xtat" || lower == "stop") {
                runOnUiThread {
                    isMediaPlaying = false
                    btnMediaPlayPause.setImageResource(R.drawable.ic_media_play)
                    webViewInlineVideo.evaluateJavascript("sendCmd('pauseVideo');", null)
                    showCaption("⏸ Pauza qilindi")
                }
            } else if (lower == "davom ettir" || lower == "qo'y" || lower == "play" || lower == "davom et") {
                runOnUiThread {
                    isMediaPlaying = true
                    btnMediaPlayPause.setImageResource(R.drawable.ic_media_pause)
                    webViewInlineVideo.evaluateJavascript("sendCmd('playVideo');", null)
                    showCaption("▶ Ijro davom etmoqda")
                }
            } else if (lower.contains("qo'shiqni yop") || lower.contains("videoni yop") || lower.contains("musiqani o'chir") ||
                lower.contains("qo'shiqdan chiq") || lower.contains("videodan chiq") || lower.contains("videoni to'xtat") ||
                lower == "yop" || lower == "o'chir" || lower == "chiq") {
                runOnUiThread {
                    if (isVideoExpanded) toggleVideoFullscreen()
                    cardInlineMedia.visibility = View.GONE
                    webViewInlineVideo.loadUrl("about:blank")
                    isMediaPlaying = false
                    showCaption("⏹ Ijro to'xtatildi")
                }
            }
        }

        runOnUiThread {
            tvInputTranscript.text = clean
            tvInputTranscript.visibility = View.VISIBLE
            if (containerWebView.visibility == View.VISIBLE) {
                try {
                    val qTranscript = JSONObject.quote(clean)
                    webViewDashboard.evaluateJavascript("if (window.onJarvisSpeechTranscript) window.onJarvisSpeechTranscript($qTranscript);", null)
                } catch (_: Exception) {}
            }
        }
    }

    fun showJarvisTurnCaption(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        activeTurnJarvisCaption = clean

        runOnUiThread {
            captionDismissRunnable?.let { captionHandler.removeCallbacks(it) }
            tvJarvisCaption.text = clean
            if (tvJarvisCaption.visibility != View.VISIBLE) {
                tvJarvisCaption.alpha = 0f
                tvJarvisCaption.visibility = View.VISIBLE
                tvJarvisCaption.animate().alpha(1f).setDuration(200).start()
            }
        }
    }

    fun showCaption(text: String, durationMs: Long = 6000L) {
        showJarvisTurnCaption(text)
        onTurnCompleted(durationMs)
    }

    fun onTurnCompleted(durationMs: Long = 6000L) {
        runOnUiThread {
            if (activeTurnUserTranscript.isNotBlank()) {
                if (fullChatHistory.isEmpty() || fullChatHistory.last().text != activeTurnUserTranscript) {
                    fullChatHistory.add(ChatHistoryMessage("user", activeTurnUserTranscript))
                }
                activeTurnUserTranscript = ""
            }
            if (activeTurnJarvisCaption.isNotBlank()) {
                if (fullChatHistory.isEmpty() || fullChatHistory.last().text != activeTurnJarvisCaption) {
                    fullChatHistory.add(ChatHistoryMessage("jarvis", activeTurnJarvisCaption))
                }
                activeTurnJarvisCaption = ""
            }

            captionDismissRunnable?.let { captionHandler.removeCallbacks(it) }
            val runnable = Runnable {
                tvJarvisCaption.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction {
                        tvJarvisCaption.visibility = View.GONE
                        tvInputTranscript.visibility = View.GONE
                    }
                    .start()
            }
            captionDismissRunnable = runnable
            captionHandler.postDelayed(runnable, durationMs)
        }
    }

    fun updateMicStatusBadge(state: String) {
        runOnUiThread {
            when (state.uppercase()) {
                "MIC_ON", "LISTENING" -> {
                    tvMicStatus.text = "● Eshitmoqdaman"
                    tvMicStatus.setTextColor(Color.parseColor("#12A550"))
                }
                "SPEAKING" -> {
                    tvMicStatus.text = "🔊 Gapiryapman"
                    tvMicStatus.setTextColor(Color.parseColor("#2563EB"))
                }
                "SEARCHING", "THINKING" -> {
                    tvMicStatus.text = "● O'ylayapman"
                    tvMicStatus.setTextColor(Color.parseColor("#D97706"))
                }
                "MIC_OFF", "IDLE" -> {
                    tvMicStatus.text = "○ Mikrofon o'chiq"
                    tvMicStatus.setTextColor(Color.parseColor("#64748B"))
                }
                "DISCONNECTED" -> {
                    tvMicStatus.text = "○ Ulanmagan"
                    tvMicStatus.setTextColor(Color.parseColor("#94A3B8"))
                }
                "PERMISSION_DENIED" -> {
                    tvMicStatus.text = "⚠ Ruxsat yo'q"
                    tvMicStatus.setTextColor(Color.parseColor("#DC2626"))
                }
                "CONNECTED" -> {
                    tvMicStatus.text = "● Aloqada"
                    tvMicStatus.setTextColor(Color.parseColor("#12A550"))
                }
            }
        }
    }

    // ── Suhbat Tarixi Bottom Sheet (Faqat pastdan tortib ochiladi) ──
    private fun showChatHistoryBottomSheet() {
        val sheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.dialog_chat_history, null)
        sheetDialog.setContentView(sheetView)

        val rv = sheetView.findViewById<RecyclerView>(R.id.rvChatHistory)
        val tvEmpty = sheetView.findViewById<TextView>(R.id.tvEmptyHistory)
        val btnClear = sheetView.findViewById<TextView>(R.id.btnClearHistory)

        if (fullChatHistory.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            rv?.visibility = View.GONE
        } else {
            tvEmpty?.visibility = View.GONE
            rv?.visibility = View.VISIBLE
            rv?.layoutManager = LinearLayoutManager(this).apply {
                stackFromEnd = true
            }
            rv?.adapter = ChatHistoryAdapter(fullChatHistory)
        }

        btnClear?.setOnClickListener {
            fullChatHistory.clear()
            tvEmpty?.visibility = View.VISIBLE
            rv?.visibility = View.GONE
            Toast.makeText(this, "Suhbat tarixi tozalandi", Toast.LENGTH_SHORT).show()
        }

        sheetDialog.show()
    }

    private fun handlePcConnectionFailed(errorMsg: String) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🖥️ Kompyuterga ulanib bo'lmadi")
                .setMessage("PC Bridge serveriga (${prefs.serverAddress}) ulanishda xatolik yuz berdi:\n\n$errorMsg\n\nMustaqil Gemini Live (telefonda to'g'ridan-to'g'ri) rejimiga o'tishni xohlaysizmi?")
                .setPositiveButton("⚡ Gemini AI rejimiga o'tish") { _, _ ->
                    prefs.connectionMode = "GEMINI_DIRECT"
                    Toast.makeText(this, "Gemini Live rejimi yoqildi", Toast.LENGTH_SHORT).show()
                    startJarvisService()
                }
                .setNegativeButton("Sozlamalar") { _, _ ->
                    openSettingsDialog()
                }
                .setNeutralButton("Yopish", null)
                .show()
        }
    }

    class ChatHistoryAdapter(private val messages: List<ChatHistoryMessage>) :
        RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val userContainer: View = v.findViewById(R.id.containerUserMessage)
            val tvUser: TextView = v.findViewById(R.id.tvUserMessage)
            val jarvisContainer: View = v.findViewById(R.id.containerJarvisMessage)
            val tvJarvis: TextView = v.findViewById(R.id.tvJarvisMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_history, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = messages[position]
            if (msg.speaker.equals("user", ignoreCase = true)) {
                holder.userContainer.visibility = View.VISIBLE
                holder.tvUser.text = msg.text
                holder.jarvisContainer.visibility = View.GONE
            } else {
                holder.userContainer.visibility = View.GONE
                holder.jarvisContainer.visibility = View.VISIBLE
                holder.tvJarvis.text = msg.text
            }
        }

        override fun getItemCount(): Int = messages.size
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

        // Notify Foreground Service to update domain on server
        try {
            val domainIntent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_SET_DOMAIN
                putExtra(JarvisForegroundService.EXTRA_DOMAIN_ID, domain.id)
            }
            startService(domainIntent)
        } catch (_: Exception) {}

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

        showJarvisTurnCaption("⚡ $shortName rejimi faol")

        // Agar PC_BRIDGE rejimida bo'lsa, server (Gemini Live) o'zi gapiradi (ikkita ovoz to'qnashmasligi uchun)
        if (speakAnnouncement && prefs.connectionMode != "PC_BRIDGE") {
            val announcement = buildDomainAnnouncement(domain)
            deliverJarvisResponseToUi(announcement, domain.icon)
        }
    }

    // ── Bottom Sheet (Yo'nalishlar: Asosiy, Yurist va Din) ────────────────
    private fun showDomainBottomSheet() {
        vibrateBrief(20)
        val sheetDialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_domain_picker, null)
        sheetDialog.setContentView(sheetView)

        val rv = sheetView.findViewById<RecyclerView>(R.id.rvDomainsGrid)
        val btnSettings = sheetView.findViewById<ImageButton>(R.id.btnSheetSettings)

        btnSettings?.setOnClickListener {
            sheetDialog.dismiss()
            openSettingsDialog()
        }

        val domainsToShow = listOf(
            JarvisDomainRegistry.ASOSIY,
            JarvisDomainRegistry.YURIST,
            JarvisDomainRegistry.DIN
        )

        val adapter = DomainGridAdapter(domainsToShow, currentDomain.id) { selected ->
            applyDomain(selected, speakAnnouncement = true)
            sheetDialog.dismiss()
        }

        rv?.layoutManager = GridLayoutManager(this, 3)
        rv?.adapter = adapter

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

                // Erkak kishi (Umid) salobatli va bosiq ovoz tembri
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(0.88f)

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

    private fun stopCurrentAudio() {
        activeSpeechJob?.cancel()
        activeSpeechJob = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun ensureAudioVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let { am ->
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (currentVol == 0 && maxVol > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.75f).toInt(), 0)
            }
        }
    }

    // ── Umid & Umida Dynamic Voice Switcher ────────────────────────────────
    private fun applyAssistantPersona(persona: String, announce: Boolean = false) {
        activeAssistantPersona = if (persona.lowercase().contains("umida")) "umida" else "umid"
        runOnUiThread {
            if (activeAssistantPersona == "umida") {
                // Ayol kishi ovozi (Umida) - Yuqori, jarangdor va muloyim pitch
                tts?.setPitch(1.35f)
                tts?.setSpeechRate(1.02f)
                try {
                    val femaleVoice = tts?.voices?.find {
                        it.name.contains("female", ignoreCase = true) ||
                        it.name.contains("woman", ignoreCase = true) ||
                        it.name.contains("zira", ignoreCase = true)
                    }
                    if (femaleVoice != null) tts?.voice = femaleVoice
                } catch (_: Exception) {}
                showCaption("👩 Aoede ovozi faollashdi (JARVIS Ayol)")
                if (announce) {
                    speakJarvis("Labbay, Axmadulloh sir! Men JARVISman, sizni eshitmoqdaman.")
                }
            } else {
                // Erkak kishi ovozi (Umid) - Chuqur, bosiq va salobatli pitch
                tts?.setPitch(0.88f)
                tts?.setSpeechRate(1.0f)
                try {
                    val maleVoice = tts?.voices?.find {
                        it.name.contains("male", ignoreCase = true) ||
                        it.name.contains("david", ignoreCase = true)
                    }
                    if (maleVoice != null) tts?.voice = maleVoice
                } catch (_: Exception) {}
                showCaption("⚡ Kore ovozi faollashdi (Asl JARVIS)")
                if (announce) {
                    speakJarvis("Labbay, Axmadulloh sir! Men sizning sadoqatli kiber-intellektingiz — JARVISman.")
                }
            }
        }
    }

    private fun speakWithServerTts(text: String, onFinished: (Boolean) -> Unit) {
        val spokenText = prepareTextForSpeech(text)
        val voiceKey = if (activeAssistantPersona == "aoede" || activeAssistantPersona == "umida") "aoede" else "kore"
        val serverBase = getHttpBaseUrl()
        val encoded = try { java.net.URLEncoder.encode(spokenText.take(450), "UTF-8") } catch (_: Exception) { "" }
        if (encoded.isBlank() || serverBase.isBlank()) {
            onFinished(false)
            return
        }

        val ttsUrl = "$serverBase/api/tts?text=$encoded&voice=$voiceKey"
        activeSpeechJob = lifecycleScope.launch {
            jarvisParticleView.setState(JarvisParticleState.SEARCHING)
            try {
                playAudioFromUrl(ttsUrl) {
                    showCaption("")
                    onFinished(true)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Server TTS failed: ${e.message}")
                onFinished(false)
            }
        }
    }

    private fun speakJarvis(text: String) {
        showCaption(text)
        stopCurrentAudio()

        // 1. By default or if SERVER_TTS, stream high-fidelity neural Jarvis voice from official HTTPS domain
        if (prefs.ttsEngine == "SERVER_TTS" || prefs.ttsEngine == "DEVICE_TTS") {
            speakWithServerTts(text) { success ->
                if (!success) {
                    runOnUiThread { speakWithDeviceTts(text) }
                }
            }
            return
        }

        when (prefs.ttsEngine) {
            "UZBEKVOICE_AI" -> speakWithUzbekVoice(text)
            "ELEVENLABS" -> speakWithElevenLabs(text)
            else -> speakWithDeviceTts(text)
        }
    }

    private fun speakWithUzbekVoice(text: String) {
        val apiKey = prefs.uzbekVoiceApiKey.ifEmpty { "393c4279-0b87-4935-a8ce-8391e859f81c:bbc076fd-1dae-48fd-a0fd-29a86cc16fa1" }
        val spokenText = prepareTextForSpeech(text)

        activeSpeechJob = lifecycleScope.launch {
            jarvisParticleView.setState(JarvisParticleState.SEARCHING)
            val result = UzbekVoiceApiClient.synthesizeSpeech(
                apiKey = apiKey,
                text = spokenText,
                model = prefs.uzbekVoiceModel
            )

            if (result.isSuccess) {
                val audioUrl = result.getOrNull()
                if (!audioUrl.isNullOrEmpty()) {
                    playAudioFromUrl(audioUrl) {
                        showCaption("")
                    }
                    return@launch
                }
            }

            Log.w("MainActivity", "UzbekVoice API fallback to Device TTS: ${result.exceptionOrNull()?.message}")
            speakWithDeviceTts(text)
        }
    }

    private fun speakWithElevenLabs(text: String) {
        val apiKey = prefs.elevenLabsApiKey.ifEmpty { "sk_cae53f7e2debb8bd6a99700edfd811ce69b9b4dbbca7995b" }
        val spokenText = prepareTextForSpeech(text)

        activeSpeechJob = lifecycleScope.launch {
            jarvisParticleView.setState(JarvisParticleState.SEARCHING)
            val tempFile = File(cacheDir, "elevenlabs_temp_${System.currentTimeMillis()}.mp3")
            val result = ElevenLabsApiClient.synthesizeSpeechToFile(
                apiKey = apiKey,
                text = spokenText,
                voiceId = prefs.elevenLabsVoiceId,
                outputFile = tempFile
            )

            if (result.isSuccess && tempFile.exists() && tempFile.length() > 0) {
                playAudioFromFile(tempFile) {
                    tempFile.delete()
                    showCaption("")
                }
                return@launch
            }

            Log.w("MainActivity", "ElevenLabs fallback to Device TTS: ${result.exceptionOrNull()?.message}")
            speakWithDeviceTts(text)
        }
    }

    private fun playAudioFromUrl(url: String, onDone: () -> Unit) {
        try {
            ensureAudioVolume()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setState(JarvisParticleState.SPEAKING)
                        jarvisParticleView.setSpeechLevel(0.88f)
                    }
                    player.start()
                }
                setOnCompletionListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.RELEASE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                }
                setOnErrorListener { player, what, extra ->
                    Log.e("MainActivity", "MediaPlayer error: what=$what, extra=$extra")
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.IDLE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e("MainActivity", "playAudioFromUrl error: ${e.message}")
            jarvisParticleView.setState(JarvisParticleState.IDLE)
            onDone()
        }
    }

    private fun playAudioFromFile(file: File, onDone: () -> Unit) {
        try {
            ensureAudioVolume()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setState(JarvisParticleState.SPEAKING)
                        jarvisParticleView.setSpeechLevel(0.88f)
                    }
                    player.start()
                }
                setOnCompletionListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.RELEASE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                }
                setOnErrorListener { player, what, extra ->
                    Log.e("MainActivity", "MediaPlayer file error: what=$what, extra=$extra")
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.IDLE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e("MainActivity", "playAudioFromFile error: ${e.message}")
            jarvisParticleView.setState(JarvisParticleState.IDLE)
            onDone()
        }
    }

    private fun playVoiceAsset(voiceName: String, onDone: () -> Unit = {}) {
        stopCurrentAudio()
        ensureAudioVolume()
        try {
            val fileName = "voices/${voiceName.lowercase().trim()}.wav"
            val afd = assets.openFd(fileName)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnPreparedListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setState(JarvisParticleState.SPEAKING)
                        jarvisParticleView.setSpeechLevel(0.85f)
                    }
                    player.start()
                }
                setOnCompletionListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.RELEASE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                }
                setOnErrorListener { player, what, extra ->
                    Log.e("MainActivity", "playVoiceAsset error: what=$what, extra=$extra")
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.IDLE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.w("MainActivity", "playVoiceAsset local error: ${e.message}, falling back to VPS preview endpoint")
            val fallbackUrl = "${getHttpBaseUrl()}/api/preview-voice?voice=${voiceName.lowercase().trim()}"
            playAudioFromUrl(fallbackUrl) {
                onDone()
            }
        }
    }

    private fun playWelcomeAsset(voiceName: String, onDone: () -> Unit = {}) {
        stopCurrentAudio()
        ensureAudioVolume()
        try {
            val fileName = "voices/welcome_${voiceName.lowercase().trim()}.wav"
            val afd = assets.openFd(fileName)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnPreparedListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setState(JarvisParticleState.SPEAKING)
                        jarvisParticleView.setSpeechLevel(0.85f)
                    }
                    player.start()
                }
                setOnCompletionListener { player ->
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.RELEASE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                }
                setOnErrorListener { player, what, extra ->
                    Log.e("MainActivity", "playWelcomeAsset error: what=$what, extra=$extra")
                    runOnUiThread {
                        jarvisParticleView.setSpeechLevel(0f)
                        jarvisParticleView.setState(JarvisParticleState.IDLE)
                        onDone()
                    }
                    player.reset()
                    player.release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.w("MainActivity", "playWelcomeAsset local error: ${e.message}, falling back to VPS welcome endpoint")
            val fallbackUrl = "${getHttpBaseUrl()}/api/welcome-voice?voice=${voiceName.lowercase().trim()}"
            playAudioFromUrl(fallbackUrl) {
                onDone()
            }
        }
    }

    private fun speakWithDeviceTts(text: String) {
        if (!isTtsReady || tts == null) {
            return
        }

        try {
            ensureAudioVolume()
            val lowerText = text.lowercase()
            if (lowerText.contains("umidaman") || lowerText.contains("men umidaman")) {
                applyAssistantPersona("umida")
            } else if (lowerText.contains("umidman") || lowerText.contains("men umidman")) {
                applyAssistantPersona("umid")
            } else {
                if (activeAssistantPersona == "umida") {
                    tts?.setPitch(1.35f)
                    tts?.setSpeechRate(1.02f)
                } else {
                    tts?.setPitch(0.88f)
                    tts?.setSpeechRate(1.0f)
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
            Log.e("MainActivity", "speakWithDeviceTts error: ${e.message}")
        }
    }

    // ── Buyruqni Bajarish (Gemini AI yoki PC Bridge) ────────────────────────
    
    private fun handleImageGenerationRequest(cmd: String) {
        showCaption("🎨 Rasm chizilmoqda...")
        jarvisParticleView.setState(JarvisParticleState.SEARCHING)

        val cleanPrompt = cmd.lowercase()
            .replace("rasm qilib ber", "")
            .replace("rasm chizib ber", "")
            .replace("rasm chiz", "")
            .replace("rasm yarat", "")
            .replace("surat qilib ber", "")
            .replace("surat chiz", "")
            .replace("rasmini chiqar", "")
            .replace("rasm generatsiya", "")
            .replace("chizib ber", "")
            .replace("rasm tayyorla", "")
            .trim()

        val promptFinal = if (cleanPrompt.isNotEmpty()) cleanPrompt else "ultra hd futuristic cyber ai artwork cinematic 8k"
        val encodedPrompt = android.net.Uri.encode(promptFinal)
        val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=768&model=flux&nologo=true&seed=${System.currentTimeMillis()}"

        runOnUiThread {
            deliverJarvisResponseToUi("Axmadulloh sir, siz so'ragan rasm muvaffaqiyatli chizildi!\n\n🎨 Mavzu: $cmd", "🎨")
            openWorldTv(imageUrl)
        }
    }

    private fun sendJarvisCommand(cmd: String, shapeEmoji: String = currentDomain.icon) {
        if (isQuronActive || (containerWebView.visibility == View.VISIBLE && quronWebUrl.contains("quron"))) {
            Log.d("MainActivity", "Qur'on o'rganish faol - fon ovozli buyruqlari qabul qilinmaydi: $cmd")
            return
        }

        val lowerCmd = cmd.lowercase()
        if (lowerCmd.contains("umida") || lowerCmd.contains("umidaxon") || lowerCmd.contains("umidaxonim")) {
            applyAssistantPersona("umida")
        } else if (lowerCmd.contains("umid") || lowerCmd.contains("umidbek") || lowerCmd.contains("umidjon")) {
            applyAssistantPersona("umid")
        }

        val detected = JarvisDomainRegistry.findDomainByVoice(cmd)
        if (detected != null && detected.id != currentDomain.id) {
            applyDomain(detected, speakAnnouncement = false)
        }

        showInputTranscript(cmd)
        fullChatHistory.add(ChatHistoryMessage("user", cmd))
        try {
            webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('scan');", null)
        } catch (_: Exception) {}
        jarvisParticleView.setState(JarvisParticleState.SEARCHING)

        val lower = cmd.lowercase().trim()
        // 0. Rasm chizish / generatsiya qilish buyrug'i
        val isImageGen = listOf(
            "rasm qilib ber", "rasm chiz", "rasm yarat", "surat qilib ber",
            "surat chiz", "rasmini chiqar", "rasmini chiz", "rasm generatsiya",
            "chizib ber", "rasm tayyorla"
        ).any { lower.contains(it) }
        if (isImageGen) {
            handleImageGenerationRequest(cmd)
            return
        }


        // 0. Mahalliy tezkor musiqa/video/TV boshqaruvi (Pauza, davom ettirish, yopish, to'xtatish)
        if (containerWebView.visibility == View.VISIBLE) {
            if (lower == "yop" || lower == "yopish" || lower == "o'chir" || lower == "chiq" || lower == "to'xtat") {
                containerWebView.visibility = View.GONE
                webViewDashboard.loadUrl("about:blank")
                showCaption("⏹ Telekanal yopildi")
                deliverJarvisResponseToUi("Axmadulloh sir, telekanal yopildi.", "📺")
                return
            }
            if (lower == "pauza" || lower == "pauza qil" || lower == "to'xtatib tur" || lower == "stop") {
                webViewDashboard.evaluateJavascript("if (window.togglePlayPause) window.togglePlayPause();", null)
                showCaption("⏸ Pauza qilindi")
                return
            }
            if (lower == "davom et" || lower == "davom ettir" || lower == "play") {
                webViewDashboard.evaluateJavascript("if (window.togglePlayPause) window.togglePlayPause();", null)
                showCaption("▶ Ijro davom etmoqda")
                return
            }
        }

        if (cardInlineMedia.visibility == View.VISIBLE || isMediaPlaying) {
            if (lower == "pauza" || lower == "pauza qil" || lower == "to'xtatib tur" || lower == "to'xtat" || lower == "stop") {
                isMediaPlaying = false
                btnMediaPlayPause.setImageResource(R.drawable.ic_media_play)
                webViewInlineVideo.evaluateJavascript("sendCmd('pauseVideo');", null)
                showCaption("⏸ Pauza qilindi")
                return
            }
            if (lower == "davom ettir" || lower == "qo'y" || lower == "play" || lower == "davom et") {
                isMediaPlaying = true
                btnMediaPlayPause.setImageResource(R.drawable.ic_media_pause)
                webViewInlineVideo.evaluateJavascript("sendCmd('playVideo');", null)
                showCaption("▶ Ijro davom etmoqda")
                return
            }
            if (lower.contains("qo'shiqni yop") || lower.contains("videoni yop") || lower.contains("musiqani o'chir") ||
                lower.contains("qo'shiqdan chiq") || lower.contains("videodan chiq") || lower.contains("videoni to'xtat") ||
                lower == "yop" || lower == "o'chir" || lower == "chiq") {
                if (isVideoExpanded) toggleVideoFullscreen()
                cardInlineMedia.visibility = View.GONE
                webViewInlineVideo.loadUrl("about:blank")
                isMediaPlaying = false
                showCaption("⏹ Ijro to'xtatildi")
                return
            }
        }

        // 0. World TV & 3D Globus (TV och deganda hech qachon Qur'on ochilmaydi!)
        val isTvOrMovie = (lower.contains("tv") || lower.contains("telekanal") || lower.contains("televizor") ||
                lower.contains("jonli efir") || lower.contains("famelack") || lower.contains("biz tv") ||
                lower.contains("sevimli") || lower.contains("zo'r") || lower.contains("zor") || lower.contains("aqlvoy") ||
                lower.contains("dunyo bo'ylab") || lower.contains("kinolar tv") || lower.contains("sport") ||
                lower.contains("milliy") || lower.contains("o'zbekiston 24") || lower.contains("bolajon") ||
                lower.contains("kino ko'r") || lower.contains("film ko'r") || lower.contains("kinolar") ||
                lower.contains("dasturxon") || lower.contains("cheksiz") || lower.contains("farg'ona") ||
                lower.contains("my5") || lower.contains("mening yurtim") || lower.contains("uzreport")) &&
                !lower.contains("qur'on") && !lower.contains("quron") && !lower.contains("tajvid")

        if (isTvOrMovie) {
            val targetChannel = when {
                lower.contains("zo'r") || lower.contains("zor") -> "Zo'r TV"
                lower.contains("sevimli") -> "Sevimli TV"
                lower.contains("my5") || lower.contains("mening yurtim") -> "MY5"
                lower.contains("milliy") -> "Milliy TV"
                lower.contains("uzreport") -> "UzReport TV"
                lower.contains("sport") -> "Sport TV"
                lower.contains("biz cinema") -> "BIZ Cinema"
                lower.contains("biz music") -> "BIZ Music"
                lower.contains("biz tv") || lower.contains("biz") -> "BIZ TV"
                lower.contains("aqlvoy") -> "Aqlvoy"
                lower.contains("dunyo bo'ylab") -> "Dunyo bo'ylab"
                lower.contains("bolajon") -> "Bolajon"
                lower.contains("o'zbekiston 24") || lower.contains("uzbekiston 24") -> "O'zbekiston 24"
                lower.contains("o'zbekiston") || lower.contains("uzbekiston") -> "O'zbekiston"
                lower.contains("tarix") -> "O'zbekiston Tarixi"
                lower.contains("dasturxon") -> "Dasturxon TV"
                lower.contains("cheksiz") -> "Cheksiz TV"
                lower.contains("farg'ona") -> "Farg'ona MTRK"
                lower.contains("kinoteatr") -> "Kinoteatr"
                lower.contains("yoshlar") -> "Yoshlar"
                lower.contains("mahalla") -> "Mahalla"
                lower.contains("madaniyat") -> "Madaniyat va ma'rifat"
                lower.contains("navo") -> "Navo"
                lower.contains("makon") -> "Makon TV"
                lower.contains("renessans") -> "Renessans TV"
                else -> ""
            }
            val targetCat = if (lower.contains("kino") || lower.contains("film") || lower.contains("movie")) "movies" else ""
            val targetCountry = when {
                lower.contains("turkiya") -> "tr"
                lower.contains("aqsh") || lower.contains("amerika") -> "us"
                lower.contains("rossiya") -> "ru"
                else -> "uz"
            }
            val tvUrl = when {
                targetChannel.isNotEmpty() -> "https://jarvis.77.83.206.138.sslip.io/tv?country=$targetCountry&channel=${android.net.Uri.encode(targetChannel)}&tab=tv"
                targetCat.isNotEmpty() -> "https://jarvis.77.83.206.138.sslip.io/tv?category=$targetCat&tab=tv"
                else -> "https://jarvis.77.83.206.138.sslip.io/tv?country=$targetCountry&tab=home"
            }
            openWorldTv(tvUrl)
            val ackMsg = if (targetChannel.isNotEmpty()) "Labbay Axmadulloh sir, «$targetChannel» telekanalini jonli efirda qo'ydim!"
                         else "Axmadulloh sir, JARVIS 3D Kiber Globus va jonli telekanallar xizmatini ochdim! Maroqli tomosha tilayman."
            deliverJarvisResponseToUi(ackMsg, "📺")
            return
        }

        // 0. Qur'on AI ochish buyruqlari (Faqat aniq Qur'on so'ralganda)
        val isQuronCmd = (lower.contains("qur'on") || lower.contains("quron") || lower.contains("tilovat") || lower.contains("qiroat") || lower.contains("tajvid") || lower.contains("muallimi") || (lower.contains("sura") && !lower.contains("surat")) || lower.contains("oyat")) && !isTvOrMovie
        if (isQuronCmd) {
            val mode = if (lower.contains("harf") || lower.contains("alifbo") || lower.contains("0 dan")) "letters"
                       else if (lower.contains("kamera") || lower.contains("mushaf") || lower.contains("qog'oz")) "camera"
                       else "recite"
            openQuronLesson("https://jarvis.77.83.206.138.sslip.io/quron?mode=$mode")
            deliverJarvisResponseToUi("Qur'on Ustozi xizmatini ochdim, Axmadulloh sir! Birgalikda tajvid va tilovatni o'rganamiz.", "📖")
            return
        }

        // 0. Kamera AI ochish buyruqlari
        if (lower.contains("kamera") || lower.contains("kamerani och") || lower.contains("kamerani yoq") || lower.contains("ko'rib ayt") || lower.contains("testni yech") || lower.contains("savolni yech")) {
            openKameraAI()
            return
        }

        // 0. Kunlik Reja buyruqlari (Aniq rejalarni aytadi va ochadi - reja1.mp4 yechimi)
        if (lower.contains("reja") || lower.contains("jadval") || lower.contains("kun tartib") || lower.contains("rejam")) {
            val cal = java.util.Calendar.getInstance()
            val todayDay = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "Dushanba"
                java.util.Calendar.TUESDAY -> "Seshanba"
                java.util.Calendar.WEDNESDAY -> "Chorshanba"
                java.util.Calendar.THURSDAY -> "Payshanba"
                java.util.Calendar.FRIDAY -> "Juma"
                java.util.Calendar.SATURDAY -> "Shanba"
                java.util.Calendar.SUNDAY -> "Yakshanba"
                else -> "Dushanba"
            }
            val targetDay = when {
                lower.contains("ertaga") || lower.contains("ertangi") -> {
                    val c2 = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
                    when (c2.get(java.util.Calendar.DAY_OF_WEEK)) {
                        java.util.Calendar.MONDAY -> "Dushanba"
                        java.util.Calendar.TUESDAY -> "Seshanba"
                        java.util.Calendar.WEDNESDAY -> "Chorshanba"
                        java.util.Calendar.THURSDAY -> "Payshanba"
                        java.util.Calendar.FRIDAY -> "Juma"
                        java.util.Calendar.SATURDAY -> "Shanba"
                        java.util.Calendar.SUNDAY -> "Yakshanba"
                        else -> "Dushanba"
                    }
                }
                lower.contains("dushanba") -> "Dushanba"
                lower.contains("seshanba") -> "Seshanba"
                lower.contains("chorshanba") -> "Chorshanba"
                lower.contains("payshanba") -> "Payshanba"
                lower.contains("juma") -> "Juma"
                lower.contains("shanba") -> "Shanba"
                lower.contains("yakshanba") -> "Yakshanba"
                else -> todayDay
            }
            if (dailyScheduleMap.isEmpty()) {
                loadDailySchedule()
            }
            val slots = dailyScheduleMap[targetDay] ?: emptyList()
            val spokenSchedule = if (slots.isNotEmpty()) {
                val slotsDesc = slots.joinToString(", ") { s ->
                    val timePart = if (s.second.isNotEmpty()) "${s.first} dan ${s.second} gacha" else "${s.first} da"
                    "$timePart — ${s.third}"
                }
                if (targetDay == todayDay) "Bugun $todayDay, Axmadulloh sir! Sizning kunlik rejangiz: $slotsDesc."
                else "$targetDay kuni uchun sizning rejangiz: $slotsDesc."
            } else {
                if (targetDay == todayDay) "Bugun $todayDay, Axmadulloh sir! Hozircha bugunga reja kiritilmagan. Menyudan «+ Reja Qo'shish» orqali kiritishingiz mumkin."
                else "$targetDay kuni uchun hozircha reja kiritilmagan, Axmadulloh sir."
            }
            runOnUiThread { showKunlikRejaSheet(targetDay) }
            deliverJarvisResponseToUi(spokenSchedule, "📅")
            return
        }

        // 0. Eslatmalar ro'yxatini ochish
        if (lower.contains("eslatmalar ro'yxati") || lower.contains("eslatmalarni och") || lower.contains("eslatmalarni ko'rsat")) {
            runOnUiThread { showEslatmalarSheet() }
            deliverJarvisResponseToUi("Barcha eslatmalaringiz ro'yxatini ochdim, Axmadulloh sir!", "⏰")
            return
        }

        // 0. YouTube ochish buyrug'i
        if (lower.contains("youtube") || lower.contains("yutub") || lower == "yt") {
            if (lower.contains("och") || lower.contains("kir") || lower.contains("ishga tushir") || lower.trim() == "youtube" || lower.trim() == "yutub") {
                val res = phoneActionManager.openApp("YouTube", "com.google.android.youtube")
                deliverJarvisResponseToUi(res, "▶️")
                return
            }
        }

        // 0. Telegram ochish buyrug'i
        if (lower.contains("telegram") || lower.contains("tg ni och") || lower.trim() == "tg") {
            if (lower.contains("och") || lower.contains("kir") || lower.contains("ishga tushir") || lower.trim() == "telegram" || lower.trim() == "tg") {
                val res = phoneActionManager.openApp("Telegram", "org.telegram.messenger")
                deliverJarvisResponseToUi(res, "✈️")
                return
            }
        }



        // 0. AI Kotib SMS yuborish ovozli tasdig'i (Voice confirmation)
        if (pendingSecretaryTarget != null) {
            val target = pendingSecretaryTarget!!
            val message = pendingSecretaryMessage!!
            if (lower == "ha" || lower.startsWith("ha ") || lower.contains("yubor") || lower.contains("jo'nat") || lower.contains("mayli") || lower.contains("tasdiqlayman") || lower.contains("yaxshi")) {
                pendingSecretaryTarget = null
                pendingSecretaryMessage = null
                val res = phoneActionManager.sendSms(target, message)
                if (res.contains("yuborildi") || res.contains("tayyorlandi")) {
                    deliverJarvisResponseToUi("Axmadulloh sir, ${target}ga xabar yubordim: «$message»", "✉️")
                } else {
                    deliverJarvisResponseToUi("Xabar yuborilmadi, sir: $res", "⚠️")
                }
                return
            } else if (lower == "yo'q" || lower.contains("bekor") || lower.contains("kerakmas") || lower.contains("yuborma")) {
                pendingSecretaryTarget = null
                pendingSecretaryMessage = null
                deliverJarvisResponseToUi("Xabar bekor qilindi, Axmadulloh sir.", "❌")
                return
            }
        }

        // 1. AI Kotib: "Onamga band ekanligimni ayt", "Onamga qo'ng'iroq qilib darsda ekanimni ayt", "Darsdaman deb ayt"
        val isSecretaryCommand = (lower.contains("ayt") || lower.contains("xabar ber") || lower.contains("yozib qo'y") || lower.contains("deb qo'y") || lower.contains("yetkaz")) &&
                (lower.contains("band") || lower.contains("dars") || lower.contains("ish") || lower.contains("majlis") || lower.contains("namoz") || lower.contains("hayda") || lower.contains("uxla") || lower.contains("dam ol"))
                || lower.contains("band ekanligimni") || lower.contains("bandligimni") || lower.contains("darsda ekanligimni") || lower.contains("ishda ekanligimni")

        if (isSecretaryCommand) {
            val target = when {
                lower.contains("onam") -> "Onam"
                lower.contains("dadam") || lower.contains("otam") -> "Dadam"
                lower.contains("akam") -> "Akam"
                lower.contains("ukam") -> "Ukam"
                lower.contains("opam") -> "Opam"
                lower.contains("singlim") -> "Singlim"
                lower.contains("ayolim") || lower.contains("rafiqam") -> "Ayolim"
                else -> {
                    cmd.replace(Regex("(?i)onamga|dadamga|akamga|ukamga|opamga|singlimga|telefon qilib|qo'ng'iroq qilib|band ekanligimni ayt|darsda ekanligimni ayt|ayt|deb ayt|xabar ber"), "").trim().ifEmpty { "Onam" }
                }
            }

            val reason = when {
                lower.contains("dars") -> "darsda"
                lower.contains("ish") -> "ishda"
                lower.contains("majlis") -> "muhim majlisda"
                lower.contains("namoz") -> "namozda"
                lower.contains("hayda") || lower.contains("mashina") -> "mashina haydamoqda"
                else -> "band"
            }

            val smsBody = "Assalomu alaykum. Axmadulloh hozir $reason, imkon bo'lishi bilan o'zi qo'ng'iroq qiladi."
            pendingSecretaryTarget = target
            pendingSecretaryMessage = smsBody

            val prompt = "Axmadulloh sir, Android xavfsizlik cheklovi sababli qo'ng'iroq ichida AI gaplasha olmaydi. Shuning uchun ${target}ga quyidagi SMS xabarni yuboraymi:\n«$smsBody»?\nYuborishim uchun 'ha' deng."
            deliverJarvisResponseToUi(prompt, "✉️")
            return
        }

        // 2. Eslatmalar (Reminders / Alarms in all domains)
        if (lower.contains("eslat") || lower.contains("budilnik") || lower.contains("taymer") || lower.contains("reminder")) {
            try { phoneActionManager.setReminder(cmd, "") } catch (_: Exception) {}
            reminderList.add(0, Pair(cmd, "Ovozli buyruq bilan"))
            deliverJarvisResponseToUi("Eslatma qo'shildi: \"$cmd\"! Eslatmalar ro'yxatiga kiritildi.", "⏰")
            return
        }

        // 3. To'g'ridan-to'g'ri Qo'ng'iroqlar (Faqat xabar yetkazish/sabab aytish bo'lmaganda)
        if (lower.startsWith("onamga telefon") || lower.contains("telefon qil") || lower.contains("qo'ng'iroq qil")) {
            val target = if (lower.contains("onam")) "onam" else cmd.replace(Regex("(?i)telefon qil|qo'ng'iroq qil"), "").trim()
            val res = phoneActionManager.makeCall(target)
            deliverJarvisResponseToUi(res, "📞")
            return
        }

        // 4. SMS xabarlar
        if (lower.contains("sms yoz") || lower.contains("sms yubor")) {
            val res = phoneActionManager.sendSms(cmd, "JARVIS xabari")
            deliverJarvisResponseToUi(res, "✉️")
            return
        }

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
        if (!sentToService && prefs.connectionMode == "GEMINI_DIRECT") {
            executeGeminiPrompt(cmd, shapeEmoji)
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
                userName = prefs.userName,
                recentHistory = fullChatHistory.map { GeminiApiClient.Turn(it.speaker, it.text, it.timestamp) },
                scheduleContext = getScheduleSummary()
            )
            result.onSuccess { reply ->
                deliverJarvisResponseToUi(reply, shapeEmoji)
                saveChatHistory()
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
                    hideChatContainerSmoothly()
                }
                "THINKING", "SEARCHING" -> {
                    jarvisParticleView.setState(JarvisParticleState.SEARCHING)
                    try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('scan');", null) } catch (_: Exception) {}
                }
                "SPEAKING" -> {
                    jarvisParticleView.setState(JarvisParticleState.SPEAKING)
                    try { webViewLivingParticle.evaluateJavascript("window.jarvisSetPhase('speak');", null) } catch (_: Exception) {}
                    hideChatContainerSmoothly()
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
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

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
        val btnToggleGeminiEye = dialog.findViewById<ImageButton>(R.id.btnToggleGeminiKeyEye)
        val btnTestConnection = dialog.findViewById<MaterialButton>(R.id.btnTestConnection)
        val tvTestConnectionStatus = dialog.findViewById<TextView>(R.id.tvTestConnectionStatus)

        val etServer = dialog.findViewById<EditText>(R.id.etDialogServer)
        val etToken = dialog.findViewById<EditText>(R.id.etDialogToken)
        val cbBoot = dialog.findViewById<CheckBox>(R.id.cbDialogAutoStart)
        val cbQuron = dialog.findViewById<CheckBox>(R.id.cbDialogQuronAlways)
        val cbShowTextInput = dialog.findViewById<CheckBox>(R.id.cbDialogShowTextInput)
        val btnGrantNotif = dialog.findViewById<MaterialButton>(R.id.btnGrantNotificationAccess)

        activeRoute = try { AudioRoute.valueOf(prefs.audioRoute) } catch (_: Exception) { AudioRoute.PHONE }

        // Route selection styling helper
        fun updateRouteUi(route: AudioRoute) {
            val isPhone = (route == AudioRoute.PHONE)
            val isEarbuds = (route == AudioRoute.EARBUDS)
            val isPc = (route == AudioRoute.PC)

            btnPhone?.setBackgroundResource(if (isPhone) R.drawable.bg_card_selector_selected else R.drawable.bg_card_selector_normal)
            btnPhone?.setTextColor(Color.parseColor(if (isPhone) "#12A550" else "#64748B"))

            btnEarbuds?.setBackgroundResource(if (isEarbuds) R.drawable.bg_card_selector_selected else R.drawable.bg_card_selector_normal)
            btnEarbuds?.setTextColor(Color.parseColor(if (isEarbuds) "#12A550" else "#64748B"))

            btnPc?.setBackgroundResource(if (isPc) R.drawable.bg_card_selector_selected else R.drawable.bg_card_selector_normal)
            btnPc?.setTextColor(Color.parseColor(if (isPc) "#12A550" else "#64748B"))
        }

        var selectedMode = prefs.connectionMode

        fun updateModeUi(mode: String) {
            val isGemini = mode == "GEMINI_DIRECT"
            if (isGemini) {
                btnModeGemini?.setBackgroundResource(R.drawable.bg_settings_pill_active)
                btnModeGemini?.setTextColor(Color.WHITE)
                btnModePcBridge?.setBackgroundColor(Color.TRANSPARENT)
                btnModePcBridge?.setTextColor(Color.parseColor("#64748B"))

                layoutGemini?.visibility = View.VISIBLE
                layoutPc?.visibility = View.GONE
                btnPc?.visibility = View.GONE

                // Gemini rejimida PC marshruti tanlangan bo'lsa, ziddiyatni bartaraf etib avtomatik telefonga o'tkazamiz
                if (activeRoute == AudioRoute.PC) {
                    activeRoute = AudioRoute.PHONE
                    updateRouteUi(activeRoute)
                }
            } else {
                btnModePcBridge?.setBackgroundResource(R.drawable.bg_settings_pill_active)
                btnModePcBridge?.setTextColor(Color.WHITE)
                btnModeGemini?.setBackgroundColor(Color.TRANSPARENT)
                btnModeGemini?.setTextColor(Color.parseColor("#64748B"))

                layoutGemini?.visibility = View.GONE
                layoutPc?.visibility = View.VISIBLE
                btnPc?.visibility = View.VISIBLE
            }
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
        etServer?.setText(prefs.serverAddress)
        etToken?.setText(prefs.authToken)
        cbBoot?.isChecked = prefs.autoStartOnBoot
        cbQuron?.isChecked = prefs.quronCardVisible

        // Password eye toggler helper
        fun setupPasswordToggle(editText: EditText?, eyeButton: ImageButton?) {
            var isVisible = false
            eyeButton?.setOnClickListener {
                isVisible = !isVisible
                if (isVisible) {
                    editText?.transformationMethod = HideReturnsTransformationMethod.getInstance()
                    eyeButton.setImageResource(R.drawable.ic_visibility_off)
                } else {
                    editText?.transformationMethod = PasswordTransformationMethod.getInstance()
                    eyeButton.setImageResource(R.drawable.ic_visibility)
                }
                editText?.setSelection(editText.text?.length ?: 0)
            }
        }

        setupPasswordToggle(etGeminiKey, btnToggleGeminiEye)

        updateRouteUi(activeRoute)

        btnPhone?.setOnClickListener {
            vibrateBrief(20)
            activeRoute = AudioRoute.PHONE
            prefs.audioRoute = activeRoute.name
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }
        btnEarbuds?.setOnClickListener {
            vibrateBrief(20)
            activeRoute = AudioRoute.EARBUDS
            prefs.audioRoute = activeRoute.name
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }
        btnPc?.setOnClickListener {
            vibrateBrief(20)
            activeRoute = AudioRoute.PC
            prefs.audioRoute = activeRoute.name
            updateRouteUi(activeRoute)
            applyAudioRoute(activeRoute)
        }

        // Assistant Name, User Name & Gemini Live Voice (Permanently locked assistant name)
        val etDialogAssistantName = dialog.findViewById<TextView>(R.id.etDialogAssistantName)
        val etDialogUserName = dialog.findViewById<EditText>(R.id.etDialogUserName)
        etDialogAssistantName?.text = "JARVIS"
        etDialogUserName?.setText(prefs.userName)

        val btnVoiceCharon = dialog.findViewById<TextView>(R.id.btnVoiceCharon)
        val btnVoicePuck = dialog.findViewById<TextView>(R.id.btnVoicePuck)
        val btnVoiceKore = dialog.findViewById<TextView>(R.id.btnVoiceKore)
        val btnVoiceFenrir = dialog.findViewById<TextView>(R.id.btnVoiceFenrir)
        val btnVoiceAoede = dialog.findViewById<TextView>(R.id.btnVoiceAoede)

        val voiceMap = mapOf(
            "Charon" to btnVoiceCharon,
            "Puck" to btnVoicePuck,
            "Kore" to btnVoiceKore,
            "Fenrir" to btnVoiceFenrir,
            "Aoede" to btnVoiceAoede
        )

        var selectedVoice = prefs.assistantVoice.ifBlank { "Kore" }

        fun updateVoiceUi(chosen: String) {
            voiceMap.forEach { (name, btn) ->
                val isSel = name.equals(chosen, ignoreCase = true)
                btn?.setBackgroundResource(if (isSel) R.drawable.bg_voice_pill_selected else R.drawable.bg_voice_pill_normal)
                btn?.setTextColor(Color.parseColor(if (isSel) "#12A550" else "#475569"))
            }
        }
        updateVoiceUi(selectedVoice)

        // Test buttons
        val btnTestVoice = dialog.findViewById<MaterialButton>(R.id.btnTestVoice)
        val btnTestMic = dialog.findViewById<MaterialButton>(R.id.btnTestMic)
        val tvAudioTestStatus = dialog.findViewById<TextView>(R.id.tvAudioTestStatus)

        voiceMap.forEach { (name, btn) ->
            btn?.setOnClickListener {
                selectedVoice = name
                updateVoiceUi(selectedVoice)
                vibrateBrief(20)
                tvAudioTestStatus?.visibility = View.VISIBLE
                tvAudioTestStatus?.setTextColor(Color.parseColor("#12A550"))
                tvAudioTestStatus?.text = "🔊 $selectedVoice ovozi..."
                playVoiceAsset(selectedVoice)
            }
        }

        btnTestConnection?.setOnClickListener {
            tvTestConnectionStatus?.visibility = View.VISIBLE
            tvTestConnectionStatus?.setTextColor(Color.parseColor("#3B82F6"))
            tvTestConnectionStatus?.text = "⏳ Ulanish tekshirilmoqda..."

            if (selectedMode == "GEMINI_DIRECT") {
                val key = etGeminiKey?.text?.toString()?.trim() ?: ""
                if (key.isBlank()) {
                    tvTestConnectionStatus?.setTextColor(Color.parseColor("#EF4444"))
                    tvTestConnectionStatus?.text = "⚠️ Gemini API kalitini kiriting"
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    val result = GeminiApiClient.generateReply(
                        apiKey = key,
                        userPrompt = "Salom",
                        activeDomain = "Test",
                        userName = "Axmadulloh"
                    )
                    result.onSuccess {
                        tvTestConnectionStatus?.setTextColor(Color.parseColor("#12A550"))
                        tvTestConnectionStatus?.text = "✅ Gemini API ulandi!"
                    }.onFailure { err ->
                        tvTestConnectionStatus?.setTextColor(Color.parseColor("#EF4444"))
                        tvTestConnectionStatus?.text = "❌ Xatolik: ${err.message?.take(40)}"
                    }
                }
            } else {
                val serverStr = etServer?.text?.toString()?.trim() ?: ""
                if (serverStr.isBlank()) {
                    tvTestConnectionStatus?.setTextColor(Color.parseColor("#EF4444"))
                    tvTestConnectionStatus?.text = "⚠️ Server IP manzilini kiriting"
                    return@setOnClickListener
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val clean = serverStr.replace("ws://", "").replace("http://", "").replace("https://", "").replace("/", "")
                        val parts = clean.split(":")
                        val host = parts[0]
                        val port = if (parts.size > 1) parts[1].filter { it.isDigit() }.toIntOrNull() ?: 8000 else 8000
                        val socket = Socket()
                        socket.connect(InetSocketAddress(host, port), 2500)
                        socket.close()
                        withContext(Dispatchers.Main) {
                            tvTestConnectionStatus?.setTextColor(Color.parseColor("#12A550"))
                            tvTestConnectionStatus?.text = "✅ Kompyuter serveri ulandi!"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            tvTestConnectionStatus?.setTextColor(Color.parseColor("#EF4444"))
                            tvTestConnectionStatus?.text = "❌ Serverga ulanib bo'lmadi"
                        }
                    }
                }
            }
        }

        btnTestVoice?.setOnClickListener {
            tvAudioTestStatus?.visibility = View.VISIBLE
            tvAudioTestStatus?.setTextColor(Color.parseColor("#12A550"))
            tvAudioTestStatus?.text = "🔊 $selectedVoice ovozi yangramoqda..."
            prefs.assistantVoice = selectedVoice
            playVoiceAsset(selectedVoice)
        }

        btnTestMic?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                tvAudioTestStatus?.visibility = View.VISIBLE
                tvAudioTestStatus?.setTextColor(Color.parseColor("#EF4444"))
                tvAudioTestStatus?.text = "⚠️ Mikrofon ruxsati berilmagan!"
                return@setOnClickListener
            }

            tvAudioTestStatus?.visibility = View.VISIBLE
            tvAudioTestStatus?.setTextColor(Color.parseColor("#3B82F6"))
            tvAudioTestStatus?.text = "🎙️ Mikrofonga gapiring (2 soniya)..."

            lifecycleScope.launch(Dispatchers.IO) {
                var maxRms = 0f
                var record: AudioRecord? = null
                try {
                    val sampleRate = 16000
                    val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    val bufferSize = if (minBuf > 0) minBuf else 2048
                    record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        record.startRecording()
                        val buf = ShortArray(bufferSize / 2)
                        val startTime = System.currentTimeMillis()
                        while (System.currentTimeMillis() - startTime < 2000) {
                            val read = record.read(buf, 0, buf.size)
                            if (read > 0) {
                                var sumSq = 0.0
                                for (i in 0 until read) {
                                    val s = buf[i].toDouble()
                                    sumSq += s * s
                                }
                                val rms = Math.sqrt(sumSq / read).toFloat()
                                if (rms > maxRms) maxRms = rms
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Mic test error: ${e.message}")
                } finally {
                    try {
                        record?.stop()
                        record?.release()
                    } catch (_: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    tvAudioTestStatus?.visibility = View.VISIBLE
                    if (maxRms > 50f) {
                        val percent = ((maxRms / 3000f).coerceIn(0.1f, 1f) * 100).toInt()
                        tvAudioTestStatus?.setTextColor(Color.parseColor("#12A550"))
                        tvAudioTestStatus?.text = "✅ Mikrofon a'lo darajada ishlayapti ($percent%)"
                    } else {
                        tvAudioTestStatus?.setTextColor(Color.parseColor("#F59E0B"))
                        tvAudioTestStatus?.text = "⚠️ Ovoz juda past yoki eshitilmadi"
                    }
                }
            }
        }

        btnGrantNotif?.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Sozlamalarni ochishda xatolik", Toast.LENGTH_SHORT).show()
            }
        }

        val btnGrantOverlay = dialog.findViewById<MaterialButton>(R.id.btnGrantOverlayAccess)
        fun updateOverlayBtnState() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                btnGrantOverlay?.text = "✅ Boshqa ilovalar ustida ruxsat berilgan"
                btnGrantOverlay?.isEnabled = false
                btnGrantOverlay?.alpha = 0.7f
            } else {
                btnGrantOverlay?.text = "🪟 Boshqa ilovalar ustida ko'rinish ruxsati"
                btnGrantOverlay?.isEnabled = true
                btnGrantOverlay?.alpha = 1.0f
            }
        }
        updateOverlayBtnState()

        btnGrantOverlay?.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ruxsatlar oynasini ochib bo'lmadi", Toast.LENGTH_SHORT).show()
                }
            }
        }


        // Sticky Bottom Save Bar
        val tvSaveFeedback = dialog.findViewById<TextView>(R.id.tvSaveFeedback)
        val btnSave = dialog.findViewById<MaterialButton>(R.id.btnDialogSave)

        btnSave?.setOnClickListener {
            vibrateBrief(30)
            val newKey = etGeminiKey?.text?.toString()?.trim() ?: ""
            prefs.geminiApiKey = newKey
            prefs.connectionMode = selectedMode
            prefs.showTextInputBar = cbShowTextInput?.isChecked == true
            prefs.serverAddress = etServer?.text?.toString()?.trim() ?: prefs.serverAddress
            prefs.authToken = etToken?.text?.toString()?.trim() ?: prefs.authToken
            prefs.autoStartOnBoot = cbBoot?.isChecked == true
            prefs.quronCardVisible = cbQuron?.isChecked == true

            prefs.assistantName = "JARVIS"
            prefs.userName = etDialogUserName?.text?.toString()?.trim()?.ifBlank { "Axmadulloh sir" } ?: "Axmadulloh sir"
            prefs.assistantVoice = selectedVoice

            val showBar = prefs.showTextInputBar
            bottomControlGroup.visibility = if (showBar) View.VISIBLE else View.GONE
            btnFloatingKeyboard.visibility = if (showBar) View.GONE else View.VISIBLE

            prefs.audioRoute = activeRoute.name
            applyAudioRoute(activeRoute)

            // Re-apply settings & restart service with newly selected voice
            startJarvisService()

            tvSaveFeedback?.visibility = View.VISIBLE
            tvSaveFeedback?.postDelayed({
                try {
                    dialog.dismiss()
                } catch (_: Exception) {}
                Toast.makeText(this, "Sozlamalar saqlandi", Toast.LENGTH_SHORT).show()
            }, 600)
        }

        dialog.show()
    }

    private fun applyAudioRoute(route: AudioRoute) {
        try {
            val intent = Intent(this, JarvisForegroundService::class.java).apply {
                action = JarvisForegroundService.ACTION_SET_ROUTE
                putExtra(JarvisForegroundService.EXTRA_ROUTE, route.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "applyAudioRoute error: ${e.message}")
        }
    }

    fun setTvFullscreen(enabled: Boolean) {
        runOnUiThread {
            isTvFullscreen = enabled
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (enabled) {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                topBar.visibility = View.GONE
                findViewById<View>(R.id.headerWebView)?.visibility = View.GONE
            } else {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.show(WindowInsetsCompat.Type.systemBars())
                topBar.visibility = View.VISIBLE
                findViewById<View>(R.id.headerWebView)?.visibility = View.VISIBLE
            }
        }
    }

    private fun exitCustomViewFullscreen() {
        if (customView != null) {
            val decor = window.decorView as FrameLayout
            decor.removeView(customView)
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
            containerWebView.visibility = View.VISIBLE
            setTvFullscreen(false)
        }
    }

    private fun createDashboardChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                launchCameraCapture()
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                setTvFullscreen(true)
                val decor = window.decorView as FrameLayout
                decor.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                containerWebView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                exitCustomViewFullscreen()
            }
        }
    }

    // ── Arabic Quran Voice Recitation Checker Bridge ─────────────────
    private var quronSpeechRecognizer: SpeechRecognizer? = null

    private fun startArabicRecitationCheck(surah: Int, ayah: Int, expectedArabic: String) {
        runOnUiThread {
            try {
                stopMobileVoiceRecognition()
                try { tts?.stop() } catch (_: Exception) {}

                quronSpeechRecognizer?.destroy()
                quronSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            webViewDashboard.evaluateJavascript("if (window.onQuronRecitationState) window.onQuronRecitationState('listening');", null)
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {
                            val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            webViewDashboard.evaluateJavascript("if (window.onQuronMicLevel) window.onQuronMicLevel($norm);", null)
                        }
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            webViewDashboard.evaluateJavascript("if (window.onQuronRecitationState) window.onQuronRecitationState('evaluating');", null)
                        }
                        override fun onError(error: Int) {
                            Log.d("MainActivity", "Quron SpeechRecognizer error: $error")
                            webViewDashboard.evaluateJavascript("if (window.onQuronRecitationError) window.onQuronRecitationError($error);", null)
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                            val best = matches.firstOrNull() ?: ""
                            webViewDashboard.evaluateJavascript("if (window.onQuronFinalResult) window.onQuronFinalResult(${JSONObject.quote(best)}, $ayah);", null)
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                            val partial = matches.firstOrNull() ?: ""
                            if (partial.isNotEmpty()) {
                                webViewDashboard.evaluateJavascript("if (window.onQuronPartialResult) window.onQuronPartialResult(${JSONObject.quote(partial)});", null)
                            }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ar-SA")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                quronSpeechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "startArabicRecitationCheck error: ${e.message}")
                webViewDashboard.evaluateJavascript("if (window.onQuronRecitationError) window.onQuronRecitationError(-1);", null)
            }
        }
    }

    private fun stopArabicRecitationCheck() {
        runOnUiThread {
            try {
                quronSpeechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    // ── In-App WebView (Qur'on, TV yoki Havolalar) ────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun openQuronLesson(url: String) {
        isQuronActive = true
        stopMobileVoiceRecognition()
        try { tts?.stop() } catch (_: Exception) {}

        containerWebView.visibility = View.VISIBLE
        tvWebTitle.text = "Qur'on Ustozi"

        // Ruxsatlarni tekshirish (Kamera va Ovoz yozish)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val permsNeeded = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permsNeeded.add(Manifest.permission.CAMERA)
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permsNeeded.add(Manifest.permission.RECORD_AUDIO)
            }
            if (permsNeeded.isNotEmpty()) {
                requestPermissions(permsNeeded.toTypedArray(), 1013)
            }
        }

        webViewDashboard.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
        }
        webViewDashboard.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (failingUrl?.contains("77.83.206.138") == true) {
                    view?.loadUrl("file:///android_asset/quron/index.html")
                }
            }
        }
        webViewDashboard.addJavascriptInterface(TvBridge(), "Android")
        webViewDashboard.webChromeClient = createDashboardChromeClient()

        val targetUrl = if (url.isNotBlank()) url else "https://jarvis.77.83.206.138.sslip.io/quron"
        quronWebUrl = targetUrl
        webViewDashboard.loadUrl(targetUrl)
    }

    inner class TvBridge {
        @android.webkit.JavascriptInterface
        fun takeNativePhoto() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        launchCameraCapture()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                } else {
                    launchCameraCapture()
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun startListening() {
            runOnUiThread {
                toggleMicFromUi()
            }
        }

        @android.webkit.JavascriptInterface
        fun sendCommand(text: String) {
            runOnUiThread {
                vibrateBrief(20)
                sendJarvisCommand(text, "📺")
            }
        }

        @android.webkit.JavascriptInterface
        fun openAttachmentPicker() {
            runOnUiThread {
                showAttachmentSourceChooser()
            }
        }

        @android.webkit.JavascriptInterface
        fun toggleFullscreen(enable: Boolean) {
            setTvFullscreen(enable)
        }

        @android.webkit.JavascriptInterface
        fun openQuronAi() {
            runOnUiThread {
                openQuronLesson("https://jarvis.77.83.206.138.sslip.io/quron")
            }
        }

        @android.webkit.JavascriptInterface
        fun startQuronVoiceCheck(surah: Int, ayah: Int, expectedArabic: String) {
            startArabicRecitationCheck(surah, ayah, expectedArabic)
        }

        @android.webkit.JavascriptInterface
        fun stopQuronVoiceCheck() {
            stopArabicRecitationCheck()
        }

        @android.webkit.JavascriptInterface
        fun openWorldTvTab(tabName: String) {
            runOnUiThread {
                openWorldTv("https://jarvis.77.83.206.138.sslip.io/tv?tab=$tabName")
            }
        }

        @android.webkit.JavascriptInterface
        fun closeTv() {
            runOnUiThread {
                if (customView != null) {
                    exitCustomViewFullscreen()
                }
                if (isTvFullscreen) {
                    setTvFullscreen(false)
                }
                val title = tvWebTitle.text?.toString() ?: ""
                isQuronActive = false
                containerWebView.visibility = View.GONE
                webViewDashboard.loadUrl("about:blank")
                when {
                    title.contains("Kamera", ignoreCase = true) -> showCaption("📷 Kamera yopildi")
                    title.contains("Qur'on", ignoreCase = true) -> showCaption("📖 Qur'on Ustozi yopildi")
                    else -> showCaption("⏹ Telekanal yopildi")
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun openExternalBrowser(url: String) {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        @android.webkit.JavascriptInterface
        fun speakText(text: String) {
            runOnUiThread {
                if (!isQuronActive) {
                    speakJarvis(text)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun openWorldTv(url: String) {
        isQuronActive = false
        containerWebView.visibility = View.VISIBLE

        webViewDashboard.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
        }
        webViewDashboard.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (failingUrl?.contains("77.83.206.138") == true) {
                    view?.loadUrl("file:///android_asset/tv/index.html")
                }
            }
        }
        webViewDashboard.webChromeClient = createDashboardChromeClient()

        webViewDashboard.addJavascriptInterface(TvBridge(), "Android")

        val targetUrl = if (url.isNotBlank()) url else "https://jarvis.77.83.206.138.sslip.io/tv"
        quronWebUrl = targetUrl

        val currentLoaded = webViewDashboard.url ?: ""
        if (currentLoaded.contains("/tv") || currentLoaded.contains("tv/index.html")) {
            val uri = Uri.parse(targetUrl)
            val chName = uri.getQueryParameter("channel")
            val tabName = uri.getQueryParameter("tab") ?: if (!chName.isNullOrEmpty()) "tv" else "home"
            val js = buildString {
                append("if (window.switchTab) window.switchTab('$tabName');")
                if (!chName.isNullOrEmpty()) {
                    append("if (window.playChannelByName) window.playChannelByName(${JSONObject.quote(chName)});")
                }
            }
            webViewDashboard.evaluateJavascript(js, null)
        } else {
            webViewDashboard.loadUrl(targetUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun playInAppYouTube(urlOrQuery: String, title: String) {
        runOnUiThread {
            try {
                val lowerCheck = (urlOrQuery + " " + title).lowercase()
                val adultWords = listOf("porno", "porn", "seks", "sex", "sekis", "faxsh", "intim", "yalang'och", "yalangoch", "18+", "erotik", "erotika", "xxx", "hentai", "nude", "boobs", "vaginal", "anal", "fahisha", "jalab", "fohisha")
                if (adultWords.any { lowerCheck.contains(it) }) {
                    Toast.makeText(this@MainActivity, "⚠️ 18+ videolarni ko'rish qat'iyan taqiqlanadi!", Toast.LENGTH_LONG).show()
                    showCaption("⚠️ 18+ video taqiqlangan!")
                    return@runOnUiThread
                }

                val bannedPreachers = listOf("abdulloh zufar", "abu saloh", "rashod qori", "sodiq samarqandiy", "yahyo turkistoniy", "abdulloh buxoriy", "hizb ut-tahrir", "ishid")
                if (bannedPreachers.any { lowerCheck.contains(it) }) {
                    Toast.makeText(this@MainActivity, "⚠️ Oliy Sud taqiqlagan shaxslar videolari bloklangan!", Toast.LENGTH_LONG).show()
                    showCaption("⚠️ Taqiqlangan shaxs videolari bloklangan")
                    return@runOnUiThread
                }

                // Parse Song title and Artist from title (e.g. "Hurshid Rasulov - Bo'laverdi")
                var songName = title.trim()
                var artistName = "YouTube Musiqa"
                if (title.contains("-")) {
                    val parts = title.split("-", limit = 2)
                    artistName = parts[0].trim()
                    songName = parts[1].trim()
                } else if (title.contains("–")) {
                    val parts = title.split("–", limit = 2)
                    artistName = parts[0].trim()
                    songName = parts[1].trim()
                }

                tvMediaSongTitle.text = songName.ifEmpty { "Musiqa ijrosi" }
                tvMediaArtist.text = artistName.ifEmpty { "JARVIS Player" }
                tvMediaVideoTitle.text = "🎵 $songName"

                cardInlineMedia.visibility = View.VISIBLE
                isMediaPlaying = true
                btnMediaPlayPause.setImageResource(R.drawable.ic_media_pause)

                webViewInlineVideo.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                webViewInlineVideo.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }
                }
                webViewInlineVideo.webChromeClient = WebChromeClient()

                val videoId = when {
                    urlOrQuery.contains("embed/") -> urlOrQuery.substringAfter("embed/").substringBefore("?").substringBefore("&").substringBefore("/").trim()
                    urlOrQuery.contains("watch?v=") -> urlOrQuery.substringAfter("watch?v=").substringBefore("&").substringBefore("#").trim()
                    urlOrQuery.contains("youtu.be/") -> urlOrQuery.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").trim()
                    urlOrQuery.length == 11 && urlOrQuery.matches(Regex("[a-zA-Z0-9_-]{11}")) -> urlOrQuery
                    else -> {
                        val m = Regex("(?:v=|/embed/|youtu\\.be/|/v/|watch\\?v=)([a-zA-Z0-9_-]{11})").find(urlOrQuery)
                        m?.groupValues?.get(1)
                    }
                }

                if (!videoId.isNullOrBlank() && !videoId.startsWith("http")) {
                    val thumbUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val input = java.net.URL(thumbUrl).openStream()
                            val bmp = BitmapFactory.decodeStream(input)
                            input.close()
                            if (bmp != null) {
                                withContext(Dispatchers.Main) {
                                    ivMediaThumb.setImageBitmap(bmp)
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                * { margin:0; padding:0; box-sizing:border-box; }
                                html, body { width:100%; height:100%; background:#000000; overflow:hidden; }
                                iframe { width:100%; height:100%; border:none; display:block; }
                            </style>
                        </head>
                        <body>
                            <iframe id="ytPlayer"
                                src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&rel=0&controls=1&origin=https://www.youtube-nocookie.com" 
                                allow="autoplay; encrypted-media; picture-in-picture" 
                                allowfullscreen>
                            </iframe>
                            <script>
                                function sendCmd(func, args) {
                                    var iframe = document.getElementById('ytPlayer');
                                    if (iframe && iframe.contentWindow) {
                                        iframe.contentWindow.postMessage(JSON.stringify({
                                            "event": "command",
                                            "func": func,
                                            "args": args || []
                                        }), "*");
                                    }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    webViewInlineVideo.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                } else {
                    val playUrl = when {
                        urlOrQuery.startsWith("http://") || urlOrQuery.startsWith("https://") -> urlOrQuery
                        else -> "https://m.youtube.com/results?search_query=" + Uri.encode(urlOrQuery)
                    }
                    webViewInlineVideo.loadUrl(playUrl)
                }

                showCaption("▶ Ijro: $songName")
            } catch (e: Exception) {
                Log.e("MainActivity", "playInAppYouTube error: ${e.message}")
            }
        }
    }

    private fun triggerWelcomeGreetingIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - prefs.lastWelcomeTimestamp > 60_000L) { // 1 minute interval
            prefs.lastWelcomeTimestamp = now
            val greeting = "Salom Axmadulloh sir! Jarvis xizmatingizda. Qanday yordam bera olaman?"
            showJarvisTurnCaption(greeting)
            playWelcomeAsset(prefs.assistantVoice)
        }
    }

    private fun getHttpBaseUrl(): String {
        val raw = prefs.serverAddress.trim()
        val host = raw
            .removePrefix("ws://")
            .removePrefix("wss://")
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val scheme = if (raw.startsWith("http://") || raw.startsWith("ws://")) "http" else "https"
        return "$scheme://$host"
    }

    // ── Ruxsatlar va Xizmatni Ishga Tushirish ──────────────────────────────
    private fun checkPermissionsAndStart() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        // Boshqa ilovalar ustida ko'rinish ruxsati (Android 10+ Background Activity Start uchun zarur)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                val reqOverlay = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(reqOverlay)
                Toast.makeText(
                    this,
                    "Axmadulloh sir, boshqa ilovalarda turganda ham YouTube va ilovalarni ochish uchun 'Boshqa ilovalar ustida ko'rinish' ruxsatini yoqing.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {}
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
            addAction(JarvisForegroundService.BROADCAST_WORLD_TV_UPDATE)
            addAction(JarvisForegroundService.BROADCAST_RMS)
            addAction(JarvisForegroundService.BROADCAST_INPUT_TRANSCRIPT)
            addAction(JarvisForegroundService.BROADCAST_CAPTION)
            addAction(JarvisForegroundService.BROADCAST_TURN_COMPLETE)
            addAction(JarvisForegroundService.BROADCAST_PC_CONNECTION_FAILED)
            addAction(JarvisForegroundService.BROADCAST_DOMAIN_CHANGE)
            addAction(JarvisForegroundService.BROADCAST_MEDIA_PLAY)
            addAction(JarvisForegroundService.BROADCAST_MEDIA_PAUSE)
            addAction(JarvisForegroundService.BROADCAST_MEDIA_STOP)
            addAction(JarvisForegroundService.BROADCAST_MEDIA_NEXT)
            addAction(JarvisForegroundService.BROADCAST_MEDIA_PREV)
            addAction(JarvisForegroundService.BROADCAST_CONNECTION_GREETING)
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
        stopCurrentAudio()
        try {
            webViewLivingParticle.removeJavascriptInterface("AndroidBridge")
            webViewLivingParticle.loadUrl("about:blank")
            webViewLivingParticle.destroy()
        } catch (_: Exception) {}
        speechRecognizer?.destroy()
        speechRecognizer = null
        quronSpeechRecognizer?.destroy()
        quronSpeechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
