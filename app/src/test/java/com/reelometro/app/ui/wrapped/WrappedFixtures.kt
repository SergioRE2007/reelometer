package com.reelometro.app.ui.wrapped

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.stats.CreatorRank
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import com.reelometro.app.stats.StatsMath
import com.reelometro.app.stats.Totals
import java.time.LocalDate

/** Martes 3 de noviembre de 2026: «el mes pasado» es octubre. */
val TODAY: LocalDate = LocalDate.of(2026, 11, 3)

/** Primer registro de la app en los datos de prueba. */
val FIRST_DAY: LocalDate = LocalDate.of(2026, 8, 3)

/**
 * Un periodo de prueba construido con la misma lógica pura que usa el repositorio
 * ([StatsMath.period]), sin Room.
 *
 * @param perDay vídeos de cada día (0 = día sin vídeos).
 */
fun periodStats(
    ref: PeriodRef = PeriodRef.LAST_MONTH,
    today: LocalDate = TODAY,
    firstDay: LocalDate? = FIRST_DAY,
    perDay: (LocalDate) -> Int = { 120 + (it.dayOfMonth * 37) % 90 },
    msPerVideo: Long = 21_000,
    creator: String? = "pepito",
    categories: Boolean = true,
    platforms: Boolean = true,
    peakHour: Int? = 22,
): PeriodStats {
    val elapsed = ref.elapsed(today, firstDay)
    val byDay = elapsed.dates()
        .filter { firstDay != null && !it.isBefore(firstDay) }
        .associateWith { d -> perDay(d).let { c -> Totals(c, c * msPerVideo, c / 50) } }
        .filterValues { it.count > 0 }
    val total = byDay.values.fold(Totals.ZERO, Totals::plus)
    val some = total.count > 0
    fun part(fraction: Double) = Totals((total.count * fraction).toInt(), (total.totalMs * fraction).toLong())
    return StatsMath.period(
        ref = ref,
        today = today,
        nowMinute = 10 * 60,
        firstDay = firstDay,
        goal = null,
        byDay = byDay,
        byHour = if (some && peakHour != null) mapOf(peakHour to part(0.6), 13 to part(0.4)) else emptyMap(),
        byPlatform = if (some && platforms) mapOf(Platform.TIKTOK to part(0.55), Platform.INSTAGRAM to part(0.3), Platform.YOUTUBE to part(0.15)) else emptyMap(),
        byCategory = if (some && categories) {
            mapOf(Category.HUMOR to part(0.4), Category.COMIDA to part(0.25), Category.DEPORTE to part(0.15), Category.OTROS to part(0.2))
        } else {
            emptyMap()
        },
        creators = if (some && creator != null) listOf(CreatorRank(1, creator, Platform.TIKTOK, 312, 312 * msPerVideo)) else emptyList(),
        previous = null,
        history = emptyMap(),
    )
}
