package com.jarvis.companion.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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

    // Active Domain Name displayed inside Neural Core
    var activeDomainTitle: String = "ASOSIY"
        set(value) {
            field = value
            invalidate()
        }

    // Dynamic Domain Colors
    private var currentPrimaryColor: Int = Color.parseColor("#00E5FF")
    private var currentGlowColor: Int = Color.parseColor("#00B0FF")
    private var targetPrimaryColor: Int = Color.parseColor("#00E5FF")
    private var targetGlowColor: Int = Color.parseColor("#00B0FF")

    private val argbEvaluator = ArgbEvaluator()
    private var colorAnimator: ValueAnimator? = null

    fun setDomainColors(primary: Int, glow: Int, domainTitle: String = activeDomainTitle) {
        activeDomainTitle = domainTitle
        targetPrimaryColor = primary
        targetGlowColor = glow

        val startPrimary = currentPrimaryColor
        val startGlow = currentGlowColor

        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700L
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                currentPrimaryColor = argbEvaluator.evaluate(f, startPrimary, targetPrimaryColor) as Int
                currentGlowColor = argbEvaluator.evaluate(f, startGlow, targetGlowColor) as Int
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    // Paints
    private val plasmaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val synapseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val soundwavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.18f
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.14f
    }

    // 3D Synaptic Nodes
    private class SynapseNode(
        val x0: Float,
        val y0: Float,
        val z0: Float,
        var px: Float = 0f,
        var py: Float = 0f,
        var pz: Float = 0f,
        var pulse: Float = 0f
    )

    private val nodeCount = 44
    private val nodes = ArrayList<SynapseNode>()

    private var rotationAngleY = 0f
    private var rotationAngleX = 0f
    private var phase = 0f
    private val lightningPath = Path()

    private val loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            val speed = when (state) {
                OrbState.THINKING -> 0.045f
                OrbState.SPEAKING -> 0.030f
                OrbState.LISTENING -> 0.022f
                else -> 0.015f
            }
            rotationAngleY += speed
            rotationAngleX += speed * 0.55f
            postInvalidateOnAnimation()
        }
    }

    init {
        // Initialize Fibonacci 3D sphere distribution
        val phi = (1.0 + sqrt(5.0)) / 2.0
        for (i in 0 until nodeCount) {
            val theta = 2.0 * PI * i / phi
            val y = 1.0 - (i / (nodeCount - 1.0f).toDouble()) * 2.0
            val radiusAtY = sqrt(1.0 - y * y)
            val x = cos(theta) * radiusAtY
            val z = sin(theta) * radiusAtY
            nodes.add(SynapseNode(x.toFloat(), y.toFloat(), z.toFloat()))
        }
        loopAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = minOf(cx, cy) * 0.92f
        if (maxRadius <= 15f) return
        val coreRadius = maxRadius * 0.44f

        // Synthetic dynamic energy when speaking or listening
        val baseAudio = audioLevel.coerceIn(0f, 1f)
        val syntheticOscillation = when (state) {
            OrbState.SPEAKING -> {
                val wave1 = abs(sin(phase * 18.0 * PI)).toFloat()
                val wave2 = abs(cos(phase * 29.0 * PI)).toFloat()
                (0.35f + 0.45f * wave1 + 0.20f * wave2).coerceIn(0.2f, 1.0f)
            }
            OrbState.LISTENING -> {
                val breathe = (sin(phase * 8.0 * PI).toFloat() + 1f) * 0.5f
                (0.25f + 0.35f * breathe + baseAudio * 0.5f).coerceIn(0.15f, 1.0f)
            }
            OrbState.THINKING -> {
                val spin = abs(sin(phase * 24.0 * PI)).toFloat()
                (0.30f + 0.35f * spin).coerceIn(0.2f, 0.8f)
            }
            else -> baseAudio * 0.8f
        }
        val activeAudio = maxOf(baseAudio, syntheticOscillation)
        val dynamicScale = 1.0f + (activeAudio * 0.25f)
        val pulseWobble = sin(phase * 2 * PI).toFloat() * 0.08f

        // High contrast colors in Day Mode so the orb pops and never looks washed out
        val effPrimaryColor = if (!isNightMode) {
            when (activeDomainTitle.uppercase()) {
                "HAYDOVCHI" -> Color.parseColor("#D97706")
                "DIN" -> Color.parseColor("#059669")
                "TALABA" -> Color.parseColor("#4F46E5")
                "DASTURCHI" -> Color.parseColor("#16A34A")
                "MOLIYA" -> Color.parseColor("#0D9488")
                else -> Color.parseColor("#0284C7")
            }
        } else {
            currentPrimaryColor
        }

        val effGlowColor = if (!isNightMode) {
            Color.parseColor("#0369A1")
        } else {
            currentGlowColor
        }

        // ── 0. High-Energy Concentric Expanding Shockwaves ────────────────
        if (state == OrbState.SPEAKING || state == OrbState.LISTENING || activeAudio > 0.15f) {
            val shockCount = 3
            shockwavePaint.strokeWidth = 2.0f
            for (s in 0 until shockCount) {
                val shockPhase = (phase * 2.2f + (s.toFloat() / shockCount)) % 1.0f
                val shockRadius = coreRadius + (maxRadius - coreRadius) * shockPhase
                val shockAlpha = ((1.0f - shockPhase) * (if (state == OrbState.SPEAKING) 160 else 90)).toInt().coerceIn(0, 255)

                shockwavePaint.color = Color.argb(
                    shockAlpha,
                    Color.red(effGlowColor),
                    Color.green(effGlowColor),
                    Color.blue(effGlowColor)
                )
                shockwavePaint.pathEffect = DashPathEffect(floatArrayOf(16f, 12f), phase * 90f)
                canvas.drawCircle(cx, cy, shockRadius, shockwavePaint)
            }
        }

        // ── 1. Deep Core Plasma & Aura (Radial Glow) ──────────────────────
        val glowRadius = coreRadius * 2.4f * dynamicScale
        val auraAlpha = if (isNightMode) 95 else 125
        val auraColor = Color.argb(
            (auraAlpha * (0.8f + activeAudio * 0.5f)).toInt().coerceIn(0, 255),
            Color.red(effGlowColor),
            Color.green(effGlowColor),
            Color.blue(effGlowColor)
        )
        val centerAlpha = if (isNightMode) 235 else 255
        val centerColor = Color.argb(
            centerAlpha,
            Color.red(effPrimaryColor),
            Color.green(effPrimaryColor),
            Color.blue(effPrimaryColor)
        )

        plasmaPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(centerColor, auraColor, Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.40f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, plasmaPaint)

        // ── 2. Quantum Cybernetic Rotating HUD Rings ───────────────────────
        val ring1Radius = coreRadius * 1.55f * dynamicScale
        ringPaint.color = Color.argb(if (isNightMode) 85 else 160, Color.red(effPrimaryColor), Color.green(effPrimaryColor), Color.blue(effPrimaryColor))
        ringPaint.strokeWidth = 2.2f
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(30f, 15f, 10f, 15f), phase * 220f)
        canvas.drawCircle(cx, cy, ring1Radius, ringPaint)

        val ring2Radius = coreRadius * 1.85f * (1f + pulseWobble)
        ringPaint.color = Color.argb(if (isNightMode) 55 else 120, Color.red(effGlowColor), Color.green(effGlowColor), Color.blue(effGlowColor))
        ringPaint.strokeWidth = 1.5f
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(14f, 26f), -phase * 300f)
        canvas.drawCircle(cx, cy, ring2Radius, ringPaint)

        // Outer HUD Track Ring
        val ring3Radius = coreRadius * 2.15f
        ringPaint.color = Color.argb(if (isNightMode) 40 else 90, Color.red(effPrimaryColor), Color.green(effPrimaryColor), Color.blue(effPrimaryColor))
        ringPaint.strokeWidth = 1.0f
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(4f, 32f), phase * 120f)
        canvas.drawCircle(cx, cy, ring3Radius, ringPaint)

        // ── 3. 3D Synaptic Neural Particle Mesh ────────────────────────────
        val sphereRadius = coreRadius * 1.28f * (dynamicScale + pulseWobble * 0.5f)

        val cosY = cos(rotationAngleY)
        val sinY = sin(rotationAngleY)
        val cosX = cos(rotationAngleX)
        val sinX = sin(rotationAngleX)

        for (node in nodes) {
            val x1 = node.x0 * cosY + node.z0 * sinY
            val z1 = -node.x0 * sinY + node.z0 * cosY
            val y2 = node.y0 * cosX - z1 * sinX
            val z2 = node.y0 * sinX + z1 * cosX

            val fov = 3.2f
            val scale = fov / (fov + z2 * 0.85f)

            node.px = cx + x1 * sphereRadius * scale
            node.py = cy + y2 * sphereRadius * scale
            node.pz = z2
            node.pulse = (sin((phase * 2 * PI + node.x0 * 3f)).toFloat() + 1f) * 0.5f
        }

        // Draw Interconnecting Synapse Filaments
        val maxDistSq = (sphereRadius * 0.74f) * (sphereRadius * 0.74f)
        for (i in 0 until nodeCount) {
            val n1 = nodes[i]
            for (j in i + 1 until nodeCount) {
                val n2 = nodes[j]
                val dx = n1.px - n2.px
                val dy = n1.py - n2.py
                val distSq = dx * dx + dy * dy

                if (distSq < maxDistSq) {
                    val normDist = 1.0f - (distSq / maxDistSq)
                    val depthAlpha = ((n1.pz + n2.pz + 2.0f) / 4.0f).coerceIn(0.18f, 1.0f)
                    val baseAlpha = if (isNightMode) (80f + activeAudio * 155f) else (140f + activeAudio * 115f)
                    val alpha = (normDist * depthAlpha * baseAlpha).toInt().coerceIn(20, 255)

                    synapseLinePaint.color = Color.argb(
                        alpha,
                        Color.red(effPrimaryColor),
                        Color.green(effPrimaryColor),
                        Color.blue(effPrimaryColor)
                    )
                    synapseLinePaint.strokeWidth = (1.2f + normDist * 1.8f + activeAudio * 1.5f)
                    canvas.drawLine(n1.px, n1.py, n2.px, n2.py, synapseLinePaint)

                    // Travelling Electrical Sparks along synapses
                    if ((i + j) % 5 == 0 && (activeAudio > 0.12f || state == OrbState.SPEAKING)) {
                        val sparkPos = (phase * 4.0f + (i * 0.19f)) % 1.0f
                        val sx = n1.px + (n2.px - n1.px) * sparkPos
                        val sy = n1.py + (n2.py - n1.py) * sparkPos
                        sparkPaint.color = Color.WHITE
                        canvas.drawCircle(sx, sy, 3.0f * dynamicScale, sparkPaint)
                    }
                }
            }
        }

        // ── 3b. High-Voltage Electric Lightning Arcs (Alive Neural Sparks!) ─
        if (state == OrbState.SPEAKING || (state == OrbState.LISTENING && activeAudio > 0.2f)) {
            lightningPaint.color = if (isNightMode) Color.WHITE else Color.parseColor("#0284C7")
            lightningPaint.strokeWidth = 2.4f
            val rand = Random(SystemClock.uptimeMillis() / 70)
            val arcCount = if (state == OrbState.SPEAKING) 3 else 1

            for (a in 0 until arcCount) {
                val idx1 = rand.nextInt(nodeCount)
                val idx2 = (idx1 + rand.nextInt(5) + 3) % nodeCount
                val p1 = nodes[idx1]
                val p2 = nodes[idx2]

                lightningPath.reset()
                lightningPath.moveTo(p1.px, p1.py)
                val midX = (p1.px + p2.px) / 2f + (rand.nextFloat() - 0.5f) * 28f
                val midY = (p1.px + p2.px) / 2f + (rand.nextFloat() - 0.5f) * 28f
                lightningPath.lineTo(midX, midY)
                lightningPath.lineTo(p2.px, p2.py)
                canvas.drawPath(lightningPath, lightningPaint)
            }
        }

        // Draw Synapse Nodes (Glowing energy dots)
        for (node in nodes) {
            val depthScale = ((node.pz + 1.2f) / 2.4f).coerceIn(0.4f, 1.3f)
            val nodeRadius = (2.8f + node.pulse * 2.5f + activeAudio * 4.2f) * depthScale
            val baseNodeAlpha = if (isNightMode) 150f else 200f
            val nodeAlpha = (baseNodeAlpha * depthScale + activeAudio * 55f).toInt().coerceIn(50, 255)

            nodePaint.color = Color.argb(
                nodeAlpha,
                Color.red(effPrimaryColor),
                Color.green(effPrimaryColor),
                Color.blue(effPrimaryColor)
            )
            canvas.drawCircle(node.px, node.py, nodeRadius, nodePaint)

            // Bright white-hot center for front-facing nodes
            if (depthScale > 0.90f) {
                nodePaint.color = Color.argb((220 * depthScale).toInt(), 255, 255, 255)
                canvas.drawCircle(node.px, node.py, nodeRadius * 0.48f, nodePaint)
            }
        }

        // ── 4. Horizontal Cybernetic Soundwave Equalizer Bars ─────────────
        val barCount = 24
        val barSpacing = w / (barCount + 4)
        val waveBaseY = cy

        soundwavePaint.strokeWidth = 3.2f
        for (i in 0 until barCount) {
            val bx = barSpacing * (i + 2.5f)
            val distFromCenter = abs(bx - cx) / cx
            val centerFalloff = (1.0f - distFromCenter * 0.70f).coerceIn(0.1f, 1.0f)

            if (abs(bx - cx) > coreRadius * 0.72f) {
                val waveVal = sin(phase * 14.0f + i * 0.60f)
                val barHeight = (5f + activeAudio * 65f * centerFalloff * abs(waveVal).toFloat())
                val baseBarAlpha = if (isNightMode) 95f else 150f
                val barAlpha = (baseBarAlpha + activeAudio * 105f).toInt().coerceIn(35, 255)

                soundwavePaint.color = Color.argb(
                    barAlpha,
                    Color.red(effPrimaryColor),
                    Color.green(effPrimaryColor),
                    Color.blue(effPrimaryColor)
                )
                canvas.drawLine(bx, waveBaseY - barHeight / 2f, bx, waveBaseY + barHeight / 2f, soundwavePaint)
            }
        }

        // ── 5. Holographic Core Center Text ─────────────────────────────────
        val textColor = if (isNightMode) Color.WHITE else Color.parseColor("#0F172A")
        textPaint.color = textColor
        textPaint.textSize = coreRadius * 0.28f
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        canvas.drawText("J.A.R.V.I.S", cx, cy - 2f, textPaint)

        subTextPaint.color = effPrimaryColor
        var subSize = coreRadius * 0.17f
        subTextPaint.textSize = subSize
        subTextPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        val modeLabel = when (state) {
            OrbState.SPEAKING -> "GAPIRMOQDA"
            OrbState.LISTENING -> "ESHITMOQDA"
            OrbState.THINKING -> "TAHLIL..."
            else -> activeDomainTitle.uppercase()
        }

        val maxSubWidth = coreRadius * 1.45f
        val measuredSub = subTextPaint.measureText(modeLabel)
        if (measuredSub > maxSubWidth && measuredSub > 0f) {
            subSize *= (maxSubWidth / measuredSub)
            subTextPaint.textSize = subSize
        }
        canvas.drawText(modeLabel, cx, cy + coreRadius * 0.32f, subTextPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!loopAnimator.isStarted) {
            loopAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        loopAnimator.cancel()
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
