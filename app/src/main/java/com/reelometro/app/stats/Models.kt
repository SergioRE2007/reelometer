package com.reelometro.app.stats

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import java.time.DayOfWeek
import java.time.LocalDate

// Modelos de estadísticas que consumen las pantallas. Son inmutables y sin Android: llegan
// calculados desde la capa de datos (ReelRepository), fuera del hilo principal.

/** Totales de un rango: vídeos, tiempo de pantalla (ms) y veces que se pulsó «compartir». */
data class Totals(val count: Int, val totalMs: Long, val shares: Int = 0) {
    /** Tiempo medio por vídeo (ms); 0 si no hay vídeos. */
    val avgMsPerVideo: Long get() = if (count == 0) 0 else totalMs / count

    val isEmpty: Boolean get() = count == 0

    operator fun plus(other: Totals): Totals =
        Totals(count + other.count, totalMs + other.totalMs, shares + other.shares)

    companion object {
        val ZERO = Totals(0, 0, 0)
    }
}

/**
 * Un día dentro de un periodo. Los días sin vídeos vienen con ceros.
 *
 * @property tracked el día es igual o posterior al primer registro (la app ya contaba). Los días
 *   anteriores salen a cero y con `tracked = false` para que las gráficas puedan atenuarlos;
 *   no cuentan para medias, rachas ni «día más tranquilo».
 */
data class DayPoint(
    val date: LocalDate,
    val count: Int,
    val totalMs: Long,
    val shares: Int = 0,
    val tracked: Boolean = true,
)

/** Una hora del día (0–23): vídeos que empezaron en esa hora local. */
data class HourPoint(val hour: Int, val count: Int, val totalMs: Long)

/**
 * Media de un día de la semana dentro de un periodo, sobre los días registrados de ese tipo.
 *
 * @property days cuántos días (p. ej. cuántos martes) se han promediado; 0 = sin datos.
 */
data class WeekdayPoint(
    val dayOfWeek: DayOfWeek,
    val averageCount: Double,
    val averageMs: Double,
    val days: Int,
    val totalCount: Int,
    val totalMs: Long,
)

/** Porción de una plataforma. [fraction] es la parte de los vídeos (0–1). */
data class PlatformSlice(val platform: Platform, val count: Int, val totalMs: Long, val fraction: Double)

/** Porción de una categoría. [fraction] es la parte de los vídeos (0–1). */
data class CategorySlice(val category: Category, val count: Int, val totalMs: Long, val fraction: Double)

/** Puesto en el ranking de creadores ([rank] empieza en 1). */
data class CreatorRank(
    val rank: Int,
    val creator: String,
    val platform: Platform,
    val count: Int,
    val totalMs: Long,
)

/** Puesto en el ranking de «a quién envías» ([rank] empieza en 1): persona o grupo de Instagram o TikTok. */
data class RecipientRank(
    val rank: Int,
    val name: String,
    val platform: Platform,
    val count: Int,
)

/**
 * Comparación de un día con la media del mismo día de la semana (los últimos 8 con datos).
 * Para hoy se cuenta **solo hasta el minuto actual** en ambos lados: «+34 % que tu martes medio a esta hora».
 *
 * @property untilMinute minuto local (0–1439) hasta el que se compara; `null` = día completo.
 * @property sampleDays cuántos días de referencia se han promediado (1–8).
 */
data class Comparison(
    val weekday: DayOfWeek,
    val untilMinute: Int?,
    val count: Int,
    val totalMs: Long,
    val averageCount: Double,
    val averageMs: Double,
    val sampleDays: Int,
) {
    /** Variación en vídeos: 0.34 = +34 %. `null` si la media es 0 (no hay porcentaje posible). */
    val countChange: Double? get() = StatsMath.change(count.toDouble(), averageCount)

    /** Variación en tiempo de pantalla. `null` si la media es 0. */
    val timeChange: Double? get() = StatsMath.change(totalMs.toDouble(), averageMs)
}

/** Progreso frente al objetivo diario («como mucho [goal] vídeos al día»). */
data class GoalProgress(val goal: Int, val count: Int) {
    /** Vídeos que quedan hasta el objetivo (0 si ya se ha alcanzado o pasado). */
    val remaining: Int get() = (goal - count).coerceAtLeast(0)

    /** Vídeos por encima del objetivo («+62 PASADO»); 0 si no se ha pasado. */
    val exceededBy: Int get() = (count - goal).coerceAtLeast(0)

    val isExceeded: Boolean get() = count > goal

    /** Proporción consumida (puede pasar de 1). */
    val fraction: Double get() = if (goal <= 0) 0.0 else count.toDouble() / goal
}

/**
 * Racha de días seguidos sin pasarse del objetivo diario (`vídeos ≤ objetivo`).
 *
 * Hoy no cuenta hasta que acaba, **salvo** que ya se haya pasado: entonces la racha actual es 0.
 * Se calcula con el objetivo actual sobre todo el historial (no se guarda el histórico de objetivos).
 *
 * @property todayExceeded hoy ya se ha pasado del objetivo (por eso `current` es 0).
 */
data class Streak(val goal: Int, val current: Int, val best: Int, val todayExceeded: Boolean)

/**
 * Tendencia frente al periodo anterior del mismo tipo.
 *
 * Si el periodo está en curso se compara con el **mismo tramo** del anterior (lunes–martes con
 * lunes–martes, 1–3 nov con 1–3 oct, y el último día solo hasta la misma hora). Si está cerrado,
 * con el anterior entero. Se comparan medias por día registrado, así que meses de distinta
 * longitud o periodos que empiezan antes del primer registro siguen siendo comparables.
 *
 * @property previous tramo del periodo anterior usado como referencia.
 * @property untilMinute minuto local de corte del último día de [previous]; `null` = completo.
 * @property countChange variación en vídeos por día: 0.12 = +12 %; `null` si el anterior es 0.
 */
data class Trend(
    val previous: DateSpan,
    val untilMinute: Int?,
    val previousTotals: Totals,
    val previousTrackedDays: Int,
    val countChange: Double?,
    val timeChange: Double?,
)

/**
 * Todo lo que necesitan Hoy y Detalle del día.
 *
 * @property untilMinute para hoy, minuto local actual (0–1439); `null` en días pasados (completos).
 * @property hours siempre 24 puntos (0–23), con ceros.
 * @property platforms solo las plataformas con vídeos, de más a menos.
 * @property categories solo las categorías con vídeos, de más a menos, «Sin clasificar» al final.
 * @property creators los 10 creadores más vistos.
 * @property comparison frente a la media del mismo día de la semana; `null` si no hay días de referencia.
 * @property goal `null` si no hay objetivo diario.
 */
data class DayStats(
    val date: LocalDate,
    val isToday: Boolean,
    val untilMinute: Int?,
    val totals: Totals,
    val hours: List<HourPoint>,
    val platforms: List<PlatformSlice>,
    val categories: List<CategorySlice>,
    val creators: List<CreatorRank>,
    val peakHour: HourPoint?,
    val comparison: Comparison?,
    val goal: GoalProgress?,
) {
    val isEmpty: Boolean get() = totals.count == 0

    /** Categoría principal sin contar «Sin clasificar». */
    val topCategory: CategorySlice? get() = categories.firstOrNull { it.category != Category.OTROS }

    val topPlatform: PlatformSlice? get() = platforms.firstOrNull()

    val topCreator: CreatorRank? get() = creators.firstOrNull()
}

/**
 * Todo lo que necesitan Estadísticas y el Wrapped para un periodo.
 *
 * @property span intervalo nominal completo (p. ej. la semana entera, aunque acabe en el futuro).
 * @property label «Esta semana», «27 oct – 2 nov», «noviembre de 2026», «2026», «Desde el 3 mar 2025».
 * @property rangeLabel fechas explícitas con año («27 oct – 2 nov 2026»).
 * @property firstDay primer día con registros de toda la app (`null` si no hay ninguno).
 * @property days un punto por día del periodo **hasta hoy** (sin días futuros), con ceros.
 * @property hours 24 puntos con los totales del periodo por hora.
 * @property weekdays 7 puntos, de lunes a domingo, con medias por día registrado.
 * @property creators top 10.
 * @property recipients a quién envías más (top 10; solo Instagram y TikTok y con el ajuste encendido; vacío si no).
 * @property trackedDays días del periodo desde el primer registro hasta hoy (hoy cuenta como día):
 *   es el divisor de las medias.
 * @property activeDays días con al menos un vídeo.
 * @property avgPerDay vídeos por día registrado.
 * @property weakestWeekday el día de la semana con **mayor** media («DÍA DÉBIL»).
 * @property worstDay el día con más vídeos (puede ser hoy).
 * @property quietestDay el día registrado con menos vídeos (puede ser 0), sin contar hoy mientras dure.
 * @property streak racha global a fecha de hoy; `null` sin objetivo.
 * @property trend frente al periodo anterior; `null` en «siempre» o si no hay datos con los que comparar.
 */
data class PeriodStats(
    val ref: PeriodRef,
    val span: DateSpan,
    val label: String,
    val rangeLabel: String,
    val today: LocalDate,
    val firstDay: LocalDate?,
    val totals: Totals,
    val days: List<DayPoint>,
    val hours: List<HourPoint>,
    val weekdays: List<WeekdayPoint>,
    val platforms: List<PlatformSlice>,
    val categories: List<CategorySlice>,
    val creators: List<CreatorRank>,
    val trackedDays: Int,
    val activeDays: Int,
    val avgPerDay: Double,
    val avgMsPerDay: Double,
    val peakHour: HourPoint?,
    val weakestWeekday: WeekdayPoint?,
    val worstDay: DayPoint?,
    val quietestDay: DayPoint?,
    val streak: Streak?,
    val trend: Trend?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val recipients: List<RecipientRank> = emptyList(),
) {
    val isEmpty: Boolean get() = totals.count == 0

    /** Tiempo medio por vídeo (ms). */
    val avgMsPerVideo: Long get() = totals.avgMsPerVideo

    /** El periodo contiene hoy (sus cifras aún pueden cambiar). */
    val inProgress: Boolean get() = today in span

    /** Categoría principal sin contar «Sin clasificar». */
    val topCategory: CategorySlice? get() = categories.firstOrNull { it.category != Category.OTROS }

    val topPlatform: PlatformSlice? get() = platforms.firstOrNull()

    val topCreator: CreatorRank? get() = creators.firstOrNull()
}

/**
 * Totales desde el primer registro (la banda «DESDE EL 3 MAR: 12.408 VÍDEOS · 61 H»).
 *
 * @property trackedDays días desde el primer registro hasta hoy, ambos incluidos (0 si no hay datos).
 */
data class AllTime(
    val firstDay: LocalDate?,
    val today: LocalDate,
    val totals: Totals,
    val trackedDays: Int,
    val avgPerDay: Double,
    val avgMsPerDay: Double,
) {
    val isEmpty: Boolean get() = totals.count == 0
}
