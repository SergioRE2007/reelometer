package com.reelometro.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodsTest {

    private val today = LocalDate.of(2026, 11, 3) // martes
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `la semana es ISO de lunes a domingo`() {
        assertEquals(DateSpan(d(2026, 11, 2), d(2026, 11, 8)), PeriodRef(PeriodKind.WEEK).span(today))
        assertEquals(DateSpan(d(2026, 10, 26), d(2026, 11, 1)), PeriodRef(PeriodKind.WEEK, -1).span(today))
        // Un domingo sigue siendo de la semana que empezó el lunes anterior.
        assertEquals(DateSpan(d(2026, 11, 2), d(2026, 11, 8)), PeriodRef(PeriodKind.WEEK).span(d(2026, 11, 8)))
        // Semana que cruza de año.
        assertEquals(DateSpan(d(2025, 12, 29), d(2026, 1, 4)), PeriodRef(PeriodKind.WEEK).span(d(2026, 1, 1)))
    }

    @Test
    fun `mes y año naturales`() {
        assertEquals(DateSpan(d(2026, 11, 1), d(2026, 11, 30)), PeriodRef(PeriodKind.MONTH).span(today))
        assertEquals(DateSpan(d(2026, 10, 1), d(2026, 10, 31)), PeriodRef(PeriodKind.MONTH, -1).span(today))
        assertEquals(DateSpan(d(2025, 12, 1), d(2025, 12, 31)), PeriodRef(PeriodKind.MONTH, -11).span(today))
        assertEquals(DateSpan(d(2028, 2, 1), d(2028, 2, 29)), PeriodRef(PeriodKind.MONTH).span(d(2028, 2, 10)))
        assertEquals(DateSpan(d(2026, 1, 1), d(2026, 12, 31)), PeriodRef(PeriodKind.YEAR).span(today))
        assertEquals(DateSpan(d(2025, 1, 1), d(2025, 12, 31)), PeriodRef(PeriodKind.YEAR, -1).span(today))
    }

    @Test
    fun `siempre va del primer registro a hoy`() {
        val all = PeriodRef(PeriodKind.ALL)
        assertEquals(DateSpan(d(2025, 3, 3), today), all.span(today, d(2025, 3, 3)))
        assertEquals(DateSpan(today, today), all.span(today, null))
        // Primer registro «en el futuro» (reloj cambiado): no se rompe.
        assertEquals(DateSpan(today, today), all.span(today, d(2026, 12, 1)))
    }

    @Test
    fun `la parte transcurrida no tiene días futuros`() {
        assertEquals(DateSpan(d(2026, 11, 2), today), PeriodRef(PeriodKind.WEEK).elapsed(today))
        assertEquals(DateSpan(d(2026, 11, 1), today), PeriodRef(PeriodKind.MONTH).elapsed(today))
        assertEquals(DateSpan(d(2026, 10, 26), d(2026, 11, 1)), PeriodRef(PeriodKind.WEEK, -1).elapsed(today))
    }

    @Test
    fun `navegación anterior y siguiente`() {
        val week = PeriodRef(PeriodKind.WEEK)
        assertNull(week.next())
        assertFalse(week.hasNext)
        assertEquals(PeriodRef(PeriodKind.WEEK, -1), week.previous())
        assertEquals(week, PeriodRef(PeriodKind.WEEK, -1).next())
        assertTrue(PeriodRef(PeriodKind.MONTH, -3).hasNext)
        assertNull(PeriodRef(PeriodKind.ALL).previous())
        assertThrows(IllegalArgumentException::class.java) { PeriodRef(PeriodKind.WEEK, 1) }
        assertThrows(IllegalArgumentException::class.java) { PeriodRef(PeriodKind.ALL, -1) }
    }

    @Test
    fun `solo se puede ir atrás si el periodo anterior toca el primer registro`() {
        val first = d(2026, 10, 28) // miércoles
        assertTrue(PeriodRef(PeriodKind.WEEK).hasPrevious(today, first))
        assertFalse(PeriodRef(PeriodKind.WEEK, -1).hasPrevious(today, first))
        assertTrue(PeriodRef(PeriodKind.MONTH).hasPrevious(today, first))
        assertFalse(PeriodRef(PeriodKind.MONTH, -1).hasPrevious(today, first))
        assertFalse(PeriodRef(PeriodKind.YEAR).hasPrevious(today, first))
        assertFalse(PeriodRef(PeriodKind.WEEK).hasPrevious(today, null))
        assertFalse(PeriodRef(PeriodKind.ALL).hasPrevious(today, first))
    }

    @Test
    fun `etiquetas de periodo en español`() {
        assertEquals("Esta semana", PeriodRef(PeriodKind.WEEK).label(today))
        assertEquals("26 oct – 1 nov", PeriodRef(PeriodKind.WEEK, -1).label(today))
        assertEquals("19 – 25 oct", PeriodRef(PeriodKind.WEEK, -2).label(today))
        assertEquals("noviembre de 2026", PeriodRef(PeriodKind.MONTH).label(today))
        assertEquals("octubre de 2026", PeriodRef(PeriodKind.MONTH, -1).label(today))
        assertEquals("2026", PeriodRef(PeriodKind.YEAR).label(today))
        assertEquals("2025", PeriodRef(PeriodKind.YEAR, -1).label(today))
        assertEquals("Desde el 3 mar 2025", PeriodRef(PeriodKind.ALL).label(today, d(2025, 3, 3)))
        // Semanas de otro año llevan el año; las que cruzan de año, los dos.
        val newYear = d(2026, 1, 8)
        assertEquals("29 dic 2025 – 4 ene 2026", PeriodRef(PeriodKind.WEEK, -1).label(newYear))
        assertEquals("22 – 28 dic 2025", PeriodRef(PeriodKind.WEEK, -2).label(newYear))
    }

    @Test
    fun `rango siempre con año`() {
        assertEquals("26 oct – 1 nov 2026", Format.rangeWithYear(d(2026, 10, 26), d(2026, 11, 1)))
        assertEquals("1 – 30 nov 2026", Format.rangeWithYear(d(2026, 11, 1), d(2026, 11, 30)))
        assertEquals("29 dic 2025 – 4 ene 2026", Format.rangeWithYear(d(2025, 12, 29), d(2026, 1, 4)))
        assertEquals("3 nov 2026", Format.rangeWithYear(today, today))
    }

    @Test
    fun `ruta de periodo ida y vuelta`() {
        assertEquals("MONTH:-1", PeriodRef(PeriodKind.MONTH, -1).toRouteArg())
        assertEquals(PeriodRef(PeriodKind.MONTH, -1), PeriodRef.parse("MONTH:-1"))
        assertEquals(PeriodRef(PeriodKind.ALL), PeriodRef.parse("all:0"))
        assertNull(PeriodRef.parse("WEEK:1"))
        assertNull(PeriodRef.parse("ALL:-1"))
        assertNull(PeriodRef.parse("DAY:0"))
        assertNull(PeriodRef.parse("WEEK"))
        assertNull(PeriodRef.parse("WEEK:x"))
    }

    @Test
    fun `intervalos de fechas`() {
        val span = DateSpan(d(2026, 10, 30), d(2026, 11, 2))
        assertEquals(4, span.days)
        assertEquals(listOf(d(2026, 10, 30), d(2026, 10, 31), d(2026, 11, 1), d(2026, 11, 2)), span.dates())
        assertTrue(d(2026, 11, 1) in span)
        assertFalse(d(2026, 11, 3) in span)
        assertEquals(DateSpan(d(2026, 11, 1), d(2026, 11, 2)), span.from(d(2026, 11, 1)))
        assertNull(span.from(d(2026, 11, 3)))
        assertEquals(DateSpan(d(2026, 10, 30), d(2026, 10, 31)), span.until(d(2026, 10, 31)))
        assertNull(span.until(d(2026, 10, 29)))
        assertEquals(DateSpan.of(d(2026, 11, 2)), span.intersect(DateSpan(d(2026, 11, 2), d(2026, 11, 9))))
        assertNull(span.intersect(DateSpan(d(2026, 11, 3), d(2026, 11, 9))))
        assertThrows(IllegalArgumentException::class.java) { DateSpan(d(2026, 11, 2), d(2026, 11, 1)) }
    }
}
