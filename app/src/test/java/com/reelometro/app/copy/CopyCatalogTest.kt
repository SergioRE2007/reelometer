package com.reelometro.app.copy

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Recorre TODOS los textos de `copy/` (constantes y funciones, con parámetros extremos y muchas
 * semillas) y comprueba las reglas de DESIGN §8.
 */
class CopyCatalogTest {

    private data class Sample(
        val source: String,
        val text: String,
        val allowEmoji: Boolean = false,
        val checkNumbers: Boolean = true,
        val upper: Boolean = false,
    )

    /** Una situación con variantes: misma entrada, distinta semilla. */
    private class Situation(val name: String, val maxDistinct: Int = 6, val text: (Long) -> String)

    private val min = 60_000L
    private val hour = 3_600_000L
    private val today = LocalDate.of(2026, 11, 3)

    private val counts = listOf(0, 1, 2, 9, 10, 99, 100, 212, 299, 300, 1_234, 10_000)
    private val durations = listOf(0L, 15_000L, 59_000L, min, 47 * min, 72 * min, 3 * hour, 10 * hour, 61 * hour, 5_000 * hour)
    private val goals = listOf(null, 1, 5, 150, 2_000)
    private val seeds = (0L..11L)

    private fun situations(): List<Situation> = buildList {
        // Hoy
        add(Situation("today.zero") { TodayCopy.headline(0, 0, it) })
        add(Situation("today.zero.first") { TodayCopy.headline(0, 0, it, firstDay = true) })
        add(Situation("today.firstDay") { TodayCopy.headline(34, 9 * min, it, firstDay = true) })
        add(Situation("today.goal.over") { TodayCopy.headline(212, 47 * min, it, goal = 150) })
        add(Situation("today.goal.at") { TodayCopy.headline(150, 40 * min, it, goal = 150) })
        add(Situation("today.goal.near") { TodayCopy.headline(130, 40 * min, it, goal = 150) })
        add(Situation("today.first") { TodayCopy.headline(3, 2 * min, it) })
        add(Situation("today.normal") { TodayCopy.headline(50, 20 * min, it) })
        add(Situation("today.high", maxDistinct = 8) { TodayCopy.headline(212, 100 * min, it) })
        add(Situation("today.extreme", maxDistinct = 8) { TodayCopy.headline(400, 4 * hour, it) })
        add(Situation("today.serviceOff") { TodayCopy.serviceOff(it) })
        add(Situation("today.serviceNever") { TodayCopy.serviceNeverEnabled(it) })
        add(Situation("today.noPlatforms") { TodayCopy.noPlatforms(it) })
        // Estadísticas
        add(Situation("stats.trend.up") { StatsCopy.trendQuote(112.0, 100.0, CopyPeriod.WEEK, it)!! })
        add(Situation("stats.trend.down") { StatsCopy.trendQuote(88.0, 100.0, CopyPeriod.MONTH, it)!! })
        add(Situation("stats.trend.same") { StatsCopy.trendQuote(101.0, 100.0, CopyPeriod.YEAR, it)!! })
        add(Situation("stats.trend.none") { StatsCopy.trendQuote(10.0, null, CopyPeriod.MONTH, it)!! })
        add(Situation("stats.trend.fromZero") { StatsCopy.trendQuote(10.0, 0.0, CopyPeriod.WEEK, it)!! })
        add(Situation("stats.trend.bothZero") { StatsCopy.trendQuote(0.0, 0.0, CopyPeriod.WEEK, it)!! })
        add(Situation("stats.streak.noGoal") { StatsCopy.noGoalStreak(it) })
        add(Situation("stats.streak.never") { StatsCopy.streakQuote(0, 0, 150, it) })
        add(Situation("stats.streak.broken") { StatsCopy.streakQuote(0, 12, 150, it) })
        add(Situation("stats.streak.record") { StatsCopy.streakQuote(4, 4, 150, it) })
        add(Situation("stats.streak.ongoing") { StatsCopy.streakQuote(4, 12, 150, it) })
        add(Situation("stats.weakDay") { StatsCopy.weakDayQuote(DayOfWeek.SUNDAY, 240.0, it) })
        listOf(1, 8, 14, 18, 23).forEach { h -> add(Situation("stats.peak.$h") { StatsCopy.peakHourQuote(h, it) }) }
        add(Situation("stats.empty.current") { StatsCopy.emptyPeriod(CopyPeriod.WEEK, true, it) })
        add(Situation("stats.empty.past") { StatsCopy.emptyPeriod(CopyPeriod.MONTH, false, it) })
        add(Situation("stats.empty.all") { StatsCopy.emptyPeriod(CopyPeriod.ALL, true, it) })
        add(Situation("stats.beforeHistory") { StatsCopy.beforeHistory(LocalDate.of(2026, 3, 3), it) })
        // Detalle del día
        add(Situation("day.zero") { DayCopy.quote(0, 0, it) })
        add(Situation("day.first") { DayCopy.quote(3, 2 * min, it) })
        add(Situation("day.normal") { DayCopy.quote(50, 20 * min, it) })
        add(Situation("day.high", maxDistinct = 6) { DayCopy.quote(212, 100 * min, it) })
        add(Situation("day.extreme") { DayCopy.quote(400, 4 * hour, it) })
        add(Situation("day.goal.over") { DayCopy.quote(212, 47 * min, it, goal = 150) })
        add(Situation("day.goal.at") { DayCopy.quote(150, 47 * min, it, goal = 150) })
        add(Situation("day.goal.under") { DayCopy.quote(90, 30 * min, it, goal = 150) })
        // Wrapped
        add(Situation("wrapped.hub.empty") { WrappedCopy.hubEmpty(it) })
        add(Situation("wrapped.cover.month") { WrappedCopy.coverTitle(CopyPeriod.MONTH, LocalDate.of(2026, 10, 1), it) })
        add(Situation("wrapped.cover.week") { WrappedCopy.coverTitle(CopyPeriod.WEEK, LocalDate.of(2026, 10, 26), it) })
        add(Situation("wrapped.cover.year") { WrappedCopy.coverTitle(CopyPeriod.YEAR, LocalDate.of(2026, 1, 1), it) })
        add(Situation("wrapped.cover.all") { WrappedCopy.coverTitle(CopyPeriod.ALL, LocalDate.of(2026, 3, 3), it) })
        add(Situation("wrapped.total.zero") { WrappedCopy.total(0, 31, it) })
        add(Situation("wrapped.total.tiny") { WrappedCopy.total(3, 31, it) })
        add(Situation("wrapped.total") { WrappedCopy.total(4_812, 31, it) })
        add(Situation("wrapped.time.days") { WrappedCopy.time(29 * hour, it) })
        add(Situation("wrapped.time.hours", maxDistinct = 8) { WrappedCopy.time(5 * hour, it) })
        add(Situation("wrapped.time.minutes") { WrappedCopy.time(47 * min, it) })
        add(Situation("wrapped.creator") { WrappedCopy.creator("pepito", 312, it) })
        add(Situation("wrapped.creator.none") { WrappedCopy.noCreator(it) })
        add(Situation("wrapped.category") { WrappedCopy.category(Category.HUMOR, 0.34, it) })
        add(Situation("wrapped.category.otros") { WrappedCopy.category(Category.OTROS, 0.6, it) })
        add(Situation("wrapped.worstDay") { WrappedCopy.worstDay(LocalDate.of(2026, 10, 14), 612, it) })
        add(Situation("wrapped.platforms.single") { WrappedCopy.platforms(listOf(Platform.TIKTOK to 1.0), it) })
        add(
            Situation("wrapped.platforms.top") {
                WrappedCopy.platforms(listOf(Platform.TIKTOK to 0.61, Platform.INSTAGRAM to 0.25, Platform.YOUTUBE to 0.14), it)
            },
        )
        add(
            Situation("wrapped.platforms.balanced") {
                WrappedCopy.platforms(listOf(Platform.TIKTOK to 0.4, Platform.INSTAGRAM to 0.35, Platform.YOUTUBE to 0.25), it)
            },
        )
        add(Situation("wrapped.closing.open") { WrappedCopy.closing(CopyPeriod.MONTH, LocalDate.of(2026, 11, 1), false, it) })
        add(Situation("wrapped.closing.month") { WrappedCopy.closing(CopyPeriod.MONTH, LocalDate.of(2026, 10, 1), true, it) })
        add(Situation("wrapped.closing.week") { WrappedCopy.closing(CopyPeriod.WEEK, LocalDate.of(2026, 10, 26), true, it) })
        add(Situation("wrapped.closing.year") { WrappedCopy.closing(CopyPeriod.YEAR, LocalDate.of(2025, 1, 1), true, it) })
        // Compartir, onboarding, avisos, estados
        add(Situation("share.footer") { ShareCopy.footer(it) })
        add(Situation("onboarding.connected") { OnboardingCopy.connected(it) })
        add(Situation("onboarding.notYet") { OnboardingCopy.notYet(it) })
        add(Situation("notif.daily.zero") { NotificationCopy.daily(0, 0, it).text })
        add(Situation("notif.daily.first") { NotificationCopy.daily(3, 2 * min, it).text })
        add(Situation("notif.daily.normal") { NotificationCopy.daily(50, 20 * min, it).text })
        add(Situation("notif.daily.high", maxDistinct = 6) { NotificationCopy.daily(212, 100 * min, it).text })
        add(Situation("notif.daily.extreme", maxDistinct = 6) { NotificationCopy.daily(400, 4 * hour, it).text })
        add(Situation("notif.daily.over") { NotificationCopy.daily(212, 47 * min, it, goal = 150).text })
        add(Situation("notif.daily.under") { NotificationCopy.daily(90, 30 * min, it, goal = 150).text })
        add(Situation("notif.daily.late") { NotificationCopy.daily(212, 47 * min, it, yesterday = true).text })
        add(Situation("notif.daily.late.zero") { NotificationCopy.daily(0, 0, it, yesterday = true).text })
        add(Situation("notif.monthly.title") { NotificationCopy.monthly(YearMonth.of(2026, 10), 4_812, 29 * hour, it).title })
        add(Situation("notif.monthly") { NotificationCopy.monthly(YearMonth.of(2026, 10), 4_812, 29 * hour, it).text })
        add(Situation("notif.monthly.zero") { NotificationCopy.monthly(YearMonth.of(2026, 10), 0, 0, it).text })
        add(Situation("state.loading") { StateCopy.loading(it) })
        add(Situation("state.empty") { StateCopy.empty(it) })
        add(Situation("state.error") { StateCopy.error(it) })
        add(Situation("eq.sentence") { Equivalences.BOOK.value(84.0).sentence(it) })
    }

    /** Todas las constantes `String` de los objetos de copy (vía reflexión). */
    private fun constants(): List<Sample> {
        val objects = listOf(
            Labels::class.java, Buttons::class.java, A11y::class.java, TodayCopy::class.java, StatsCopy::class.java,
            DayCopy::class.java, WrappedCopy::class.java, ShareCopy::class.java, OnboardingCopy::class.java,
            DisclosureCopy::class.java, SettingsCopy::class.java, NotificationCopy::class.java, StateCopy::class.java,
            WidgetCopy::class.java, CalibrationCopy::class.java, QuickSendCopy::class.java,
        )
        return objects.flatMap { cls ->
            cls.declaredFields
                .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
                .map { f ->
                    f.isAccessible = true
                    val name = "${cls.simpleName}.${f.name}"
                    val upper = cls == Labels::class.java || cls == Buttons::class.java
                    Sample(name, f.get(null) as String, upper = upper, checkNumbers = name !in VERSION_NUMBERS)
                }
        }
    }

    /** Textos con números de versión («SIL Open Font License 1.1»), que llevan punto y no coma. */
    private val VERSION_NUMBERS = setOf("SettingsCopy.FONTS_LICENSE")

    private fun samples(): List<Sample> = buildList {
        addAll(constants())

        // Hoy: rejilla completa de recuentos, tiempos, objetivos, primer día y semillas
        for (c in counts) for (ms in durations) for (g in goals) for (first in listOf(false, true)) for (s in seeds) {
            add(Sample("TodayCopy.headline($c, $ms, $s, $g, $first)", TodayCopy.headline(c, ms, s, g, first)))
        }
        for (c in counts) for (ms in durations) for (g in goals) for (s in seeds) {
            add(Sample("DayCopy.quote($c, $ms, $s, $g)", DayCopy.quote(c, ms, s, g)))
            val n = NotificationCopy.daily(c, ms, s, g)
            add(Sample("NotificationCopy.daily.title($c)", n.title))
            add(Sample("NotificationCopy.daily.text($c, $ms, $g)", n.text))
        }
        for (c in counts) for (g in listOf(1, 5, 150, 2_000)) {
            add(Sample("TodayCopy.goalStatus($c, $g)", TodayCopy.goalStatus(c, g), upper = true))
            add(Sample("TodayCopy.goalStatusDescription($c, $g)", TodayCopy.goalStatusDescription(c, g)))
            add(Sample("TodayCopy.goalLabel($g)", TodayCopy.goalLabel(g), upper = true))
        }
        for (c in counts) for (ms in durations) {
            add(Sample("TodayCopy.screenTime($ms, $c)", TodayCopy.screenTime(ms, c), upper = true))
            add(Sample("TodayCopy.platformValue", TodayCopy.platformValue(c, ms), upper = true))
            add(Sample("TodayCopy.sinceBand", TodayCopy.sinceBand(LocalDate.of(2026, 3, 3), today, c.toLong(), ms), upper = true))
            add(Sample("TodayCopy.sinceBandDescription", TodayCopy.sinceBandDescription(LocalDate.of(2025, 3, 3), today, c.toLong(), ms)))
            add(Sample("WidgetCopy.description", WidgetCopy.description(c, ms)))
            add(Sample("WidgetCopy.time", WidgetCopy.time(ms), upper = true))
            add(Sample("SettingsCopy.deleteDialogBody", SettingsCopy.deleteDialogBody(c.toLong(), ms)))
            add(Sample("StatsCopy.summaryLine", StatsCopy.summaryLine(c.toLong(), ms, c / 7.0, if (c == 0) 0 else ms / c), upper = true))
            for (s in seeds) {
                add(Sample("WrappedCopy.total($c)", WrappedCopy.total(c.toLong(), 31, s)))
                add(Sample("WrappedCopy.time($ms)", WrappedCopy.time(ms, s)))
                add(Sample("NotificationCopy.monthly($c, $ms)", NotificationCopy.monthly(YearMonth.of(2026, 10), c.toLong(), ms, s).text))
            }
        }
        for (c in counts) {
            add(Sample("TodayCopy.counterLabel($c)", TodayCopy.counterLabel(c), upper = true))
            add(Sample("TodayCopy.counterDescription($c)", TodayCopy.counterDescription(c)))
            add(Sample("TodayCopy.peakHour", TodayCopy.peakHour(23, c), upper = true))
            add(Sample("StatsCopy.peakHourValue", StatsCopy.peakHourValue(1, c), upper = true))
            add(Sample("StatsCopy.streakValue($c)", StatsCopy.streakValue(c), upper = true))
            add(Sample("StatsCopy.activeDaysValue", StatsCopy.activeDaysValue(c, 10_000), upper = true))
            add(Sample("StatsCopy.barDescription", StatsCopy.barDescription(today, c, isMax = true)))
            add(Sample("StatsCopy.hourDescription", StatsCopy.hourDescription(1, c, isMax = true)))
            add(Sample("StatsCopy.heatCellDescription", StatsCopy.heatCellDescription(today, c)))
            add(Sample("StatsCopy.dailyBarsSummary", StatsCopy.dailyBarsSummary(c.toLong(), today, c)))
            add(Sample("SettingsCopy.exportDone($c)", SettingsCopy.exportDone(c)))
            add(Sample("SettingsCopy.calibrationTapsLeft($c)", SettingsCopy.calibrationTapsLeft(c)))
            add(Sample("CalibrationCopy.events($c)", CalibrationCopy.events(c), upper = true))
            add(Sample("DayCopy.rankLine($c)", DayCopy.rankLine(c, 10_000) ?: "—", upper = true))
            for (s in seeds) {
                add(Sample("WrappedCopy.creator($c)", WrappedCopy.creator("@pepito", c, s)))
                add(Sample("WrappedCopy.worstDay($c)", WrappedCopy.worstDay(today, c, s)))
                for (best in listOf(0, 1, 2, 12)) for (g in listOf(1, 150, 2_000)) {
                    add(Sample("StatsCopy.streakQuote($c, $best)", StatsCopy.streakQuote(c.coerceAtMost(30), best, g, s)))
                }
            }
        }
        for (d in DayOfWeek.entries) for (avg in listOf(0.0, 1.0, 3.24, 240.0, 12_345.6)) for (s in seeds) {
            add(Sample("StatsCopy.weakDayQuote($d, $avg)", StatsCopy.weakDayQuote(d, avg, s)))
            add(Sample("StatsCopy.weakDayValue($d, $avg)", StatsCopy.weakDayValue(d, avg), upper = true))
            for (untilNow in listOf(true, false)) {
                for (todayCount in listOf(0.0, 1.0, 50.0, 134.0, 300.0)) for (avgOrNull in listOf(null, 0.0, avg)) {
                    val line = TodayCopy.comparison(todayCount, avgOrNull, d, untilNow)
                    add(Sample("TodayCopy.comparison.text", line.text, upper = true))
                    add(Sample("TodayCopy.comparison.description", line.description))
                }
            }
        }
        for (h in -1..24) for (s in seeds) add(Sample("StatsCopy.peakHourQuote($h)", StatsCopy.peakHourQuote(h, s)))
        for (p in CopyPeriod.entries) {
            add(Sample("StatsCopy.tab($p)", StatsCopy.tab(p), upper = true))
            add(Sample("WrappedCopy.coverLabel($p)", WrappedCopy.coverLabel(p, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), upper = true))
            add(Sample("ShareCopy.periodLine($p)", ShareCopy.periodLine(p, LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)), upper = true))
            add(Sample("ShareCopy.fileName($p)", ShareCopy.fileName(p, LocalDate.of(2026, 10, 27)), checkNumbers = false))
            add(Sample("ShareCopy.shareText($p)", ShareCopy.shareText(p, LocalDate.of(2026, 10, 1), 4_812, 29 * hour), allowEmoji = true))
            add(Sample("ShareCopy.imageDescription($p)", ShareCopy.imageDescription(p, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 4_812, 29 * hour)))
            for (s in seeds) {
                add(Sample("WrappedCopy.coverTitle($p)", WrappedCopy.coverTitle(p, LocalDate.of(2026, 10, 1), s)))
                for (finished in listOf(true, false)) {
                    add(Sample("WrappedCopy.closing($p, $finished)", WrappedCopy.closing(p, LocalDate.of(2026, 12, 1), finished, s)))
                }
                for (current in listOf(true, false)) add(Sample("StatsCopy.emptyPeriod($p)", StatsCopy.emptyPeriod(p, current, s)))
                for ((cur, prev) in listOf(0.0 to null, 0.0 to 0.0, 5.0 to 0.0, 112.0 to 100.0, 88.0 to 100.0, 101.0 to 100.0, 300.0 to 100.0, 50.0 to 100.0, 0.0 to 100.0)) {
                    StatsCopy.trend(cur, prev, p)?.let {
                        add(Sample("StatsCopy.trend.text($p)", it.text, upper = true))
                        add(Sample("StatsCopy.trend.description($p)", it.description))
                    }
                    StatsCopy.trendQuote(cur, prev, p, s)?.let { add(Sample("StatsCopy.trendQuote($p, $cur, $prev)", it)) }
                }
            }
        }
        for (m in 1..12) {
            val ym = YearMonth.of(2026, m)
            add(Sample("StatsCopy.monthLabel($m)", StatsCopy.monthLabel(ym), upper = true))
            for (s in seeds) add(Sample("WrappedCopy.hubHeadline($m)", WrappedCopy.hubHeadline(ym, s)))
        }
        add(Sample("StatsCopy.weekLabel", StatsCopy.weekLabel(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 2)), upper = true))
        add(Sample("StatsCopy.weekLabel(año)", StatsCopy.weekLabel(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)), upper = true))
        add(Sample("StatsCopy.allLabel", StatsCopy.allLabel(LocalDate.of(2026, 3, 3)), upper = true))
        add(Sample("StatsCopy.allLabel(null)", StatsCopy.allLabel(null), upper = true))
        for (date in listOf(today, today.minusDays(1), today.minusDays(9), LocalDate.of(2025, 12, 31))) {
            add(Sample("DayCopy.title($date)", DayCopy.title(date, today)))
            add(Sample("DayCopy.label($date)", DayCopy.label(date, today), upper = true))
            add(Sample("TodayCopy.header($date)", TodayCopy.header(date), upper = true))
        }
        add(Sample("DayCopy.firstLastLine", DayCopy.firstLastLine(8, 12, 1, 47), upper = true))
        for (cat in Category.entries) for (f in listOf(0.0, 0.004, 0.34, 1.0)) for (s in seeds) {
            add(Sample("WrappedCopy.category($cat, $f)", WrappedCopy.category(cat, f, s)))
        }
        val platformMixes = listOf(
            emptyList(),
            listOf(Platform.YOUTUBE to 1.0),
            listOf(Platform.TIKTOK to 0.7, Platform.INSTAGRAM to 0.3),
            listOf(Platform.INSTAGRAM to 0.5, Platform.TIKTOK to 0.5),
            listOf(Platform.TIKTOK to 0.34, Platform.INSTAGRAM to 0.33, Platform.YOUTUBE to 0.33),
        )
        for (mix in platformMixes) for (s in seeds) add(Sample("WrappedCopy.platforms", WrappedCopy.platforms(mix, s)))
        for (s in seeds) add(Sample("WrappedCopy.noCreator", WrappedCopy.noCreator(s)))

        // Compartir (aquí SÍ hay emojis)
        val eqs = Equivalences.pick(29 * hour, 3, 7)
        ShareCopy.lines(4_812, 29 * hour, 31, LocalDate.of(2026, 10, 14), 612, 23, "pepito", Category.HUMOR, 0.34).forEach {
            add(Sample("ShareCopy.lines.label", it.label, allowEmoji = true))
            add(Sample("ShareCopy.lines.value", it.value))
        }
        ShareCopy.equivalenceLines(eqs).forEach {
            add(Sample("ShareCopy.equivalenceLines.label", it.label, allowEmoji = true))
            add(Sample("ShareCopy.equivalenceLines.value", it.value))
        }
        add(Sample("ShareCopy.totalLine", ShareCopy.totalLine(29 * hour).value, upper = true))
        eqs.forEach { add(Sample("WrappedCopy.equivalenceLine", WrappedCopy.equivalenceLine(it))) }

        // Onboarding, divulgación y ajustes (listas)
        OnboardingCopy.PRIVACY_POINTS.forEach {
            add(Sample("OnboardingCopy.PRIVACY_POINTS.label", it.label, upper = true))
            add(Sample("OnboardingCopy.PRIVACY_POINTS.text", it.text))
        }
        (OnboardingCopy.GUIDE_STEPS + OnboardingCopy.RESTRICTED_STEPS).forEach { add(Sample("OnboardingCopy.step", it.text)) }
        for (h in 0..23) add(Sample("OnboardingCopy.notifyDaily($h)", OnboardingCopy.notifyDaily(h, 30)))
        add(Sample("OnboardingCopy.goalRange", OnboardingCopy.goalRange(5, 2_000)))
        listOf(null, 0, 1, 150, 2_000).forEach { add(Sample("OnboardingCopy.goalChosen($it)", OnboardingCopy.goalChosen(it))) }
        add(Sample("OnboardingCopy.stepLabel", OnboardingCopy.stepLabel(3), upper = true))
        DisclosureCopy.fullText.forEach { add(Sample("DisclosureCopy.fullText", it)) }
        DisclosureCopy.SECTIONS.forEach { add(Sample("DisclosureCopy.SECTIONS.label", it.label, upper = true)) }
        SettingsCopy.SECTIONS.forEach {
            add(Sample("SettingsCopy.section.title", it.title, upper = true))
            add(Sample("SettingsCopy.section.description", it.description))
        }
        SettingsCopy.PRIVACY_PARAGRAPHS.forEach { add(Sample("SettingsCopy.PRIVACY_PARAGRAPHS", it)) }
        listOf(null, 0, 150, 2_000).forEach { add(Sample("SettingsCopy.goalValue($it)", SettingsCopy.goalValue(it), upper = true)) }
        add(Sample("SettingsCopy.dailyDescription", SettingsCopy.dailyDescription(1, 5)))
        add(Sample("SettingsCopy.versionLabel", SettingsCopy.versionLabel("1.0.0"), checkNumbers = false, upper = true))
        add(Sample("SettingsCopy.exportFileName", SettingsCopy.exportFileName(today), checkNumbers = false))
        listOf(emptyList(), listOf(Platform.TIKTOK), Platform.entries.toList()).forEach {
            add(Sample("SettingsCopy.serviceConnected", SettingsCopy.serviceConnected(it)))
        }
        add(Sample("SettingsCopy.serviceStatus", SettingsCopy.serviceStatus(true), upper = true))
        add(Sample("NotificationCopy.serviceLost.title", NotificationCopy.serviceLost.title))
        add(Sample("NotificationCopy.serviceLost.text", NotificationCopy.serviceLost.text))
        add(Sample("A11y.slide", A11y.slide(3, 10)))
        // Envío rápido (Ajustes y pastillas sobre los reels)
        add(Sample("SettingsCopy.quickSendFull", SettingsCopy.quickSendFull(3)))
        listOf(true, false).forEach { add(Sample("SettingsCopy.quickSendPinDescription", SettingsCopy.quickSendPinDescription("Los del piso", it))) }
        add(Sample("QuickSendCopy.bubbleDescription", QuickSendCopy.bubbleDescription("Los del piso")))
        add(Sample("QuickSendCopy.sendingDescription", QuickSendCopy.sendingDescription("Los del piso")))
        add(Sample("QuickSendCopy.sentDescription", QuickSendCopy.sentDescription("Los del piso")))
        add(Sample("QuickSendCopy.failedDescription", QuickSendCopy.failedDescription("Los del piso")))
        listOf(QuickSendCopy.ASK_TURN_OFF, QuickSendCopy.ASK_KEEP, SettingsCopy.QUICK_SEND_RELOCATE).forEach {
            add(Sample("QuickSendCopy.boton", it, upper = true))
        }
    }

    @Test
    fun `todos los textos cumplen las reglas de estilo`() {
        val problems = samples().mapNotNull { s ->
            val v = CopyRules.violations(s.text, s.allowEmoji, s.checkNumbers).toMutableList()
            if (s.upper && s.text != Format.upper(s.text)) v += "debería ir en MAYÚSCULAS"
            if (v.isEmpty()) null else "${s.source}: «${CopyRules.plain(s.text)}» → $v"
        }.distinct()
        if (problems.isNotEmpty()) fail("${problems.size} textos incumplen las reglas:\n" + problems.take(60).joinToString("\n"))
    }

    @Test
    fun `cada situación tiene entre 3 y 6 variantes`() {
        val wrong = situations().mapNotNull { sit ->
            val distinct = (0L until 60L).map(sit.text).toSet()
            if (distinct.size in 3..sit.maxDistinct) null else "${sit.name}: ${distinct.size} variantes"
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `cada variante cumple las reglas`() {
        val problems = situations().flatMap { sit ->
            (0L until 60L).map(sit.text).toSet().mapNotNull { text ->
                CopyRules.violations(text).takeIf { it.isNotEmpty() }?.let { "${sit.name}: «${CopyRules.plain(text)}» → $it" }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `la elección es determinista y no repite dos días seguidos`() {
        for (sit in situations()) {
            for (seed in 0L until 120L) {
                assertEquals("${sit.name} no es determinista", sit.text(seed), sit.text(seed))
                assertNotEquals("${sit.name} repite el día $seed → ${seed + 1}", sit.text(seed), sit.text(seed + 1))
            }
        }
    }

    @Test
    fun `solo la imagen compartible lleva emojis`() {
        val withEmoji = samples().filter { !it.allowEmoji && CopyRules.containsEmoji(it.text) }
        assertTrue(withEmoji.joinToString { it.source }, withEmoji.isEmpty())
        val ticket = ShareCopy.lines(10, 60_000, 2)
        assertTrue(ticket.all { CopyRules.containsEmoji(it.label) })
    }

    @Test
    fun `strings xml cumple las reglas`() {
        val file = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }
        val strings = Regex("<string name=\"([^\"]+)\">([^<]*)</string>").findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
        val required = listOf(
            "app_name", "accessibility_service_label", "accessibility_service_description", "accessibility_service_summary",
            "accessibility_service_intro", "notification_channel_name", "notification_channel_daily_name",
            "notification_channel_daily_description", "notification_channel_monthly_name",
            "notification_channel_monthly_description", "widget_description",
        )
        required.forEach { assertTrue("Falta $it", it in strings) }
        assertEquals("Reelómetro", strings["app_name"])
        val description = strings.getValue("accessibility_service_description")
        listOf("TikTok", "Instagram Reels", "YouTube Shorts", "móvil").forEach {
            assertTrue("La descripción del servicio debe mencionar «$it»", it in description)
        }
        val problems = strings.mapNotNull { (k, v) -> CopyRules.violations(v).takeIf { it.isNotEmpty() }?.let { "$k → $it" } }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
