package com.reelometro.app.copy

import com.reelometro.app.data.Category
import com.reelometro.app.stats.EquivalenceValue
import com.reelometro.app.stats.Format
import java.time.LocalDate

/**
 * Textos de la **imagen compartible** (1080×1920, «ticket de tu tiempo») y del envío.
 *
 * Es el **único** sitio donde se permiten emojis (en las líneas del ticket y en el texto que
 * acompaña a la imagen). Semilla recomendada: la misma que el Wrapped (epochDay del inicio del periodo).
 */
object ShareCopy {

    /** Cabecera del ticket. */
    const val BRAND = "REELÓMETRO"

    /** Subtítulo del ticket. */
    const val TITLE = "TICKET DE TU TIEMPO"

    /** Sello rotado en `signal`. */
    const val STAMP = "PAGADO CON TU TIEMPO"

    /** Lema de la marca. */
    const val TAGLINE = "CUENTA LO QUE NO CUENTAS"

    /** Pie de marca: «REELÓMETRO · CUENTA LO QUE NO CUENTAS». */
    const val BRAND_FOOTER = "$BRAND · $TAGLINE"

    /** Título del selector de compartir de Android (`Intent.createChooser`). */
    const val CHOOSER_TITLE = "Compartir tu ticket"

    /** Etiqueta de la línea de total. */
    const val TOTAL_LABEL = "TOTAL"

    /** Periodo del ticket: «OCTUBRE 2026», «27 OCT – 2 NOV 2026», «2026», «DESDE EL 3 MAR 2026». */
    fun periodLine(period: CopyPeriod, start: LocalDate, end: LocalDate): String = when (period) {
        CopyPeriod.WEEK -> StatsCopy.weekLabel(start, end)
        CopyPeriod.MONTH -> Format.upper("${Format.monthName(start.monthValue)} ${start.year}")
        CopyPeriod.YEAR -> start.year.toString()
        CopyPeriod.ALL -> StatsCopy.allLabel(start)
    }

    /**
     * Líneas principales del ticket (etiqueta con emoji · valor). Las opcionales se omiten si son `null`.
     *
     * @param days días del periodo (para la media; con menos de 2 no se muestra).
     * @param worstDay día con más vídeos y [worstDayCount] sus vídeos.
     * @param topCreator usuario del creador n.º 1 (con o sin «@»).
     * @param topCategoryFraction cuota de [topCategory] (0–1).
     * @param includeTotals `false` para omitir las líneas de vídeos y tiempo (el ticket ya los
     *   enseña en la cifra grande y en la línea de total).
     */
    fun lines(
        count: Long,
        totalMs: Long,
        days: Int,
        worstDay: LocalDate? = null,
        worstDayCount: Int = 0,
        peakHour: Int? = null,
        topCreator: String? = null,
        topCategory: Category? = null,
        topCategoryFraction: Double = 0.0,
        includeTotals: Boolean = true,
    ): List<TicketLine> = buildList {
        if (includeTotals) {
            add(TicketLine("📱 ${Labels.VIDEOS}", Format.integer(count)))
            add(TicketLine("⏱️ ${Labels.TIME}", Words.durationLabel(totalMs)))
        }
        if (days >= 2) add(TicketLine("📅 AL DÍA", Format.decimal(count.toDouble() / days)))
        if (worstDay != null && worstDayCount > 0) {
            add(TicketLine("🔥 ${Labels.WORST_DAY}", "${Format.upper(Format.dayShort(worstDay))} · ${Format.integer(worstDayCount)}"))
        }
        if (peakHour != null) {
            val h = Math.floorMod(peakHour, 24)
            val icon = if (h >= 21 || h <= 6) "🌙" else "☀️"
            add(TicketLine("$icon ${Labels.PEAK_HOUR}", Format.hour(h)))
        }
        if (!topCreator.isNullOrBlank()) add(TicketLine("👑 ${Labels.TOP_CREATOR}", Words.handle(topCreator)))
        if (topCategory != null) {
            add(TicketLine("${emoji(topCategory.emoji)} ${Format.upper(topCategory.label)}", Format.percent(topCategoryFraction)))
        }
    }

    /** Equivalencias como líneas de ticket: «📚 LIBROS · 14», «🐉 JUEGO DE TRONOS · ×2». */
    fun equivalenceLines(values: List<EquivalenceValue>): List<TicketLine> =
        values.map { TicketLine("${emoji(it.equivalence.emoji)} ${it.equivalence.label}", it.ticketValue) }

    /**
     * Fuerza la presentación en color de los símbolos de un solo carácter del plano básico («⚡»,
     * «⚽», «⏳»): sin el selector U+FE0F, una fuente que tenga el glifo (JetBrains Mono lo tiene)
     * lo pinta en monocromo.
     */
    internal fun emoji(symbol: String): String {
        if (symbol.length != 1) return symbol
        val c = symbol[0].code
        val textDefault = c in 0x2190..0x2BFF
        return if (textDefault) symbol + VARIATION_EMOJI else symbol
    }

    private const val VARIATION_EMOJI = '️'

    /** Línea de total: «TOTAL · 29 H». */
    fun totalLine(totalMs: Long): TicketLine = TicketLine(TOTAL_LABEL, Words.durationLabel(totalMs))

    /** Nota al pie del ticket, con variantes. */
    fun footer(seed: Long): String = pick(
        seed, "share.footer",
        "No se admiten devoluciones.",
        "Guarda este ticket. El tiempo no se devuelve.",
        "IVA no incluido. El tiempo, todo.",
        "Precio final. Sin descuentos ni devoluciones.",
    )

    /** Texto que acompaña a la imagen al compartir: «Mi octubre en Reelómetro: 4.812 vídeos y 29 horas. No se admiten devoluciones. 🧾». */
    fun shareText(period: CopyPeriod, start: LocalDate, count: Long, totalMs: Long): String {
        val what = when (period) {
            CopyPeriod.WEEK -> "Mi semana"
            CopyPeriod.MONTH -> "Mi ${Format.monthName(start.monthValue)}"
            CopyPeriod.YEAR -> "Mi ${start.year}"
            CopyPeriod.ALL -> "Mi historial"
        }
        return "$what en Reelómetro: ${Words.videos(count)} y ${Words.duration(totalMs)}. No se admiten devoluciones. 🧾"
    }

    /** Nombre del fichero (ASCII): «reelometro-octubre-2026.png», «reelometro-semana-2026-10-27.png». */
    fun fileName(period: CopyPeriod, start: LocalDate): String = when (period) {
        CopyPeriod.WEEK -> "reelometro-semana-$start.png"
        CopyPeriod.MONTH -> "reelometro-${Format.monthName(start.monthValue)}-${start.year}.png"
        CopyPeriod.YEAR -> "reelometro-${start.year}.png"
        CopyPeriod.ALL -> "reelometro-historial.png"
    }

    /** contentDescription de la vista previa: «Ticket de octubre de 2026: 4.812 vídeos y 29 horas.». */
    fun imageDescription(period: CopyPeriod, start: LocalDate, end: LocalDate, count: Long, totalMs: Long): String {
        val what = when (period) {
            CopyPeriod.WEEK -> "de la semana del ${Format.dayShort(start)} al ${Format.dayShort(end)}"
            CopyPeriod.MONTH -> "de ${Format.monthYear(start)}"
            CopyPeriod.YEAR -> "de ${start.year}"
            CopyPeriod.ALL -> "de todo tu historial"
        }
        return "Ticket $what: ${Words.videos(count)} y ${Words.duration(totalMs)}."
    }
}
