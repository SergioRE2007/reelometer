package com.reelometro.app.ui.today

import androidx.compose.runtime.Immutable
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.stats.AllTime
import com.reelometro.app.stats.DayStats
import java.time.LocalDate

/** Estado de la pantalla **Hoy**: cargando, vacío (cero vídeos o sin señal), error o contenido. */
sealed interface TodayUiState {
    val header: TodayHeader

    data class Loading(override val header: TodayHeader) : TodayUiState

    data class Error(override val header: TodayHeader, val message: String) : TodayUiState

    /** Hoy sin vídeos, o el servicio sin señal: marcador («000» o «– – –»), frase o aviso; sin secciones. */
    data class Empty(override val header: TodayHeader, val top: TodayTop) : TodayUiState

    data class Content(override val header: TodayHeader, val top: TodayTop, val sections: TodaySections) : TodayUiState
}

/** «HOY · MARTES 3 NOV»; [epochDay] da la clave del elemento compartido con el detalle. */
@Immutable
data class TodayHeader(val text: String, val epochDay: Long)

/**
 * Lo que va arriba: el marcador y, si toca, el aviso integrado. [canOpenDay] = tocar el marcador
 * abre el detalle (no con las fichas en blanco). [band] es la banda «desde que usas Reelometer».
 */
@Immutable
data class TodayTop(
    val hero: HeroModel,
    val notice: TodayNotice?,
    val band: BandModel?,
    val canOpenDay: Boolean,
)

/** Aviso con acción: conectar el servicio (divulgación + guía) o abrir Ajustes. */
@Immutable
data class TodayNotice(val text: String, val action: String, val target: NoticeTarget)

enum class NoticeTarget { CONNECT_SERVICE, SETTINGS }

/** Banda a sangre: «DESDE EL 3 MAR» + «12.408 VÍDEOS · 61 H». */
@Immutable
data class BandModel(val kicker: String, val text: String)

/** 01 plataformas · 02 hora a hora · 03 categorías · 04 creadores (las vacías no se pintan). */
@Immutable
data class TodaySections(
    val platforms: PlatformsModel?,
    val hours: HoursModel,
    val categories: List<RankRow>,
    val creators: List<CreatorRow>,
)

/** Mapeo puro de los datos a [TodayUiState] (sin Android). */
object TodayMapper {
    /** Creadores que se enseñan en Hoy. */
    const val CREATOR_ROWS = 5

    fun header(date: LocalDate): TodayHeader = TodayHeader(TodayCopy.header(date), date.toEpochDay())

    fun loading(today: LocalDate): TodayUiState = TodayUiState.Loading(header(today))

    fun error(today: LocalDate): TodayUiState =
        TodayUiState.Error(header(today), StateCopy.error(Variants.daySeed(today)))

    /**
     * @param serviceOn el servicio de accesibilidad está activado en los ajustes del sistema.
     */
    fun map(day: DayStats, allTime: AllTime, settings: AppSettings, serviceOn: Boolean): TodayUiState {
        val date = day.date
        val seed = Variants.daySeed(date)
        val header = header(date)
        val epochDay = date.toEpochDay()
        val band = band(allTime)

        if (!serviceOn) {
            // Nunca se ha aceptado la divulgación: se ofrece verla (pantalla de conectar), no los ajustes del sistema.
            val notice = if (settings.disclosureAccepted) {
                TodayNotice(TodayCopy.serviceOff(seed), Buttons.CONNECT, NoticeTarget.CONNECT_SERVICE)
            } else {
                TodayNotice(TodayCopy.serviceNeverEnabled(seed), Buttons.SEE_PERMISSION, NoticeTarget.CONNECT_SERVICE)
            }
            val hero = HeroModel(
                epochDay = epochDay,
                label = TodayCopy.SERVICE_OFF_LABEL,
                count = null,
                description = TodayCopy.SERVICE_OFF_DESCRIPTION,
                alert = true,
            )
            return TodayUiState.Empty(header, TodayTop(hero, notice, band, canOpenDay = false))
        }

        val count = day.totals.count
        val noHistory = allTime.firstDay == null || allTime.firstDay == date
        val headline = TodayCopy.headline(count, day.totals.totalMs, seed, goal = day.goal?.goal, firstDay = noHistory)
        val hero = HeroModel(
            epochDay = epochDay,
            label = TodayCopy.counterLabel(count),
            count = count.toLong(),
            description = TodayCopy.counterDescription(count),
            goal = DayParts.goal(day),
            screenTime = DayParts.screenTime(day.totals),
            quote = TodayCopy.quoted(headline),
            comparison = DayParts.comparison(day),
        )
        val notice = if (settings.enabledPlatforms.isEmpty()) {
            TodayNotice(TodayCopy.noPlatforms(seed), Buttons.OPEN_SETTINGS, NoticeTarget.SETTINGS)
        } else {
            null
        }
        val top = TodayTop(hero, notice, band, canOpenDay = true)
        if (day.isEmpty) return TodayUiState.Empty(header, top)

        val sections = TodaySections(
            platforms = DayParts.platforms(day.platforms) { TodayCopy.platformValue(it.count, it.totalMs) },
            hours = DayParts.hours(day),
            categories = DayParts.categories(day.categories, DayParts.CATEGORY_ROWS) { TodayCopy.rankValue(it.count, it.totalMs) },
            creators = DayParts.creators(day.creators, CREATOR_ROWS) { TodayCopy.rankValue(it.count, it.totalMs) },
        )
        return TodayUiState.Content(header, top, sections)
    }

    /** Sin banda si no hay historial o si empieza hoy (repetiría el marcador). */
    internal fun band(allTime: AllTime): BandModel? {
        val first = allTime.firstDay ?: return null
        if (allTime.isEmpty || !first.isBefore(allTime.today)) return null
        return BandModel(
            kicker = TodayCopy.sinceBandKicker(first, allTime.today),
            text = TodayCopy.sinceBandValue(allTime.totals.count.toLong(), allTime.totals.totalMs),
        )
    }
}
