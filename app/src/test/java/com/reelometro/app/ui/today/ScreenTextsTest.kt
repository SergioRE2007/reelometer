package com.reelometro.app.ui.today

import com.reelometro.app.copy.CopyPeriod
import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.CopyRules.plain
import com.reelometro.app.copy.DayCopy
import com.reelometro.app.copy.Direction
import com.reelometro.app.copy.StatsCopy
import com.reelometro.app.copy.TodayCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * Los textos que se añadieron para Hoy, Día y Estadísticas: casan con los que ya había y cumplen
 * las reglas de DESIGN §8 (`CopyRules`), con cifras extremas.
 */
class ScreenTextsTest {

    private val counts = listOf(0, 1, 2, 9, 99, 212, 1_234, 10_000)
    private val durations = listOf(0L, 15_000L, MIN, 47 * MIN, 72 * MIN, 3 * HOUR, 61 * HOUR, 5_000 * HOUR)
    private val dates = listOf(TODAY, TODAY.minusDays(1), LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 1))

    @Test
    fun `la línea de tiempo y la banda se parten en dos piezas sin cambiar el texto`() {
        for (ms in durations) for (shares in counts) {
            assertEquals(TodayCopy.screenTime(ms, shares), "${TodayCopy.screenTimeValue(ms)} ${TodayCopy.screenTimeRest(shares)}")
        }
        for (first in dates) for (n in counts) for (ms in durations) {
            assertEquals(
                TodayCopy.sinceBand(first, TODAY, n.toLong(), ms),
                "${TodayCopy.sinceBandKicker(first, TODAY)}: ${TodayCopy.sinceBandValue(n.toLong(), ms)}",
            )
        }
    }

    @Test
    fun `piezas concretas`() {
        assertEquals("«De momento, ganas tú.»", TodayCopy.quoted("De momento, ganas tú."))
        assertEquals("47 minutos de pantalla. 4 vídeos compartidos.", plain(TodayCopy.screenTimeDescription(47 * MIN, 4)))
        assertEquals("Un minuto de pantalla.", plain(TodayCopy.screenTimeDescription(MIN)))
        assertEquals("↑", TodayCopy.arrow(Direction.UP))
        assertEquals("↓", TodayCopy.arrow(Direction.DOWN))
        assertNull(TodayCopy.arrow(Direction.SAME))
        assertEquals("41 vídeos\n23:00–24:00", plain(TodayCopy.hourTooltip(23, 41)))
        assertEquals("TikTok, 120 vídeos; Reels, 1 vídeo.", plain(TodayCopy.platformsDescription(listOf("TikTok" to 120, "Reels" to 1))))
        assertEquals("+62 PASADO", plain(DayCopy.goalStatus(212, 150)))
        assertEquals("EN EL LÍMITE", DayCopy.goalStatus(150, 150))
        assertEquals("38 POR DEBAJO", plain(DayCopy.goalStatus(112, 150)))
        assertEquals("1 vídeo por debajo del objetivo de 150", plain(DayCopy.goalStatusDescription(149, 150)))
        assertEquals("212 vídeos\nmar 3 nov", plain(StatsCopy.dayTooltip(TODAY, 212)))
        assertEquals("MAR 3 NOV · 412", plain(StatsCopy.worstDayValue(TODAY, 412, TODAY)))
        assertEquals("LUN 29 DIC 2025 · 7", plain(StatsCopy.worstDayValue(LocalDate.of(2025, 12, 29), 7, TODAY)))
        assertEquals("1.204 · 34 %", plain(StatsCopy.countShareValue(1_204, 0.34)))
        assertEquals("29 DIC 2025 – 4 ENE 2026", plain(StatsCopy.navigatorLabel(CopyPeriod.WEEK, LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4), null)))
        assertEquals("NOVIEMBRE 2026", StatsCopy.navigatorLabel(CopyPeriod.MONTH, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null))
        assertEquals("DESDE EL 3 MAR 2026", StatsCopy.navigatorLabel(CopyPeriod.ALL, LocalDate.of(2026, 3, 3), TODAY, LocalDate.of(2026, 3, 3)))
    }

    @Test
    fun `todos los textos nuevos cumplen las reglas de estilo`() {
        val samples = buildList {
            for (n in counts) for (ms in durations) {
                add(TodayCopy.screenTimeValue(ms))
                add(TodayCopy.screenTimeRest(n))
                add(TodayCopy.screenTimeDescription(ms, n))
                add(TodayCopy.sinceBandValue(n.toLong(), ms))
                add(TodayCopy.rankValue(n, ms))
            }
            for (first in dates) add(TodayCopy.sinceBandKicker(first, TODAY))
            for (h in 0..23) for (n in counts) {
                add(TodayCopy.hourTooltip(h, n))
                add(TodayCopy.hoursDescription(h, n))
            }
            add(TodayCopy.hoursDescription(null, 0))
            add(TodayCopy.platformsDescription(emptyList()))
            add(TodayCopy.platformsDescription(counts.map { "TikTok" to it }))
            add(TodayCopy.SINCE_BAND_ACTION)
            for (goal in listOf(1, 5, 150, 2_000)) for (n in counts) {
                add(DayCopy.goalStatus(n, goal))
                add(DayCopy.goalStatusDescription(n, goal))
            }
            add(DayCopy.SECTION_HOURS)
            for (d in dates) for (n in counts) {
                add(StatsCopy.dayTooltip(d, n))
                add(StatsCopy.weekTooltip(d, n))
                add(StatsCopy.worstDayValue(d, n, TODAY))
                add(StatsCopy.countShareValue(n, 0.004))
                add(StatsCopy.calendarDescription(n, 31))
                add(StatsCopy.countDescription(n.toLong()))
                add(StatsCopy.sharesValue(n))
            }
            for (d in dates) {
                add(StatsCopy.untrackedTooltip(d))
                add(StatsCopy.futureTooltip(d))
                for (p in CopyPeriod.entries) add(StatsCopy.navigatorLabel(p, d, d.plusDays(6), d))
            }
            addAll(
                listOf(
                    StatsCopy.LINE_PEAK_HOUR, StatsCopy.LINE_WEAK_DAY, StatsCopy.LINE_WORST_DAY, StatsCopy.LINE_ACTIVE_DAYS,
                    StatsCopy.LINE_PER_VIDEO, StatsCopy.LINE_SHARES, StatsCopy.LINE_STREAK, StatsCopy.LINE_BEST_STREAK,
                    StatsCopy.LINE_STREAK_NO_GOAL, StatsCopy.BY_WEEK, StatsCopy.OPEN_WORST_DAY, StatsCopy.SET_GOAL_ACTION,
                ),
            )
        }
        // Los tooltips llevan dos líneas: se comprueba cada una.
        val problems = samples.flatMap { it.split('\n') }.mapNotNull { text ->
            CopyRules.violations(text).takeIf { it.isNotEmpty() }?.let { "«${plain(text)}» → $it" }
        }.distinct()
        if (problems.isNotEmpty()) fail("${problems.size} textos incumplen las reglas:\n" + problems.take(40).joinToString("\n"))
    }
}
