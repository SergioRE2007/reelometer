package com.reelometro.app.ui.day

import androidx.compose.runtime.Immutable
import com.reelometro.app.copy.DayCopy
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.StatsCopy
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.stats.DayRank
import com.reelometro.app.stats.DayStats
import com.reelometro.app.ui.today.CreatorRow
import com.reelometro.app.ui.today.DayParts
import com.reelometro.app.ui.today.HeroModel
import com.reelometro.app.ui.today.HoursModel
import com.reelometro.app.ui.today.PlatformsModel
import com.reelometro.app.ui.today.RankRow
import java.time.LocalDate

/** Estado del **detalle de un día**: cargando, vacío (sin vídeos), error o contenido. */
sealed interface DayUiState {
    val top: DayTop

    /** Mientras carga, el marcador ya está (en blanco) para recibir la cifra que llega de Hoy. */
    data class Loading(override val top: DayTop, val hero: HeroModel) : DayUiState

    data class Error(override val top: DayTop, val message: String) : DayUiState

    data class Empty(override val top: DayTop, val hero: HeroModel) : DayUiState

    /** [rank] = «N.º 3 DE 64 DÍAS CONTADOS» (llega aparte: puede tardar algo más). */
    data class Content(
        override val top: DayTop,
        val hero: HeroModel,
        val sections: DaySections,
        val rank: String?,
    ) : DayUiState
}

/** «AYER · LUNES 2 NOV» + «Ayer, lunes 2 de noviembre». */
@Immutable
data class DayTop(val epochDay: Long, val label: String, val title: String)

/** 01 hora a hora · 02 plataformas · 03 categorías · 04 creadores. */
@Immutable
data class DaySections(
    val hoursTitle: String,
    val hours: HoursModel,
    val platforms: PlatformsModel?,
    val categories: List<RankRow>,
    val creators: List<CreatorRow>,
)

/** Mapeo puro de los datos a [DayUiState]. */
object DayMapper {
    const val CREATOR_ROWS = 10

    fun top(epochDay: Long, today: LocalDate): DayTop {
        val date = LocalDate.ofEpochDay(epochDay)
        return DayTop(epochDay, DayCopy.label(date, today), DayCopy.title(date, today))
    }

    fun loading(epochDay: Long, today: LocalDate): DayUiState = DayUiState.Loading(
        top(epochDay, today),
        HeroModel(epochDay = epochDay, label = Labels.VIDEOS, count = null, description = StateCopy.LOADING_DESCRIPTION),
    )

    fun error(epochDay: Long, today: LocalDate): DayUiState =
        DayUiState.Error(top(epochDay, today), StateCopy.error(Variants.daySeed(today)))

    /**
     * @param rank puesto del día entre todos los contados y primer registro (SQL); `null` mientras
     *   no ha llegado o si ha fallado.
     */
    fun map(epochDay: Long, today: LocalDate, day: DayStats, rank: DayRank?): DayUiState {
        val date = day.date
        val top = top(epochDay, today)
        val seed = Variants.daySeed(date)
        val count = day.totals.count
        val firstDay = rank?.firstDay
        val beforeHistory = firstDay != null && date.isBefore(firstDay)
        val quote = when {
            day.isToday -> TodayCopy.headline(
                count, day.totals.totalMs, seed, goal = day.goal?.goal, firstDay = firstDay == null || firstDay == date,
            )
            count == 0 && beforeHistory -> StatsCopy.beforeHistory(firstDay, seed)
            else -> DayCopy.quote(count, day.totals.totalMs, seed, goal = day.goal?.goal)
        }
        val hero = HeroModel(
            epochDay = epochDay,
            label = TodayCopy.counterLabel(count),
            count = count.toLong(),
            description = TodayCopy.counterDescription(count),
            goal = if (beforeHistory) null else DayParts.goal(day),
            screenTime = if (beforeHistory) null else DayParts.screenTime(day.totals),
            quote = TodayCopy.quoted(quote),
            comparison = if (beforeHistory) null else DayParts.comparison(day),
        )
        if (day.isEmpty) return DayUiState.Empty(top, hero)

        val sections = DaySections(
            hoursTitle = if (day.isToday) TodayCopy.SECTION_HOURS else DayCopy.SECTION_HOURS,
            hours = DayParts.hours(day),
            platforms = DayParts.platforms(day.platforms) { TodayCopy.platformValue(it.count, it.totalMs) },
            categories = DayParts.categories(day.categories, DayParts.CATEGORY_ROWS) { TodayCopy.rankValue(it.count, it.totalMs) },
            creators = DayParts.creators(day.creators, CREATOR_ROWS) { TodayCopy.rankValue(it.count, it.totalMs) },
        )
        return DayUiState.Content(top, hero, sections, rankLine(day, rank))
    }

    /**
     * «N.º 3 DE 64 DÍAS CONTADOS» a partir del puesto calculado en SQL (empates: comparten puesto).
     * `null` si no ha llegado, si el día no tiene vídeos o si el puesto es de otro día.
     */
    internal fun rankLine(day: DayStats, rank: DayRank?): String? {
        if (rank == null || day.isEmpty || rank.date != day.date || !rank.isRanked) return null
        return DayCopy.rankLine(rank.rank, rank.totalDays)
    }
}
