package com.reelometro.app.ui.wrapped

import androidx.compose.runtime.Immutable
import com.reelometro.app.copy.CopyPeriod
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.ShareCopy
import com.reelometro.app.copy.TicketLine
import com.reelometro.app.copy.Variants
import com.reelometro.app.copy.Words
import com.reelometro.app.copy.WrappedCopy
import com.reelometro.app.data.Platform
import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import java.time.YearMonth

// Modelo puro del Resumen: qué tiras enseña el hub, qué diapositivas tiene cada historia y qué
// lleva el ticket compartible. Sin Android ni Compose (salvo @Immutable): se prueba en la JVM.

internal fun PeriodKind.copyPeriod(): CopyPeriod = when (this) {
    PeriodKind.WEEK -> CopyPeriod.WEEK
    PeriodKind.MONTH -> CopyPeriod.MONTH
    PeriodKind.YEAR -> CopyPeriod.YEAR
    PeriodKind.ALL -> CopyPeriod.ALL
}

/** Semilla de los textos de un periodo: la story y la imagen dicen siempre lo mismo para él. */
internal val PeriodStats.seed: Long get() = Variants.daySeed(span.start)

/** Días que cuentan para las medias (desde el primer registro; al menos 1). */
internal val PeriodStats.countedDays: Int get() = trackedDays.coerceAtLeast(1)

/** Días con registro, en orden: la base del código de barras y de las barras del peor día. */
internal val PeriodStats.trackedCounts: List<Float>
    get() = days.filter { it.tracked }.map { it.count.toFloat() }

/** «REELÓMETRO · OCTUBRE 2026», la cabecera de la historia. */
internal fun PeriodStats.storyLabel(): String = WrappedCopy.coverLabel(ref.kind.copyPeriod(), span.start, span.end)

// ====================================================================== hub

/**
 * Una tira del hub. [featured] = la destacada en `signal` (el mes pasado si tiene datos; si no,
 * el primer periodo que los tenga). Sin datos ([hasData] `false`) se pinta atenuada y no se abre.
 */
@Immutable
data class HubStrip(
    val ref: PeriodRef,
    val title: String,
    val range: String,
    val countText: String,
    val detail: String,
    val hasData: Boolean,
    val featured: Boolean,
    val description: String,
)

internal object HubModel {

    /** Periodos del hub, en su orden canónico. */
    val PERIODS: List<PeriodRef> = listOf(PeriodRef.LAST_MONTH, PeriodRef.THIS_MONTH, PeriodRef.THIS_WEEK, PeriodRef.THIS_YEAR)

    fun title(ref: PeriodRef): String = when {
        ref.kind == PeriodKind.MONTH && ref.offset == -1 -> WrappedCopy.HUB_LAST_MONTH
        ref.kind == PeriodKind.MONTH && ref.offset == 0 -> WrappedCopy.HUB_THIS_MONTH
        ref.kind == PeriodKind.WEEK && ref.offset == 0 -> WrappedCopy.HUB_THIS_WEEK
        ref.kind == PeriodKind.YEAR && ref.offset == 0 -> WrappedCopy.HUB_THIS_YEAR
        ref.kind == PeriodKind.ALL -> WrappedCopy.HUB_ALL
        else -> Format.upper(ref.kind.copyPeriod().noun)
    }

    /**
     * Tiras del hub: primero la destacada, después las demás con datos en orden canónico y al
     * final las que no tienen (atenuadas). Lista vacía si [stats] está vacío.
     */
    fun strips(stats: List<PeriodStats>): List<HubStrip> {
        val featuredRef = (stats.firstOrNull { it.ref == PeriodRef.LAST_MONTH && !it.isEmpty } ?: stats.firstOrNull { !it.isEmpty })?.ref
        val ordered = stats.sortedWith(
            compareByDescending<PeriodStats> { it.ref == featuredRef }.thenByDescending { !it.isEmpty },
        )
        return ordered.map { strip(it, featured = it.ref == featuredRef) }
    }

    /** El hub no tiene nada que enseñar. */
    fun isEmpty(stats: List<PeriodStats>): Boolean = stats.all { it.isEmpty }

    /** Mes del titular: el del mes pasado si tiene datos; si no, el actual. */
    fun headlineMonth(stats: List<PeriodStats>): YearMonth {
        val last = stats.firstOrNull { it.ref == PeriodRef.LAST_MONTH }
        val chosen = if (last != null && !last.isEmpty) last else stats.firstOrNull { it.ref == PeriodRef.THIS_MONTH } ?: last
        return chosen?.let { YearMonth.from(it.span.start) } ?: YearMonth.now()
    }

    private fun strip(s: PeriodStats, featured: Boolean): HubStrip {
        val title = title(s.ref)
        val range = ShareCopy.periodLine(s.ref.kind.copyPeriod(), s.span.start, s.span.end)
        val hasData = !s.isEmpty
        val detail = if (!hasData) {
            WrappedCopy.HUB_NO_DATA
        } else {
            WrappedCopy.hubDetail(s.totals.totalMs, s.inProgress)
        }
        val spokenTitle = Format.capitalize(title.lowercase(Format.locale))
        val spokenRange = range.lowercase(Format.locale)
        val description = WrappedCopy.hubStripDescription(spokenTitle, spokenRange, s.totals.count, s.totals.totalMs, hasData)
        return HubStrip(
            ref = s.ref,
            title = title,
            range = range,
            countText = Format.integer(s.totals.count),
            detail = detail,
            hasData = hasData,
            featured = featured && hasData,
            description = description,
        )
    }
}

// ====================================================================== diapositivas

/** Línea de equivalencia con su base de cálculo (nota `dataS`). */
@Immutable
data class EquivalenceLine(val text: String, val basis: String)

/** Fila de ranking (categorías): [fraction] 0–1. */
@Immutable
data class ShareRow(val label: String, val fraction: Float, val valueLabel: String)

/** Plataforma en la diapositiva de ritmo. */
@Immutable
data class PlatformRow(val platform: Platform, val fraction: Float, val value: String)

/**
 * Una diapositiva de la historia, ya con sus textos. [details] son líneas de ticket que van al pie
 * (etiqueta en mayúsculas · valor).
 */
@Immutable
sealed interface Slide {
    /** Clave estable (tests, `AnimatedContent`). */
    val key: String

    data class Cover(val title: String, val hint: String, val barcode: List<Float>) : Slide {
        override val key: String get() = "cover"
    }

    data class Total(val count: Long, val quote: String, val details: List<TicketLine>) : Slide {
        override val key: String get() = "total"
    }

    data class Time(val value: String, val quote: String, val details: List<TicketLine>) : Slide {
        override val key: String get() = "time"
    }

    /** [lines] vacío → no hay con qué comparar y se enseña [empty]. */
    data class Equivalences(val label: String, val lines: List<EquivalenceLine>, val empty: String?) : Slide {
        override val key: String get() = "equivalences"
    }

    /** [handle] `null` → no se pudo leer ningún creador. */
    data class Creator(
        val handle: String?,
        val platform: Platform?,
        val quote: String,
        val details: List<TicketLine>,
    ) : Slide {
        override val key: String get() = "creator"
    }

    data class Category(val name: String, val quote: String, val ranking: List<ShareRow>) : Slide {
        override val key: String get() = "category"
    }

    /** [bars] un valor por día registrado; [highlight] el índice del peor día dentro de [bars]. */
    data class WorstDay(
        val day: String,
        val weekday: String,
        val quote: String,
        val bars: List<Float>,
        val highlight: Int,
        val details: List<TicketLine>,
    ) : Slide {
        override val key: String get() = "worst-day"
    }

    /** Hora punta y reparto por plataformas (cualquiera de los dos puede faltar, no los dos). */
    data class Rhythm(
        val peakHour: String?,
        val peakQuote: String?,
        val platforms: List<PlatformRow>,
        val platformsText: String?,
    ) : Slide {
        override val key: String get() = "rhythm"
    }

    /** Cierre: frase + el ticket (la imagen va aparte, en el estado de la pantalla). */
    data class Closing(val label: String, val quote: String, val imageDescription: String) : Slide {
        override val key: String get() = "closing"
    }

    /** Periodo sin vídeos: diapositiva única. */
    data class Empty(val title: String) : Slide {
        override val key: String get() = "empty"
    }
}

internal object StorySlides {

    /** Cuántas categorías enseña el ranking. */
    const val TOP_CATEGORIES = 4

    /**
     * Diapositivas de un periodo, en orden: portada → total → tiempo → equivalencias → creador n.º 1 →
     * categoría → peor día → hora punta / plataformas → cierre. Se omiten las que no tienen datos
     * (sin tiempo no hay tiempo ni equivalencias; sin categorías no hay categoría; el peor día pide
     * al menos dos días con registro). Sin vídeos: una única diapositiva [Slide.Empty].
     */
    fun build(stats: PeriodStats): List<Slide> {
        val seed = stats.seed
        val period = stats.ref.kind.copyPeriod()
        val start = stats.span.start
        val totals = stats.totals
        val count = totals.count.toLong()
        val days = stats.countedDays
        if (totals.count <= 0) return listOf(Slide.Empty(WrappedCopy.total(0, days, seed)))

        return buildList {
            add(Slide.Cover(WrappedCopy.coverTitle(period, start, seed), WrappedCopy.COVER_HINT, stats.trackedCounts))

            add(
                Slide.Total(
                    count = count,
                    quote = WrappedCopy.total(count, days, seed),
                    details = buildList {
                        if (days >= 2) add(TicketLine(Labels.DAILY_AVERAGE, Format.decimal(stats.avgPerDay)))
                        add(TicketLine(Labels.ACTIVE_DAYS, WrappedCopy.activeDaysValue(stats.activeDays, days)))
                    },
                ),
            )

            if (totals.totalMs > 0) {
                add(
                    Slide.Time(
                        value = WrappedCopy.timeValue(totals.totalMs),
                        quote = WrappedCopy.time(totals.totalMs, seed),
                        details = buildList {
                            add(TicketLine(Labels.PER_VIDEO, Format.upper(Format.seconds(totals.avgMsPerVideo))))
                            if (totals.shares > 0) add(TicketLine(Labels.SHARES, Format.integer(totals.shares)))
                        },
                    ),
                )
                val picks = Equivalences.pick(totals.totalMs, 3, seed)
                val lines = if (picks.any { it.reachesOne }) {
                    picks.map { EquivalenceLine(WrappedCopy.equivalenceLine(it), it.basis) }
                } else {
                    emptyList()
                }
                add(
                    Slide.Equivalences(
                        label = WrappedCopy.EQUIVALENCES_LABEL,
                        lines = lines,
                        empty = if (lines.isEmpty()) WrappedCopy.EQUIVALENCES_EMPTY else null,
                    ),
                )
            }

            val creator = stats.topCreator
            add(
                if (creator != null) {
                    Slide.Creator(
                        handle = Words.handle(creator.creator),
                        platform = creator.platform,
                        quote = WrappedCopy.creator(creator.creator, creator.count, seed),
                        details = listOf(
                            TicketLine(Labels.VIDEOS, Format.integer(creator.count)),
                            TicketLine(Labels.TIME, Words.durationLabel(creator.totalMs)),
                        ),
                    )
                } else {
                    Slide.Creator(handle = null, platform = null, quote = WrappedCopy.noCreator(seed), details = emptyList())
                },
            )

            val category = stats.topCategory ?: stats.categories.firstOrNull()
            if (category != null) {
                add(
                    Slide.Category(
                        name = category.category.label,
                        quote = WrappedCopy.category(category.category, category.fraction, seed),
                        ranking = stats.categories.take(TOP_CATEGORIES).map {
                            ShareRow(it.category.label, it.fraction.toFloat(), Format.percent(it.fraction))
                        },
                    ),
                )
            }

            val worst = stats.worstDay
            if (worst != null && worst.count > 0 && days >= 2) {
                val tracked = stats.days.filter { it.tracked }
                add(
                    Slide.WorstDay(
                        day = Format.upper(Format.dayShort(worst.date)),
                        weekday = Format.upper(Format.weekday(worst.date.dayOfWeek)),
                        quote = WrappedCopy.worstDay(worst.date, worst.count, seed),
                        bars = tracked.map { it.count.toFloat() },
                        highlight = tracked.indexOfFirst { it.date == worst.date },
                        details = listOf(
                            TicketLine(Labels.VIDEOS, Format.integer(worst.count)),
                            TicketLine(Labels.TIME, Words.durationLabel(worst.totalMs)),
                        ),
                    ),
                )
            }

            val peak = stats.peakHour?.takeIf { it.count > 0 }
            val platforms = stats.platforms.filter { it.count > 0 }
            if (peak != null || platforms.isNotEmpty()) {
                add(
                    Slide.Rhythm(
                        peakHour = peak?.let { Format.hour(it.hour) },
                        peakQuote = peak?.let { WrappedCopy.peakHour(it.hour, seed) },
                        platforms = platforms.map {
                            PlatformRow(it.platform, it.fraction.toFloat(), WrappedCopy.platformRowValue(it.fraction, it.count))
                        },
                        platformsText = if (platforms.isEmpty()) null else WrappedCopy.platforms(platforms.map { it.platform to it.fraction }, seed),
                    ),
                )
            }

            add(
                Slide.Closing(
                    label = ShareCopy.TITLE,
                    quote = WrappedCopy.closing(period, start, finished = !stats.inProgress, seed = seed),
                    imageDescription = ShareCopy.imageDescription(period, start, stats.span.end, count, totals.totalMs),
                ),
            )
        }
    }
}

// ====================================================================== ticket

/** Todo lo que pinta el ticket compartible (1080×1920), ya formateado. */
@Immutable
data class TicketData(
    val brand: String,
    val title: String,
    val period: String,
    val countLabel: String,
    val count: String,
    val lines: List<TicketLine>,
    val equivalencesLabel: String,
    val equivalences: List<TicketLine>,
    val total: TicketLine,
    val footer: String,
    val stamp: String,
    val stampDate: String,
    val barcode: List<Float>,
    val barcodeCaption: String,
    val brandFooter: String,
)

internal object TicketModel {

    fun build(stats: PeriodStats): TicketData {
        val seed = stats.seed
        val period = stats.ref.kind.copyPeriod()
        val totals = stats.totals
        val worst = stats.worstDay?.takeIf { it.count > 0 && stats.countedDays >= 2 }
        val category = stats.topCategory ?: stats.categories.firstOrNull()
        val lines = ShareCopy.lines(
            count = totals.count.toLong(),
            totalMs = totals.totalMs,
            days = stats.countedDays,
            worstDay = worst?.date,
            worstDayCount = worst?.count ?: 0,
            peakHour = stats.peakHour?.takeIf { it.count > 0 }?.hour,
            topCreator = stats.topCreator?.creator,
            topCategory = category?.category,
            topCategoryFraction = category?.fraction ?: 0.0,
            includeTotals = false,
        )
        val stampDay = stats.span.end.coerceAtMost(stats.today)
        return TicketData(
            brand = ShareCopy.BRAND,
            title = ShareCopy.TITLE,
            period = ShareCopy.periodLine(period, stats.span.start, stats.span.end),
            countLabel = Labels.VIDEOS,
            count = Format.integer(totals.count),
            lines = lines,
            equivalencesLabel = WrappedCopy.EQUIVALENCES_LABEL,
            equivalences = ShareCopy.equivalenceLines(Equivalences.pick(totals.totalMs, 3, seed)),
            total = ShareCopy.totalLine(totals.totalMs),
            footer = ShareCopy.footer(seed),
            stamp = ShareCopy.STAMP,
            stampDate = Format.upper(Format.dayShortWithYear(stampDay)),
            barcode = stats.trackedCounts,
            barcodeCaption = barcodeCaption(totals.count, totals.totalMs, stats.span.start.year),
            brandFooter = ShareCopy.BRAND_FOOTER,
        )
    }

    /** Pie del código de barras, como un EAN: «0004812 · 029 · 2026» (vídeos, horas, año). Es un código, no una cifra. */
    fun barcodeCaption(count: Int, totalMs: Long, year: Int): String {
        val hours = (totalMs / 3_600_000L).coerceIn(0, 999)
        val c = count.coerceIn(0, 9_999_999)
        return "${c.toString().padStart(7, '0')} · ${hours.toString().padStart(3, '0')} · $year"
    }

    /** Parte [text] en dos líneas lo más parejas posible, por palabras («PAGADO CON» / «TU TIEMPO»). */
    fun balancedLines(text: String): List<String> {
        val words = text.trim().split(' ').filter { it.isNotEmpty() }
        if (words.size < 2) return listOf(text.trim())
        var best = 1
        var bestWidth = Int.MAX_VALUE
        for (cut in 1 until words.size) {
            val a = words.subList(0, cut).joinToString(" ").length
            val b = words.subList(cut, words.size).joinToString(" ").length
            val w = maxOf(a, b)
            if (w < bestWidth) {
                bestWidth = w
                best = cut
            }
        }
        return listOf(words.subList(0, best).joinToString(" "), words.subList(best, words.size).joinToString(" "))
    }
}

/**
 * Código de barras del ticket: una barra por día (1–4 módulos de grosor según los vídeos) con
 * guías en los extremos, como el componente `Barcode` pero para `android.graphics`. Si no caben
 * todas sin bajar de [minModulePx], agrupa días contiguos (media).
 */
internal object TicketBarcode {

    /** Un rectángulo: [x] y [width] en píxeles; [guard] = barra guía (más larga). */
    data class Bar(val x: Float, val width: Float, val guard: Boolean)

    fun encode(values: List<Float>, width: Float, minModulePx: Float): List<Bar> {
        if (values.isEmpty() || width <= 0f) return emptyList()
        val maxBars = maxOf(1, ((width / minModulePx - GUARD_MODULES) / AVG_MODULES_PER_BAR).toInt())
        val data = if (values.size <= maxBars) values else bucket(values, (values.size + maxBars - 1) / maxBars)
        val top = data.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val bars = IntArray(data.size)
        val gaps = IntArray(data.size)
        var modules = GUARD_MODULES.toInt()
        for (k in data.indices) {
            val v = (data[k] / top).coerceIn(0f, 1f)
            bars[k] = 1 + Math.round(v * 3f)
            gaps[k] = if (k == data.lastIndex) 0 else if (v < 0.5f) 2 else 1
            modules += bars[k] + gaps[k]
        }
        val unit = width / modules
        val out = ArrayList<Bar>(data.size + 4)
        fun bar(from: Int, count: Int, guard: Boolean) {
            val left = Math.round(from * unit).toFloat()
            val right = Math.round((from + count) * unit).toFloat()
            out += Bar(left, maxOf(1f, right - left), guard)
        }
        var m = 0
        bar(m, 1, true); m += 2
        bar(m, 1, true); m += 1 + 2
        for (k in data.indices) {
            bar(m, bars[k], false)
            m += bars[k] + gaps[k]
        }
        m += 2
        bar(m, 1, true); m += 2
        bar(m, 1, true)
        return out
    }

    /** Media de cada grupo de [size] valores contiguos. */
    fun bucket(values: List<Float>, size: Int): List<Float> =
        values.chunked(size.coerceAtLeast(1)) { chunk -> chunk.sum() / chunk.size }

    /** Guías (3 + 3) y márgenes (2 + 2). */
    private const val GUARD_MODULES = 10f

    /** Módulos medios por barra ≈ 2,5 de barra + 1,5 de hueco. */
    private const val AVG_MODULES_PER_BAR = 4f
}
