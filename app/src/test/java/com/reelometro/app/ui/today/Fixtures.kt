package com.reelometro.app.ui.today

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.stats.AllTime
import com.reelometro.app.stats.CategorySlice
import com.reelometro.app.stats.Comparison
import com.reelometro.app.stats.CreatorRank
import com.reelometro.app.stats.DateSpan
import com.reelometro.app.stats.DayPoint
import com.reelometro.app.stats.DayStats
import com.reelometro.app.stats.Format
import com.reelometro.app.stats.GoalProgress
import com.reelometro.app.stats.HourPoint
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import com.reelometro.app.stats.PlatformSlice
import com.reelometro.app.stats.Streak
import com.reelometro.app.stats.Totals
import com.reelometro.app.stats.Trend
import com.reelometro.app.stats.WeekdayPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.DayOfWeek
import java.time.LocalDate

// Datos falsos para los ViewModels de Hoy, Día y Estadísticas (sin Room ni Android).

internal const val MIN = 60_000L
internal const val HOUR = 3_600_000L

/** Martes 3 de noviembre de 2026. */
internal val TODAY: LocalDate = LocalDate.of(2026, 11, 3)

/** `Dispatchers.Main` = un despachador de pruebas (lo usa `viewModelScope`). */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Un día con [count] vídeos (un tercio a las 9 h y el resto a las 21 h), TikTok y Reels, dos categorías y dos creadores. */
internal fun dayStats(
    date: LocalDate = TODAY,
    count: Int = 212,
    totalMs: Long = 47 * MIN,
    shares: Int = 4,
    goal: Int? = null,
    average: Double? = 158.0,
    isToday: Boolean = date == TODAY,
): DayStats {
    val morning = count / 3
    val hours = List(24) { h ->
        when (h) {
            9 -> HourPoint(h, morning, totalMs / 3)
            21 -> HourPoint(h, count - morning, totalMs - totalMs / 3)
            else -> HourPoint(h, 0, 0)
        }
    }
    val tiktok = (count * 0.6).toInt()
    val platforms = if (count == 0) emptyList() else listOf(
        PlatformSlice(Platform.TIKTOK, tiktok, totalMs * 6 / 10, tiktok.toDouble() / count),
        PlatformSlice(Platform.INSTAGRAM, count - tiktok, totalMs * 4 / 10, (count - tiktok).toDouble() / count),
    )
    val categories = if (count == 0) emptyList() else listOf(
        CategorySlice(Category.HUMOR, tiktok, totalMs / 2, 0.6),
        CategorySlice(Category.OTROS, count - tiktok, totalMs / 2, 0.4),
    )
    val creators = if (count == 0) emptyList() else listOf(
        CreatorRank(1, "cocinaconpaco", Platform.TIKTOK, 31, 7 * MIN),
        CreatorRank(2, "@futbolclips", Platform.YOUTUBE, 22, 5 * MIN),
    )
    val untilMinute = if (isToday) 21 * 60 + 30 else null
    return DayStats(
        date = date,
        isToday = isToday,
        untilMinute = untilMinute,
        totals = Totals(count, totalMs, shares),
        hours = hours,
        platforms = platforms,
        categories = categories,
        creators = creators,
        peakHour = hours.maxBy { it.count }.takeIf { it.count > 0 },
        comparison = average?.let { Comparison(date.dayOfWeek, untilMinute, count, totalMs, it, it * 13_000, 8) },
        goal = goal?.let { GoalProgress(it, count) },
    )
}

internal fun allTime(firstDay: LocalDate? = LocalDate.of(2026, 3, 3), count: Int = 12_408, totalMs: Long = 61 * HOUR): AllTime {
    val days = firstDay?.let { DateSpan(it, TODAY).days } ?: 0
    return AllTime(
        firstDay = firstDay,
        today = TODAY,
        totals = if (firstDay == null) Totals.ZERO else Totals(count, totalMs, 30),
        trackedDays = days,
        avgPerDay = if (days == 0) 0.0 else count.toDouble() / days,
        avgMsPerDay = if (days == 0) 0.0 else totalMs.toDouble() / days,
    )
}

/**
 * Un periodo con un dato por día desde su inicio hasta hoy ([perDay]; 0 antes de [firstDay]).
 */
internal fun periodStats(
    ref: PeriodRef,
    today: LocalDate = TODAY,
    firstDay: LocalDate? = today.minusDays(69),
    perDay: (LocalDate) -> Int = { 100 + it.dayOfMonth },
    streak: Streak? = null,
    trend: Trend? = null,
): PeriodStats {
    val span = ref.span(today, firstDay)
    val elapsed = DateSpan(span.start, minOf(span.end, today))
    val days = elapsed.dates().map { d ->
        val tracked = firstDay != null && !d.isBefore(firstDay)
        val n = if (tracked) perDay(d) else 0
        DayPoint(d, n, n * 15_000L, 0, tracked)
    }
    val trackedDays = days.count { it.tracked }
    val totals = Totals(days.sumOf { it.count }, days.sumOf { it.totalMs }, 3)
    val empty = totals.count == 0
    return PeriodStats(
        ref = ref,
        span = span,
        label = ref.label(today, firstDay),
        rangeLabel = Format.rangeWithYear(span.start, span.end),
        today = today,
        firstDay = firstDay,
        totals = totals,
        days = days,
        hours = List(24) { HourPoint(it, if (it == 23) totals.count / 3 else 0, 0) },
        weekdays = DayOfWeek.entries.map { WeekdayPoint(it, if (it == DayOfWeek.SUNDAY) 240.0 else 100.0, 0.0, 1, 100, 0) },
        platforms = if (empty) emptyList() else listOf(PlatformSlice(Platform.TIKTOK, totals.count, totals.totalMs, 1.0)),
        categories = if (empty) emptyList() else listOf(CategorySlice(Category.HUMOR, totals.count, totals.totalMs, 1.0)),
        creators = if (empty) emptyList() else listOf(CreatorRank(1, "gatosdemadrid", Platform.INSTAGRAM, 14, 3 * MIN)),
        trackedDays = trackedDays,
        activeDays = days.count { it.count > 0 },
        avgPerDay = if (trackedDays == 0) 0.0 else totals.count.toDouble() / trackedDays,
        avgMsPerDay = if (trackedDays == 0) 0.0 else totals.totalMs.toDouble() / trackedDays,
        peakHour = if (empty) null else HourPoint(23, totals.count / 3, 0),
        weakestWeekday = if (empty) null else WeekdayPoint(DayOfWeek.SUNDAY, 240.0, 0.0, 4, 960, 0),
        worstDay = days.filter { it.count > 0 }.maxByOrNull { it.count },
        quietestDay = days.filter { it.tracked }.minByOrNull { it.count },
        streak = streak,
        trend = trend,
        hasPrevious = ref.hasPrevious(today, firstDay),
        hasNext = ref.hasNext,
    )
}
