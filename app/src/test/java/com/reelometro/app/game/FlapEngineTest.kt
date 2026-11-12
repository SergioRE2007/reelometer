package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** El cálculo de Flap ([FlapEngine]): hoy, la semana, la racha, el primer registro y lo último visto. */
class FlapEngineTest {

    /** Lunes 16 de noviembre de 2026. */
    private val today: LocalDate = LocalDate.of(2026, 11, 16)

    /** [videos] por día, seguidos desde [from]. */
    private fun daily(from: LocalDate, vararg videos: Int): Map<LocalDate, Int> =
        videos.withIndex().associate { (i, v) -> from.plusDays(i.toLong()) to v }

    private fun compute(
        counts: Map<LocalDate, Int>,
        goal: Int? = 40,
        lastSeen: FlapMood? = null,
        on: LocalDate = today,
    ): FlapState = FlapEngine.compute(on, goal, counts, lastSeen)

    // ------------------------------------------------------------------ hoy

    @Test
    fun `instalación nueva - a tope, sin semana ni racha`() {
        val state = compute(emptyMap(), goal = null)
        assertEquals(today, state.today)
        assertNull(state.goal)
        assertFalse(state.hasGoal)
        assertEquals(FlapRules.DEFAULT_LIMIT, state.limit)
        assertEquals(0, state.count)
        assertEquals(FlapMood.EN_FORMA, state.mood)
        assertEquals(100, state.life)
        assertEquals(75, state.videosToWorse)
        assertNull(state.previousMood)
        assertEquals(0, state.trackedDays)
        assertEquals(0, state.healthyStreak)
        assertEquals(FlapRules.WEEK_DAYS, state.week.size)
        assertTrue(state.week.all { it.count == null && it.mood == null })
        assertEquals((7 downTo 1).map { today.minusDays(it.toLong()) }, state.week.map { it.date })
    }

    @Test
    fun `los vídeos de hoy mandan en el estado, la vida y lo que falta`() {
        val state = compute(mapOf(today to 31, today.minusDays(1) to 500))
        assertEquals(40, state.goal)
        assertTrue(state.hasGoal)
        assertEquals(40, state.limit)
        assertEquals(31, state.count)
        assertEquals(FlapMood.NORMAL, state.mood)
        assertEquals(FlapRules.life(31, 40), state.life)
        assertEquals(1, state.videosToWorse)
    }

    @Test
    fun `KO con el doble del objetivo, sin vídeos que faltar`() {
        val state = compute(mapOf(today to 80))
        assertEquals(FlapMood.KO, state.mood)
        assertEquals(0, state.life)
        assertNull(state.videosToWorse)
    }

    @Test
    fun `objetivo nulo, 0 o negativo - aguanta 150 y no hay objetivo`() {
        for (goal in listOf(null, 0, -10)) {
            val state = compute(mapOf(today to 120), goal = goal)
            assertNull("$goal", state.goal)
            assertEquals("$goal", 150, state.limit)
            assertEquals("$goal", FlapMood.CANSADO, state.mood)
        }
    }

    @Test
    fun `lo de días futuros no cuenta ni para el primer registro`() {
        val state = compute(mapOf(today.plusDays(1) to 999, today.plusDays(30) to 5))
        assertEquals(0, state.count)
        assertEquals(0, state.trackedDays)
        assertTrue(state.week.all { it.count == null })
        assertNull(FlapEngine.firstRecord(today, mapOf(today.plusDays(1) to 3)))
    }

    // ------------------------------------------------------------------ la semana

    @Test
    fun `semana completa - del más antiguo a ayer, con el estado de cada día`() {
        // 21 días con vídeos hasta ayer: la semana son los 7 últimos.
        val counts = daily(today.minusDays(21), *IntArray(14) { 10 }, 10, 25, 35, 45, 90, 0, 40) + (today to 3)
        val state = compute(counts)
        assertEquals((7 downTo 1).map { today.minusDays(it.toLong()) }, state.week.map { it.date })
        assertEquals(listOf(10, 25, 35, 45, 90, 0, 40), state.week.map { it.count })
        assertEquals(
            listOf(FlapMood.EN_FORMA, FlapMood.NORMAL, FlapMood.CANSADO, FlapMood.ENFERMO, FlapMood.KO, FlapMood.EN_FORMA, FlapMood.CANSADO),
            state.week.map { it.mood },
        )
        assertEquals(22, state.trackedDays)
    }

    @Test
    fun `días sin vídeos después del primer registro valen 0`() {
        // Solo hay vídeos hace 6 días y hoy: los de en medio no están en el mapa.
        val state = compute(mapOf(today.minusDays(6) to 12, today to 1))
        assertEquals(listOf(null, 12, 0, 0, 0, 0, 0), state.week.map { it.count })
        assertEquals(listOf(null, FlapMood.EN_FORMA, FlapMood.EN_FORMA, FlapMood.EN_FORMA, FlapMood.EN_FORMA, FlapMood.EN_FORMA, FlapMood.EN_FORMA), state.week.map { it.mood })
        assertEquals(7, state.trackedDays)
        assertEquals(6, state.healthyStreak)
    }

    @Test
    fun `primer registro a mitad de semana - lo de antes va sin datos`() {
        val first = today.minusDays(3)
        val state = compute(daily(first, 50, 20, 0))
        assertEquals(listOf(null, null, null, null, 50, 20, 0), state.week.map { it.count })
        assertNull(state.week[3].mood)
        assertEquals(FlapMood.ENFERMO, state.week[4].mood)
        assertEquals(4, state.trackedDays)
        assertEquals(first, FlapEngine.firstRecord(today, daily(first, 50, 20, 0)))
        // Ayer (0) y anteayer (20) sanos; el día anterior (50) enfermó.
        assertEquals(2, state.healthyStreak)
        assertEquals(0, state.count)
    }

    @Test
    fun `primer registro hoy - un día con registro, semana vacía y sin racha`() {
        val state = compute(mapOf(today to 5))
        assertEquals(1, state.trackedDays)
        assertEquals(0, state.healthyStreak)
        assertTrue(state.week.all { it.count == null })
    }

    @Test
    fun `un día con 0 en el mapa antes del primer vídeo no es el primer registro`() {
        val state = compute(mapOf(today.minusDays(5) to 0, today.minusDays(2) to 7))
        assertEquals(listOf(null, null, null, null, null, 7, 0), state.week.map { it.count })
        assertEquals(3, state.trackedDays)
    }

    @Test
    fun `la semana usa el aguante de ahora`() {
        val counts = daily(today.minusDays(7), 100, 100, 100, 100, 100, 100, 100)
        assertTrue(compute(counts, goal = 40).week.all { it.mood == FlapMood.KO })
        assertTrue(compute(counts, goal = 100).week.all { it.mood == FlapMood.CANSADO })
        assertTrue(compute(counts, goal = null).week.all { it.mood == FlapMood.NORMAL })
    }

    // ------------------------------------------------------------------ la racha

    @Test
    fun `racha - días seguidos hasta ayer sin enfermar, aunque sean más de 7`() {
        val counts = daily(today.minusDays(30), *IntArray(30) { 40 })
        assertEquals(30, compute(counts).healthyStreak)
        assertEquals(31, compute(counts).trackedDays)
        // Con un objetivo más bajo, los mismos días ya enferman: la racha usa el aguante de ahora.
        assertEquals(0, compute(counts, goal = 39).healthyStreak)
    }

    @Test
    fun `racha - un día enfermo la corta y hoy no cuenta`() {
        val counts = daily(today.minusDays(10), 10, 10, 41, 10, 10, 10, 0, 0, 10, 40) + (today to 500)
        val state = compute(counts)
        assertEquals(7, state.healthyStreak)
        assertEquals(FlapMood.KO, state.mood)
        // Ayer enfermó: racha 0.
        assertEquals(0, compute(counts + (today.minusDays(1) to 41)).healthyStreak)
    }

    @Test
    fun `racha sin objetivo con el aguante de 150`() {
        val counts = daily(today.minusDays(3), 150, 151, 149)
        assertEquals(1, compute(counts, goal = null).healthyStreak)
    }

    // ------------------------------------------------------------------ lo último visto

    @Test
    fun `previousMood - lo visto si es distinto de ahora, y si no, nada`() {
        val counts = mapOf(today to 5)
        assertNull(compute(counts, lastSeen = null).previousMood)
        assertNull(compute(counts, lastSeen = FlapMood.EN_FORMA).previousMood)
        val revived = compute(counts, lastSeen = FlapMood.KO)
        assertEquals(FlapMood.KO, revived.previousMood)
        assertTrue(revived.recovered)
        assertFalse(revived.worsened)
        val worse = compute(mapOf(today to 60), lastSeen = FlapMood.NORMAL)
        assertEquals(FlapMood.NORMAL, worse.previousMood)
        assertTrue(worse.worsened)
        assertFalse(worse.recovered)
    }

    @Test
    fun `a medianoche revive - el mismo historial con el día nuevo`() {
        val counts = mapOf(today to 120)
        val night = compute(counts, lastSeen = FlapMood.KO)
        assertEquals(FlapMood.KO, night.mood)
        assertNull(night.previousMood)
        val morning = compute(counts, lastSeen = FlapMood.KO, on = today.plusDays(1))
        assertEquals(FlapMood.EN_FORMA, morning.mood)
        assertEquals(100, morning.life)
        assertEquals(FlapMood.KO, morning.previousMood)
        assertTrue(morning.recovered)
        assertEquals(120, morning.week.last().count)
        assertEquals(FlapMood.KO, morning.week.last().mood)
        assertEquals(0, morning.healthyStreak)
        assertEquals(2, morning.trackedDays)
    }

    @Test
    fun `lo mismo da lo mismo`() {
        val counts = daily(today.minusDays(9), 3, 60, 0, 12, 41, 7, 7, 7, 90, 40)
        assertEquals(compute(counts, lastSeen = FlapMood.CANSADO), compute(counts, lastSeen = FlapMood.CANSADO))
    }
}
