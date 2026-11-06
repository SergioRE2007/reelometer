package com.reelometro.app.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Números y reglas pequeñas del modo Pasaporte (puras, sin Android). El cálculo completo está en
 * [PassportEngine]; la tabla con todas las reglas, en los tests (`PassportRulesTest`).
 *
 * Recordatorio: la experiencia sale de días bajo la referencia, rachas, retos y sellos. **Nunca**
 * de vídeos vistos.
 */
object PassportRules {

    // ------------------------------------------------------------------ experiencia

    /** Por cada día cerrado en la referencia o por debajo. */
    const val DAY_XP = 10

    /** Por cada reto semanal cumplido. */
    const val CHALLENGE_XP = 50

    /** Bonificación de racha al llegar a cada longitud (días seguidos bajo la referencia). */
    val STREAK_BONUSES: Map<Int, Int> = mapOf(3 to 15, 7 to 30, 14 to 60, 30 to 120)

    /** A partir de 30 días, cada 30 más (60, 90…) da esta bonificación. */
    const val STREAK_BONUS_EVERY_30 = 120

    /** Sellos de racha por longitud. */
    val STREAK_STAMPS: Map<Int, StampId> = mapOf(
        3 to StampId.RACHA_3,
        7 to StampId.RACHA_7,
        14 to StampId.RACHA_14,
        30 to StampId.RACHA_30,
    )

    // ------------------------------------------------------------------ referencia y Flap

    /** Días de la ventana de Flap. */
    const val WINDOW_DAYS = 7

    /** Días de la base sin objetivo: las 4 semanas anteriores a la ventana. */
    const val BASELINE_DAYS = 28

    /** Sin objetivo, hace falta al menos este número de días con registro en la base para tener referencia. */
    const val MIN_BASELINE_DAYS = 7

    // ------------------------------------------------------------------ reto semanal

    /** Días con registro que necesita la semana anterior para que haya reto. */
    const val MIN_GHOST_DAYS = 5

    /** Retos ganados seguidos (los más recientes) para pasar a −20 %. */
    const val WINS_FOR_TWENTY = 3

    // ------------------------------------------------------------------ sellos

    /** MES_A_LA_BAJA: días con registro que necesita el mes anterior. */
    const val MONTH_MIN_TRACKED_DAYS = 14

    /** MADRUGADOR: de las 3:00 (minuto 180) a las 4:59 (minuto 299), ambos incluidos. */
    const val EARLY_BIRD_FROM_MINUTE = 180
    const val EARLY_BIRD_TO_MINUTE = 299

    /** MARATON: **más** de este número de vídeos en una misma hora del reloj (14:00–14:59). */
    const val MARATHON_PER_HOUR = 100

    /** TRES_PLATAFORMAS: plataformas distintas en un mismo día. */
    const val PLATFORMS_IN_A_DAY = 3

    /** CINCO_CATEGORIAS: categorías distintas (sin contar «Sin clasificar») en una semana. */
    const val CATEGORIES_IN_A_WEEK = 5

    const val THOUSAND_VIDEOS = 1_000
    const val TEN_THOUSAND_VIDEOS = 10_000

    // ------------------------------------------------------------------ funciones

    /** Bonificación de racha al llegar a [length] días seguidos (0 si esa longitud no da nada). */
    fun streakBonus(length: Int): Int =
        STREAK_BONUSES[length] ?: if (length > 30 && length % 30 == 0) STREAK_BONUS_EVERY_30 else 0

    /**
     * Estado de Flap según los días de la ventana que se pasaron de la referencia ([daysOver]) entre
     * los que tienen registro ([daysTracked]). Con la ventana completa equivale a «días en la
     * referencia o por debajo»: 6–7 EN_FORMA, 4–5 NORMAL, 2–3 CANSADO, 0–1 ZOMBI. Los días anteriores
     * al primer registro no cuentan en contra (beneficio de la duda); sin ningún día, NORMAL.
     */
    fun moodFor(daysOver: Int, daysTracked: Int): FlapMood = when {
        daysTracked <= 0 -> FlapMood.NORMAL
        daysOver <= 1 -> FlapMood.EN_FORMA
        daysOver <= 3 -> FlapMood.NORMAL
        daysOver <= 5 -> FlapMood.CANSADO
        else -> FlapMood.ZOMBI
    }

    /**
     * Dificultad del reto según los retos anteriores, **el más reciente primero**: −20 % si se
     * ganaron los [WINS_FOR_TWENTY] últimos, −10 % si se ganó el último y, si no (o no hay ninguno),
     * 0 («menos que la anterior»).
     */
    fun reductionPercent(previousNewestFirst: List<ChallengeResult>): Int = when {
        previousNewestFirst.size >= WINS_FOR_TWENTY && previousNewestFirst.take(WINS_FOR_TWENTY).all { it.won } -> 20
        previousNewestFirst.firstOrNull()?.won == true -> 10
        else -> 0
    }

    /**
     * Tope del reto: con 0 %, uno menos que el fantasma; con 10 o 20 %, ese porcentaje por debajo
     * (redondeando hacia abajo). Nunca negativo.
     */
    fun challengeTarget(ghostTotal: Int, reductionPercent: Int): Int {
        val target = if (reductionPercent <= 0) {
            ghostTotal - 1
        } else {
            (ghostTotal.toLong() * (100 - reductionPercent) / 100).toInt()
        }
        return target.coerceAtLeast(0)
    }

    /** Lunes de la semana de [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * CINCO_CATEGORIAS: [firstSeen] tiene, por cada semana y categoría, el primer día de esa
     * categoría en su semana. Devuelve el primer día en que alguna semana llegó a [min] categorías.
     */
    fun varietyDay(firstSeen: Collection<LocalDate>, min: Int = CATEGORIES_IN_A_WEEK): LocalDate? =
        firstSeen.groupBy { weekStart(it) }
            .values
            .filter { it.size >= min }
            .map { days -> days.sorted()[min - 1] }
            .minOrNull()
}
