package com.reelometro.app.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class FormatTest {

    private fun nb(s: String) = s.replace(' ', Format.NBSP)

    @Test
    fun `enteros con punto de millar siempre`() {
        assertEquals("7", Format.integer(7))
        assertEquals("212", Format.integer(212))
        assertEquals("1.234", Format.integer(1234))
        assertEquals("12.408", Format.integer(12408))
        assertEquals("1.234.567", Format.integer(1_234_567))
        assertEquals("−1.234", Format.integer(-1234))
    }

    @Test
    fun `decimales con coma`() {
        assertEquals("3", Format.decimal(3.0))
        assertEquals("3,2", Format.decimal(3.24))
        assertEquals("13", Format.decimal(12.7))
        assertEquals("1.235", Format.decimal(1234.5))
        assertEquals("0,4", Format.decimal(0.4))
        assertEquals("10", Format.decimal(9.96))
        assertEquals("2,25", Format.decimal(2.25, 2))
    }

    @Test
    fun `porcentajes`() {
        assertEquals(nb("34 %"), Format.percent(0.34))
        assertEquals(nb("0,4 %"), Format.percent(0.004))
        assertEquals(nb("0 %"), Format.percent(0.0))
        assertEquals(nb("+34 %"), Format.signedPercent(0.34))
        assertEquals(nb("−12 %"), Format.signedPercent(-0.12))
        assertEquals(nb("±0 %"), Format.signedPercent(0.001))
    }

    @Test
    fun `duraciones`() {
        assertEquals(nb("45 s"), Format.duration(45_000))
        assertEquals(nb("47 min"), Format.duration(47 * 60_000L))
        // Una sola cifra: todos los espacios son duros y «1 h 12 min» nunca se parte entre líneas.
        assertEquals(nb("1 h 12 min"), Format.duration(72 * 60_000L))
        assertEquals(nb("3 h"), Format.duration(3 * 3_600_000L))
        assertEquals(nb("312 h"), Format.duration(312 * 3_600_000L + 5 * 60_000L))
        assertEquals(nb("47 min"), Format.durationCompact(47 * 60_000L))
        assertEquals(nb("3,2 h"), Format.durationCompact((3.2 * 3_600_000).toLong()))
        assertEquals(Format.HoursMinutes(1, 12), Format.hoursMinutes(72 * 60_000L))
    }

    @Test
    fun `fechas en español`() {
        val d = LocalDate.of(2026, 11, 3)
        assertEquals("martes 3 de noviembre", Format.dayLong(d))
        assertEquals("3 nov", Format.dayShort(d))
        assertEquals("octubre de 2026", Format.monthYear(LocalDate.of(2026, 10, 1)))
        assertEquals("X", Format.weekdayInitial(DayOfWeek.WEDNESDAY))
        assertEquals("miércoles", Format.weekday(DayOfWeek.WEDNESDAY))
        assertEquals("27 oct – 2 nov", Format.range(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 2)))
        assertEquals("3 – 9 nov", Format.range(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9)))
        assertEquals("29 dic 2025 – 4 ene 2026", Format.range(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)))
        assertEquals("23:00–24:00", Format.hourRange(23))
        assertEquals("21:30", Format.time(21, 30))
        assertEquals("MIÉRCOLES", Format.upper("miércoles"))
    }
}
