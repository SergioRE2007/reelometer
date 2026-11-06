package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprobaciones del contrato de `game/` (modelos, sprite y datos falsos). */
class GameContractTest {

    @Test
    fun `cada fotograma de Flap es una rejilla cuadrada con píxeles válidos`() {
        for (mood in FlapMood.entries) for (frame in FlapFrame.entries) {
            val rows = FlapSprite.frame(mood, frame)
            assertEquals("$mood/$frame filas", FlapSprite.SIZE, rows.size)
            rows.forEach { row ->
                assertEquals("$mood/$frame ancho de «$row»", FlapSprite.SIZE, row.length)
                assertTrue("$mood/$frame píxel no válido en «$row»", row.all { it in FlapSprite.PIXELS })
            }
        }
    }

    @Test
    fun `los niveles suben con la experiencia y el último no tiene siguiente`() {
        assertEquals(TravelerLevel.PASAJERO, LevelProgress.of(0).level)
        assertEquals(TravelerLevel.PASAJERO, LevelProgress.of(-5).level)
        TravelerLevel.entries.forEach { level ->
            assertEquals(level, LevelProgress.of(level.minXp).level)
        }
        val top = LevelProgress.of(TravelerLevel.PLATINO.minXp + 999)
        assertNull(top.next)
        assertNull(top.xpForNext)
        assertEquals(1f, top.fraction)
        val mid = LevelProgress.of((TravelerLevel.FRECUENTE.minXp + TravelerLevel.PLATA.minXp) / 2)
        assertEquals(TravelerLevel.FRECUENTE, mid.level)
        assertTrue(mid.fraction in 0.49f..0.51f)
    }

    @Test
    fun `los sellos irónicos no dan experiencia`() {
        StampId.entries.filter { it.ironic }.forEach { assertEquals("$it", 0, it.xp) }
        assertTrue(StampId.entries.size in 20..25)
    }

    @Test
    fun `los datos falsos son coherentes`() {
        val s = PassportFakes.sample(withPending = true)
        assertEquals(StampId.entries.size, s.stamps.size)
        assertEquals(7, s.challenge!!.days.size)
        assertEquals(2, s.pending.size)
        val fresh = PassportFakes.fresh()
        assertEquals(0, fresh.earnedStamps)
        assertTrue(fresh.flap.warmingUp)
    }
}
