package com.jarvis.companion.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    IDLE,
    LISTENING,
    SPEAKING,
    THINKING
}

class JarvisOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var state: OrbState = OrbState.IDLE
        set(value) {
            field = value
            invalidate()
        }

    var isNightMode: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var audioLevel: Float = 0.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            invalidate()
        }

    // Toggle between Female AI Hologram and Pure Neural Sphere
    var showFemaleAiPersona: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val horizontalWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val neuralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val personaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val personaFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var phase: Float = 0f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            postInvalidateOnAnimation()
        }
    }

    init {
        animator.start()
        setOnClickListener {
            showFemaleAiPersona = !showFemaleAiPersona
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isRunning) animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = (minOf(w, h) / 2f) * 0.82f

        val time = SystemClock.uptimeMillis() / 1000f

        val breath = (sin(time * 2.2) * 0.5 + 0.5).toFloat()
        val fastBreath = (sin(time * 6.5) * 0.5 + 0.5).toFloat()

        val statePulse = when (state) {
            OrbState.IDLE -> 0.04f * breath
            OrbState.LISTENING -> 0.12f * breath + (audioLevel * 0.22f)
            OrbState.SPEAKING -> 0.10f + (fastBreath * 0.16f) + (audioLevel * 0.22f)
            OrbState.THINKING -> 0.08f * fastBreath
        }

        val baseRadius = maxRadius * 0.58f
        val currentRadius = baseRadius * (1f + statePulse)

        // Palette
        val cyanPrimary = if (isNightMode) Color.parseColor("#00E5FF") else Color.parseColor("#00B4D8")
        val cyanSoft = if (isNightMode) Color.parseColor("#8000E5FF") else Color.parseColor("#8000B4D8")
        val cyanDim = if (isNightMode) Color.parseColor("#2500E5FF") else Color.parseColor("#2000B4D8")
        val coreDiscColor = if (isNightMode) Color.parseColor("#EA0B132B") else Color.parseColor("#F5FFFFFF")
        val textColorPrimary = if (isNightMode) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
        val textColorSecondary = if (isNightMode) Color.parseColor("#00E5FF") else Color.parseColor("#0284C7")

        // 1. Draw Horizontal Soundwave Bars (Left and Right of the orb, matching image!)
        drawHorizontalSoundwaves(canvas, cx, cy, currentRadius, w, time, cyanPrimary, cyanSoft)

        // 2. Draw outer ambient halo
        val haloRadius = currentRadius * 1.45f
        haloPaint.shader = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(cyanDim, Color.TRANSPARENT),
            floatArrayOf(0.35f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, haloRadius, haloPaint)

        // 3. Draw neural mesh lines & synapses on the sphere
        drawNeuralSphere(canvas, cx, cy, currentRadius, time, cyanSoft, cyanPrimary)

        // 4. Draw central core disc
        corePaint.shader = RadialGradient(
            cx, cy, currentRadius * 0.62f,
            intArrayOf(coreDiscColor, if (isNightMode) Color.parseColor("#B00B132B") else Color.parseColor("#D0FFFFFF")),
            floatArrayOf(0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, currentRadius * 0.62f, corePaint)

        // Border ring around center disc
        wavePaint.color = cyanSoft
        wavePaint.strokeWidth = 2.0f
        canvas.drawCircle(cx, cy, currentRadius * 0.62f, wavePaint)

        // 5. Draw Female AI Persona OR Pure Text in center
        if (showFemaleAiPersona) {
            drawFemaleAiPersona(canvas, cx, cy, currentRadius * 0.62f, time, cyanPrimary, textColorPrimary)
        } else {
            drawCenterText(canvas, cx, cy, currentRadius * 0.62f, textColorPrimary, textColorSecondary)
        }

        // 6. Draw orbiting light satellites (futuristic particles)
        val particleCount = if (state == OrbState.SPEAKING || state == OrbState.THINKING) 6 else 4
        particlePaint.color = cyanPrimary
        val orbitSpeed = if (state == OrbState.THINKING) 3.2f else 1.2f
        for (i in 0 until particleCount) {
            val angle = time * orbitSpeed + (i * (2 * Math.PI / particleCount))
            val orbitDist = currentRadius * (1.08f + 0.12f * sin(time * 3 + i).toFloat())
            val px = cx + (orbitDist * cos(angle)).toFloat()
            val py = cy + (orbitDist * sin(angle)).toFloat()
            val pSize = 3.5f + 2f * sin(time * 5 + i).toFloat()
            canvas.drawCircle(px, py, pSize, particlePaint)
        }
    }

    private fun drawHorizontalSoundwaves(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        width: Float,
        time: Float,
        colorMain: Int,
        colorDim: Int
    ) {
        val barCount = 14
        val stepX = 14f
        val maxWaveHeight = if (state == OrbState.SPEAKING) 48f else if (state == OrbState.LISTENING) 36f else 18f

        horizontalWavePaint.strokeWidth = 3.2f

        for (i in 1..barCount) {
            val dist = radius * 0.95f + (i * stepX)
            if (dist > width / 2f - 10f) break

            val waveMod = sin(time * 4.5f + (i * 0.45f)).toFloat()
            val falloff = (1.0f - (i.toFloat() / barCount)).coerceIn(0.15f, 1.0f)
            val h = abs(waveMod) * maxWaveHeight * falloff + (audioLevel * 25f * falloff) + 6f

            val alpha = (falloff * 230).toInt().coerceIn(30, 255)
            horizontalWavePaint.color = (colorMain and 0x00FFFFFF) or (alpha shl 24)

            // Right bars
            canvas.drawLine(cx + dist, cy - h / 2f, cx + dist, cy + h / 2f, horizontalWavePaint)
            // Left bars
            canvas.drawLine(cx - dist, cy - h / 2f, cx - dist, cy + h / 2f, horizontalWavePaint)
        }
    }

    private fun drawNeuralSphere(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        time: Float,
        lineColor: Int,
        nodeColor: Int
    ) {
        val nodeCount = 10
        val nodePoints = mutableListOf<Pair<Float, Float>>()

        for (i in 0 until nodeCount) {
            val a = (i * (2 * Math.PI / nodeCount)) + (time * 0.35f)
            val r = radius * (0.82f + 0.12f * sin(time * 2.0f + i).toFloat())
            val nx = cx + (r * cos(a)).toFloat()
            val ny = cy + (r * sin(a)).toFloat()
            nodePoints.add(Pair(nx, ny))
        }

        // Draw connections
        neuralPaint.color = lineColor
        for (i in 0 until nodeCount) {
            val p1 = nodePoints[i]
            val p2 = nodePoints[(i + 3) % nodeCount]
            canvas.drawLine(p1.first, p1.second, p2.first, p2.second, neuralPaint)
        }

        // Draw nodes
        particlePaint.color = nodeColor
        for (p in nodePoints) {
            canvas.drawCircle(p.first, p.second, 4.0f, particlePaint)
        }
    }

    private fun drawFemaleAiPersona(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        discRadius: Float,
        time: Float,
        accentColor: Int,
        textColor: Int
    ) {
        val scale = discRadius / 70f
        val pulse = (sin(time * 6.0) * 0.5 + 0.5).toFloat()

        // 1. Draw sleek holographic female AI silhouette head contour
        personaPaint.color = accentColor
        personaPaint.strokeWidth = 2.2f * scale

        val headPath = Path()
        // Head / hair contour
        headPath.moveTo(cx - 24f * scale, cy + 8f * scale)
        headPath.cubicTo(cx - 30f * scale, cy - 25f * scale, cx + 30f * scale, cy - 25f * scale, cx + 24f * scale, cy + 8f * scale)
        // Jawline
        headPath.cubicTo(cx + 18f * scale, cy + 26f * scale, cx - 18f * scale, cy + 26f * scale, cx - 24f * scale, cy + 8f * scale)
        canvas.drawPath(headPath, personaPaint)

        // 2. Eyes (Cybernetic glowing calm eyes)
        personaFillPaint.color = accentColor
        val eyeW = 4f * scale
        val eyeH = 2f * scale
        canvas.drawOval(cx - 11f * scale - eyeW, cy - 2f * scale - eyeH, cx - 11f * scale + eyeW, cy - 2f * scale + eyeH, personaFillPaint)
        canvas.drawOval(cx + 11f * scale - eyeW, cy - 2f * scale - eyeH, cx + 11f * scale + eyeW, cy - 2f * scale + eyeH, personaFillPaint)

        // 3. Speaking mouth vocal waveform
        val mouthY = cy + 13f * scale
        val mouthWidth = if (state == OrbState.SPEAKING) (8f + pulse * 7f) * scale else 6f * scale
        val mouthOpen = if (state == OrbState.SPEAKING) (2f + pulse * 4f + audioLevel * 5f) * scale else 1f * scale

        personaPaint.strokeWidth = 1.8f * scale
        val mouthPath = Path().apply {
            moveTo(cx - mouthWidth, mouthY)
            quadTo(cx, mouthY + mouthOpen, cx + mouthWidth, mouthY)
        }
        canvas.drawPath(mouthPath, personaPaint)

        // 4. Subtle status badge under persona
        textPaint.color = textColor
        textPaint.textSize = 10f * scale
        textPaint.isFakeBoldText = true
        textPaint.letterSpacing = 0.12f
        canvas.drawText("J.A.R.V.I.S.", cx, cy + discRadius - 10f * scale, textPaint)

        textPaint.color = accentColor
        textPaint.textSize = 8.5f * scale
        textPaint.isFakeBoldText = false
        val stateText = when (state) {
            OrbState.SPEAKING -> "gapirmoqda..."
            OrbState.LISTENING -> "eshitmoqda..."
            OrbState.THINKING -> "o'ylamoqda..."
            OrbState.IDLE -> "faol"
        }
        canvas.drawText(stateText, cx, cy + discRadius + 4f * scale, textPaint)
    }

    private fun drawCenterText(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        discRadius: Float,
        primaryTextColor: Int,
        accentTextColor: Int
    ) {
        val scale = discRadius / 70f

        textPaint.color = primaryTextColor
        textPaint.textSize = 14f * scale
        textPaint.isFakeBoldText = true
        textPaint.letterSpacing = 0.16f
        canvas.drawText("J.A.R.V.I.S.", cx, cy - 4f * scale, textPaint)

        textPaint.color = accentTextColor
        textPaint.textSize = 10.5f * scale
        textPaint.isFakeBoldText = false
        textPaint.letterSpacing = 0.05f

        val stateDesc = when (state) {
            OrbState.LISTENING -> "eshitmoqda..."
            OrbState.SPEAKING -> "gapirmoqda..."
            OrbState.THINKING -> "o'ylamoqda..."
            OrbState.IDLE -> "tayyor"
        }
        canvas.drawText(stateDesc, cx, cy + 16f * scale, textPaint)
    }
}
