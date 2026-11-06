package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PassportRulesTest {

    @Test
    fun `los umbrales de Flap con la ventana entera`() {
        // Días en la referencia o por debajo → estado (7 días con registro).
        val expected = mapOf(
            7 to FlapMood.EN_FORMA, 6 to FlapMood.EN_FORMA,
            5 to FlapMood.NORMAL, 4 to FlapMood.NORMAL,
            3 to FlapMood.CANSADO, 2 to FlapMood.CANSADO,
            1 to FlapMood.ZOMBI, 0 to FlapMood.ZOMBI,
        )
        expected.forEach { (under, mood) -> assertEquals("$under días bajo", mood, PassportRules.moodFor(7 - under, 7)) }
    }

    @Test
    fun `sin días con registro Flap está NORMAL y los que faltan no cuentan en contra`() {
        assertEquals(FlapMood.NORMAL, PassportRules.moodFor(0, 0))
        assertEquals(FlapMood.EN_FORMA, PassportRules.moodFor(1, 2))
        assertEquals(FlapMood.NORMAL, PassportRules.moodFor(2, 2))
        assertEquals(FlapMood.CANSADO, PassportRules.moodFor(4, 4))
    }

    @Test
    fun `las bonificaciones de racha llegan a 3, 7, 14, 30 y cada 30 más`() {
        val bonuses = (1..120).associateWith { PassportRules.streakBonus(it) }.filterValues { it > 0 }
        assertEquals(mapOf(3 to 15, 7 to 30, 14 to 60, 30 to 120, 60 to 120, 90 to 120, 120 to 120), bonuses)
        assertEquals(0, PassportRules.streakBonus(0))
    }

    @Test
    fun `la dificultad del reto sale de los retos anteriores`() {
        fun r(won: Boolean, week: Long) = ChallengeResult(LUNES.minusWeeks(week), 100, 90, won)
        assertEquals(0, PassportRules.reductionPercent(emptyList()))
        assertEquals(10, PassportRules.reductionPercent(listOf(r(true, 1))))
        assertEquals(0, PassportRules.reductionPercent(listOf(r(false, 1), r(true, 2), r(true, 3))))
        assertEquals(10, PassportRules.reductionPercent(listOf(r(true, 1), r(true, 2), r(false, 3))))
        assertEquals(20, PassportRules.reductionPercent(listOf(r(true, 1), r(true, 2), r(true, 3), r(false, 4))))
    }

    @Test
    fun `el tope del reto nunca es negativo y redondea hacia abajo`() {
        assertEquals(999, PassportRules.challengeTarget(1_000, 0))
        assertEquals(900, PassportRules.challengeTarget(1_000, 10))
        assertEquals(800, PassportRules.challengeTarget(1_000, 20))
        assertEquals(4, PassportRules.challengeTarget(5, 10))
        assertEquals(0, PassportRules.challengeTarget(1, 0))
        assertEquals(0, PassportRules.challengeTarget(0, 0))
        assertEquals(0, PassportRules.challengeTarget(1, 20))
    }

    @Test
    fun `la semana empieza en lunes, también al cambiar de año`() {
        assertEquals(LUNES, PassportRules.weekStart(LUNES))
        assertEquals(LUNES, PassportRules.weekStart(LUNES.plusDays(6)))
        assertEquals(LocalDate.of(2026, 12, 28), PassportRules.weekStart(LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun `cinco categorías en una misma semana`() {
        val week = listOf(dia(0), dia(1), dia(1), dia(4), dia(6))
        assertEquals(dia(6), PassportRules.varietyDay(week))
        assertNull(PassportRules.varietyDay(week.dropLast(1)))
        // Cuatro una semana y dos la siguiente no suman: cada semana va por separado.
        assertNull(PassportRules.varietyDay(week.dropLast(1) + listOf(dia(7), dia(8))))
        // Dos semanas con cinco: la primera fecha en que se llegó.
        val later = (7..11).map { dia(it) }
        assertEquals(dia(6), PassportRules.varietyDay(later + week))
        assertEquals(dia(11), PassportRules.varietyDay(later))
    }
}
