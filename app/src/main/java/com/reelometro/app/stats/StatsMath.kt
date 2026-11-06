package com.reelometro.app.stats

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Tramo del periodo anterior con el que se calcula una [Trend]. */
data class TrendWindow(val span: DateSpan, val untilMinute: Int?)

/**
 * Cálculos derivados, puros y deterministas (sin Android ni base de datos). La capa de datos hace
 * las agregaciones en SQL y aquí se rellenan huecos, se calculan medias, rachas, tendencias y
 * comparaciones, y se montan [DayStats] y [PeriodStats].
 */
object StatsMath {

    /** Último minuto del día (23:59). */
    const val LAST_MINUTE = 1439

    /** Días de referencia con los que se compara un día («tu martes medio»). */
    const val BASELINE_SAMPLES = 8

    /** Semanas hacia atrás en las que se buscan esos días de referencia. */
    const val BASELINE_LOOKBACK_WEEKS = 16

    /** Tamaño de los rankings de creadores. */
    const val TOP_CREATORS = 10

    /** Personas o grupos en el ranking de «a quién envías». */
    const val TOP_RECIPIENTS = 10

    /** Variación relativa: `current / baseline − 1` (0.34 = +34 %). `null` si la base es 0. */
    fun change(current: Double, baseline: Double): Double? =
        if (baseline <= 0.0 || baseline.isNaN() || current.isNaN()) null else current / baseline - 1.0

    // ------------------------------------------------------------------ repartos

    /** 24 horas (0–23) con ceros donde no hay datos. */
    fun hours(byHour: Map<Int, Totals>): List<HourPoint> = List(24) { h ->
        val t = byHour[h]
        HourPoint(h, t?.count ?: 0, t?.totalMs ?: 0)
    }

    /** Hora con más vídeos (empates: más tiempo, luego la más temprana); `null` sin vídeos. */
    fun peakHour(hours: List<HourPoint>): HourPoint? = hours
        .filter { it.count > 0 }
        .maxWithOrNull(compareBy<HourPoint> { it.count }.thenBy { it.totalMs }.thenByDescending { it.hour })

    /** Plataformas con vídeos, de más a menos, con su fracción del total. */
    fun platformSlices(byPlatform: Map<Platform, Totals>): List<PlatformSlice> {
        val total = byPlatform.values.sumOf { it.count }
        return byPlatform
            .filter { it.value.count > 0 }
            .map { (platform, t) -> PlatformSlice(platform, t.count, t.totalMs, fraction(t.count, total)) }
            .sortedWith(
                compareByDescending<PlatformSlice> { it.count }
                    .thenByDescending { it.totalMs }
                    .thenBy { it.platform.ordinal },
            )
    }

    /** Categorías con vídeos, de más a menos, con «Sin clasificar» siempre al final. */
    fun categorySlices(byCategory: Map<Category, Totals>): List<CategorySlice> {
        val total = byCategory.values.sumOf { it.count }
        return byCategory
            .filter { it.value.count > 0 }
            .map { (category, t) -> CategorySlice(category, t.count, t.totalMs, fraction(t.count, total)) }
            .sortedWith(
                compareBy<CategorySlice> { it.category == Category.OTROS }
                    .thenByDescending { it.count }
                    .thenByDescending { it.totalMs }
                    .thenBy { it.category.ordinal },
            )
    }

    // ------------------------------------------------------------------ días

    /** Un punto por cada día de [range] (huecos a cero). Los anteriores a [firstDay] van con `tracked = false`. */
    fun fillDays(range: DateSpan, firstDay: LocalDate?, byDay: Map<LocalDate, Totals>): List<DayPoint> =
        range.dates().map { date ->
            val t = byDay[date]
            DayPoint(
                date = date,
                count = t?.count ?: 0,
                totalMs = t?.totalMs ?: 0,
                shares = t?.shares ?: 0,
                tracked = firstDay != null && !date.isBefore(firstDay),
            )
        }

    /** Días de [range] desde el primer registro y hasta hoy (hoy cuenta). 0 si no hay registros. */
    fun trackedDays(range: DateSpan, firstDay: LocalDate?, today: LocalDate): Int {
        if (firstDay == null) return 0
        return range.from(firstDay)?.until(today)?.days ?: 0
    }

    /** Medias por día de la semana (lunes a domingo) sobre los días registrados. */
    fun weekdayAverages(days: List<DayPoint>): List<WeekdayPoint> {
        val byWeekday = days.filter { it.tracked }.groupBy { it.date.dayOfWeek }
        return DayOfWeek.entries.map { dow ->
            val list = byWeekday[dow].orEmpty()
            val n = list.size
            val count = list.sumOf { it.count }
            val ms = list.sumOf { it.totalMs }
            WeekdayPoint(
                dayOfWeek = dow,
                averageCount = if (n == 0) 0.0 else count.toDouble() / n,
                averageMs = if (n == 0) 0.0 else ms.toDouble() / n,
                days = n,
                totalCount = count,
                totalMs = ms,
            )
        }
    }

    /** «Día débil»: el día de la semana con **mayor** media (empates: más tiempo, luego el primero de la semana). */
    fun weakestWeekday(points: List<WeekdayPoint>): WeekdayPoint? = points
        .filter { it.days > 0 && it.averageCount > 0.0 }
        .maxWithOrNull(
            compareBy<WeekdayPoint> { it.averageCount }
                .thenBy { it.averageMs }
                .thenByDescending { it.dayOfWeek.value },
        )

    /** Día con más vídeos (empates: más tiempo, luego el más reciente); `null` sin vídeos. */
    fun worstDay(days: List<DayPoint>): DayPoint? = days
        .filter { it.count > 0 }
        .maxWithOrNull(compareBy<DayPoint> { it.count }.thenBy { it.totalMs }.thenBy { it.date })

    /**
     * Día registrado con menos vídeos (puede ser 0), sin contar hoy porque aún no ha acabado
     * (empates: menos tiempo, luego el más reciente).
     */
    fun quietestDay(days: List<DayPoint>, today: LocalDate): DayPoint? = days
        .filter { it.tracked && it.date.isBefore(today) }
        .minWithOrNull(compareBy<DayPoint> { it.count }.thenBy { it.totalMs }.thenByDescending { it.date })

    // ------------------------------------------------------------------ racha

    /**
     * Racha de días seguidos con `vídeos ≤ goal`, desde el primer registro. Hoy no cuenta hasta que
     * acaba salvo que ya se haya pasado (entonces `current = 0`). Días sin datos tras el primer
     * registro cuentan como 0 vídeos (dentro del objetivo).
     *
     * @return `null` si no hay objetivo.
     */
    fun streak(goal: Int?, firstDay: LocalDate?, today: LocalDate, countsByDay: Map<LocalDate, Int>): Streak? {
        if (goal == null) return null
        if (firstDay == null || firstDay.isAfter(today)) return Streak(goal, current = 0, best = 0, todayExceeded = false)
        var run = 0
        var best = 0
        var day: LocalDate = firstDay
        while (day.isBefore(today)) {
            if ((countsByDay[day] ?: 0) <= goal) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            day = day.plusDays(1)
        }
        val exceeded = (countsByDay[today] ?: 0) > goal
        return Streak(goal, current = if (exceeded) 0 else run, best = best, todayExceeded = exceeded)
    }

    // ------------------------------------------------------------------ comparación con el mismo día de la semana

    /** Fechas candidatas de referencia: el mismo día de la semana en las [lookbackWeeks] semanas anteriores. */
    fun baselineDays(date: LocalDate, lookbackWeeks: Int = BASELINE_LOOKBACK_WEEKS): List<LocalDate> =
        (1..lookbackWeeks).map { date.minusWeeks(it.toLong()) }

    /**
     * @param baseline totales de cada día de referencia (solo días con datos), ya cortados en
     *   [untilMinute] si se compara «a esta hora».
     */
    fun comparison(date: LocalDate, untilMinute: Int?, current: Totals, baseline: List<Totals>): Comparison? {
        if (baseline.isEmpty()) return null
        val n = baseline.size
        return Comparison(
            weekday = date.dayOfWeek,
            untilMinute = untilMinute,
            count = current.count,
            totalMs = current.totalMs,
            averageCount = baseline.sumOf { it.count }.toDouble() / n,
            averageMs = baseline.sumOf { it.totalMs }.toDouble() / n,
            sampleDays = n,
        )
    }

    // ------------------------------------------------------------------ tendencia

    /**
     * Tramo del periodo anterior con el que comparar [ref] (ver [Trend]); `null` para «siempre».
     * Si el periodo está en curso, el mismo número de días desde el inicio y cortado a [nowMinute]
     * (si el anterior es más corto, p. ej. febrero frente a marzo, se toma entero).
     */
    fun trendWindow(ref: PeriodRef, today: LocalDate, nowMinute: Int, firstDay: LocalDate?): TrendWindow? {
        val previousRef = ref.previous() ?: return null
        val current = ref.span(today, firstDay)
        val previous = previousRef.span(today, firstDay)
        if (current.end.isBefore(today)) return TrendWindow(previous, untilMinute = null)
        val elapsed = ChronoUnit.DAYS.between(current.start, today)
        val end = previous.start.plusDays(elapsed)
        return if (end.isAfter(previous.end)) {
            TrendWindow(previous, untilMinute = null)
        } else {
            TrendWindow(DateSpan(previous.start, end), untilMinute = nowMinute.coerceIn(0, LAST_MINUTE))
        }
    }

    /** Compara medias por día registrado; `null` si alguno de los dos lados no tiene días registrados. */
    fun trend(
        current: Totals,
        currentTrackedDays: Int,
        window: TrendWindow,
        previous: Totals,
        previousTrackedDays: Int,
    ): Trend? {
        if (currentTrackedDays <= 0 || previousTrackedDays <= 0) return null
        return Trend(
            previous = window.span,
            untilMinute = window.untilMinute,
            previousTotals = previous,
            previousTrackedDays = previousTrackedDays,
            countChange = change(
                current.count.toDouble() / currentTrackedDays,
                previous.count.toDouble() / previousTrackedDays,
            ),
            timeChange = change(
                current.totalMs.toDouble() / currentTrackedDays,
                previous.totalMs.toDouble() / previousTrackedDays,
            ),
        )
    }

    // ------------------------------------------------------------------ montaje

    /**
     * Monta las estadísticas de un día a partir de las agregaciones SQL.
     *
     * @param nowMinute minuto local actual; solo se usa si [date] es hoy.
     * @param baseline días de referencia (ver [comparison]).
     */
    fun day(
        date: LocalDate,
        today: LocalDate,
        nowMinute: Int,
        goal: Int?,
        byHour: Map<Int, Totals>,
        shares: Int,
        byPlatform: Map<Platform, Totals>,
        byCategory: Map<Category, Totals>,
        creators: List<CreatorRank>,
        baseline: List<Totals>,
    ): DayStats {
        val isToday = date == today
        val untilMinute = if (isToday) nowMinute.coerceIn(0, LAST_MINUTE) else null
        val hours = hours(byHour)
        val totals = Totals(hours.sumOf { it.count }, hours.sumOf { it.totalMs }, shares)
        return DayStats(
            date = date,
            isToday = isToday,
            untilMinute = untilMinute,
            totals = totals,
            hours = hours,
            platforms = platformSlices(byPlatform),
            categories = categorySlices(byCategory),
            creators = creators,
            peakHour = peakHour(hours),
            comparison = if (date.isAfter(today)) null else comparison(date, untilMinute, totals, baseline),
            goal = goal?.let { GoalProgress(it, totals.count) },
        )
    }

    /**
     * Monta las estadísticas de un periodo a partir de las agregaciones SQL.
     *
     * @param byDay totales por día (vídeos y compartidos) dentro de la parte transcurrida.
     * @param previous totales del tramo [trendWindow] (o `null` si no hay tramo).
     * @param history vídeos por día desde el primer registro hasta hoy (para la racha).
     */
    fun period(
        ref: PeriodRef,
        today: LocalDate,
        nowMinute: Int,
        firstDay: LocalDate?,
        goal: Int?,
        byDay: Map<LocalDate, Totals>,
        byHour: Map<Int, Totals>,
        byPlatform: Map<Platform, Totals>,
        byCategory: Map<Category, Totals>,
        creators: List<CreatorRank>,
        previous: Totals?,
        history: Map<LocalDate, Int>,
    ): PeriodStats {
        val span = ref.span(today, firstDay)
        val elapsed = ref.elapsed(today, firstDay)
        val days = fillDays(elapsed, firstDay, byDay)
        val totals = days.fold(Totals.ZERO) { acc, d -> acc + Totals(d.count, d.totalMs, d.shares) }
        val trackedDays = trackedDays(elapsed, firstDay, today)
        val hours = hours(byHour)
        val weekdays = weekdayAverages(days)
        val window = trendWindow(ref, today, nowMinute, firstDay)
        val trend = if (window != null && previous != null) {
            trend(totals, trackedDays, window, previous, trackedDays(window.span, firstDay, today))
        } else {
            null
        }
        return PeriodStats(
            ref = ref,
            span = span,
            label = Format.periodLabel(ref, span, today),
            rangeLabel = Format.rangeWithYear(span.start, span.end),
            today = today,
            firstDay = firstDay,
            totals = totals,
            days = days,
            hours = hours,
            weekdays = weekdays,
            platforms = platformSlices(byPlatform),
            categories = categorySlices(byCategory),
            creators = creators,
            trackedDays = trackedDays,
            activeDays = days.count { it.count > 0 },
            avgPerDay = if (trackedDays == 0) 0.0 else totals.count.toDouble() / trackedDays,
            avgMsPerDay = if (trackedDays == 0) 0.0 else totals.totalMs.toDouble() / trackedDays,
            peakHour = peakHour(hours),
            weakestWeekday = weakestWeekday(weekdays),
            worstDay = worstDay(days),
            quietestDay = quietestDay(days, today),
            streak = streak(goal, firstDay, today, history),
            trend = trend,
            hasPrevious = ref.hasPrevious(today, firstDay),
            hasNext = ref.hasNext,
        )
    }

    /** Totales desde el primer registro. */
    fun allTime(firstDay: LocalDate?, today: LocalDate, totals: Totals): AllTime {
        val tracked = if (firstDay == null) 0 else DateSpan(minOf(firstDay, today), today).days
        return AllTime(
            firstDay = firstDay,
            today = today,
            totals = totals,
            trackedDays = tracked,
            avgPerDay = if (tracked == 0) 0.0 else totals.count.toDouble() / tracked,
            avgMsPerDay = if (tracked == 0) 0.0 else totals.totalMs.toDouble() / tracked,
        )
    }

    private fun fraction(part: Int, total: Int): Double = if (total <= 0) 0.0 else part.toDouble() / total
}
