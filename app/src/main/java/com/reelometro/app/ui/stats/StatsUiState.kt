package com.reelometro.app.ui.stats

import androidx.compose.runtime.Immutable
import com.reelometro.app.copy.CopyPeriod
import com.reelometro.app.copy.DeltaLine
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.StatsCopy
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.stats.DateSpan
import com.reelometro.app.stats.DayPoint
import com.reelometro.app.stats.Format
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import com.reelometro.app.stats.WeekComparison
import com.reelometro.app.ui.components.charts.GhostBar
import com.reelometro.app.ui.components.charts.HeatDay
import com.reelometro.app.ui.components.charts.HeatmapMode
import com.reelometro.app.ui.today.CreatorRow
import com.reelometro.app.ui.today.DayParts
import com.reelometro.app.ui.today.PlatformsModel
import com.reelometro.app.ui.today.RankRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Estado de **Estadísticas**. La cabecera (pestañas y navegador) sigue a la selección al
 * instante; el cuerpo puede ser del periodo anterior durante los milisegundos que tarda en
 * llegar el nuevo (así la cifra cambia una sola vez, animada, sin pasar por «cargando»).
 */
sealed interface StatsUiState {
    val header: StatsHeader

    data class Loading(override val header: StatsHeader) : StatsUiState

    data class Error(override val header: StatsHeader, val message: String) : StatsUiState

    /** Periodo sin un solo vídeo: cifra a 0 y una frase. */
    data class Empty(override val header: StatsHeader, val summary: PeriodSummary, val message: String) : StatsUiState

    data class Content(override val header: StatsHeader, val summary: PeriodSummary, val sections: StatsSections) : StatsUiState
}

/**
 * @property direction +1 = hacia periodos más recientes o pestañas a la derecha, −1 = al revés:
 *   decide por dónde sale la cifra.
 */
@Immutable
data class StatsHeader(
    val tabs: List<String>,
    val tabIndex: Int,
    val navigatorLabel: String,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val direction: Int,
)

/**
 * La cifra del periodo y sus líneas. [key] identifica el periodo (anima solo al cambiar de periodo).
 * [lineDescription] = [line] dicha para TalkBack (sin abreviaturas ni «·»).
 */
@Immutable
data class PeriodSummary(
    val key: String,
    val count: String,
    val description: String,
    val line: String,
    val trend: DeltaLine?,
    val lineDescription: String? = null,
)

/**
 * [week] = comparativa con la semana anterior (solo en SEMANA y si la anterior tiene datos).
 * [recipients] = a quién envías más en Instagram (vacío si el ajuste está apagado o no hay envíos).
 */
@Immutable
data class StatsSections(
    val daily: DailyModel,
    val calendar: CalendarModel,
    val patterns: PatternsModel,
    val platforms: PlatformsModel?,
    val categories: List<RankRow>,
    val creators: List<CreatorRow>,
    val week: WeekCompareModel? = null,
    val recipients: List<CreatorRow> = emptyList(),
)

/**
 * Barras de CADA DÍA. [days] = día que abre cada barra (`null`: futuro, sin datos o semana).
 * [actionLabels] = acción de TalkBack para abrir cada día («Abrir el martes 3 de noviembre»); solo
 * en semana y mes (en año serían cientos), `null` donde no se puede abrir.
 */
@Immutable
data class DailyModel(
    val values: List<Float>,
    val labels: List<String>,
    val tooltips: List<String>,
    val days: List<Long?>,
    val highlightIndex: Int?,
    val average: Float?,
    val trailing: String?,
    val description: String,
    val actionLabels: List<String?> = emptyList(),
)

/**
 * «Contra la semana anterior»: las barras de la semana con la línea fantasma de la anterior
 * ([bars], para `GhostWeekChart`), hoy resaltado y los totales como líneas de ticket.
 */
@Immutable
data class WeekCompareModel(
    val bars: List<GhostBar>,
    val todayIndex: Int?,
    val legend: String,
    val lines: List<PatternLine>,
    val description: String,
)

/** Uno o varios mapas de calor (en «siempre», uno por año, el más reciente arriba). */
@Immutable
data class CalendarModel(
    val mode: HeatmapMode,
    val blocks: List<CalendarBlock>,
    val tooltips: Map<LocalDate, String>,
    val openable: Set<LocalDate>,
    val today: LocalDate,
    val description: String,
)

@Immutable
data class CalendarBlock(val key: String, val label: String?, val days: List<HeatDay>)

/** Líneas de PATRONES; [noGoal] = racha sin objetivo (lleva a Ajustes); [quote] = aparte en serif. */
@Immutable
data class PatternsModel(val lines: List<PatternLine>, val noGoal: String?, val quote: String?)

/** [epochDay] = la línea abre ese día (peor día). [alert] = valor en `signalText`. */
@Immutable
data class PatternLine(
    val key: String,
    val label: String,
    val value: String,
    val alert: Boolean = false,
    val epochDay: Long? = null,
)

/** Resultado de pedir un periodo al repositorio. */
sealed interface PeriodResult {
    val ref: PeriodRef

    /** [previous] = la semana anterior (solo en SEMANA, para la comparativa); `null` si no la hay. */
    data class Data(override val ref: PeriodRef, val stats: PeriodStats, val previous: PeriodStats? = null) : PeriodResult

    data class Failed(override val ref: PeriodRef) : PeriodResult
}

/** Mapeo puro de los datos a [StatsUiState]. */
object StatsMapper {
    val KINDS = listOf(PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.YEAR, PeriodKind.ALL)
    // `get()`: se relee al cambiar de idioma con la app abierta (antes se quedaba en el anterior).
    val TABS: List<String> get() = KINDS.map { StatsCopy.tab(copyPeriod(it)) }

    const val CATEGORY_ROWS = 8
    const val CREATOR_ROWS = 10

    /** Con más días que esto, «siempre» se dibuja por semanas. */
    const val MAX_DAILY_BARS = 366

    fun copyPeriod(kind: PeriodKind): CopyPeriod = when (kind) {
        PeriodKind.WEEK -> CopyPeriod.WEEK
        PeriodKind.MONTH -> CopyPeriod.MONTH
        PeriodKind.YEAR -> CopyPeriod.YEAR
        PeriodKind.ALL -> CopyPeriod.ALL
    }

    fun header(ref: PeriodRef, direction: Int, today: LocalDate, firstDay: LocalDate?): StatsHeader {
        val span = ref.span(today, firstDay)
        return StatsHeader(
            tabs = TABS,
            tabIndex = KINDS.indexOf(ref.kind),
            navigatorLabel = StatsCopy.navigatorLabel(copyPeriod(ref.kind), span.start, span.end, firstDay),
            canGoPrevious = ref.hasPrevious(today, firstDay),
            canGoNext = ref.hasNext,
            direction = direction,
        )
    }

    fun loading(ref: PeriodRef, today: LocalDate): StatsUiState = StatsUiState.Loading(header(ref, 0, today, null))

    /**
     * @param ref selección actual (manda en la cabecera).
     * @param result último resultado recibido; puede ser de la selección anterior mientras llega el nuevo.
     */
    fun map(ref: PeriodRef, direction: Int, result: PeriodResult?, today: LocalDate): StatsUiState {
        val firstDay = (result as? PeriodResult.Data)?.stats?.firstDay
        val header = header(ref, direction, today, firstDay)
        val seed = Variants.daySeed(today)
        return when (result) {
            null -> StatsUiState.Loading(header)
            is PeriodResult.Failed -> StatsUiState.Error(header, StateCopy.error(seed))
            is PeriodResult.Data -> {
                val stats = result.stats
                val summary = summary(stats)
                if (stats.isEmpty) {
                    StatsUiState.Empty(header, summary, StatsCopy.emptyPeriod(copyPeriod(stats.ref.kind), stats.inProgress, seed))
                } else {
                    StatsUiState.Content(header, summary, sections(stats, seed, result.previous))
                }
            }
        }
    }

    fun summary(stats: PeriodStats): PeriodSummary {
        val count = stats.totals.count.toLong()
        return PeriodSummary(
            key = stats.ref.toRouteArg(),
            count = Format.integer(count),
            description = StatsCopy.countDescription(count),
            line = unbreakableSegments(StatsCopy.summaryLine(count, stats.totals.totalMs, stats.avgPerDay, stats.avgMsPerVideo)),
            trend = trend(stats),
            lineDescription = StatsCopy.summaryDescription(count, stats.totals.totalMs, stats.avgPerDay, stats.avgMsPerVideo),
        )
    }

    /** La línea solo se puede partir por los « · »: «18 S POR VÍDEO» nunca queda en dos renglones. */
    internal fun unbreakableSegments(line: String): String =
        // El «·» va pegado (espacio duro) a lo de antes: si la línea parte, queda al final, no al principio de la siguiente.
        line.split(SEPARATOR).joinToString(Format.NBSP + "· ") { it.replace(' ', Format.NBSP) }

    private const val SEPARATOR = " · "

    /** Frente al periodo anterior, en vídeos por día (lo que compara la capa de datos). `null` en «siempre». */
    internal fun trend(stats: PeriodStats): DeltaLine? {
        val period = copyPeriod(stats.ref.kind)
        if (period == CopyPeriod.ALL) return null
        val trend = stats.trend ?: return StatsCopy.trend(stats.avgPerDay, null, period)
        val change = trend.countChange
        return when {
            change != null -> StatsCopy.trend(1.0 + change, 1.0, period)
            trend.previousTrackedDays <= 0 -> StatsCopy.trend(stats.avgPerDay, null, period)
            else -> StatsCopy.trend(stats.avgPerDay, 0.0, period)
        }
    }

    /** @param previous la semana anterior a [stats] (solo en SEMANA), para la comparativa. */
    fun sections(stats: PeriodStats, seed: Long, previous: PeriodStats? = null): StatsSections = StatsSections(
        daily = daily(stats),
        calendar = calendar(stats),
        patterns = patterns(stats, seed),
        platforms = DayParts.platforms(stats.platforms) { StatsCopy.countShareValue(it.count, it.fraction) },
        categories = DayParts.categories(stats.categories, CATEGORY_ROWS) { StatsCopy.countShareValue(it.count, it.fraction) },
        creators = DayParts.creators(stats.creators, CREATOR_ROWS) { TodayCopy.rankValue(it.count, it.totalMs) },
        week = week(stats, previous),
        recipients = stats.recipients.take(CREATOR_ROWS).map {
            CreatorRow("${it.platform.name}:${it.name}", it.name, it.platform, StatsCopy.recipientValue(it.count))
        },
    )

    // ------------------------------------------------------------------ 01 cada día

    internal fun daily(stats: PeriodStats): DailyModel {
        val kind = stats.ref.kind
        if (kind == PeriodKind.ALL && stats.days.size > MAX_DAILY_BARS) return weekly(stats)
        val today = stats.today
        val byDate = stats.days.associateBy { it.date }
        // El periodo entero (también los días que faltan, vacíos), salvo «siempre», que acaba hoy.
        val dates = if (kind == PeriodKind.ALL) stats.days.map { it.date } else stats.span.dates()
        val worst = stats.worstDay?.takeIf { it.count > 0 }
        return DailyModel(
            values = dates.map { (byDate[it]?.count ?: 0).toFloat() },
            labels = when (kind) {
                PeriodKind.WEEK -> dates.map { Format.weekdayInitial(it.dayOfWeek) }
                PeriodKind.MONTH -> dates.map { it.dayOfMonth.toString() }
                PeriodKind.YEAR, PeriodKind.ALL -> monthLabels(dates)
            },
            tooltips = dates.map { date -> tooltip(date, byDate[date], today) },
            days = dates.map { date -> date.toEpochDay().takeIf { isOpenable(date, byDate[date], today) } },
            highlightIndex = if (kind == PeriodKind.WEEK || kind == PeriodKind.MONTH) dates.indexOf(today).takeIf { it >= 0 } else null,
            average = stats.avgPerDay.toFloat().takeIf { stats.trackedDays >= 2 && it > 0f },
            trailing = null,
            description = StatsCopy.dailyBarsSummary(stats.totals.count.toLong(), worst?.date, worst?.count ?: 0),
            actionLabels = if (kind == PeriodKind.WEEK || kind == PeriodKind.MONTH) {
                dates.map { date -> if (isOpenable(date, byDate[date], today)) StatsCopy.openDayAction(date) else null }
            } else {
                emptyList()
            },
        )
    }

    /** «Siempre» con más de un año de historial: una barra por semana (lunes). */
    private fun weekly(stats: PeriodStats): DailyModel {
        val weeks = stats.days
            .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap()
        val mondays = weeks.keys.toList()
        val totals = weeks.values.map { days -> days.sumOf { it.count } }
        return DailyModel(
            values = totals.map { it.toFloat() },
            labels = mondays.mapIndexed { i, monday ->
                if (i == 0 || mondays[i - 1].month != monday.month) Format.upper(Format.monthShort(monday.monthValue)) else ""
            },
            tooltips = mondays.mapIndexed { i, monday -> StatsCopy.weekTooltip(monday, totals[i]) },
            days = mondays.map { null },
            highlightIndex = null,
            average = totals.average().toFloat().takeIf { totals.size >= 2 && it > 0f },
            trailing = StatsCopy.BY_WEEK,
            description = StatsCopy.dailyBarsSummary(stats.totals.count.toLong(), null, 0),
        )
    }

    /** Mes abreviado en el día 1 de cada mes; si no hay ninguno, en la primera barra. */
    private fun monthLabels(dates: List<LocalDate>): List<String> {
        val labels = dates.map { if (it.dayOfMonth == 1) Format.upper(Format.monthShort(it.monthValue)) else "" }
        if (labels.any { it.isNotEmpty() } || dates.isEmpty()) return labels
        return labels.toMutableList().also { it[0] = Format.upper(Format.monthShort(dates[0].monthValue)) }
    }

    private fun tooltip(date: LocalDate, point: DayPoint?, today: LocalDate): String = when {
        date.isAfter(today) -> StatsCopy.futureTooltip(date)
        point == null || !point.tracked -> StatsCopy.untrackedTooltip(date)
        else -> StatsCopy.dayTooltip(date, point.count)
    }

    private fun isOpenable(date: LocalDate, point: DayPoint?, today: LocalDate): Boolean =
        !date.isAfter(today) && point != null && point.tracked

    // ------------------------------------------------------------------ contra la semana anterior

    /** Comparativa día a día con la semana anterior; `null` fuera de SEMANA o si la anterior no tiene datos. */
    internal fun week(stats: PeriodStats, previous: PeriodStats?): WeekCompareModel? {
        val week = WeekComparison.of(stats, previous) ?: return null
        val today = stats.today
        val bars = week.days.map { day ->
            GhostBar(
                label = Format.weekdayInitial(day.date.dayOfWeek),
                count = day.count,
                ghost = day.previous,
                description = StatsCopy.ghostTooltip(day.date, day.count, day.previous, today),
            )
        }
        val lines = buildList {
            val head = if (week.inProgress) StatsCopy.LINE_THIS_WEEK_SO_FAR else StatsCopy.LINE_THAT_WEEK
            add(PatternLine("week", head, StatsCopy.weekTotalValue(week.total)))
            week.previousTotal?.let { previousTotal ->
                val label = if (week.inProgress) StatsCopy.LINE_PREVIOUS_SAME_STRETCH else StatsCopy.LINE_PREVIOUS
                add(PatternLine("previous", label, StatsCopy.weekTotalValue(previousTotal)))
            }
            if (week.inProgress) add(PatternLine("previousWhole", StatsCopy.LINE_PREVIOUS_WHOLE, StatsCopy.weekTotalValue(week.previousWeekTotal)))
            week.change?.let { change ->
                val label = if (week.perDay) StatsCopy.LINE_DIFFERENCE_PER_DAY else StatsCopy.LINE_DIFFERENCE
                // Más vídeos que la semana anterior = acento (como el resto de comparaciones); menos, normal.
                add(PatternLine("difference", label, StatsCopy.weekDifferenceValue(change), alert = (change * 100).roundToInt() > 0))
            }
        }
        return WeekCompareModel(
            bars = bars,
            todayIndex = week.todayIndex,
            legend = StatsCopy.GHOST_LEGEND,
            lines = lines,
            description = StatsCopy.weekComparisonDescription(week.total, week.previousTotal, week.change, week.inProgress, week.perDay),
        )
    }

    // ------------------------------------------------------------------ 02 calendario

    internal fun calendar(stats: PeriodStats): CalendarModel {
        val kind = stats.ref.kind
        val today = stats.today
        val heat = stats.days.map { HeatDay(it.date, if (it.tracked) it.count.toFloat() else 0f) }
        val byDate = stats.days.associateBy { it.date }
        val blocks = if (kind == PeriodKind.ALL) {
            val years = heat.groupBy { it.date.year }.toSortedMap(compareByDescending { it })
            years.map { (year, days) -> CalendarBlock(year.toString(), if (years.size > 1) year.toString() else null, days) }
        } else {
            listOf(CalendarBlock(stats.ref.toRouteArg(), null, heat))
        }
        // Tooltips para todo lo que puede dibujar el mapa: el periodo entero, o los años completos en «siempre».
        val range = if (kind == PeriodKind.ALL) {
            val first = stats.days.firstOrNull()?.date ?: today
            DateSpan(LocalDate.of(first.year, 1, 1), LocalDate.of(today.year, 12, 31)).dates()
        } else {
            stats.span.dates()
        }
        return CalendarModel(
            mode = when (kind) {
                PeriodKind.WEEK -> HeatmapMode.WEEK
                PeriodKind.MONTH -> HeatmapMode.MONTH
                PeriodKind.YEAR, PeriodKind.ALL -> HeatmapMode.YEAR
            },
            blocks = blocks,
            tooltips = range.associateWith { tooltip(it, byDate[it], today) },
            openable = stats.days.filter { isOpenable(it.date, it, today) }.map { it.date }.toSet(),
            today = today,
            description = StatsCopy.calendarDescription(stats.activeDays, stats.trackedDays),
        )
    }

    // ------------------------------------------------------------------ 03 patrones

    internal fun patterns(stats: PeriodStats, seed: Long): PatternsModel {
        val peak = stats.peakHour?.takeIf { it.count > 0 }
        val lines = buildList {
            if (peak != null) add(PatternLine("peak", StatsCopy.LINE_PEAK_HOUR, StatsCopy.peakHourValue(peak.hour, peak.count)))
            // En una semana cada día de la semana es un solo día: el «día débil» sería el peor día otra vez.
            val weak = stats.weakestWeekday?.takeIf { stats.ref.kind != PeriodKind.WEEK && it.days > 0 && it.averageCount > 0 }
            if (weak != null) add(PatternLine("weak", StatsCopy.LINE_WEAK_DAY, StatsCopy.weakDayValue(weak.dayOfWeek, weak.averageCount)))
            val worst = stats.worstDay?.takeIf { it.count > 0 }
            if (worst != null) {
                add(
                    PatternLine(
                        key = "worst",
                        label = StatsCopy.LINE_WORST_DAY,
                        value = StatsCopy.worstDayValue(worst.date, worst.count, stats.today),
                        alert = true,
                        epochDay = worst.date.toEpochDay(),
                    ),
                )
            }
            add(PatternLine("active", StatsCopy.LINE_ACTIVE_DAYS, StatsCopy.activeDaysValue(stats.activeDays, stats.trackedDays)))
            add(PatternLine("perVideo", StatsCopy.LINE_PER_VIDEO, StatsCopy.perVideoValue(stats.avgMsPerVideo)))
            add(PatternLine("shares", StatsCopy.LINE_SHARES, StatsCopy.sharesValue(stats.totals.shares)))
            stats.streak?.let { streak ->
                add(PatternLine("streak", StatsCopy.LINE_STREAK, StatsCopy.streakValue(streak.current), alert = streak.todayExceeded))
                add(PatternLine("best", StatsCopy.LINE_BEST_STREAK, StatsCopy.streakValue(streak.best)))
            }
        }
        return PatternsModel(
            lines = lines,
            noGoal = if (stats.streak == null) StatsCopy.noGoalStreak(seed) else null,
            quote = peak?.let { StatsCopy.peakHourQuote(it.hour, seed) },
        )
    }
}
