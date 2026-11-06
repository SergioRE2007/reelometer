package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reto semanal contra el «yo fantasma» (la semana anterior entera). */
class PassportChallengeTest {

    @Test
    fun `sin semana anterior con registro no hay reto`() {
        assertNull(calcular(dia(2), null, iguales(LUNES, 3, 100)).state.challenge)
        // Cuatro días de la semana anterior no bastan; cinco sí.
        assertNull(calcular(dia(9), null, iguales(dia(3), 7, 100)).state.challenge)
        val five = calcular(dia(9), null, iguales(dia(2), 8, 100)).state.challenge
        assertEquals(dia(7), five?.weekStart)
    }

    @Test
    fun `con la semana anterior a medias el fantasma se estima con su media`() {
        // Fantasma: de miércoles a domingo, 100 al día. Esta semana: 50 al día hasta hoy (miércoles).
        val counts = iguales(dia(2), 5, 100) + iguales(dia(7), 3, 50)
        val c = calcular(dia(9), null, counts).state.challenge!!
        assertEquals(700, c.ghostTotal)
        assertEquals(0, c.reductionPercent)
        assertEquals(699, c.target)
        assertEquals(150, c.total)
        assertEquals(549, c.remaining)
        assertEquals(300, c.ghostSoFar)
        assertEquals(ChallengeStatus.EN_CURSO, c.status)
        assertEquals((7..13).map { dia(it) }, c.days.map { it.date })
        assertEquals(listOf(50, 50, 50, null, null, null, null), c.days.map { it.count })
        assertEquals(listOf(null, null, 100, 100, 100, 100, 100), c.days.map { it.ghost })
    }

    @Test
    fun `una semana anterior sin vídeos no da reto`() {
        val counts = iguales(LUNES, 7, 100) + (dia(16) to 5)
        assertNull(calcular(dia(16), null, counts).state.challenge)
    }

    @Test
    fun `la dificultad sube con los retos ganados y vuelve a 0 al perder`() {
        val counts = iguales(LUNES, 7, 1_000 / 7) + (dia(6) to 1_000 - 6 * (1_000 / 7))
        fun target(goal: Int?, vararg won: Boolean): Int {
            val results = won.mapIndexed { i, w -> ChallengeResult(dia(-7 * (won.size - i)), 100, 90, w) }
            return calcular(dia(8), goal, counts, estrenada().copy(challenges = results)).state.challenge!!.target
        }
        assertEquals(999, target(null))
        assertEquals(900, target(null, true))
        assertEquals(800, target(null, true, true, true))
        assertEquals(999, target(null, true, true, false))
        assertEquals(800, target(null, false, true, true, true))
        // Si la semana fantasma ya estaba dentro del objetivo (1.000 ≤ 7 × 200), no se aprieta más.
        assertEquals(999, target(200, true, true, true))
        assertEquals(800, target(100, true, true, true))
    }

    @Test
    fun `fallado en cuanto se pasa del tope, en curso si lo iguala`() {
        val ghost = iguales(LUNES, 7, 100)
        val equal = calcular(dia(7), null, ghost + (dia(7) to 699)).state.challenge!!
        assertEquals(ChallengeStatus.EN_CURSO, equal.status)
        assertEquals(0, equal.remaining)
        val over = calcular(dia(7), null, ghost + (dia(7) to 700)).state.challenge!!
        assertEquals(ChallengeStatus.FALLADO, over.status)
        assertEquals(0, over.remaining)
        assertEquals(100, over.ghostSoFar)
    }

    @Test
    fun `las semanas pasadas se cierran en orden aunque no se abriera la app`() {
        val counts = iguales(LUNES, 7, 100) + iguales(dia(7), 7, 600 / 7) + (dia(13) to 600 - 6 * (600 / 7)) +
            iguales(dia(14), 7, 100)
        val r = calcular(dia(21), null, counts)
        val week2 = ChallengeResult(dia(7), target = 699, total = 600, won = true)
        val week3 = ChallengeResult(dia(14), target = 540, total = 700, won = false)
        assertEquals(listOf(week3, week2), r.state.pastChallenges)
        assertEquals(listOf(week2, week3), r.memory.challenges)
        assertEquals(PassportRules.CHALLENGE_XP, r.state.xp.challenges)
        assertEquals(dia(13), r.state.fecha(StampId.PRIMER_RETO))
        // Esta semana: tras perder la anterior, otra vez «menos que la anterior».
        val current = r.state.challenge!!
        assertEquals(dia(21), current.weekStart)
        assertEquals(0, current.reductionPercent)
        assertEquals(699, current.target)
    }

    @Test
    fun `un reto guardado no cambia aunque cambien los datos o el objetivo`() {
        val counts = iguales(LUNES, 7, 100) + iguales(dia(7), 7, 80) + iguales(dia(14), 7, 100)
        val stored = ChallengeResult(dia(7), target = 100, total = 560, won = false)
        val r = calcular(dia(21), 50, counts, estrenada().copy(challenges = listOf(stored)))
        assertEquals(stored, r.state.pastChallenges.last())
        // La siguiente semana sale de lo guardado (perdido → 0 %): fantasma 560, tope 559.
        assertEquals(ChallengeResult(dia(14), target = 559, total = 700, won = false), r.state.pastChallenges.first())
        assertNull(r.state.fecha(StampId.PRIMER_RETO))
    }

    @Test
    fun `el reto cruza el cambio de año`() {
        val start = LocalDate.of(2026, 12, 14)
        val today = LocalDate.of(2027, 1, 2)
        val c = calcular(today, null, iguales(start, 20, 10)).state.challenge!!
        assertEquals(LocalDate.of(2026, 12, 28), c.weekStart)
        assertEquals(LocalDate.of(2027, 1, 3), c.days.last().date)
        assertNull(c.days.last().count)
        assertEquals(10, c.days[5].count)
        assertEquals(70, c.ghostTotal)
        assertEquals(69, c.target)
        assertEquals(60, c.total)
        assertEquals(ChallengeStatus.EN_CURSO, c.status)
    }

    @Test
    fun `el reto ganado se celebra una vez`() {
        val counts = iguales(LUNES, 7, 100) + iguales(dia(7), 7, 50)
        val r = calcular(dia(14), null, counts, estrenada())
        val won = r.state.pastChallenges.single()
        assertTrue(won.won)
        assertTrue(Celebration.ChallengeWon(won) in r.state.pending)
        val seen = r.memory.celebrate(listOf(Celebration.ChallengeWon(won).key))
        val again = calcular(dia(15), null, counts, seen).state
        assertFalse(again.pending.any { it is Celebration.ChallengeWon })
    }
}
