package com.reelometro.app.game

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El contrato de `game/` que usan la pantalla y el widget: [FlapState], [FlapFakes] y [FakeFlapSource]. */
class FlapContractTest {

    @Test
    fun `los datos falsos cumplen las reglas`() {
        for (goal in listOf(40, null)) {
            val limit = FlapRules.limitFor(goal)
            val all = FlapFakes.all(goal)
            assertEquals("$goal", FlapMood.entries, all.map { it.mood })
            all.forEach { s ->
                val tag = "$goal/${s.mood}"
                assertEquals(tag, goal, s.goal)
                assertEquals(tag, limit, s.limit)
                assertEquals(tag, FlapRules.moodFor(s.count, limit), s.mood)
                assertEquals(tag, FlapRules.life(s.count, limit), s.life)
                assertEquals(tag, FlapRules.videosToWorse(s.count, limit), s.videosToWorse)
                assertEquals(tag, (FlapRules.WEEK_DAYS downTo 1).map { s.today.minusDays(it.toLong()) }, s.week.map { it.date })
                s.week.forEach { d -> assertEquals(tag, d.count?.let { FlapRules.moodFor(it, limit) }, d.mood) }
                // La racha cuadra con la semana: ayer y anteayer sanos, hace 3 días KO.
                assertEquals(tag, 2, s.healthyStreak)
            }
        }
    }

    @Test
    fun `la instalación nueva de ejemplo está a tope y sin semana`() {
        val fresh = FlapFakes.fresh()
        assertEquals(0, fresh.trackedDays)
        assertEquals(0, fresh.healthyStreak)
        assertEquals(FlapMood.EN_FORMA, fresh.mood)
        assertEquals(FlapRules.MAX_LIFE, fresh.life)
        assertFalse(fresh.hasGoal)
        assertEquals(FlapRules.DEFAULT_LIMIT, fresh.limit)
        assertEquals(FlapRules.WEEK_DAYS, fresh.week.size)
        assertTrue(fresh.week.all { it.count == null && it.mood == null })
    }

    @Test
    fun `con pocos días de registro la semana de ejemplo deja huecos`() {
        val state = FlapFakes.state(tracked = 3)
        assertEquals(listOf(null, null, null, null, null), state.week.take(5).map { it.count })
        assertTrue(state.week.drop(5).all { it.count != null && it.mood != null })
        assertEquals(2, state.healthyStreak)
        assertEquals(0, FlapFakes.state(tracked = 1).healthyStreak)
    }

    @Test
    fun `recovered y worsened comparan con lo último visto`() {
        val tired = FlapFakes.sample(FlapMood.CANSADO)
        assertNull(tired.previousMood)
        assertFalse(tired.recovered)
        assertFalse(tired.worsened)
        assertTrue(tired.copy(previousMood = FlapMood.KO).recovered)
        assertFalse(tired.copy(previousMood = FlapMood.KO).worsened)
        assertTrue(tired.copy(previousMood = FlapMood.EN_FORMA).worsened)
        assertFalse(tired.copy(previousMood = FlapMood.EN_FORMA).recovered)
        // Lo visto igual que ahora no es un cambio.
        assertNull(FlapFakes.sample(FlapMood.NORMAL, previousMood = FlapMood.NORMAL).previousMood)
    }

    @Test
    fun `FakeFlapSource apunta lo visto y solo quita el cambio si es el estado de ahora`() = runBlocking {
        val source = FakeFlapSource(FlapFakes.sample(FlapMood.ENFERMO, previousMood = FlapMood.NORMAL))
        assertEquals(FlapMood.NORMAL, source.snapshot().previousMood)
        source.markMoodSeen(FlapMood.KO)
        assertEquals(FlapMood.NORMAL, source.observe().first().previousMood)
        source.markMoodSeen(FlapMood.ENFERMO)
        assertNull(source.snapshot().previousMood)
        assertEquals(listOf(FlapMood.KO, FlapMood.ENFERMO), source.seen)
    }
}
