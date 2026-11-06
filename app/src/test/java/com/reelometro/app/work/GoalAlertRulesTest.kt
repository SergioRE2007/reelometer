package com.reelometro.app.work

import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.work.GoalAlertRules.Skip
import com.reelometro.app.work.LevelAlertRules.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class GoalAlertRulesTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val yesterday = today.minusDays(1)

    private fun decide(
        enabled: Boolean = true,
        goal: Int? = 150,
        count: Int = 151,
        lastSent: LocalDate? = null,
        force: Boolean = false,
    ): Skip? = GoalAlertRules.decide(enabled, goal, count, today, lastSent, force)

    // ------------------------------------------------------------------ aviso en directo

    @Test
    fun `al superar el objetivo avisa`() {
        assertNull(decide(count = 151))
        assertNull(decide(count = 400))
    }

    @Test
    fun `igualar el objetivo no avisa`() {
        assertEquals(Skip.NOT_OVER, decide(count = 150))
        assertEquals(Skip.NOT_OVER, decide(count = 0))
    }

    @Test
    fun `apagado no avisa aunque se haya pasado`() {
        assertEquals(Skip.DISABLED, decide(enabled = false, count = 300))
        assertEquals(Skip.DISABLED, GoalAlertRules.precheck(false, 150, today, null))
    }

    @Test
    fun `sin objetivo no avisa nunca, ni a la fuerza`() {
        assertEquals(Skip.NO_GOAL, decide(goal = null, count = 999))
        assertEquals(Skip.NO_GOAL, decide(goal = 0, count = 999))
        assertEquals(Skip.NO_GOAL, decide(goal = null, count = 999, force = true))
    }

    @Test
    fun `una sola vez al dia`() {
        assertNull(decide(count = 151, lastSent = null))
        assertEquals(Skip.ALREADY_SENT, decide(count = 152, lastSent = today))
        assertEquals(Skip.ALREADY_SENT, decide(count = 500, lastSent = today))
        // Lo barato ya lo sabe sin contar.
        assertEquals(Skip.ALREADY_SENT, GoalAlertRules.precheck(true, 150, today, today))
    }

    @Test
    fun `al cambiar de dia vuelve a poder avisar`() {
        assertNull(decide(count = 151, lastSent = yesterday))
        assertNull(decide(count = 151, lastSent = today.minusDays(30)))
        // Y el día nuevo empieza por debajo: no avisa hasta volver a pasarse.
        assertEquals(Skip.NOT_OVER, decide(count = 3, lastSent = yesterday))
    }

    @Test
    fun `a la fuerza salta el ajuste y el ya enviado pero no la cuenta`() {
        assertNull(decide(enabled = false, count = 151, lastSent = today, force = true))
        assertEquals(Skip.NOT_OVER, decide(enabled = false, count = 150, lastSent = today, force = true))
    }

    @Test
    fun `el aviso caduca a medianoche`() {
        val zone = ZoneId.of("Europe/Madrid")
        val now = ZonedDateTime.of(2026, 11, 4, 22, 15, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 11, 5, 0, 0, 0, 0, zone), GoalAlertRules.expiry(now))
    }

    // ------------------------------------------------------------------ nivel nuevo

    @Test
    fun `la primera vez el nivel actual es la base y no avisa`() {
        assertEquals(Outcome.Baseline(TravelerLevel.PLATA), LevelAlertRules.decide(true, TravelerLevel.PLATA, null))
        assertEquals(Outcome.Baseline(TravelerLevel.PASAJERO), LevelAlertRules.decide(true, TravelerLevel.PASAJERO, null))
    }

    @Test
    fun `avisa cuando el nivel supera al ultimo avisado`() {
        assertEquals(
            Outcome.Notify(TravelerLevel.PLATA),
            LevelAlertRules.decide(true, TravelerLevel.PLATA, TravelerLevel.FRECUENTE),
        )
        assertEquals(
            Outcome.Notify(TravelerLevel.ORO),
            LevelAlertRules.decide(true, TravelerLevel.ORO, TravelerLevel.PASAJERO),
        )
        assertEquals(Outcome.Keep, LevelAlertRules.decide(true, TravelerLevel.PLATA, TravelerLevel.PLATA))
    }

    @Test
    fun `con el modo pasaporte apagado nunca avisa ni toca la base`() {
        assertEquals(Outcome.Keep, LevelAlertRules.decide(false, TravelerLevel.PLATINO, TravelerLevel.PASAJERO))
        assertEquals(Outcome.Keep, LevelAlertRules.decide(false, TravelerLevel.PLATA, null))
        assertFalse(LevelAlertRules.shouldCheckOnVideo(false, today, null))
    }

    @Test
    fun `si el nivel baja la base baja con el`() {
        assertEquals(
            Outcome.Baseline(TravelerLevel.PASAJERO),
            LevelAlertRules.decide(true, TravelerLevel.PASAJERO, TravelerLevel.ORO),
        )
    }

    @Test
    fun `el nivel se mira con el primer video de cada dia`() {
        assertTrue(LevelAlertRules.shouldCheckOnVideo(true, today, null))
        assertTrue(LevelAlertRules.shouldCheckOnVideo(true, today, yesterday))
        assertFalse(LevelAlertRules.shouldCheckOnVideo(true, today, today))
    }

    @Test
    fun `el nivel guardado se lee por su nombre`() {
        assertEquals(TravelerLevel.ORO, LevelAlertRules.parse("ORO"))
        assertNull(LevelAlertRules.parse(null))
        assertNull(LevelAlertRules.parse("DIAMANTE"))
    }
}
