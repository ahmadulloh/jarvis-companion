package com.jarvis.companion.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
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
    private var colorTransitionFraction: Float = 1.0f
    private var colorAnimator: ValueAnimator? = null

    fun setDomainColors(primary: Int, glow: Int, domainTitle: String = activeDomainTitle) {
        activeDomainTitle = domainTitle
        targetPrimaryColor = primary
        targetGlowColor = glow

        val startPrimary = currentPrimaryColor
        val startGlow = currentGlowColor

        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750L
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
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    // Neural Synapse Nodes in 3D Spherical Space (Fibonacci Lattice)
    private class SynapseNode(
        val x0: Float,
        val y0: Float,
        val z0: Float,
        var px: Float = 0f,
        var py: Float = 0f,
        var pz: Float = 0f,
        var pulse: Float = 0f
    )

    private val nodeCount = 38
    private val nodes = ArrayList<SynapseNode>()

    private var rotationAngleY = 0f
    private var rotationAngleX = 0f
    private var phase = 0f

    private val loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            rotationAngleY += 0.016f
            rotationAngleX += 0.009f
            postInvalidateOnAnimation()
        }
    }

    init {
        // Initialize Fibonacci 3D sphere distribution
        val phi = (1.0 + sqrt(5.0)) / 2.0 // Golden ratio
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
        val maxRadius = minOf(cx, cy) * 0.88f
        val coreRadius = maxRadius * 0.44f

        val activeAudio = audioLevel.coerceIn(0f, 1f)
        val dynamicScale = 1.0f + (activeAudio * 0.22f)
        val pulseWobble = sin(phase * 2 * PI).toFloat() * 0.06f

        // ── 1. Deep Core Plasma & Aura (Radial Glow) ──────────────────────
        val glowRadius = coreRadius * 2.2f * dynamicScale
        val auraAlpha = if (isNightMode) 80 else 55
        val auraColor = Color.argb(
            (auraAlpha * (0.8f + activeAudio * 0.5f)).toInt().coerceIn(0, 255),
            Color.red(currentGlowColor),
            Color.green(currentGlowColor),
            Color.blue(currentGlowColor)
        )
        val centerAlpha = if (isNightMode) 220 else 240
        val centerColor = Color.argb(
            centerAlpha,
            Color.red(currentPrimaryColor),
            Color.green(currentPrimaryColor),
            Color.blue(currentPrimaryColor)
        )

        plasmaPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(centerColor, auraColor, Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.45f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, plasmaPaint)

        // ── 2. Quantum Cybernetic Rotating HUD Rings ───────────────────────
        val ring1Radius = coreRadius * 1.55f * dynamicScale
        ringPaint.color = Color.argb(70, Color.red(currentPrimaryColor), Color.green(currentPrimaryColor), Color.blue(currentPrimaryColor))
        ringPaint.strokeWidth = 1.8f
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(24f, 16f, 8f, 16f), phase * 180f)
        canvas.drawCircle(cx, cy, ring1Radius, ringPaint)

        val ring2Radius = coreRadius * 1.85f * (1f + pulseWobble)
        ringPaint.color = Color.argb(45, Color.red(currentGlowColor), Color.green(currentGlowColor), Color.blue(currentGlowColor))
        ringPaint.strokeWidth = 1.2f
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(12f, 28f), -phase * 240f)
        canvas.drawCircle(cx, cy, ring2Radius, ringPaint)

        // ── 3. 3D Synaptic Neural Particle Mesh ────────────────────────────
        val sphereRadius = coreRadius * 1.25f * (dynamicScale + pulseWobble * 0.5f)

        val cosY = cos(rotationAngleY)
        val sinY = sin(rotationAngleY)
        val cosX = cos(rotationAngleX)
        val sinX = sin(rotationAngleX)

        // Project 3D nodes to 2D screen with rotation
        for (node in nodes) {
            // Rotate Y
            val x1 = node.x0 * cosY + node.z0 * sinY
            val z1 = -node.x0 * sinY + node.z0 * cosY
            // Rotate X
            val y2 = node.y0 * cosX - z1 * sinX
            val z2 = node.y0 * sinX + z1 * cosX

            // Perspective scale
            val fov = 3.2f
            val scale = fov / (fov + z2 * 0.85f)

            node.px = cx + x1 * sphereRadius * scale
            node.py = cy + y2 * sphereRadius * scale
            node.pz = z2
            node.pulse = (sin((phase * 2 * PI + node.x0 * 3f)).toFloat() + 1f) * 0.5f
        }

        // Draw Interconnecting Synapse Filaments (Lines between nearby nodes)
        val maxDistSq = (sphereRadius * 0.72f) * (sphereRadius * 0.72f)
        for (i in 0 until nodeCount) {
            val n1 = nodes[i]
            for (j in i + 1 until nodeCount) {
                val n2 = nodes[j]
                val dx = n1.px - n2.px
                val dy = n1.py - n2.py
                val distSq = dx * dx + dy * dy

                if (distSq < maxDistSq) {
                    val normDist = 1.0f - (distSq / maxDistSq)
                    val depthAlpha = ((n1.pz + n2.pz + 2.0f) / 4.0f).coerceIn(0.15f, 1.0f)
                    val alpha = (normDist * depthAlpha * (60 + activeAudio * 140)).toInt().coerceIn(10, 240)

                    synapseLinePaint.color = Color.argb(
                        alpha,
                        Color.red(currentPrimaryColor),
                        Color.green(currentPrimaryColor),
                        Color.blue(currentPrimaryColor)
                    )
                    synapseLinePaint.strokeWidth = (1.0f + normDist * 1.5f + activeAudio * 1.2f)
                    canvas.drawLine(n1.px, n1.py, n2.px, n2.py, synapseLinePaint)

                    // Occasional electrical spark traversing filament
                    if ((i + j) % 7 == 0 && (activeAudio > 0.1f || state == OrbState.SPEAKING)) {
                        val sparkPos = (phase * 3.5f + (i * 0.13f)) % 1.0f
                        val sx = n1.px + (n2.px - n1.px) * sparkPos
                        val sy = n1.py + (n2.py - n1.py) * sparkPos
                        sparkPaint.color = Color.WHITE
                        canvas.drawCircle(sx, sy, 2.5f * dynamicScale, sparkPaint)
                    }
                }
            }
        }

        // Draw Synapse Nodes (Glowing energy dots)
        for (node in nodes) {
            val depthScale = ((node.pz + 1.2f) / 2.4f).coerceIn(0.4f, 1.3f)
            val nodeRadius = (2.4f + node.pulse * 2.2f + activeAudio * 3.5f) * depthScale
            val nodeAlpha = (140 * depthScale + activeAudio * 115).toInt().coerceIn(40, 255)

            nodePaint.color = Color.argb(
                nodeAlpha,
                Color.red(currentPrimaryColor),
                Color.green(currentPrimaryColor),
                Color.blue(currentPrimaryColor)
            )
            canvas.drawCircle(node.px, node.py, nodeRadius, nodePaint)

            // White hot center for prominent nodes
            if (depthScale > 0.95f) {
                nodePaint.color = Color.argb((200 * depthScale).toInt(), 255, 255, 255)
                canvas.drawCircle(node.px, node.py, nodeRadius * 0.45f, nodePaint)
            }
        }

        // ── 4. Horizontal Cybernetic Soundwave Bars ──────────────────────
        val barCount = 20
        val barSpacing = w / (barCount + 4)
        val waveBaseY = cy

        soundwavePaint.strokeWidth = 3f
        for (i in 0 until barCount) {
            val bx = barSpacing * (i + 2.5f)
            val distFromCenter = abs(bx - cx) / cx
            val centerFalloff = (1.0f - distFromCenter * 0.75f).coerceIn(0.1f, 1.0f)

            // Only draw outside the inner core circle
            if (abs(bx - cx) > coreRadius * 0.75f) {
                val waveVal = sin(phase * 12.0f + i * 0.65f)
                val barHeight = (4f + activeAudio * 55f * centerFalloff * abs(waveVal).toFloat())
                val barAlpha = (90 + activeAudio * 140).toInt().coerceIn(30, 255)

                soundwavePaint.color = Color.argb(
                    barAlpha,
                    Color.red(currentPrimaryColor),
                    Color.green(currentPrimaryColor),
                    Color.blue(currentPrimaryColor)
                )
                canvas.drawLine(bx, waveBaseY - barHeight / 2f, bx, waveBaseY + barHeight / 2f, soundwavePaint)
            }
        }

        // ── 5. Minimal Holographic Core Center Text ────────────────────────
        val textColor = if (isNightMode) Color.WHITE else Color.parseColor("#0F172A")
        textPaint.color = textColor
        textPaint.textSize = coreRadius * 0.28f
        textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        canvas.drawText("J.A.R.V.I.S", cx, cy - 2f, textPaint)

        subTextPaint.color = currentPrimaryColor
        subTextPaint.textSize = coreRadius * 0.17f
        subTextPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        val modeLabel = if (state == OrbState.SPEAKING) "GAPIRMOQDA" else if (state == OrbState.LISTENING) "ESHITMOQDA" else activeDomainTitle.uppercase()
        canvas.drawText(modeLabel, cx, cy + coreRadius * 0.32f, subTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loopAnimator.cancel()
        colorAnimator?.cancel()
    }
}
