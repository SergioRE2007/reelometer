package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.data.Category
import com.reelometro.app.stats.EquivalenceValue
import com.reelometro.app.stats.Format
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Textos de la **imagen compartible** (1080×1920, «ticket de tu tiempo») y del envío.
 * Las palabras (y los emojis) están en `res/values/copy_share.xml`, con el prefijo `share_`.
 *
 * Es el **único** sitio donde se permiten emojis (en las líneas del ticket y en el texto que
 * acompaña a la imagen). Semilla recomendada: la misma que el Wrapped (epochDay del inicio del periodo).
 */
object ShareCopy {

    /** Cabecera del ticket. */
    val BRAND: String get() = str(R.string.share_brand)

    /** Subtítulo del ticket. */
    val TITLE: String get() = str(R.string.share_title)

    /** Sello rotado en `signal`. */
    val STAMP: String get() = str(R.string.share_stamp)

    /** Lema de la marca. */
    val TAGLINE: String get() = str(R.string.share_tagline)

    /** Pie de marca: «REELÓMETRO · CUENTA LO QUE NO CUENTAS». */
    val BRAND_FOOTER: String get() = str(R.string.share_brand_footer, BRAND, TAGLINE)

    /** Título del selector de compartir de Android (`Intent.createChooser`). */
    val CHOOSER_TITLE: String get() = str(R.string.share_chooser_title)

    /** Etiqueta de la línea de total. */
    val TOTAL_LABEL: String get() = str(R.string.share_total_label)

    /** Periodo del ticket: «OCTUBRE 2026», «27 OCT – 2 NOV 2026», «2026», «DESDE EL 3 MAR 2026». */
    fun periodLine(period: CopyPeriod, start: LocalDate, end: LocalDate): String = when (period) {
        CopyPeriod.WEEK -> StatsCopy.weekLabel(start, end)
        CopyPeriod.MONTH -> Format.upper(Format.monthYearLabel(start.monthValue, start.year))
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
            add(TicketLine(str(R.string.share_line_videos, Labels.VIDEOS), Format.integer(count)))
            add(TicketLine(str(R.string.share_line_time, Labels.TIME), Words.durationLabel(totalMs)))
        }
        if (days >= 2) add(TicketLine(str(R.string.share_line_per_day), Format.decimal(count.toDouble() / days)))
        if (worstDay != null && worstDayCount > 0) {
            val value = "${Format.upper(Format.dayShort(worstDay))} · ${Format.integer(worstDayCount)}"
            add(TicketLine(str(R.string.share_line_worst_day, Labels.WORST_DAY), value))
        }
        if (peakHour != null) {
            val h = Math.floorMod(peakHour, 24)
            val label = if (h >= 21 || h <= 6) R.string.share_line_peak_hour_night else R.string.share_line_peak_hour_day
            add(TicketLine(str(label, Labels.PEAK_HOUR), Format.hour(h)))
        }
        if (!topCreator.isNullOrBlank()) add(TicketLine(str(R.string.share_line_top_creator, Labels.TOP_CREATOR), Words.handle(topCreator)))
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
    fun footer(seed: Long): String = pick(seed, "share.footer", R.array.share_footer)

    /** Texto que acompaña a la imagen al compartir: «Mi octubre en Reelómetro: 4.812 vídeos y 29 horas. No se admiten devoluciones. 🧾». */
    fun shareText(period: CopyPeriod, start: LocalDate, count: Long, totalMs: Long): String {
        val v = Words.videos(count)
        val t = Words.duration(totalMs)
        return when (period) {
            CopyPeriod.WEEK -> str(R.string.share_text_week, v, t)
            CopyPeriod.MONTH -> str(R.string.share_text_month, Format.monthStandalone(start.monthValue), v, t)
            CopyPeriod.YEAR -> str(R.string.share_text_year, start.year.toString(), v, t)
            CopyPeriod.ALL -> str(R.string.share_text_all, v, t)
        }
    }

    /**
     * Nombre del fichero (ASCII): «reelometro-octubre-2026.png», «reelometro-semana-2026-10-27.png».
     * Las palabras salen del idioma de los textos; el mes se pasa a ASCII sin acentos y, si no queda
     * nada (otro alfabeto), se usa su número («reelometro-10-2026.png»).
     */
    fun fileName(period: CopyPeriod, start: LocalDate): String = when (period) {
        CopyPeriod.WEEK -> str(R.string.share_file_week, start.toString())
        CopyPeriod.MONTH -> str(R.string.share_file_month, fileMonth(start.monthValue), start.year.toString())
        CopyPeriod.YEAR -> str(R.string.share_file_year, start.year.toString())
        CopyPeriod.ALL -> str(R.string.share_file_all)
    }

    /** El mes para un nombre de fichero: minúsculas ASCII sin acentos, o su número con dos cifras. */
    private fun fileMonth(month: Int): String {
        val plain = Normalizer.normalize(Format.monthStandalone(month).lowercase(Format.locale), Normalizer.Form.NFD)
            .filter { it in 'a'..'z' || it in '0'..'9' }
        return plain.ifEmpty { "%02d".format(Locale.ROOT, month) }
    }

    /** contentDescription de la vista previa: «Ticket de octubre de 2026: 4.812 vídeos y 29 horas.». */
    fun imageDescription(period: CopyPeriod, start: LocalDate, end: LocalDate, count: Long, totalMs: Long): String {
        val v = Words.videos(count)
        val t = Words.duration(totalMs)
        return when (period) {
            CopyPeriod.WEEK -> str(R.string.share_image_description_week, Format.dayShort(start), Format.dayShort(end), v, t)
            CopyPeriod.MONTH -> str(R.string.share_image_description_month, Format.monthYear(start), v, t)
            CopyPeriod.YEAR -> str(R.string.share_image_description_year, start.year.toString(), v, t)
            CopyPeriod.ALL -> str(R.string.share_image_description_all, v, t)
        }
    }
}
