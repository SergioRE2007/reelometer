package com.reelometro.app.game

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Cálculo de Flap (puro: sin Android ni corrutinas). Recibe los vídeos por día y devuelve todo lo
 * que pinta la pestaña ([FlapState]); los números salen de [FlapRules]. El widget no pasa por aquí:
 * solo usa [FlapRules.moodFor] con los vídeos de hoy.
 *
 * - Hoy: los vídeos de `today` frente al aguante ([FlapRules.limitFor] del objetivo).
 * - La semana: los [FlapRules.WEEK_DAYS] días anteriores a hoy, del más antiguo a ayer. Los anteriores
 *   al primer registro (el primer día con vídeos) van sin datos; desde ahí, un día sin vídeos vale 0.
 * - La racha: días cerrados seguidos, hasta ayer, en que Flap no enfermó con el aguante de ahora.
 */
object FlapEngine {

    /**
     * @param goal objetivo diario de Ajustes; `null` o ≤ 0 es no tener objetivo.
     * @param counts vídeos por día (los días sin vídeos pueden faltar; los posteriores a [today] no cuentan).
     * @param lastSeen último estado que el usuario vio en la pestaña ([FlapSource.markMoodSeen]).
     */
    fun compute(today: LocalDate, goal: Int?, counts: Map<LocalDate, Int>, lastSeen: FlapMood?): FlapState {
        val realGoal = goal?.takeIf { it > 0 }
        val limit = FlapRules.limitFor(realGoal)
        val count = (counts[today] ?: 0).coerceAtLeast(0)
        val mood = FlapRules.moodFor(count, limit)
        val first = firstRecord(today, counts)
        val week = (FlapRules.WEEK_DAYS downTo 1).map { back ->
            val date = today.minusDays(back.toLong())
            val videos = if (first == null || date.isBefore(first)) null else videosOn(date, counts)
            FlapDay(date = date, count = videos, mood = videos?.let { FlapRules.moodFor(it, limit) })
        }
        return FlapState(
            today = today,
            goal = realGoal,
            limit = limit,
            count = count,
            mood = mood,
            life = FlapRules.life(count, limit),
            videosToWorse = FlapRules.videosToWorse(count, limit),
            previousMood = lastSeen?.takeIf { it != mood },
            week = week,
            healthyStreak = healthyStreak(today, first, counts, limit),
            trackedDays = if (first == null) 0 else (ChronoUnit.DAYS.between(first, today) + 1).toInt(),
        )
    }

    /** Primer día con vídeos hasta [today] incluido; `null` si no hay ninguno. */
    fun firstRecord(today: LocalDate, counts: Map<LocalDate, Int>): LocalDate? {
        var first: LocalDate? = null
        for ((date, videos) in counts) {
            if (videos <= 0 || date.isAfter(today)) continue
            if (first == null || date.isBefore(first)) first = date
        }
        return first
    }

    private fun videosOn(date: LocalDate, counts: Map<LocalDate, Int>): Int = (counts[date] ?: 0).coerceAtLeast(0)

    /** Días cerrados seguidos, de ayer hacia atrás y sin pasar del primer registro, en que Flap no enfermó. */
    private fun healthyStreak(today: LocalDate, first: LocalDate?, counts: Map<LocalDate, Int>, limit: Int): Int {
        if (first == null) return 0
        var streak = 0
        var date = today.minusDays(1)
        while (!date.isBefore(first) && FlapRules.healthy(videosOn(date, counts), limit)) {
            streak++
            date = date.minusDays(1)
        }
        return streak
    }
}
