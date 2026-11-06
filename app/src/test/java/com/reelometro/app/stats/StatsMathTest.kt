package com.reelometro.app.stats

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsMathTest {

    private val today = LocalDate.of(2026, 11, 3) // martes
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private fun t(count: Int, ms: Long = count * 10_000L, shares: Int = 0) = Totals(count, ms, shares)

    // ------------------------------------------------------------------ huecos y repartos

    @Test
    fun `los días sin datos salen a cero y los anteriores al primer registro sin contar`() {
        val days = StatsMath.fillDays(
            DateSpan(d(10, 30), d(11, 2)),
            firstDay = d(10, 31),
            byDay = mapOf(d(10, 31) to t(5, shares = 1), d(11, 2) to t(2)),
        )
        assertEquals(listOf(d(10, 30), d(10, 31), d(11, 1), d(11, 2)), days.map { it.date })
        assertEquals(listOf(0, 5, 0, 2), days.map { it.count })
        assertEquals(listOf(false, true, true, true), days.map { it.tracked })
        assertEquals(1, days[1].shares)
        assertEquals(3, StatsMath.trackedDays(DateSpan(d(10, 30), d(11, 2)), d(10, 31), today))
        assertEquals(0, StatsMath.trackedDays(DateSpan(d(10, 30), d(11, 2)), null, today))
        // Hoy cuenta como día registrado; mañana no.
        assertEquals(2, StatsMath.trackedDays(DateSpan(d(11, 2), d(11, 8)), d(10, 1), today))
    }

    @Test
    fun `24 horas con ceros y hora punta`() {
        val hours = StatsMath.hours(mapOf(23 to t(41), 8 to t(41, ms = 1_000), 14 to t(3)))
        assertEquals(24, hours.size)
        assertEquals((0..23).toList(), hours.map { it.hour })
        assertEquals(0, hours[0].count)
        // Empate en vídeos: gana la de más tiempo.
        assertEquals(23, StatsMath.peakHour(hours)?.hour)
        // Empate total: la más temprana.
        val tie = StatsMath.hours(mapOf(9 to t(4), 21 to t(4)))
        assertEquals(9, StatsMath.peakHour(tie)?.hour)
        assertNull(StatsMath.peakHour(StatsMath.hours(emptyMap())))
    }

    @Test
    fun `plataformas con fracción y de más a menos`() {
        val slices = StatsMath.platformSlices(
            mapOf(Platform.YOUTUBE to t(20), Platform.TIKTOK to t(60), Platform.INSTAGRAM to t(0)),
        )
        assertEquals(listOf(Platform.TIKTOK, Platform.YOUTUBE), slices.map { it.platform })
        assertEquals(0.75, slices[0].fraction, 1e-9)
        assertEquals(1.0, slices.sumOf { it.fraction }, 1e-9)
        assertTrue(StatsMath.platformSlices(emptyMap()).isEmpty())
    }

    @Test
    fun `sin clasificar siempre al final de las categorías`() {
        val slices = StatsMath.categorySlices(
            mapOf(Category.OTROS to t(50), Category.HUMOR to t(30), Category.COMIDA to t(20)),
        )
        assertEquals(listOf(Category.HUMOR, Category.COMIDA, Category.OTROS), slices.map { it.category })
        assertEquals(0.5, slices.last().fraction, 1e-9)
    }

    // ------------------------------------------------------------------ días de la semana, peor y más tranquilo

    @Test
    fun `día débil es el día de la semana con mayor media`() {
        // Dos semanas: lunes 10 y 20 (media 15), sábado 40 y 0 (media 20), domingo sin registrar.
        val days = listOf(
            DayPoint(d(10, 19), 10, 0), DayPoint(d(10, 24), 40, 0), DayPoint(d(10, 25), 0, 0, tracked = false),
            DayPoint(d(10, 26), 20, 0), DayPoint(d(10, 31), 0, 0), DayPoint(d(11, 1), 99, 0, tracked = false),
        )
        val points = StatsMath.weekdayAverages(days)
        assertEquals(DayOfWeek.entries, points.map { it.dayOfWeek })
        val monday = points.first { it.dayOfWeek == DayOfWeek.MONDAY }
        assertEquals(15.0, monday.averageCount, 1e-9)
        assertEquals(2, monday.days)
        val saturday = points.first { it.dayOfWeek == DayOfWeek.SATURDAY }
        assertEquals(20.0, saturday.averageCount, 1e-9)
        val sunday = points.first { it.dayOfWeek == DayOfWeek.SUNDAY }
        assertEquals(0, sunday.days) // los días sin registrar no cuentan
        assertEquals(DayOfWeek.SATURDAY, StatsMath.weakestWeekday(points)?.dayOfWeek)
        assertNull(StatsMath.weakestWeekday(StatsMath.weekdayAverages(emptyList())))
    }

    @Test
    fun `peor día y día más tranquilo`() {
        val days = listOf(
            DayPoint(d(10, 30), 0, 0, tracked = false),
            DayPoint(d(10, 31), 12, 0),
            DayPoint(d(11, 1), 3, 0),
            DayPoint(d(11, 2), 80, 0),
            DayPoint(today, 1, 0), // hoy aún no ha acabado
        )
        assertEquals(d(11, 2), StatsMath.worstDay(days)?.date)
        assertEquals(d(11, 1), StatsMath.quietestDay(days, today)?.date)
        // Un día registrado sin vídeos puede ser el más tranquilo; de los empatados, el más reciente.
        val withZeros = days + DayPoint(d(10, 29), 0, 0) + DayPoint(d(10, 28), 0, 0)
        assertEquals(d(10, 29), StatsMath.quietestDay(withZeros, today)?.date)
        assertNull(StatsMath.quietestDay(listOf(DayPoint(today, 5, 0)), today))
        assertNull(StatsMath.worstDay(listOf(DayPoint(today, 0, 0))))
    }

    // ------------------------------------------------------------------ rachas

    @Test
    fun `sin objetivo no hay racha`() {
        assertNull(StatsMath.streak(null, d(10, 1), today, emptyMap()))
    }

    @Test
    fun `racha actual y mejor por debajo del objetivo`() {
        val first = d(10, 20)
        val counts = mapOf(
            d(10, 20) to 50, d(10, 21) to 40, d(10, 22) to 300, // racha de 2 rota
            d(10, 23) to 10, d(10, 24) to 100, d(10, 25) to 0, d(10, 26) to 99, // 0 y sin datos cuentan
            // 27 y 28 sin datos (cuentan como 0)
            d(10, 29) to 101, // rota otra vez: mejor = 6 (23–28)
            d(10, 30) to 20, d(10, 31) to 30, d(11, 1) to 100, d(11, 2) to 5, // actual = 4
            today to 60, // hoy va bien: no suma todavía
        )
        val streak = StatsMath.streak(100, first, today, counts)
        assertEquals(Streak(goal = 100, current = 4, best = 6, todayExceeded = false), streak)
    }

    @Test
    fun `si hoy ya te has pasado la racha actual es cero`() {
        val counts = mapOf(d(11, 1) to 10, d(11, 2) to 10, today to 151)
        val streak = StatsMath.streak(150, d(11, 1), today, counts)
        assertEquals(0, streak?.current)
        assertEquals(2, streak?.best)
        assertEquals(true, streak?.todayExceeded)
    }

    @Test
    fun `el día del primer registro no tiene racha todavía`() {
        assertEquals(Streak(100, 0, 0, false), StatsMath.streak(100, today, today, mapOf(today to 3)))
        assertEquals(Streak(100, 0, 0, false), StatsMath.streak(100, null, today, emptyMap()))
    }

    // ------------------------------------------------------------------ comparación

    @Test
    fun `comparación con la media del mismo día de la semana`() {
        assertEquals(8, StatsMath.baselineDays(today, 8).size)
        assertTrue(StatsMath.baselineDays(today).all { it.dayOfWeek == DayOfWeek.TUESDAY && it.isBefore(today) })
        assertEquals(d(10, 27), StatsMath.baselineDays(today).first())

        val c = StatsMath.comparison(today, 870, t(9, 90_000), listOf(t(10), t(6), t(8), t(0)))
        assertNotNull(c)
        c!!
        assertEquals(6.0, c.averageCount, 1e-9)
        assertEquals(4, c.sampleDays)
        assertEquals(0.5, c.countChange!!, 1e-9) // «+50 % que tu martes medio a esta hora»
        assertEquals(870, c.untilMinute)
        assertEquals(DayOfWeek.TUESDAY, c.weekday)
        assertNull(StatsMath.comparison(today, 870, t(9), emptyList()))
        assertNull(StatsMath.comparison(today, 600, t(9), listOf(t(0)))!!.countChange)
    }

    @Test
    fun `variación relativa`() {
        assertEquals(0.34, StatsMath.change(134.0, 100.0)!!, 1e-9)
        assertEquals(-0.5, StatsMath.change(50.0, 100.0)!!, 1e-9)
        assertNull(StatsMath.change(10.0, 0.0))
    }

    // ------------------------------------------------------------------ tendencia

    @Test
    fun `la semana en curso se compara con el mismo tramo de la anterior hasta la misma hora`() {
        val window = StatsMath.trendWindow(PeriodRef(PeriodKind.WEEK), today, 870, d(1, 1))
        assertEquals(TrendWindow(DateSpan(d(10, 26), d(10, 27)), 870), window)
    }

    @Test
    fun `un periodo cerrado se compara con el anterior entero`() {
        val window = StatsMath.trendWindow(PeriodRef(PeriodKind.WEEK, -1), today, 870, d(1, 1))
        assertEquals(TrendWindow(DateSpan(d(10, 19), d(10, 25)), null), window)
        val month = StatsMath.trendWindow(PeriodRef(PeriodKind.MONTH, -1), today, 870, d(1, 1))
        assertEquals(TrendWindow(DateSpan(d(9, 1), d(9, 30)), null), month)
        assertNull(StatsMath.trendWindow(PeriodRef(PeriodKind.ALL), today, 870, d(1, 1)))
    }

    @Test
    fun `si el mes anterior es más corto se toma entero`() {
        val march31 = LocalDate.of(2026, 3, 31)
        val window = StatsMath.trendWindow(PeriodRef(PeriodKind.MONTH), march31, 600, d(1, 1))
        assertEquals(TrendWindow(DateSpan(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)), null), window)
        val march3 = LocalDate.of(2026, 3, 3)
        assertEquals(
            TrendWindow(DateSpan(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3)), 600),
            StatsMath.trendWindow(PeriodRef(PeriodKind.MONTH), march3, 600, d(1, 1)),
        )
    }

    @Test
    fun `la tendencia compara medias por día registrado`() {
        val window = TrendWindow(DateSpan(d(9, 1), d(9, 30)), null)
        // Octubre: 310 en 31 días (10/día). Septiembre: 240 en 30 días (8/día) → +25 %.
        val trend = StatsMath.trend(t(310), 31, window, t(240), 30)
        assertEquals(0.25, trend!!.countChange!!, 1e-9)
        // El anterior solo tenía 10 días registrados: 240 en 10 días = 24/día → −58 %.
        assertEquals(10.0 / 24.0 - 1.0, StatsMath.trend(t(310), 31, window, t(240), 10)!!.countChange!!, 1e-9)
        assertNull(StatsMath.trend(t(310), 31, window, t(0), 0))
        assertNull(StatsMath.trend(t(310), 31, window, t(0), 30)!!.countChange)
    }

    // ------------------------------------------------------------------ montaje

    @Test
    fun `estadísticas del día`() {
        val stats = StatsMath.day(
            date = today, today = today, nowMinute = 14 * 60 + 30, goal = 150,
            byHour = mapOf(8 to t(30, 300_000), 23 to t(182, 2_520_000)),
            shares = 4,
            byPlatform = mapOf(Platform.TIKTOK to t(120), Platform.INSTAGRAM to t(92)),
            byCategory = mapOf(Category.HUMOR to t(100), Category.OTROS to t(112)),
            creators = listOf(CreatorRank(1, "michi_filosofo", Platform.TIKTOK, 12, 120_000)),
            baseline = listOf(t(150), t(166)),
        )
        assertTrue(stats.isToday)
        assertEquals(870, stats.untilMinute)
        assertEquals(Totals(212, 2_820_000, 4), stats.totals)
        assertEquals(62, stats.goal!!.exceededBy)
        assertTrue(stats.goal!!.isExceeded)
        assertEquals(23, stats.peakHour!!.hour)
        assertEquals(Category.HUMOR, stats.topCategory!!.category)
        assertEquals(212.0 / 158.0 - 1, stats.comparison!!.countChange!!, 1e-9)
        assertEquals(870, stats.comparison!!.untilMinute)

        val past = StatsMath.day(
            d(11, 2), today, 870, null, mapOf(10 to t(5)), 0, emptyMap(), emptyMap(), emptyList(), listOf(t(5)),
        )
        assertFalse(past.isToday)
        assertNull(past.untilMinute)
        assertNull(past.comparison!!.untilMinute)
        assertNull(past.goal)
    }

    @Test
    fun `estadísticas de la semana en curso`() {
        val first = d(10, 28)
        val stats = StatsMath.period(
            ref = PeriodRef(PeriodKind.WEEK),
            today = today,
            nowMinute = 870,
            firstDay = first,
            goal = 100,
            byDay = mapOf(d(11, 2) to t(120, shares = 2), today to t(40)),
            byHour = mapOf(22 to t(100), 9 to t(60)),
            byPlatform = mapOf(Platform.TIKTOK to t(160)),
            byCategory = mapOf(Category.OTROS to t(160)),
            creators = emptyList(),
            previous = t(80),
            history = mapOf(d(10, 28) to 10, d(10, 29) to 20, d(10, 30) to 30, d(10, 31) to 40, d(11, 1) to 50, d(11, 2) to 120, today to 40),
        )
        assertEquals(DateSpan(d(11, 2), d(11, 8)), stats.span)
        assertEquals(listOf(d(11, 2), today), stats.days.map { it.date }) // sin días futuros
        assertEquals(Totals(160, 1_600_000, 2), stats.totals)
        assertEquals(2, stats.trackedDays)
        assertEquals(2, stats.activeDays)
        assertEquals(80.0, stats.avgPerDay, 1e-9)
        assertEquals("Esta semana", stats.label)
        assertEquals("2 – 8 nov 2026", stats.rangeLabel)
        assertEquals(22, stats.peakHour!!.hour)
        assertEquals(d(11, 2), stats.worstDay!!.date)
        assertEquals(d(11, 2), stats.quietestDay!!.date) // hoy no cuenta
        assertEquals(DayOfWeek.MONDAY, stats.weakestWeekday!!.dayOfWeek)
        // El tramo anterior (lun 26–mar 27 oct) es anterior al primer registro (mié 28): sin tendencia.
        assertNull(stats.trend)
        assertEquals(Streak(100, current = 0, best = 5, todayExceeded = false), stats.streak)
        assertTrue(stats.inProgress)
        assertTrue(stats.hasPrevious)
        assertFalse(stats.hasNext)
    }

    @Test
    fun `tendencia de un periodo con datos en el anterior`() {
        val stats = StatsMath.period(
            ref = PeriodRef(PeriodKind.WEEK),
            today = today,
            nowMinute = 870,
            firstDay = d(9, 1),
            goal = null,
            byDay = mapOf(d(11, 2) to t(70), today to t(50)),
            byHour = emptyMap(),
            byPlatform = emptyMap(),
            byCategory = emptyMap(),
            creators = emptyList(),
            previous = t(100),
            history = emptyMap(),
        )
        val trend = stats.trend!!
        assertEquals(DateSpan(d(10, 26), d(10, 27)), trend.previous)
        assertEquals(870, trend.untilMinute)
        assertEquals(0.2, trend.countChange!!, 1e-9) // 120 frente a 100 en el mismo tramo
        assertNull(stats.streak)
    }

    @Test
    fun `totales desde siempre`() {
        val all = StatsMath.allTime(d(10, 25), today, t(900))
        assertEquals(10, all.trackedDays)
        assertEquals(90.0, all.avgPerDay, 1e-9)
        val empty = StatsMath.allTime(null, today, Totals.ZERO)
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.trackedDays)
    }
}
