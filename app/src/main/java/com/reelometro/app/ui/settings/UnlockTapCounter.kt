package com.reelometro.app.ui.settings

/**
 * Los 7 toques en la versión que desbloquean Calibración (como las opciones de desarrollo de
 * Android). Si pasan más de [windowMs] entre dos toques, la cuenta vuelve a empezar.
 */
class UnlockTapCounter(val required: Int = REQUIRED, private val windowMs: Long = WINDOW_MS) {
    private var count = 0
    private var lastAt = 0L

    /** Registra un toque en [now] (epoch ms) y devuelve los que faltan (0 = desbloquear ahora). */
    fun tap(now: Long): Int {
        if (count > 0 && (now - lastAt > windowMs || now < lastAt)) count = 0
        lastAt = now
        count = (count + 1).coerceAtMost(required)
        return required - count
    }

    fun reset() {
        count = 0
    }

    companion object {
        const val REQUIRED = 7

        /** La cuenta atrás se enseña cuando faltan estos toques o menos (tras el 3.º toque). */
        const val COUNTDOWN_FROM = 4

        const val WINDOW_MS = 4_000L
    }
}
