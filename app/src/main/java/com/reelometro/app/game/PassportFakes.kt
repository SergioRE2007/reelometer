package com.reelometro.app.game

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Estados de ejemplo coherentes para previews, galería y tests de la pantalla (sin datos reales).
 * No los uses en la app de verdad.
 */
object PassportFakes {

    /** Pasaporte recién estrenado: sin sellos, sin reto, Flap calentando. */
    fun fresh(today: LocalDate = LocalDate.of(2026, 11, 4)): PassportState = PassportState(
        today = today,
        goal = null,
        flap = FlapState(mood = FlapMood.NORMAL),
        level = LevelProgress.of(0),
        xp = XpBreakdown(),
        streak = 0,
        stamps = StampId.entries.map { StampState(it) },
        challenge = null,
        pastChallenges = emptyList(),
        pending = emptyList(),
        trackedDays = 0,
    )

    /**
     * Un pasaporte con historia: [mood] de Flap, unos cuantos sellos, reto en curso y, si
     * [withPending], un sello y un nivel nuevos por celebrar.
     */
    fun sample(
        today: LocalDate = LocalDate.of(2026, 11, 4),
        mood: FlapMood = FlapMood.NORMAL,
        xp: Int = 520,
        goal: Int? = 150,
        withPending: Boolean = false,
    ): PassportState {
        val earned = setOf(
            StampId.PRIMER_DIA, StampId.RACHA_3, StampId.RACHA_7, StampId.PRIMERA_SEMANA,
            StampId.WRAPPED_COMPARTIDO, StampId.MADRUGADOR, StampId.MIL_VIDEOS, StampId.PRIMER_RETO,
            StampId.TRES_PLATAFORMAS,
        )
        val stamps = StampId.entries.mapIndexed { i, id ->
            if (id in earned) {
                StampState(id, earnedOn = today.minusDays((40 - i * 3).toLong()), celebrated = true)
            } else {
                StampState(id, progress = if (id == StampId.RACHA_14) 0.5f else null)
            }
        }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val ghost = listOf(160, 140, 190, 120, 175, 230, 210)
        val mine = listOf(130, 125, 150)
        val days = (0 until 7).map { i ->
            val date = monday.plusDays(i.toLong())
            ChallengeDay(date, count = if (date.isAfter(today)) null else mine.getOrElse(i) { 90 }, ghost = ghost[i])
        }
        val total = days.sumOf { it.count ?: 0 }
        val elapsed = days.count { it.count != null }
        val challenge = WeeklyChallenge(
            weekStart = monday,
            reductionPercent = 10,
            ghostTotal = ghost.sum(),
            target = ghost.sum() * 9 / 10,
            total = total,
            ghostSoFar = ghost.take(elapsed).sum(),
            days = days,
            status = ChallengeStatus.EN_CURSO,
        )
        val past = (1..5).map { w ->
            val start = monday.minusWeeks(w.toLong())
            ChallengeResult(start, target = 1_100 - w * 20, total = 1_000 + (w % 2) * 200, won = w % 2 == 0 || w == 1)
        }
        val level = LevelProgress.of(xp)
        return PassportState(
            today = today,
            goal = goal,
            flap = FlapState(
                mood = mood,
                previousMood = null,
                daysUnder = when (mood) {
                    FlapMood.EN_FORMA -> 7
                    FlapMood.NORMAL -> 5
                    FlapMood.CANSADO -> 3
                    FlapMood.ZOMBI -> 0
                },
                daysTracked = 7,
                reference = goal ?: 140,
                referenceIsGoal = goal != null,
            ),
            level = level,
            xp = XpBreakdown(days = xp / 2, streaks = xp / 5, challenges = 40, stamps = xp - xp / 2 - xp / 5 - 40),
            streak = 4,
            stamps = stamps,
            challenge = challenge,
            pastChallenges = past,
            pending = if (withPending) {
                listOf(Celebration.StampEarned(StampId.RACHA_7, today), Celebration.LevelUp(level.level))
            } else {
                emptyList()
            },
            trackedDays = 63,
        )
    }
}

/** [PassportSource] en memoria para previews, galería y tests de ViewModels. */
class FakePassportSource(initial: PassportState = PassportFakes.sample()) : PassportSource {
    val state = MutableStateFlow(initial)
    val events = mutableListOf<GameEvent>()
    val seenMoods = mutableListOf<FlapMood>()

    override fun observe(): Flow<PassportState> = state

    override suspend fun snapshot(): PassportState = state.first()

    override suspend fun markCelebrated(keys: Collection<String>) {
        state.update { s ->
            val stamps = s.stamps.map { st ->
                if ("stamp:${st.id.name}" in keys) st.copy(celebrated = true) else st
            }
            s.copy(stamps = stamps, pending = s.pending.filterNot { it.key in keys })
        }
    }

    override suspend fun markMoodSeen(mood: FlapMood) {
        seenMoods += mood
        state.update { s -> s.copy(flap = s.flap.copy(previousMood = null)) }
    }

    override suspend fun recordEvent(event: GameEvent) {
        events += event
    }
}
