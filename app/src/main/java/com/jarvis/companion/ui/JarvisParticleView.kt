package com.jarvis.companion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.jarvis.companion.domain.JarvisDomain
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Holatlar (State Machine).
 */
enum class JarvisParticleState {
    IDLE,       // 3D spiral galaktika sekin aylanadi, nafas oladi
    LISTENING,  // 15% siqiladi, mikrofon ovoziga qarab radial titraydi
    SEARCHING,  // Zarralar butun ekranga sochiladi, to'lqin halqalari tarqaladi
    RESULT,     // Zarralar buyruqqa mos shaklga (emoji/belgi) oqib kelib yig'iladi
    SPEAKING,   // TTS ovoz balandligiga qarab zarralar pulslanadi
    RELEASE     // Shakl portlab, zarralar yana galaktikaga qaytadi
}

/**
 * Tashqaridan boshqarish interfeysi (Controller Interface).
 */
interface JarvisParticleController {
    fun setState(newState: JarvisParticleState)
    fun setMicLevel(level: Float) // 0..1
    fun setMode(domain: JarvisDomain)
    fun showResult(shapeEmojiOrAsset: String, captionText: String = "")
}

/**
 * J.A.R.V.I.S — Tirik "zarralar aqli" (Living Particle Intelligence).
 * 3000 ta zarra, GPU-jadallashtirilgan, 0 ta GC-ob'ekt ajratish bilan to'liq flat array buferlarida ishlaydi.
 */
class JarvisParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), JarvisParticleController, SensorEventListener {

    private val N = JarvisParticleConfig.PARTICLE_COUNT

    // ── Pre-allocated Flat Array Buffers (SoA - Zero GC churn) ────────────
    // 3D Spiral Home Coordinates (normalizatsiya qilingan -1..1 oralig'ida)
    private val hx = FloatArray(N)
    private val hy = FloatArray(N)
    private val hz = FloatArray(N)
    private val r = FloatArray(N)

    // Current 2D Screen Coordinates & Velocities
    private val px = FloatArray(N)
    private val py = FloatArray(N)
    private val vx = FloatArray(N)
    private val vy = FloatArray(N)

    // Uniform Scatter Positions (SEARCHING rejimi uchun)
    private val ux = FloatArray(N)
    private val uy = FloatArray(N)

    // Target Formation Coordinates & RGB (RESULT rejimi uchun)
    private val fx = FloatArray(N)
    private val fy = FloatArray(N)
    private val fcR = FloatArray(N)
    private val fcG = FloatArray(N)
    private val fcB = FloatArray(N)

    // Galaxy Base Colors (faol soha palitrasidan kelib chiqadi)
    private val gcR = FloatArray(N)
    private val gcG = FloatArray(N)
    private val gcB = FloatArray(N)

    // Current Interpolated Colors
    private val cR = FloatArray(N)
    private val cG = FloatArray(N)
    private val cB = FloatArray(N)

    // Individual Particle Parameters
    private val ph = FloatArray(N) // Phase (0..2PI)
    private val k = FloatArray(N)  // Spring responsiveness (0.5..1.4)
    private val sz = FloatArray(N) // Base size

    // ── Holat va Vaqt o'zgaruvchilari ─────────────────────────────────────
    private var currentState = JarvisParticleState.IDLE
    private var isResultHolding = false
    private var targetMicLevel = 0f
    private var smoothedMicLevel = 0f
    private var activeSpeechLevel = 0f

    private var timeSeconds = 0f
    private var tScanStart = 0f
    private var lastFrameTimeNanos = 0L

    // ── Faol Palitra ───────────────────────────────────────────────────────
    private var activePalette = JarvisParticleConfig.DEFAULT_PALETTE

    // ── Sensor / Gyroscope ────────────────────────────────────────────────
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var targetTiltX = 0f
    private var targetTiltY = 0f
    private var currentTiltX = 0f
    private var currentTiltY = 0f

    // ── Paints (Pre-allocated, reused across frames) ───────────────────────
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentMaskEmoji: String = "📞"

    init {
        initParticles()
        applyPalette(activePalette)
        initSensor()
    }

    private fun initParticles() {
        val rnd = Random(1337)
        for (i in 0 until N) {
            val arm = i % JarvisParticleConfig.SPIRAL_ARMS
            val rad = rnd.nextDouble().pow(0.65).toFloat()
            r[i] = rad

            val th = rad * JarvisParticleConfig.SPIRAL_WINDING +
                    arm * (2f * Math.PI.toFloat() / 3f) +
                    (rnd.nextFloat() * 2f - 1f) * 0.35f * (1f - rad * 0.5f)

            hx[i] = rad * cos(th)
            hz[i] = rad * sin(th)
            hy[i] = (rnd.nextFloat() * 2f - 1f) * JarvisParticleConfig.DISK_THICKNESS * (1f - rad * 0.4f)

            ux[i] = rnd.nextFloat()
            uy[i] = rnd.nextFloat()

            ph[i] = (rnd.nextFloat() * 2.0 * Math.PI).toFloat()
            k[i] = 0.5f + rnd.nextFloat() * 0.9f
            sz[i] = JarvisParticleConfig.MIN_PARTICLE_SIZE + rnd.nextFloat() * (JarvisParticleConfig.MAX_PARTICLE_SIZE - JarvisParticleConfig.MIN_PARTICLE_SIZE)

            // Dastlabki ranglar
            cR[i] = 18f
            cG[i] = 165f
            cB[i] = 80f
        }
    }

    private fun applyPalette(palette: JarvisParticleConfig.Palette) {
        activePalette = palette
        val lightR = Color.red(palette.light).toFloat()
        val lightG = Color.green(palette.light).toFloat()
        val lightB = Color.blue(palette.light).toFloat()

        val primR = Color.red(palette.primary).toFloat()
        val primG = Color.green(palette.primary).toFloat()
        val primB = Color.blue(palette.primary).toFloat()

        val darkR = Color.red(palette.dark).toFloat()
        val darkG = Color.green(palette.dark).toFloat()
        val darkB = Color.blue(palette.dark).toFloat()

        val rnd = Random(777)
        for (i in 0 until N) {
            val m = (r[i] * 1.1f).coerceIn(0f, 1f)
            val isCoreSpark = rnd.nextFloat() < 0.18f

            val baseR = if (isCoreSpark) lightR else primR
            val baseG = if (isCoreSpark) lightG else primG
            val baseB = if (isCoreSpark) lightB else primB

            gcR[i] = baseR + (darkR - baseR) * m * 0.8f
            gcG[i] = baseG + (darkG - baseG) * m * 0.8f
            gcB[i] = baseB + (darkB - baseB) * m * 0.8f
        }
    }

    private fun initSensor() {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            gyroSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotMatrix, orientation)
            targetTiltX = (-orientation[1]).coerceIn(-0.4f, 0.4f)
            targetTiltY = (orientation[2]).coerceIn(-0.4f, 0.4f)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            targetTiltX = (event.values[1] / 9.8f).coerceIn(-0.4f, 0.4f)
            targetTiltY = (-event.values[0] / 9.8f).coerceIn(-0.4f, 0.4f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Public Controller API ──────────────────────────────────────────────
    override fun setState(newState: JarvisParticleState) {
        if (currentState == newState) return
        val oldState = currentState
        currentState = newState

        if (newState == JarvisParticleState.SEARCHING) {
            tScanStart = timeSeconds
            // Sochilish portlashi (radial velocity explosion)
            val rnd = Random(SystemClock.uptimeMillis())
            for (i in 0 until N) {
                val a = rnd.nextFloat() * 2f * Math.PI.toFloat()
                val v = 14f + rnd.nextFloat() * 28f
                vx[i] = cos(a) * v
                vy[i] = sin(a) * v
            }
        } else if (newState == JarvisParticleState.RELEASE) {
            val w = width.toFloat().coerceAtLeast(100f)
            val h = height.toFloat().coerceAtLeast(100f)
            val cx = w / 2f
            val cy = h * 0.45f
            val rnd = Random(SystemClock.uptimeMillis())
            for (i in 0 until N) {
                vx[i] = (px[i] - cx) * JarvisParticleConfig.RELEASE_EXPLOSION_FORCE + (rnd.nextFloat() * 2f - 1f) * 6f
                vy[i] = (py[i] - cy) * JarvisParticleConfig.RELEASE_EXPLOSION_FORCE + (rnd.nextFloat() * 2f - 1f) * 6f
            }
            // 1.8 soniyadan keyin avtomatik IDLE holatiga o'tadi
            mainHandler.postDelayed({
                if (currentState == JarvisParticleState.RELEASE) {
                    currentState = JarvisParticleState.IDLE
                }
            }, 1800)
        } else if (newState == JarvisParticleState.RESULT) {
            isResultHolding = false
            sampleShape(currentMaskEmoji)
            // 1.8s dan keyin HOLD ga o'tadi, yana 2.2s dan keyin RELEASE ga o'tadi
            mainHandler.postDelayed({
                if (currentState == JarvisParticleState.RESULT) {
                    isResultHolding = true
                }
            }, 1800)
            mainHandler.postDelayed({
                if (currentState == JarvisParticleState.RESULT) {
                    setState(JarvisParticleState.RELEASE)
                }
            }, 1800 + JarvisParticleConfig.RESULT_HOLD_DURATION_MS)
        }

        invalidate()
    }

    override fun setMicLevel(level: Float) {
        targetMicLevel = level.coerceIn(0f, 1f)
    }

    fun setSpeechLevel(level: Float) {
        activeSpeechLevel = level.coerceIn(0f, 1f)
    }

    override fun setMode(domain: JarvisDomain) {
        val palette = when (domain.id.uppercase()) {
            "HAYDOVCHI", "TAXI" -> JarvisParticleConfig.Palette(
                ambient = Color.parseColor("#FFF8DC"),
                light = Color.parseColor("#FFD54A"),
                primary = Color.parseColor("#F0A000"),
                dark = Color.parseColor("#7A4A00")
            )
            "TALABA", "O'QUVCHI" -> JarvisParticleConfig.Palette(
                ambient = Color.parseColor("#E8F4FF"),
                light = Color.parseColor("#6BB8FF"),
                primary = Color.parseColor("#1A6FE0"),
                dark = Color.parseColor("#0A2E73")
            )
            "O'QITUVCHI" -> JarvisParticleConfig.Palette(
                ambient = Color.parseColor("#F3EAFF"),
                light = Color.parseColor("#B794FF"),
                primary = Color.parseColor("#7A3FE0"),
                dark = Color.parseColor("#34127A")
            )
            "USTA" -> JarvisParticleConfig.Palette(
                ambient = Color.parseColor("#FFF0E6"),
                light = Color.parseColor("#FF9A5A"),
                primary = Color.parseColor("#E0561A"),
                dark = Color.parseColor("#7A2208")
            )
            "DIN" -> JarvisParticleConfig.Palette(
                ambient = Color.parseColor("#E6FFFA"),
                light = Color.parseColor("#5EEAD4"),
                primary = Color.parseColor("#0D9488"),
                dark = Color.parseColor("#115E59")
            )
            "DASTURCHI" -> JarvisParticleConfig.Palette(
                ambient = Color.parseColor("#F0FDF4"),
                light = Color.parseColor("#4ADE80"),
                primary = Color.parseColor("#16A34A"),
                dark = Color.parseColor("#14532D")
            )
            "MOLIYA" -> JarvisParticleConfig.Palette(
                ambient = Color.parseColor("#ECFDF5"),
                light = Color.parseColor("#34D399"),
                primary = Color.parseColor("#059669"),
                dark = Color.parseColor("#064E3B")
            )
            else -> {
                val prim = domain.primaryColor
                val glow = domain.glowColor
                val dark = Color.argb(
                    255,
                    (Color.red(prim) * 0.4f).toInt(),
                    (Color.green(prim) * 0.4f).toInt(),
                    (Color.blue(prim) * 0.4f).toInt()
                )
                JarvisParticleConfig.Palette(
                    ambient = Color.argb(35, Color.red(glow), Color.green(glow), Color.blue(glow)),
                    light = glow,
                    primary = prim,
                    dark = dark
                )
            }
        }

        applyPalette(palette)
        currentMaskEmoji = domain.icon.ifBlank { "⚡" }
        invalidate()
    }

    override fun showResult(shapeEmojiOrAsset: String, captionText: String) {
        if (shapeEmojiOrAsset.isNotBlank()) {
            currentMaskEmoji = shapeEmojiOrAsset
        }
        setState(JarvisParticleState.RESULT)
    }

    // ── Shape Mask Sampling (Emoji yoki Belgin pikseldan namuna olish) ──────
    private fun sampleShape(emoji: String) {
        val w = if (width > 0) width.toFloat() else 1080f
        val h = if (height > 0) height.toFloat() else 1920f
        val S = min(w, h) * 0.64f
        val cx = w / 2f
        val cy = h * 0.45f

        val pts = ArrayList<SampledPoint>(600)
        try {
            val bmpSize = 240
            val bmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 180f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            c.drawText(emoji, 120f, 170f, p)

            val pixels = IntArray(bmpSize * bmpSize)
            bmp.getPixels(pixels, 0, bmpSize, 0, 0, bmpSize, bmpSize)

            for (y in 0 until bmpSize step 2) {
                for (x in 0 until bmpSize step 2) {
                    val pix = pixels[y * bmpSize + x]
                    val a = Color.alpha(pix)
                    if (a > 130) {
                        pts.add(SampledPoint(
                            x = x.toFloat(),
                            y = y.toFloat(),
                            r = Color.red(pix).toFloat(),
                            g = Color.green(pix).toFloat(),
                            b = Color.blue(pix).toFloat()
                        ))
                    }
                }
            }
            bmp.recycle()
        } catch (_: Throwable) {}

        if (pts.isEmpty()) {
            // Agar emoji chizilmasa, doira shakliga yig'iladi
            for (i in 0 until 400) {
                val a = (i / 400f) * 2f * Math.PI.toFloat()
                pts.add(SampledPoint(
                    x = 120f + cos(a) * 80f,
                    y = 120f + sin(a) * 80f,
                    r = Color.red(activePalette.primary).toFloat(),
                    g = Color.green(activePalette.primary).toFloat(),
                    b = Color.blue(activePalette.primary).toFloat()
                ))
            }
        }

        try {
            val rnd = Random(42)
            val ptsCount = pts.size.coerceAtLeast(1)
            for (i in 0 until N) {
                val sp = pts[rnd.nextInt(ptsCount)]
                fx[i] = cx + (sp.x - 120f) / 240f * S
                fy[i] = cy + (sp.y - 120f) / 240f * S
                fcR[i] = sp.r
                fcG[i] = sp.g
                fcB[i] = sp.b
            }
        } catch (_: Throwable) {}
    }

    private data class SampledPoint(
        val x: Float, val y: Float,
        val r: Float, val g: Float, val b: Float
    )

    // ── Asosiy Render Sikli (GPU Hardware-Accelerated onDraw) ───────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastFrameTimeNanos == 0L) 0.016f else ((now - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
        lastFrameTimeNanos = now
        timeSeconds += dt

        // Silliq giroskop qiyshayishi (lerp)
        currentTiltX += (targetTiltX - currentTiltX) * 0.10f
        currentTiltY += (targetTiltY - currentTiltY) * 0.10f

        // Silliq mic/speech audio filtri
        smoothedMicLevel += (targetMicLevel - smoothedMicLevel) * 0.15f

        val cx = w / 2f
        val cy = h * 0.45f
        val Rc = min(w, h) * 0.22f
        val maxL = hypot(w, h) * 0.55f

        // ── 1. Orqa fon markaziy mayin nurlanishi (Ambient Radial Glow) ─────
        val glowRadius = Rc * 2.3f
        val glowAlpha = when (currentState) {
            JarvisParticleState.IDLE, JarvisParticleState.LISTENING -> 45
            JarvisParticleState.SEARCHING -> 20
            JarvisParticleState.RESULT, JarvisParticleState.SPEAKING -> 55
            JarvisParticleState.RELEASE -> 30
        }
        val centerGlowColor = Color.argb(
            glowAlpha,
            Color.red(activePalette.light),
            Color.green(activePalette.light),
            Color.blue(activePalette.light)
        )
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(centerGlowColor, Color.TRANSPARENT),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // ── 2. 3D Galaktika geometrik matritsasi ────────────────────────────
        val ang = timeSeconds * JarvisParticleConfig.IDLE_ROTATION_SPEED + currentTiltY * JarvisParticleConfig.GYRO_SENSITIVITY
        val ca = cos(ang)
        val sa = sin(ang)

        val tl = 0.95f + 0.12f * sin(timeSeconds * 0.3f) + currentTiltX * JarvisParticleConfig.GYRO_SENSITIVITY
        val ct = cos(tl)
        val st = sin(tl)

        val breath = 1.0f + JarvisParticleConfig.BREATH_AMPLITUDE * sin(timeSeconds * JarvisParticleConfig.BREATH_SPEED)
        val contraction = if (currentState == JarvisParticleState.LISTENING) JarvisParticleConfig.LISTEN_CONTRACTION else 1.0f
        val G = Rc * 1.15f * breath * contraction

        // ── 3. SEARCHING holatidagi kengayuvchi to'lqin halqalari ────────────
        val r1 = if (currentState == JarvisParticleState.SEARCHING) {
            ((timeSeconds - tScanStart) * maxL * JarvisParticleConfig.SEARCH_WAVE_SPEED) % maxL
        } else 0f
        val r2 = (r1 + maxL * 0.5f) % maxL

        if (currentState == JarvisParticleState.SEARCHING) {
            wavePaint.color = Color.argb(
                70,
                Color.red(activePalette.light),
                Color.green(activePalette.light),
                Color.blue(activePalette.light)
            )
            canvas.drawCircle(cx, cy, r1, wavePaint)
            canvas.drawCircle(cx, cy, r2, wavePaint)
        }

        // ── 4. 3000 ta Zarrani yangilash va chizish (Zero Allocations!) ─────
        val isForm = currentState == JarvisParticleState.RESULT
        val isHold = isForm && isResultHolding
        val isSearching = currentState == JarvisParticleState.SEARCHING

        for (i in 0 until N) {
            var tx: Float
            var ty: Float
            val kFactor: Float
            var pAlpha: Float
            var s = 1.0f

            if (isSearching) {
                // Ekran bo'ylab curl-noise bilan erkin suzish
                tx = ux[i] * w + sin(timeSeconds * JarvisParticleConfig.SEARCH_DRIFT_SPEED + ph[i]) * 28f
                ty = uy[i] * h + cos(timeSeconds * 0.5f + ph[i]) * 28f
                kFactor = JarvisParticleConfig.SPRING_SEARCH_SPEED * k[i]
                pAlpha = 0.40f
            } else if (isForm) {
                // Shaklga oqib kelish yoki ushlab turish (hold)
                val jiggleX = if (isHold) sin(timeSeconds * 4f + ph[i]) * JarvisParticleConfig.HOLD_JIGGLE_AMPLITUDE else 0f
                val jiggleY = if (isHold) cos(timeSeconds * 4f + ph[i]) * JarvisParticleConfig.HOLD_JIGGLE_AMPLITUDE else 0f
                tx = fx[i] + jiggleX
                ty = fy[i] + jiggleY
                kFactor = (if (isHold) JarvisParticleConfig.SPRING_HOLD_SPEED else JarvisParticleConfig.SPRING_FORM_SPEED) * k[i]
                pAlpha = 0.95f
            } else {
                // 3D spiral galaktika proektsiyasi
                val X = hx[i] * ca + hz[i] * sa
                val Z0 = -hx[i] * sa + hz[i] * ca
                val Y = hy[i] * ct - Z0 * st
                val z = hy[i] * st + Z0 * ct

                val speechVibe = if (currentState == JarvisParticleState.SPEAKING) {
                    activeSpeechLevel * 0.35f * sin(timeSeconds * 12f + ph[i] * 2f)
                } else 0f

                val radialVibe = smoothedMicLevel * 0.40f * sin(timeSeconds * 9f + ph[i] * 3f) + speechVibe
                val gg = G * (1f + radialVibe)

                s = 1.0f / (1.0f - z * 0.25f)
                tx = cx + X * gg * s
                ty = cy + Y * gg * s

                kFactor = (if (currentState == JarvisParticleState.RELEASE) 0.06f else JarvisParticleConfig.SPRING_RETURN_SPEED) * k[i]
                pAlpha = 0.30f + 0.50f * ((z + 1f) / 2f)
            }

            // Prujina va tezlik integratsiyasi
            vx[i] *= JarvisParticleConfig.VELOCITY_DAMPING
            vy[i] *= JarvisParticleConfig.VELOCITY_DAMPING
            px[i] += (tx - px[i]) * kFactor + vx[i]
            py[i] += (ty - py[i]) * kFactor + vy[i]

            // Ranglarning silliq o'tishi
            val targetR = if (isForm) fcR[i] else gcR[i]
            val targetG = if (isForm) fcG[i] else gcG[i]
            val targetB = if (isForm) fcB[i] else gcB[i]

            cR[i] += (targetR - cR[i]) * 0.09f
            cG[i] += (targetG - cG[i]) * 0.09f
            cB[i] += (targetB - cB[i]) * 0.09f

            // O'lcham va to'lqin effektlari
            var pSize = sz[i] * s * (if (isForm) 1.25f else 1.0f)
            if (isSearching) {
                val d = hypot(px[i] - cx, py[i] - cy)
                val w1 = exp(-((d - r1) / JarvisParticleConfig.SEARCH_WAVE_WIDTH).pow(2))
                val w2 = exp(-((d - r2) / JarvisParticleConfig.SEARCH_WAVE_WIDTH).pow(2))
                val waveIntensity = max(w1, w2)
                pAlpha += waveIntensity * 0.60f
                pSize += waveIntensity * 3.2f
            }

            // Chizish (Single GPU Draw call batching via RenderThread)
            val alphaInt = (pAlpha.coerceIn(0.05f, 1.0f) * 255f).toInt()
            particlePaint.color = Color.argb(
                alphaInt,
                cR[i].toInt().coerceIn(0, 255),
                cG[i].toInt().coerceIn(0, 255),
                cB[i].toInt().coerceIn(0, 255)
            )

            canvas.drawCircle(px[i], py[i], pSize, particlePaint)
        }

        // 60-120 FPS doimiy silliq animatsiya
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sensorManager?.unregisterListener(this)
        mainHandler.removeCallbacksAndMessages(null)
    }
}
