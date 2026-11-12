package com.reelometro.app.game

/**
 * Reglas de la vida de Flap (puras, en enteros para que los bordes no bailen con decimales).
 *
 * Con aguante L (el objetivo diario o, sin objetivo, [DEFAULT_LIMIT]) y c vídeos hoy:
 *
 * | vídeos de hoy         | estado    | vida      |
 * |-----------------------|-----------|-----------|
 * | c < L/2               | EN_FORMA  | 100 – 76  |
 * | L/2 ≤ c < 0,8·L       | NORMAL    | 75 – 61   |
 * | 0,8·L ≤ c ≤ L         | CANSADO   | 60 – 50   |
 * | L < c < 2·L           | ENFERMO   | 49 – 1    |
 * | c ≥ 2·L               | KO        | 0         |
 *
 * La vida baja en línea recta de 100 (0 vídeos) a 0 (2·L), redondeando hacia arriba: solo llega a 0
 * en KO, y en ENFERMO se queda en 49 como mucho (el 50 es del aguante justo). Cada día empieza de cero
 * (EN_FORMA, 100).
 */
object FlapRules {
    /**
     * Aguante sin objetivo: el mismo que propone Ajustes al poner uno
     * (`SettingsViewModel.DEFAULT_GOAL`).
     */
    const val DEFAULT_LIMIT = 150

    const val MAX_LIFE = 100

    /** Con este múltiplo del aguante, Flap cae KO. */
    const val KO_FACTOR = 2

    /** Días cerrados de la tira de la semana. */
    const val WEEK_DAYS = 7

    /** Aguante de un objetivo (`null` o ≤ 0 = sin objetivo → [DEFAULT_LIMIT]). */
    fun limitFor(goal: Int?): Int = goal?.takeIf { it > 0 } ?: DEFAULT_LIMIT

    fun moodFor(count: Int, limit: Int): FlapMood {
        val c = count.coerceAtLeast(0).toLong()
        val l = limit.coerceAtLeast(1).toLong()
        return when {
            c * 2 < l -> FlapMood.EN_FORMA
            c * 5 < l * 4 -> FlapMood.NORMAL
            c <= l -> FlapMood.CANSADO
            c < l * KO_FACTOR -> FlapMood.ENFERMO
            else -> FlapMood.KO
        }
    }

    /**
     * Primer número de vídeos del día con el que Flap ya está en [mood] (o peor, si el tramo de
     * [mood] queda vacío con un aguante muy pequeño).
     */
    fun startOf(mood: FlapMood, limit: Int): Int {
        val l = limit.coerceAtLeast(1)
        return when (mood) {
            FlapMood.EN_FORMA -> 0
            FlapMood.NORMAL -> (l + 1) / 2
            FlapMood.CANSADO -> (4 * l + 4) / 5
            FlapMood.ENFERMO -> l + 1
            FlapMood.KO -> l * KO_FACTOR
        }
    }

    /**
     * Siguiente estado peor que se alcanzaría viendo más vídeos; `null` en KO. Es el estado en el
     * que Flap estará de verdad: con un aguante muy pequeño hay tramos vacíos (con aguante 1, NORMAL
     * y ENFERMO) y se salta al siguiente (con 1: de EN_FORMA a CANSADO y de CANSADO a KO).
     */
    fun nextWorse(count: Int, limit: Int): FlapMood? {
        val c = count.coerceAtLeast(0)
        val current = moodFor(c, limit)
        val next = FlapMood.entries.drop(current.ordinal + 1).firstOrNull { startOf(it, limit) > c } ?: return null
        return moodFor(startOf(next, limit), limit)
    }

    /** Vídeos que faltan para que Flap pase a un estado peor ([nextWorse]); `null` si ya está KO. */
    fun videosToWorse(count: Int, limit: Int): Int? {
        val c = count.coerceAtLeast(0)
        val next = nextWorse(c, limit) ?: return null
        return startOf(next, limit) - c
    }

    /**
     * Vida 0–[MAX_LIFE]: 100 con 0 vídeos, 50 con el aguante, 0 solo con el doble (KO). Por encima
     * del aguante (ENFERMO) nunca pasa de 49: con aguantes grandes, el redondeo hacia arriba daría
     * 50 a los primeros vídeos de más, la misma vida que CANSADO con el aguante justo.
     */
    fun life(count: Int, limit: Int): Int {
        val c = count.coerceAtLeast(0).toLong()
        val l = limit.coerceAtLeast(1).toLong()
        val span = KO_FACTOR.toLong() * l
        if (c >= span) return 0
        val life = ((MAX_LIFE * (span - c) + span - 1) / span).toInt().coerceIn(1, MAX_LIFE)
        return if (c > l) life.coerceAtMost(MAX_LIFE / KO_FACTOR - 1) else life
    }

    /** El día acabó sin que Flap enfermara (vídeos dentro del aguante). */
    fun healthy(count: Int, limit: Int): Boolean = count <= limit.coerceAtLeast(1)
}
