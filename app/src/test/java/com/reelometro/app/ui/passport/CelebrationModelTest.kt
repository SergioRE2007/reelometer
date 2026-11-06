package com.reelometro.app.ui.passport

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.Celebration
import com.reelometro.app.game.ChallengeResult
import com.reelometro.app.game.PassportFakes
import com.reelometro.app.game.StampId
import com.reelometro.app.game.TravelerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Celebraciones: una vez cada una, en orden, y con el texto y el arte que tocan. */
class CelebrationModelTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val state = PassportFakes.sample(today, withPending = true)

    @Test
    fun `sale la primera pendiente que no se haya cerrado`() {
        val pending = state.pending
        assertEquals(pending[0], CelebrationModel.next(pending, emptySet()))
        assertEquals(pending[1], CelebrationModel.next(pending, setOf(pending[0].key)))
        assertNull(CelebrationModel.next(pending, pending.map { it.key }.toSet()))
        assertNull(CelebrationModel.next(emptyList(), emptySet()))
    }

    @Test
    fun `sello nuevo - se estampa conseguido y lleva al pasaporte`() {
        val ui = CelebrationModel.of(Celebration.StampEarned(StampId.RACHA_7, today), state)
        assertEquals(CelebrationType.STAMP, ui.type)
        assertEquals(PassportCopy.CELEBRATE_STAMP, ui.label)
        assertEquals("Racha de 7", ui.title)
        assertEquals("stamp:RACHA_7", ui.key)
        val stamp = ui.stamp!!
        assertTrue(stamp.earned)
        assertEquals(PassportCopy.stampDate(today), stamp.date)
        assertEquals(PassportCopy.SEE_PASSPORT, ui.actionLabel)
    }

    @Test
    fun `nivel nuevo - panel con el código y ticket compartible`() {
        val ui = CelebrationModel.of(Celebration.LevelUp(TravelerLevel.ORO), state)
        assertEquals(CelebrationType.LEVEL, ui.type)
        assertEquals("ORO", ui.title)
        assertEquals("ORO", ui.levelCode)
        assertEquals(TravelerLevel.ORO, ui.level)
        assertEquals(PassportCopy.SHARE_TICKET, ui.actionLabel)
        assertEquals(PassportCopy.SHARE_TICKET_DESCRIPTION, ui.actionDescription)
    }

    @Test
    fun `reto cumplido - la cifra es la protagonista`() {
        val result = ChallengeResult(LocalDate.of(2026, 10, 26), target = 1_100, total = 980, won = true)
        val ui = CelebrationModel.of(Celebration.ChallengeWon(result), state)
        assertEquals(CelebrationType.CHALLENGE, ui.type)
        assertEquals("980", ui.figure)
        assertEquals("Semana del 26 de octubre", ui.title)
        assertEquals("challenge:2026-10-26", ui.key)
    }

    @Test
    fun `todos los textos de celebración cumplen las reglas`() {
        val all = StampId.entries.map { Celebration.StampEarned(it, today) } +
            TravelerLevel.entries.map { Celebration.LevelUp(it) } +
            listOf(Celebration.ChallengeWon(ChallengeResult(today, 1_234, 1, true)))
        for (days in 0L..14L) {
            val s = state.copy(today = today.plusDays(days))
            all.forEach { c ->
                val ui = CelebrationModel.of(c, s)
                listOfNotNull(ui.label, ui.title, ui.caption, ui.actionLabel, ui.actionDescription, ui.figure, ui.figureCaption).forEach { t ->
                    assertTrue("«$t»: ${CopyRules.violations(t)}", CopyRules.violations(t).isEmpty())
                }
            }
        }
    }
}
