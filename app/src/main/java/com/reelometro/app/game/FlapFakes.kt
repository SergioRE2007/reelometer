package com.reelometro.app.game

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Estados de ejemplo coherentes para previews, galería y tests de la pantalla (sin datos reales).
 * No los uses en la app de verdad.
 */
object FlapFakes {
    val DAY: LocalDate = LocalDate.of(2026, 11, 4)

    /** Vídeos de ejemplo de los 7 días anteriores, como fracción del aguante (×100). */
    private val WEEK_PERCENT = listOf(40, 95, 130, 70, 210, 55, 85)

    /** Instalación nueva: 0 vídeos, sin semana, sin objetivo. */
    fun fresh(today: LocalDate = DAY): FlapState = state(today = today, goal = null, count = 0, tracked = 0)

    /** Flap en [mood] con un número de vídeos de hoy dentro de su tramo. */
    fun sample(
        mood: FlapMood = FlapMood.NORMAL,
        goal: Int? = 40,
        today: LocalDate = DAY,
        previousMood: FlapMood? = null,
        healthyStreak: Int? = null,
    ): FlapState {
        val limit = FlapRules.limitFor(goal)
        val lo = FlapRules.startOf(mood, limit)
        val hi = FlapMood.entries.getOrNull(mood.ordinal + 1)?.let { FlapRules.startOf(it, limit) - 1 } ?: (lo + limit / 2)
        val count = if (mood == FlapMood.EN_FORMA) minOf(3, hi) else (lo + hi.coerceAtLeast(lo)) / 2
        return state(today, goal, count, tracked = 30, previousMood = previousMood, healthyStreak = healthyStreak)
    }

    /** Uno por estado, del mejor al peor. */
    fun all(goal: Int? = 40): List<FlapState> = FlapMood.entries.map { sample(it, goal) }

    fun state(
        today: LocalDate = DAY,
        goal: Int? = 40,
        count: Int = 0,
        tracked: Int = 30,
        previousMood: FlapMood? = null,
        /** `null`: la que sale de la semana de ejemplo (días sanos seguidos hasta ayer). */
        healthyStreak: Int? = null,
    ): FlapState {
        val limit = FlapRules.limitFor(goal)
        val mood = FlapRules.moodFor(count, limit)
        val week = (FlapRules.WEEK_DAYS downTo 1).mapIndexed { i, back ->
            val date = today.minusDays(back.toLong())
            if (back >= tracked) {
                FlapDay(date, null, null)
            } else {
                val c = limit * WEEK_PERCENT[i % WEEK_PERCENT.size] / 100
                FlapDay(date, c, FlapRules.moodFor(c, limit))
            }
        }
        return FlapState(
            today = today,
            goal = goal?.takeIf { it > 0 },
            limit = limit,
            count = count,
            mood = mood,
            life = FlapRules.life(count, limit),
            videosToWorse = FlapRules.videosToWorse(count, limit),
            previousMood = previousMood?.takeIf { it != mood },
            week = week,
            healthyStreak = healthyStreak
                ?: week.asReversed().takeWhile { d -> d.count?.let { FlapRules.healthy(it, limit) } == true }.size,
            trackedDays = tracked,
        )
    }
}

/** [FlapSource] en memoria para tests, previews y galería. */
class FakeFlapSource(initial: FlapState = FlapFakes.sample()) : FlapSource {
    val state = MutableStateFlow(initial)

    /** Estados marcados como vistos, en orden. */
    val seen: MutableList<FlapMood> = mutableListOf()

    override fun observe(): Flow<FlapState> = state

    override suspend fun snapshot(): FlapState = state.value

    override suspend fun markMoodSeen(mood: FlapMood) {
        seen += mood
        state.update { if (it.mood == mood) it.copy(previousMood = null) else it }
    }
}
