package com.jarvis.companion.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
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

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var phase: Float = 0f
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            postInvalidateOnAnimation()
        }
    }

    init {
        animator.start()
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
        val maxRadius = (minOf(w, h) / 2f) * 0.85f

        val time = SystemClock.uptimeMillis() / 1000f

        // Breathing cycle: 0..1
        val breath = (sin(time * 2.0) * 0.5 + 0.5).toFloat()
        val fastBreath = (sin(time * 6.0) * 0.5 + 0.5).toFloat()

        // Dynamic scale based on state and audio level
        val statePulse = when (state) {
            OrbState.IDLE -> 0.05f * breath
            OrbState.LISTENING -> 0.15f * breath + (audioLevel * 0.25f)
            OrbState.SPEAKING -> 0.10f + (fastBreath * 0.18f) + (audioLevel * 0.20f)
            OrbState.THINKING -> 0.08f * fastBreath
        }

        val baseRadius = maxRadius * 0.50f
        val currentRadius = baseRadius * (1f + statePulse)

        // Palette setup
        val colorCoreCenter: Int
        val colorCoreOuter: Int
        val colorHalo: Int
        val colorWave: Int
        val colorParticle: Int

        if (isNightMode) {
            // Dark / OLED mode palette: Neon Cyan, Electric Blue, Violet
            when (state) {
                OrbState.IDLE -> {
                    colorCoreCenter = Color.parseColor("#8000F0FF")
                    colorCoreOuter = Color.parseColor("#300072FF")
                    colorHalo = Color.parseColor("#1500F0FF")
                    colorWave = Color.parseColor("#4000E5FF")
                    colorParticle = Color.parseColor("#9900F0FF")
                }
                OrbState.LISTENING -> {
                    colorCoreCenter = Color.parseColor("#9900FFB2")
                    colorCoreOuter = Color.parseColor("#4000C853")
                    colorHalo = Color.parseColor("#2000FFB2")
                    colorWave = Color.parseColor("#6000FFB2")
                    colorParticle = Color.parseColor("#B300FFB2")
                }
                OrbState.SPEAKING -> {
                    colorCoreCenter = Color.parseColor("#CC00F0FF")
                    colorCoreOuter = Color.parseColor("#55A855F7")
                    colorHalo = Color.parseColor("#258B5CF6")
                    colorWave = Color.parseColor("#7000F0FF")
                    colorParticle = Color.parseColor("#CCF43F5E")
                }
                OrbState.THINKING -> {
                    colorCoreCenter = Color.parseColor("#B3F59E0B")
                    colorCoreOuter = Color.parseColor("#408B5CF6")
                    colorHalo = Color.parseColor("#20F59E0B")
                    colorWave = Color.parseColor("#60F59E0B")
                    colorParticle = Color.parseColor("#B38B5CF6")
                }
            }
        } else {
            // Light / Day mode palette: Crisp Sapphire, Ethereal Cyan, Soft Iris
            when (state) {
                OrbState.IDLE -> {
                    colorCoreCenter = Color.parseColor("#990284C7")
                    colorCoreOuter = Color.parseColor("#330369A1")
                    colorHalo = Color.parseColor("#120284C7")
                    colorWave = Color.parseColor("#400284C7")
                    colorParticle = Color.parseColor("#800284C7")
                }
                OrbState.LISTENING -> {
                    colorCoreCenter = Color.parseColor("#99059669")
                    colorCoreOuter = Color.parseColor("#33047857")
                    colorHalo = Color.parseColor("#1510B981")
                    colorWave = Color.parseColor("#50059669")
                    colorParticle = Color.parseColor("#99059669")
                }
                OrbState.SPEAKING -> {
                    colorCoreCenter = Color.parseColor("#B34F46E5")
                    colorCoreOuter = Color.parseColor("#407C3AED")
                    colorHalo = Color.parseColor("#206366F1")
                    colorWave = Color.parseColor("#604F46E5")
                    colorParticle = Color.parseColor("#990284C7")
                }
                OrbState.THINKING -> {
                    colorCoreCenter = Color.parseColor("#B3D97706")
                    colorCoreOuter = Color.parseColor("#407C3AED")
                    colorHalo = Color.parseColor("#15F59E0B")
                    colorWave = Color.parseColor("#50D97706")
                    colorParticle = Color.parseColor("#997C3AED")
                }
            }
        }

        // 1. Draw outer glowing halo
        val haloRadius = currentRadius * 1.55f
        haloPaint.shader = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(colorHalo, Color.TRANSPARENT),
            floatArrayOf(0.2f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, haloRadius, haloPaint)

        // 2. Draw harmonic expanding ripples/soundwaves
        val waveCount = if (state == OrbState.SPEAKING) 4 else 3
        for (i in 0 until waveCount) {
            val wavePhase = (phase + (i.toFloat() / waveCount)) % 1.0f
            val waveRadius = baseRadius + (maxRadius - baseRadius) * wavePhase
            val alphaMod = ((1.0f - wavePhase) * 255 * (if (state == OrbState.SPEAKING) 0.8f else 0.4f)).toInt()
            wavePaint.color = (colorWave and 0x00FFFFFF) or (alphaMod shl 24)
            wavePaint.strokeWidth = if (state == OrbState.SPEAKING) 4f + (1f - wavePhase) * 3f else 2.5f
            canvas.drawCircle(cx, cy, waveRadius, wavePaint)
        }

        // 3. Draw central glowing core
        corePaint.shader = RadialGradient(
            cx, cy, currentRadius,
            intArrayOf(colorCoreCenter, colorCoreOuter, Color.TRANSPARENT),
            floatArrayOf(0.15f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, currentRadius, corePaint)

        // 4. Draw orbiting light satellites (futuristic particles)
        val particleCount = if (state == OrbState.SPEAKING || state == OrbState.THINKING) 5 else 3
        particlePaint.color = colorParticle
        val orbitSpeed = if (state == OrbState.THINKING) 3.5f else 1.2f
        for (i in 0 until particleCount) {
            val angle = time * orbitSpeed + (i * (2 * Math.PI / particleCount))
            val orbitDist = currentRadius * (1.15f + 0.15f * sin(time * 3 + i).toFloat())
            val px = cx + (orbitDist * cos(angle)).toFloat()
            val py = cy + (orbitDist * sin(angle)).toFloat()
            val pSize = 4f + 2f * sin(time * 5 + i).toFloat()
            canvas.drawCircle(px, py, pSize, particlePaint)
        }
    }
}
