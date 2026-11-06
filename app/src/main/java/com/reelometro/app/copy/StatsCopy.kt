package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Format
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Textos de **Estadísticas** (semana / mes / año / siempre).
 *
 * Las palabras están en `res/values/copy_stats.xml`; aquí se decide qué frase toca y con qué huecos.
 * Las frases que concuerdan con el periodo («esta semana» / «este mes») tienen una versión por periodo.
 */
object StatsCopy {

    val TITLE: String get() = str(R.string.stats_title)
    val SECTION_DAILY: String get() = str(R.string.stats_section_daily)
    val SECTION_CALENDAR: String get() = str(R.string.stats_section_calendar)
    val SECTION_PATTERNS: String get() = str(R.string.stats_section_patterns)
    val SECTION_PLATFORMS: String get() = str(R.string.stats_section_platforms)
    val SECTION_CATEGORIES: String get() = str(R.string.stats_section_categories)
    val SECTION_CREATORS: String get() = str(R.string.stats_section_creators)

    /** Ranking de personas y grupos a los que envías vídeos en Instagram (ajuste «A quién envías»). */
    val SECTION_RECIPIENTS: String get() = str(R.string.stats_section_recipients)

    /** Pestaña: «SEMANA», «MES», «AÑO», «SIEMPRE». */
    fun tab(period: CopyPeriod): String = when (period) {
        CopyPeriod.WEEK -> Labels.WEEK
        CopyPeriod.MONTH -> Labels.MONTH
        CopyPeriod.YEAR -> Labels.YEAR
        CopyPeriod.ALL -> Labels.ALL
    }

    // ------------------------------------------------------------------ navegador de periodo

    /** «27 OCT – 2 NOV 2026»; si cruza de año, «29 DIC 2025 – 4 ENE 2026». */
    fun weekLabel(start: LocalDate, end: LocalDate): String = Format.upper(Format.rangeWithYear(start, end))

    /** «OCTUBRE 2026». */
    fun monthLabel(month: YearMonth): String = Format.upper(Format.monthYearLabel(month.monthValue, month.year))

    /** «2026» (los años nunca llevan punto de millar). */
    fun yearLabel(year: Int): String = year.toString()

    /** «DESDE EL 3 MAR 2026»; «SIEMPRE» si aún no hay datos. */
    fun allLabel(firstDay: LocalDate?): String =
        if (firstDay == null) Labels.ALL else Format.upper(str(R.string.stats_all_label, Format.dayShortWithYear(firstDay)))

    // ------------------------------------------------------------------ cifra principal

    /**
     * Línea bajo la cifra grande: «VÍDEOS · 9 H 12 MIN · 172/DÍA · 27 S POR VÍDEO».
     * Con 0 vídeos, solo «VÍDEOS».
     */
    fun summaryLine(count: Long, totalMs: Long, averagePerDay: Double, averageMsPerVideo: Long): String {
        if (count <= 0) return Labels.VIDEOS
        return Format.upper(
            plural(
                R.plurals.stats_summary_line, count,
                Format.upper(Format.duration(totalMs)), Format.decimal(averagePerDay), perVideoValue(averageMsPerVideo),
            ),
        )
    }

    /**
     * Tendencia frente al periodo anterior: «+12 % QUE LA SEMANA ANTERIOR», «COMO EL MES ANTERIOR»,
     * «EL DOBLE QUE EL AÑO ANTERIOR», «SIN DATOS DE LA SEMANA ANTERIOR». `null` en [CopyPeriod.ALL].
     *
     * [current] y [previous] pueden ser totales o medias diarias (lo que compare la capa de datos);
     * [previous] = `null` si no hay periodo anterior con datos.
     */
    fun trend(current: Double, previous: Double?, period: CopyPeriod): DeltaLine? {
        val prev = period.previous ?: return null
        return when (val d = Deltas.of(current, previous)) {
            Delta.NoBase -> DeltaLine(
                str(byPeriod(period, R.string.stats_trend_no_base_label_week, R.string.stats_trend_no_base_label_month, R.string.stats_trend_no_base_label_year)),
                str(byPeriod(period, R.string.stats_trend_no_base_week, R.string.stats_trend_no_base_month, R.string.stats_trend_no_base_year)),
                Direction.NONE,
            )

            Delta.BothZero -> Deltas.ratioLine(1.0, prev)
            Delta.FromZero -> DeltaLine(
                str(byPeriod(period, R.string.stats_trend_from_zero_label_week, R.string.stats_trend_from_zero_label_month, R.string.stats_trend_from_zero_label_year)),
                str(byPeriod(period, R.string.stats_trend_from_zero_week, R.string.stats_trend_from_zero_month, R.string.stats_trend_from_zero_year)),
                Direction.UP,
            )

            is Delta.Ratio -> Deltas.ratioLine(d.ratio, prev)
        }
    }

    /** La tendencia en frase, con un remate. `null` en [CopyPeriod.ALL]. */
    fun trendQuote(current: Double, previous: Double?, period: CopyPeriod, seed: Long): String? {
        if (period.previous == null) return null
        val line = trend(current, previous, period) ?: return null
        val desc = line.description
        return when (Deltas.of(current, previous)) {
            Delta.NoBase -> pick(
                seed, "stats.trend.none",
                byPeriod(period, R.array.stats_trend_none_week, R.array.stats_trend_none_month, R.array.stats_trend_none_year),
            )

            Delta.FromZero -> pick(
                seed, "stats.trend.fromZero",
                byPeriod(period, R.array.stats_trend_from_zero_week, R.array.stats_trend_from_zero_month, R.array.stats_trend_from_zero_year),
            )

            Delta.BothZero -> pick(
                seed, "stats.trend.bothZero",
                byPeriod(period, R.array.stats_trend_both_zero_week, R.array.stats_trend_both_zero_month, R.array.stats_trend_both_zero_year),
            )

            is Delta.Ratio -> when (line.direction) {
                Direction.UP -> pick(seed, "stats.trend.up", R.array.stats_trend_up, desc)
                Direction.DOWN -> pick(seed, "stats.trend.down", R.array.stats_trend_down, desc)
                else -> pick(seed, "stats.trend.same", R.array.stats_trend_same, desc)
            }
        }
    }

    // ------------------------------------------------------------------ patrones

    /** Valor de racha: «4 DÍAS», «1 DÍA», «0 DÍAS». */
    fun streakValue(days: Int): String = Format.upper(Words.days(days.coerceAtLeast(0)))

    /** Línea de racha sin objetivo (lleva a Ajustes): «Ponte un objetivo y empezamos a contar.». */
    fun noGoalStreak(seed: Long): String = pick(seed, "stats.streak.noGoal", R.array.stats_streak_no_goal)

    /**
     * Frase de racha (días seguidos sin pasar del objetivo).
     * @param current racha actual en días; @param best mejor racha (≥ actual); @param goal objetivo diario.
     */
    fun streakQuote(current: Int, best: Int, goal: Int, seed: Long): String {
        val g = Format.integer(goal)
        val cur = current.coerceAtLeast(0)
        val top = best.coerceAtLeast(cur)
        return when {
            cur == 0 && top == 0 -> pick(seed, "stats.streak.never", R.array.stats_streak_never, g)

            cur == 0 -> pick(seed, "stats.streak.broken", R.array.stats_streak_broken, Words.days(top))

            cur >= top -> pick(
                seed, "stats.streak.record",
                if (cur == 1) R.array.stats_streak_record_one else R.array.stats_streak_record,
                Words.days(cur), g,
            )

            else -> {
                val left = top - cur
                pick(
                    seed, "stats.streak.ongoing",
                    if (left == 1) R.array.stats_streak_ongoing_one_left else R.array.stats_streak_ongoing,
                    Words.days(cur), Words.days(top), Words.days(left),
                )
            }
        }
    }

    /** Valor de día débil (el de mayor media): «DOMINGO · 240 DE MEDIA». */
    fun weakDayValue(weekday: DayOfWeek, average: Double): String =
        Format.upper(str(R.string.stats_weak_day_value, Format.weekday(weekday), Format.decimal(average)))

    /** Frase de día débil. */
    fun weakDayQuote(weekday: DayOfWeek, average: Double, seed: Long): String {
        val the = strArray(R.array.stats_weekday_the)[weekday.value - 1]
        val every = strArray(R.array.stats_weekday_every)[weekday.value - 1]
        return pick(
            seed, "stats.weakDay", R.array.stats_weak_day,
            the, Format.capitalize(the), Format.capitalize(every), averageVideos(average),
        )
    }

    /** Valor de hora punta: «23:00 · 41 VÍDEOS». */
    fun peakHourValue(hour: Int, count: Int): String =
        str(R.string.stats_peak_hour_value, Format.hour(hour), Format.upper(Words.videos(count)))

    /** Frase de hora punta según la franja (madrugada, mañana, sobremesa, tarde, noche). */
    fun peakHourQuote(hour: Int, seed: Long): String {
        val h = Math.floorMod(hour, 24)
        val hh = Format.hour(h)
        val at = Words.atHour(h)
        return when (h) {
            in 0..5 -> pick(seed, "stats.peak.night", R.array.stats_peak_night, hh, at)
            in 6..11 -> pick(seed, "stats.peak.morning", R.array.stats_peak_morning, hh, at)
            in 12..16 -> pick(seed, "stats.peak.afternoon", R.array.stats_peak_afternoon, hh, at)
            in 17..20 -> pick(seed, "stats.peak.evening", R.array.stats_peak_evening, hh, at)
            else -> pick(seed, "stats.peak.late", R.array.stats_peak_late, hh, at)
        }
    }

    /** «27 S»; a partir de un minuto, «1 MIN», «1 H 2 MIN». */
    fun perVideoValue(averageMs: Long): String =
        Format.upper(if (averageMs < 59_500) Format.seconds(averageMs) else Format.duration(averageMs))

    /** «172». */
    fun dailyAverageValue(average: Double): String = Format.decimal(average)

    /** «26 DE 31». */
    fun activeDaysValue(active: Int, total: Int): String =
        str(R.string.stats_active_days_value, Format.integer(active), Format.integer(total))

    /** «34 %» (cuota de una plataforma, categoría o creador). */
    fun shareValue(fraction: Double): String = Format.percent(fraction)

    // ------------------------------------------------------------------ piezas de la pantalla

    /** Etiquetas de las líneas de PATRONES (`ReceiptLine`, en frase). */
    val LINE_PEAK_HOUR: String get() = str(R.string.stats_line_peak_hour)
    val LINE_WEAK_DAY: String get() = str(R.string.stats_line_weak_day)
    val LINE_WORST_DAY: String get() = str(R.string.stats_line_worst_day)
    val LINE_ACTIVE_DAYS: String get() = str(R.string.stats_line_active_days)
    val LINE_PER_VIDEO: String get() = str(R.string.stats_line_per_video)
    val LINE_SHARES: String get() = str(R.string.stats_line_shares)
    val LINE_STREAK: String get() = str(R.string.stats_line_streak)
    val LINE_BEST_STREAK: String get() = str(R.string.stats_line_best_streak)
    val LINE_STREAK_NO_GOAL: String get() = str(R.string.stats_line_streak_no_goal)

    /** Dato de la cabecera de CADA DÍA cuando «siempre» se agrupa por semanas. */
    val BY_WEEK: String get() = str(R.string.stats_by_week)

    /** Acción de la línea del peor día para TalkBack. */
    val OPEN_WORST_DAY: String get() = str(R.string.stats_open_worst_day)

    /** Acción de la línea de racha sin objetivo para TalkBack (lleva a Ajustes). */
    val SET_GOAL_ACTION: String get() = str(R.string.stats_set_goal_action)

    /** Navegador: «27 OCT – 2 NOV 2026», «NOVIEMBRE 2026», «2026», «DESDE EL 3 MAR 2026». */
    fun navigatorLabel(period: CopyPeriod, start: LocalDate, end: LocalDate, firstDay: LocalDate?): String = when (period) {
        CopyPeriod.WEEK -> weekLabel(start, end)
        CopyPeriod.MONTH -> monthLabel(YearMonth.from(start))
        CopyPeriod.YEAR -> yearLabel(start.year)
        CopyPeriod.ALL -> allLabel(firstDay)
    }

    /** contentDescription de la cifra grande: «1.204 vídeos en este periodo». */
    fun countDescription(count: Long): String = str(R.string.stats_count_description, Words.videos(count))

    /** Tooltip de una barra o celda: «212 vídeos\nmar 3 nov». */
    fun dayTooltip(date: LocalDate, count: Int): String = str(R.string.stats_day_tooltip, Words.videos(count), tooltipDate(date))

    /** Tooltip de un día anterior al primer registro: «sin datos\nmar 3 nov». */
    fun untrackedTooltip(date: LocalDate): String =
        str(R.string.stats_day_tooltip, str(R.string.stats_tooltip_no_data), tooltipDate(date))

    /** Tooltip de un día que todavía no ha llegado: «por llegar\nmié 4 nov». */
    fun futureTooltip(date: LocalDate): String =
        str(R.string.stats_day_tooltip, str(R.string.stats_tooltip_future), tooltipDate(date))

    /** Tooltip de una barra semanal: «1.204 vídeos\nsemana del 27 oct». */
    fun weekTooltip(monday: LocalDate, count: Int): String =
        str(R.string.stats_week_tooltip, Words.videos(count), Format.dayShort(monday))

    /** Valor del peor día: «MAR 3 NOV · 412» (con año si no es el de [today]). */
    fun worstDayValue(date: LocalDate, count: Int, today: LocalDate): String {
        val day = if (date.year != today.year) Format.dayShortWithYear(date) else Format.dayShort(date)
        return Format.upper(str(R.string.stats_worst_day_value, Format.weekdayShort(date.dayOfWeek), day, Format.integer(count)))
    }

    /** «1.204 · 34 %» (vídeos y cuota de una plataforma o categoría). */
    fun countShareValue(count: Int, fraction: Double): String =
        str(R.string.stats_count_share_value, Format.integer(count), Format.percent(fraction))

    /** «4» (veces que se pulsó compartir). */
    fun sharesValue(shares: Int): String = Format.integer(shares)

    /** Valor de una fila de «A QUIÉN ENVÍAS»: los vídeos enviados a esa persona o grupo («12»). */
    fun recipientValue(count: Int): String = Format.integer(count)

    /** Resumen hablado del calendario: «Calendario: 26 días con vídeos de 31.». */
    fun calendarDescription(activeDays: Int, days: Int): String =
        plural(R.plurals.stats_calendar_description, activeDays, Format.integer(activeDays), Format.integer(days))

    /** «mar 3 nov». */
    private fun tooltipDate(date: LocalDate): String = Format.weekdayDayShort(date)

    // ------------------------------------------------------------------ vacíos

    /**
     * Periodo sin un solo vídeo. [current] = `true` si es el periodo en curso («esta semana»),
     * `false` si es uno pasado («esa semana»).
     */
    fun emptyPeriod(period: CopyPeriod, current: Boolean, seed: Long): String {
        if (period == CopyPeriod.ALL) return pick(seed, "stats.empty.all", R.array.stats_empty_all)
        return if (current) {
            pick(
                seed, "stats.empty.current",
                byPeriod(period, R.array.stats_empty_current_week, R.array.stats_empty_current_month, R.array.stats_empty_current_year),
            )
        } else {
            pick(
                seed, "stats.empty.past",
                byPeriod(period, R.array.stats_empty_past_week, R.array.stats_empty_past_month, R.array.stats_empty_past_year),
            )
        }
    }

    /** Periodo anterior al primer día con datos. */
    fun beforeHistory(firstDay: LocalDate, seed: Long): String =
        pick(seed, "stats.beforeHistory", R.array.stats_before_history, Format.dayFull(firstDay))

    // ------------------------------------------------------------------ accesibilidad de gráficas

    /** Barra diaria: «Martes 3 de noviembre: 212 vídeos, el día con más.». */
    fun barDescription(date: LocalDate, count: Int, isMax: Boolean): String = str(
        if (isMax && count > 0) R.string.stats_bar_description_max else R.string.stats_bar_description,
        Format.capitalize(Format.dayLong(date)), Words.videos(count),
    )

    /** Punto de la línea horaria: «23:00: 41 vídeos, la hora con más.». */
    fun hourDescription(hour: Int, count: Int, isMax: Boolean = false): String = str(
        if (isMax && count > 0) R.string.stats_hour_description_max else R.string.stats_hour_description,
        Format.hour(hour), Words.videos(count),
    )

    /** Celda del mapa de calor: «Martes 3 de noviembre: 212 vídeos.». */
    fun heatCellDescription(date: LocalDate, count: Int): String =
        str(R.string.stats_heat_cell_description, Format.capitalize(Format.dayLong(date)), Words.videos(count))

    /** Resumen hablado de la gráfica diaria: «1.204 vídeos. El día con más, martes 3 de noviembre, con 212.». */
    fun dailyBarsSummary(total: Long, maxDate: LocalDate?, maxCount: Int): String {
        if (maxDate == null || maxCount <= 0) return str(R.string.stats_daily_bars_summary, Words.videos(total))
        return str(R.string.stats_daily_bars_summary_max, Words.videos(total), Format.dayLong(maxDate), Format.integer(maxCount))
    }

    /** «240 vídeos», «3,2 vídeos», «1 vídeo» (la media, con la palabra que le toca). */
    internal fun averageVideos(average: Double): String {
        val text = Format.decimal(average)
        // Misma cuenta que Format.decimal: con decimales por debajo de 10, entera a partir de 10.
        val tenths = (abs(average) * 10).roundToLong()
        val whole = when {
            abs(average) >= 10 -> abs(average).roundToLong()
            tenths % 10 == 0L -> tenths / 10
            else -> null
        }
        return if (whole != null) plural(R.plurals.stats_average_videos, whole, text) else str(R.string.stats_average_videos_decimal, text)
    }

    /** El recurso que toca al periodo (semana, mes o año; «siempre» no tiene estas frases). */
    private fun byPeriod(period: CopyPeriod, week: Int, month: Int, year: Int): Int = when (period) {
        CopyPeriod.WEEK -> week
        CopyPeriod.MONTH -> month
        CopyPeriod.YEAR, CopyPeriod.ALL -> year
    }

    // ------------------------------------------------------------------ semana frente a la anterior

    /** Sección de la comparativa semanal (pestaña SEMANA). Corta: cabe en una línea al 130 %. */
    val SECTION_WEEK_VS: String get() = str(R.string.stats_section_week_vs)

    /** Leyenda de la línea fantasma («- - SEMANA ANTERIOR»). */
    val GHOST_LEGEND: String get() = str(R.string.stats_ghost_legend)

    val LINE_THIS_WEEK_SO_FAR: String get() = str(R.string.stats_line_this_week_so_far)
    val LINE_THAT_WEEK: String get() = str(R.string.stats_line_that_week)
    val LINE_PREVIOUS_SAME_STRETCH: String get() = str(R.string.stats_line_previous_same_stretch)
    val LINE_PREVIOUS: String get() = str(R.string.stats_line_previous)
    val LINE_PREVIOUS_WHOLE: String get() = str(R.string.stats_line_previous_whole)
    val LINE_DIFFERENCE: String get() = str(R.string.stats_line_difference)
    val LINE_DIFFERENCE_PER_DAY: String get() = str(R.string.stats_line_difference_per_day)

    /** «1.204». */
    fun weekTotalValue(count: Int): String = Format.integer(count)

    /** «+12 %», «−8 %», «±0 %». */
    fun weekDifferenceValue(change: Double): String = Format.signedPercent(change)

    /**
     * Tooltip de un día de la comparativa: «125 vídeos\nmar 3 nov · la anterior, 140»; sin dato,
     * «por llegar» o «sin datos».
     */
    fun ghostTooltip(date: LocalDate, count: Int?, previous: Int?, today: LocalDate): String {
        val head = when {
            count != null -> Words.videos(count)
            date.isAfter(today) -> str(R.string.stats_tooltip_future)
            else -> str(R.string.stats_tooltip_no_data)
        }
        return if (previous != null) {
            str(R.string.stats_ghost_tooltip, head, tooltipDate(date), Format.integer(previous))
        } else {
            str(R.string.stats_ghost_tooltip_no_previous, head, tooltipDate(date))
        }
    }

    /**
     * Resumen hablado de la comparativa: «Esta semana llevas 205 vídeos; la anterior, a estas
     * alturas, 230. Un 11 % menos.».
     *
     * @param inProgress la semana mostrada es la actual (y sigue en curso).
     * @param perDay la diferencia compara medias por día registrado.
     */
    fun weekComparisonDescription(total: Int, previousTotal: Int?, change: Double?, inProgress: Boolean, perDay: Boolean): String {
        val v = Words.videos(total)
        val first = when {
            previousTotal == null -> str(if (inProgress) R.string.stats_week_vs_current_no_previous else R.string.stats_week_vs_past_no_previous, v)
            inProgress -> str(R.string.stats_week_vs_current, v, Format.integer(previousTotal))
            else -> str(R.string.stats_week_vs_past, v, Format.integer(previousTotal))
        }
        val diff = change?.let { c ->
            val pct = (c * 100).roundToInt()
            val amount = Format.percent(abs(c))
            when {
                pct > 0 -> str(if (perDay) R.string.stats_week_vs_more_per_day else R.string.stats_week_vs_more, amount)
                pct < 0 -> str(if (perDay) R.string.stats_week_vs_less_per_day else R.string.stats_week_vs_less, amount)
                else -> str(R.string.stats_week_vs_same)
            }
        } ?: return first
        return str(R.string.stats_week_vs_description, first, diff)
    }

    // ------------------------------------------------------------------ accesibilidad

    /** Acción de TalkBack para abrir un día desde la gráfica: «Abrir el martes 3 de noviembre». */
    fun openDayAction(date: LocalDate): String = str(R.string.stats_open_day, Format.dayLong(date))

    /**
     * Línea bajo la cifra grande, dicha: «1.204 vídeos, 9 horas y 12 minutos, 172 al día, 27 segundos
     * por vídeo.» (la versión visible usa abreviaturas y «·»).
     */
    fun summaryDescription(count: Long, totalMs: Long, averagePerDay: Double, averageMsPerVideo: Long): String {
        if (count <= 0) return str(R.string.stats_summary_description_zero, Words.videos(0))
        return str(
            R.string.stats_summary_description,
            Words.videos(count), Words.duration(totalMs), Format.decimal(averagePerDay), Words.duration(averageMsPerVideo),
        )
    }
}
