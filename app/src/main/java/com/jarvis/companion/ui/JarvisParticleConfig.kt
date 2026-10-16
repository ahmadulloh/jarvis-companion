package com.jarvis.companion.ui

import android.graphics.Color

/**
 * JARVIS Living Particle Intelligence Configuration.
 * Barcha asosiy parametrlar (zarralar soni, tezlik, fizika va ranglar) bitta joyda jamlangan.
 */
object JarvisParticleConfig {

    // ── 1. Zarralar tizimi ─────────────────────────────────────────────────
    /** Ekrandagi umumiy zarralar soni (2500 - 4000) */
    const val PARTICLE_COUNT = 3000

    /** Galaktika spiral yenglari soni */
    const val SPIRAL_ARMS = 3

    /** Spiral burchak tezligi (aylanish koeffitsienti) */
    const val SPIRAL_WINDING = 5.2f

    /** Asosiy zarra o'lchamlari oralig'i (dp ko'paytuvchisi bilan) */
    const val MIN_PARTICLE_SIZE = 1.0f
    const val MAX_PARTICLE_SIZE = 2.4f

    // ── 2. Vaqt va Aylanish dinamikasi ─────────────────────────────────────
    /** 3D Galaktika o'z o'qi atrofida sekin aylanish tezligi */
    const val IDLE_ROTATION_SPEED = 0.25f

    /** Galaktikaning tinch holatda nafas olish tezligi */
    const val BREATH_SPEED = 1.2f

    /** Nafas olish amplitudasining tebranishi (1.00 -> 1.04) */
    const val BREATH_AMPLITUDE = 0.04f

    /** 3D disk qalinligi (markazdan chetga kamayib boradi) */
    const val DISK_THICKNESS = 0.12f

    // ── 3. Holatlar (State Machine) parametrlari ───────────────────────────
    /** LISTENING holatida galaktikaning siqilish nisbati (15% siqiladi -> 0.85) */
    const val LISTEN_CONTRACTION = 0.85f

    /** SEARCHING holatida markazdan chiqadigan to'lqin halqasi oralig'i (sekund) */
    const val SEARCH_WAVE_INTERVAL = 0.80f

    /** SEARCHING to'lqin halqasi tarqalish tezligi */
    const val SEARCH_WAVE_SPEED = 0.55f

    /** To'lqin halqasi kengligi (zarralar yorishishi uchun Gauss egri chizig'i) */
    const val SEARCH_WAVE_WIDTH = 48f

    /** SEARCHING holatida zarralar ekranga sochilishidagi suzish tezligi */
    const val SEARCH_DRIFT_SPEED = 0.6f

    /** RESULT shaklining ekranda turish vaqti (millisekund) */
    const val RESULT_HOLD_DURATION_MS = 2200L

    /** RESULT (HOLD) holatidagi mayin organik titrash amplitudasi */
    const val HOLD_JIGGLE_AMPLITUDE = 1.4f

    /** RELEASE holatida portlash kuchining koeffitsienti */
    const val RELEASE_EXPLOSION_FORCE = 0.07f

    // ── 4. Fizika va Harakat (Spring / Lerp) ───────────────────────────────
    /** Inertsiya va tezlik so'nishi (friction damping) */
    const val VELOCITY_DAMPING = 0.90f

    /** Galaktika holatiga qaytish prujina tezligi */
    const val SPRING_RETURN_SPEED = 0.09f

    /** SEARCHING holatidagi sekin suzish qovushqoqligi */
    const val SPRING_SEARCH_SPEED = 0.014f

    /** RESULT shakliga yig'ilish prujina tezligi */
    const val SPRING_FORM_SPEED = 0.06f

    /** RESULT shaklini ushlab turish prujina tezligi */
    const val SPRING_HOLD_SPEED = 0.15f

    /** Giroskop / Akselerometr orqali qiyshayish sezgirligi */
    const val GYRO_SENSITIVITY = 0.35f

    // ── 5. Standart Rang Palitrasi (Asosiy / Standart) ──────────────────────
    /** Orqa fon: sof, toza oq rang */
    val BACKGROUND_COLOR: Int = Color.parseColor("#FFFFFF")

    /**
     * Standart 4 pog'onali zumrad yashil palitra:
     * 0: O'ta och fon nurlanishi (#EAFFF1)
     * 1: Och yashil (#5FE08F)
     * 2: Asosiy yashil (#12A550)
     * 3: To'q yashil (#05532A)
     */
    val DEFAULT_PALETTE = Palette(
        ambient = Color.parseColor("#EAFFF1"),
        light = Color.parseColor("#5FE08F"),
        primary = Color.parseColor("#12A550"),
        dark = Color.parseColor("#05532A")
    )

    data class Palette(
        val ambient: Int,
        val light: Int,
        val primary: Int,
        val dark: Int
    )
}
