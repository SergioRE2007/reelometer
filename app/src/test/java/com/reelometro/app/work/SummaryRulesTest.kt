package com.reelometro.app.work

import com.reelometro.app.work.SummaryRules.Run
import com.reelometro.app.work.SummaryRules.Skip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SummaryRulesTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val nine = LocalTime.of(21, 30)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0, zone: ZoneId = madrid) =
        ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi, s), zone)

    // --- Siguiente aviso diario ---------------------------------------------------------------

    @Test
    fun `si todavia no es la hora, el diario es hoy`() {
        val now = at(2026, 11, 3, 18, 0)
        assertEquals(at(2026, 11, 3, 21, 30), SummaryRules.nextDaily(now, nine))
    }

    @Test
    fun `si ya ha pasado la hora, el diario es mañana`() {
        val now = at(2026, 11, 3, 21, 30)
        assertEquals(at(2026, 11, 4, 21, 30), SummaryRules.nextDaily(now, nine))
    }

    @Test
    fun `cambio a horario de verano en marzo - el dia dura 23 horas`() {
        // Europe/Madrid: el domingo 29 de marzo de 2026 a las 02:00 pasan a ser las 03:00.
        val now = at(2026, 3, 28, 21, 30, 5)
        val next = SummaryRules.nextDaily(now, nine, after = LocalDate.of(2026, 3, 28))
        assertEquals(LocalDateTime.of(2026, 3, 29, 21, 30), next.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(2), next.offset)
        assertEquals(Duration.ofHours(23).minusSeconds(5), Duration.between(now, next))
    }

    @Test
    fun `cambio a horario de invierno en octubre - el dia dura 25 horas`() {
        // Europe/Madrid: el domingo 25 de octubre de 2026 a las 03:00 vuelven a ser las 02:00.
        val now = at(2026, 10, 24, 21, 30)
        val next = SummaryRules.nextDaily(now, nine, after = LocalDate.of(2026, 10, 24))
        assertEquals(LocalDateTime.of(2026, 10, 25, 21, 30), next.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(1), next.offset)
        assertEquals(Duration.ofHours(25), Duration.between(now, next))
    }

    @Test
    fun `una hora que no existe en marzo pasa a la siguiente valida`() {
        val now = at(2026, 3, 28, 23, 0)
        val next = SummaryRules.nextDaily(now, LocalTime.of(2, 30))
        assertEquals(LocalDateTime.of(2026, 3, 29, 3, 30), next.toLocalDateTime())
        assertEquals(Duration.ofHours(3).plusMinutes(30), Duration.between(now, next))
    }

    @Test
    fun `una hora repetida en octubre solo avisa una vez`() {
        val first = SummaryRules.nextDaily(at(2026, 10, 24, 23, 0), LocalTime.of(2, 30))
        assertEquals(ZoneOffset.ofHours(2), first.offset) // la primera de las dos 02:30
        // Tras ejecutarse, el siguiente es otro día, no la segunda 02:30 de ese mismo domingo.
        val second = SummaryRules.nextDaily(first.plusSeconds(1), LocalTime.of(2, 30), after = first.toLocalDate())
        assertEquals(LocalDateTime.of(2026, 10, 26, 2, 30), second.toLocalDateTime())
    }

    @Test
    fun `tras avisar, el siguiente es otro dia aunque el reloj vaya justo`() {
        // El trabajo de las 21:30 se ejecuta un segundo antes de su hora (reloj ajustado).
        val now = at(2026, 11, 3, 21, 29, 59)
        assertEquals(at(2026, 11, 3, 21, 30), SummaryRules.nextDaily(now, nine))
        assertEquals(at(2026, 11, 4, 21, 30), SummaryRules.nextDaily(now, nine, after = LocalDate.of(2026, 11, 3)))
    }

    // --- Ejecución del diario -----------------------------------------------------------------

    @Test
    fun `a su hora resume hoy`() {
        val run = SummaryRules.dailyRun(LocalDate.of(2026, 11, 3), nine, at(2026, 11, 3, 21, 30, 2))
        assertEquals(Run.Notify(LocalDate.of(2026, 11, 3), late = false), run)
    }

    @Test
    fun `se ejecuta tarde, despues de medianoche - resume el dia programado y dice ayer`() {
        val run = SummaryRules.dailyRun(LocalDate.of(2026, 11, 3), nine, at(2026, 11, 4, 0, 40))
        assertEquals(Run.Notify(LocalDate.of(2026, 11, 3), late = true), run)
    }

    @Test
    fun `tarde pero el mismo dia sigue siendo hoy`() {
        val run = SummaryRules.dailyRun(LocalDate.of(2026, 11, 3), LocalTime.of(9, 0), at(2026, 11, 3, 23, 50))
        assertEquals(Run.Notify(LocalDate.of(2026, 11, 3), late = false), run)
    }

    @Test
    fun `mas de 12 horas tarde se omite`() {
        assertEquals(Run.Stale, SummaryRules.dailyRun(LocalDate.of(2026, 11, 3), nine, at(2026, 11, 4, 9, 31)))
        assertEquals(Run.Stale, SummaryRules.dailyRun(LocalDate.of(2026, 11, 1), nine, at(2026, 11, 4, 21, 30)))
    }

    @Test
    fun `tarde la noche del cambio de hora de octubre cuenta las horas reales`() {
        // 21:30 del sábado 24 + 12 h reales = 08:30 del domingo 25 (ese día tiene una hora más).
        val day = LocalDate.of(2026, 10, 24)
        assertEquals(Run.Notify(day, late = true), SummaryRules.dailyRun(day, nine, at(2026, 10, 25, 8, 30)))
        assertEquals(Run.Stale, SummaryRules.dailyRun(day, nine, at(2026, 10, 25, 8, 31)))
    }

    @Test
    fun `antes de hora (la zona ha cambiado hacia el oeste) no avisa y se reprograma`() {
        // Programado para las 21:30 de Madrid; el móvil ya está en Nueva York (15:30 allí).
        val ny = ZoneId.of("America/New_York")
        val now = at(2026, 11, 3, 21, 30).withZoneSameInstant(ny)
        val day = LocalDate.of(2026, 11, 3)
        assertEquals(Run.Early, SummaryRules.dailyRun(day, nine, now))
        assertEquals(LocalDateTime.of(2026, 11, 3, 21, 30), SummaryRules.nextDaily(now, nine).toLocalDateTime())
    }

    // --- Mensual ------------------------------------------------------------------------------

    @Test
    fun `el mensual es el dia 1 a las 10`() {
        assertEquals(at(2026, 12, 1, 10, 0), SummaryRules.nextMonthly(at(2026, 11, 3, 18, 0)))
        assertEquals(at(2026, 11, 1, 10, 0), SummaryRules.nextMonthly(at(2026, 11, 1, 9, 59)))
        assertEquals(at(2027, 1, 1, 10, 0), SummaryRules.nextMonthly(at(2026, 12, 1, 10, 0, 1)))
    }

    @Test
    fun `el mensual de abril salta el cambio de hora de marzo`() {
        val now = at(2026, 3, 1, 10, 0, 1)
        val next = SummaryRules.nextMonthly(now, after = LocalDate.of(2026, 3, 1))
        assertEquals(LocalDateTime.of(2026, 4, 1, 10, 0), next.toLocalDateTime())
        assertEquals(Duration.ofDays(31).minusHours(1).minusSeconds(1), Duration.between(now, next))
    }

    @Test
    fun `el mensual resume el mes anterior y vale mientras dure el mes`() {
        val first = LocalDate.of(2026, 11, 1)
        assertEquals(Run.Notify(LocalDate.of(2026, 10, 1), late = false), SummaryRules.monthlyRun(first, at(2026, 11, 1, 10, 0, 3)))
        assertEquals(Run.Notify(LocalDate.of(2026, 10, 1), late = true), SummaryRules.monthlyRun(first, at(2026, 11, 3, 8, 0)))
        assertEquals(Run.Stale, SummaryRules.monthlyRun(first, at(2026, 12, 1, 9, 0)))
        assertEquals(Run.Early, SummaryRules.monthlyRun(first, at(2026, 11, 1, 9, 0)))
    }

    @Test
    fun `el mensual de enero resume diciembre del año anterior`() {
        val run = SummaryRules.monthlyRun(LocalDate.of(2027, 1, 1), at(2027, 1, 1, 10, 0))
        assertEquals(Run.Notify(LocalDate.of(2026, 12, 1), late = false), run)
    }

    // --- Avisar o no --------------------------------------------------------------------------

    @Test
    fun `sin permiso no se avisa nunca, ni forzado`() {
        assertEquals(Skip.NO_PERMISSION, SummaryRules.dailySkip(enabled = true, canNotify = false, serviceOn = true))
        assertEquals(Skip.NO_PERMISSION, SummaryRules.dailySkip(enabled = true, canNotify = false, serviceOn = true, force = true))
        assertEquals(Skip.NO_PERMISSION, SummaryRules.monthlySkip(true, canNotify = false, trackedDays = 30, count = 10, serviceOn = true, force = true))
    }

    @Test
    fun `con el servicio desactivado no se avisa del dia (un cero enganaria)`() {
        assertEquals(Skip.SERVICE_OFF, SummaryRules.dailySkip(enabled = true, canNotify = true, serviceOn = false))
        assertNull(SummaryRules.dailySkip(enabled = true, canNotify = true, serviceOn = true))
    }

    @Test
    fun `con el aviso apagado no se avisa salvo forzado para QA`() {
        assertEquals(Skip.DISABLED, SummaryRules.dailySkip(enabled = false, canNotify = true, serviceOn = true))
        assertNull(SummaryRules.dailySkip(enabled = false, canNotify = true, serviceOn = false, force = true))
    }

    @Test
    fun `el mes sin registros se omite y el mes a cero con el servicio apagado tambien`() {
        assertEquals(Skip.NO_DATA, SummaryRules.monthlySkip(true, true, trackedDays = 0, count = 0, serviceOn = true))
        assertEquals(Skip.SERVICE_OFF, SummaryRules.monthlySkip(true, true, trackedDays = 31, count = 0, serviceOn = false))
        assertNull(SummaryRules.monthlySkip(true, true, trackedDays = 31, count = 0, serviceOn = true))
        assertNull(SummaryRules.monthlySkip(true, true, trackedDays = 12, count = 4_812, serviceOn = false))
        assertEquals(Skip.DISABLED, SummaryRules.monthlySkip(false, true, trackedDays = 31, count = 4_812, serviceOn = true))
    }

    // --- Caducidad y configuración ------------------------------------------------------------

    @Test
    fun `el diario caduca a medianoche, tambien el dia del cambio de hora`() {
        assertEquals(at(2026, 11, 4, 0, 0), SummaryRules.dailyExpiry(at(2026, 11, 3, 21, 30)))
        // 01:00 del domingo 25 de octubre: hasta la medianoche quedan 23 h de reloj, pero 24 h reales.
        val now = at(2026, 10, 25, 1, 0)
        assertEquals(at(2026, 10, 26, 0, 0), SummaryRules.dailyExpiry(now))
        assertEquals(Duration.ofHours(24), Duration.between(now, SummaryRules.dailyExpiry(now)))
    }

    @Test
    fun `el mensual caduca al empezar el mes siguiente`() {
        assertEquals(at(2026, 12, 1, 0, 0), SummaryRules.monthlyExpiry(at(2026, 11, 1, 10, 0)))
    }

    @Test
    fun `la clave de configuracion cambia con la hora y la zona y no con nada mas`() {
        val key = SummaryRules.dailyKey(nine, madrid)
        assertEquals("daily@21:30@Europe/Madrid", key)
        assertEquals(key, SummaryRules.dailyKey(LocalTime.of(21, 30), ZoneId.of("Europe/Madrid")))
        assertNotEquals(key, SummaryRules.dailyKey(LocalTime.of(22, 0), madrid))
        assertNotEquals(key, SummaryRules.dailyKey(nine, ZoneId.of("Atlantic/Canary")))
        assertTrue(SummaryRules.monthlyKey(madrid).startsWith("monthly@10:00@"))
    }
}
