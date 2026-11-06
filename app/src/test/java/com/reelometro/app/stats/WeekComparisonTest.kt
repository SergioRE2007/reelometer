package com.reelometro.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekComparisonTest {

    /** Martes 3 de noviembre de 2026: esta semana va del lunes 2 al domingo 8. */
    private val today = LocalDate.of(2026, 11, 3)

    /** Un periodo con [perDay] vídeos cada día registrado (desde [firstDay]) hasta hoy. */
    private fun period(
        ref: PeriodRef,
        firstDay: LocalDate? = today.minusDays(60),
        perDay: (LocalDate) -> Int = { it.dayOfMonth },
        trend: Trend? = null,
    ): PeriodStats {
        val days = ref.elapsed(today, firstDay).dates().map { d ->
            val tracked = firstDay != null && !d.isBefore(firstDay)
            DayPoint(d, if (tracked) perDay(d) else 0, 0L, 0, tracked)
        }
        val trackedDays = days.count { it.tracked }
        return PeriodStats(
            ref = ref,
            span = ref.span(today, firstDay),
            label = "",
            rangeLabel = "",
            today = today,
            firstDay = firstDay,
            totals = Totals(days.sumOf { it.count }, 0L),
            days = days,
            hours = emptyList(),
            weekdays = emptyList(),
            platforms = emptyList(),
            categories = emptyList(),
            creators = emptyList(),
            trackedDays = trackedDays,
            activeDays = days.count { it.count > 0 },
            avgPerDay = 0.0,
            avgMsPerDay = 0.0,
            peakHour = null,
            weakestWeekday = null,
            worstDay = null,
            quietestDay = null,
            streak = null,
            trend = trend,
            hasPrevious = false,
            hasNext = false,
        )
    }

    private fun week(offset: Int, firstDay: LocalDate? = today.minusDays(60), trend: Trend? = null) =
        period(PeriodRef(PeriodKind.WEEK, offset), firstDay, trend = trend)

    @Test
    fun `cada día va frente al mismo día de la semana anterior`() {
        val c = WeekComparison.of(week(0), week(-1))!!
        assertEquals(7, c.days.size)
        assertEquals(WeekDayPair(LocalDate.of(2026, 11, 2), 2, LocalDate.of(2026, 10, 26), 26), c.days[0])
        assertEquals(3, c.days[1].count)
        assertNull("el miércoles todavía no ha llegado", c.days[2].count)
        assertEquals(28, c.days[2].previous)
        assertEquals(1, c.days[6].previous) // domingo 1 de noviembre
        assertEquals(1, c.todayIndex)
        assertTrue(c.inProgress)
        assertEquals(5, c.total)
        assertEquals(26 + 27 + 28 + 29 + 30 + 31 + 1, c.previousWeekTotal)
    }

    @Test
    fun `una semana cerrada no tiene hoy y tiene los siete días`() {
        val c = WeekComparison.of(week(-1), week(-2))!!
        assertFalse(c.inProgress)
        assertNull(c.todayIndex)
        assertTrue(c.days.all { it.count != null && it.previous != null })
    }

    @Test
    fun `sin ningún día registrado en la anterior no hay comparativa`() {
        val first = LocalDate.of(2026, 11, 2)
        assertNull(WeekComparison.of(week(0, first), week(-1, first)))
    }

    @Test
    fun `la primera semana a medias deja huecos en el fantasma`() {
        val first = LocalDate.of(2026, 10, 29) // jueves
        val c = WeekComparison.of(week(0, first), week(-1, first))!!
        assertNull(c.days[0].previous)
        assertNull(c.days[2].previous)
        assertEquals(29, c.days[3].previous)
        assertEquals(29 + 30 + 31 + 1, c.previousWeekTotal)
    }

    @Test
    fun `solo compara una semana con la inmediatamente anterior`() {
        assertNull(WeekComparison.of(week(0), null))
        assertNull(WeekComparison.of(week(0), week(-2)))
        assertNull(WeekComparison.of(period(PeriodRef.THIS_MONTH), week(-1)))
        assertNull(WeekComparison.of(week(0), period(PeriodRef(PeriodKind.MONTH, -1))))
    }

    @Test
    fun `los totales y la diferencia salen de la tendencia del periodo`() {
        val span = DateSpan(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27))
        val same = Trend(span, 21 * 60, Totals(60, 0L), previousTrackedDays = 2, countChange = -0.1, timeChange = null)
        val c = WeekComparison.of(week(0, trend = same), week(-1))!!
        assertEquals(60, c.previousTotal)
        assertEquals(-0.1, c.change!!, 1e-9)
        assertFalse("mismos días contados: se comparan totales", c.perDay)

        val uneven = same.copy(previousTrackedDays = 1)
        assertTrue("distintos días contados: se comparan medias por día", WeekComparison.of(week(0, trend = uneven), week(-1))!!.perDay)

        val none = WeekComparison.of(week(0), week(-1))
        assertNotNull(none)
        assertNull(none!!.previousTotal)
        assertNull(none.change)
    }
}
