package com.reelometro.app.stats

import com.reelometro.app.R
import com.reelometro.app.copy.AppTexts
import com.reelometro.app.copy.str
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Formato de cifras y fechas en el idioma de los textos de la app ([AppTexts]: el de la carpeta
 * `values-xx` que se está enseñando, no el del sistema).
 *
 *  - **Español** (el idioma por defecto): determinista y con tablas propias, igual que siempre. No
 *    depende de los datos de localización del dispositivo (ICU agrupa "1234" sin punto y abrevia
 *    "nov."; aquí siempre "1.234" y "nov").
 *  - **Otros idiomas**: separadores de miles y decimales de `DecimalFormatSymbols`, nombres de días
 *    y meses de `java.time` y el orden de las piezas (y las unidades: "h", "min", "%") de las frases
 *    `format_*` de `res/values/format.xml` (y sus traducciones), que cada traducción adapta.
 *
 *  1.234 · 3,2 · 34 % · +12 % · 1 h 12 min · 3,2 h · 3 nov · martes 3 de noviembre
 */
object Format {

    /** Idioma de los textos en uso (manda en separadores, nombres y mayúsculas). */
    val locale: Locale get() = AppTexts.current.locale

    private val spanish: Boolean get() = locale.language == "es"

    /** Espacio duro entre cifra y unidad ("34 %", "12 min"): nunca se parten en dos líneas. */
    const val NBSP = '\u00A0'

    /** Signo menos tipográfico (no el guion). */
    const val MINUS = '−'

    private class Symbols(val locale: Locale, val group: Char, val decimal: Char)

    /** Separador de miles del idioma de los textos («.» en español, «,» en inglés, espacio fino en francés). */
    val groupingSeparator: Char get() = symbols().group

    @Volatile
    private var symbols: Symbols? = null

    private fun symbols(): Symbols {
        val l = locale
        symbols?.let { if (it.locale == l) return it }
        val s = if (l.language == "es") {
            Symbols(l, '.', ',')
        } else {
            val d = DecimalFormatSymbols.getInstance(l)
            Symbols(l, d.groupingSeparator, d.decimalSeparator)
        }
        return s.also { symbols = it }
    }

    // ------------------------------------------------------------------ números

    /** 7 -> "7", 1234 -> "1.234", 1234567 -> "1.234.567" (en inglés, "1,234"). */
    fun integer(value: Long): String {
        val group = symbols().group
        val digits = abs(value).toString()
        val sb = StringBuilder(digits.length + digits.length / 3)
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(group)
            sb.append(c)
        }
        return if (value < 0) "$MINUS$sb" else sb.toString()
    }

    fun integer(value: Int): String = integer(value.toLong())

    /**
     * Decimal con coma (o con el separador del idioma). Por defecto, un decimal por debajo de 10 y
     * ninguno a partir de 10: 3.0 -> "3", 3.24 -> "3,2", 12.7 -> "13", 1234.5 -> "1.235".
     */
    fun decimal(value: Double, maxFractionDigits: Int = if (abs(value) >= 10) 0 else 1): String {
        if (value.isNaN() || value.isInfinite()) return "–"
        val factor = 10.0.pow(maxFractionDigits).toLong()
        val rounded = (abs(value) * factor).roundToLong()
        val intPart = rounded / factor
        val frac = rounded % factor
        val sign = if (value < 0 && rounded != 0L) MINUS.toString() else ""
        val fracText = if (maxFractionDigits == 0 || frac == 0L) {
            ""
        } else {
            symbols().decimal + frac.toString().padStart(maxFractionDigits, '0').trimEnd('0')
        }
        return sign + integer(intPart) + fracText
    }

    /** 0.34 -> "34 %", 0.004 -> "0,4 %", 0.0 -> "0 %". */
    fun percent(fraction: Double): String {
        val pct = fraction * 100
        val text = if (pct == 0.0 || abs(pct) >= 1) integer(pct.roundToInt()) else decimal(pct, 1)
        return str(R.string.format_percent, text)
    }

    /** 0.34 -> "+34 %", -0.12 -> "−12 %", 0.001 -> "±0 %". */
    fun signedPercent(fraction: Double): String {
        val pct = (fraction * 100).roundToInt()
        val text = when {
            pct > 0 -> "+${integer(pct)}"
            pct < 0 -> "$MINUS${integer(-pct)}"
            else -> "±0"
        }
        return str(R.string.format_percent, text)
    }

    // ------------------------------------------------------------------ tiempo

    data class HoursMinutes(val hours: Long, val minutes: Int)

    fun hoursMinutes(ms: Long): HoursMinutes {
        val totalMin = (ms / 60_000).coerceAtLeast(0)
        return HoursMinutes(totalMin / 60, (totalMin % 60).toInt())
    }

    /** "45 s", "12 min", "1 h 12 min", "3 h", "312 h" (sin minutos a partir de 100 h). */
    fun duration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h >= 100 -> str(R.string.format_hours, integer(h))
            h > 0 && m > 0 -> str(R.string.format_hours_minutes, integer(h), integer(m))
            h > 0 -> str(R.string.format_hours, integer(h))
            m > 0 -> str(R.string.format_minutes, integer(m))
            else -> str(R.string.format_seconds, integer(s))
        }
    }

    /** Por debajo de una hora, minutos ("47 min"); a partir de ahí, horas con un decimal ("3,2 h"). */
    fun durationCompact(ms: Long): String {
        val minutes = (ms / 60_000.0).roundToInt()
        return if (minutes < 60) {
            str(R.string.format_minutes, integer(minutes))
        } else {
            str(R.string.format_hours, decimal(ms / 3_600_000.0))
        }
    }

    /** Segundos redondeados: "27 s". */
    fun seconds(ms: Long): String = str(R.string.format_seconds, integer((ms / 1000.0).roundToLong()))

    fun hours(ms: Long): Double = ms / 3_600_000.0

    /** "21:30" (24 horas en todos los idiomas: es la estética del marcador). */
    fun time(hour: Int, minute: Int): String = "%02d:%02d".format(Locale.ROOT, hour, minute)

    /** "23:00" */
    fun hour(hour: Int): String = time(hour, 0)

    /** "23:00–24:00" */
    fun hourRange(hour: Int): String = "${hour(hour)}–${hour(hour + 1)}"

    // ------------------------------------------------------------------ fechas

    private val MONTHS = arrayOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )
    private val MONTHS_SHORT = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
    private val WEEKDAYS = arrayOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val WEEKDAYS_SHORT = arrayOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    private val WEEKDAY_INITIALS = arrayOf("L", "M", "X", "J", "V", "S", "D")

    /** 1..12 -> "enero".."diciembre", en la forma que va dentro de una fecha ("3 de noviembre"). */
    fun monthName(month: Int): String =
        if (spanish) MONTHS[month - 1] else Month.of(month).getDisplayName(TextStyle.FULL, locale)

    /** 1..12 -> el mes solo, como título ("noviembre", "noviembre de 2026"). En español, igual que [monthName]. */
    fun monthStandalone(month: Int): String =
        if (spanish) MONTHS[month - 1] else Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)

    /** 1..12 -> "ene".."dic" */
    fun monthShort(month: Int): String =
        if (spanish) MONTHS_SHORT[month - 1] else Month.of(month).getDisplayName(TextStyle.SHORT, locale)

    /** "martes" */
    fun weekday(day: DayOfWeek): String =
        if (spanish) WEEKDAYS[day.value - 1] else day.getDisplayName(TextStyle.FULL, locale)

    /** "mar" */
    fun weekdayShort(day: DayOfWeek): String =
        if (spanish) WEEKDAYS_SHORT[day.value - 1] else day.getDisplayName(TextStyle.SHORT, locale)

    /** L M X J V S D */
    fun weekdayInitial(day: DayOfWeek): String =
        if (spanish) WEEKDAY_INITIALS[day.value - 1] else day.getDisplayName(TextStyle.NARROW_STANDALONE, locale)

    private fun d(date: LocalDate) = date.dayOfMonth.toString()

    /** "martes 3 de noviembre" */
    fun dayLong(date: LocalDate): String =
        str(R.string.format_day_long, weekday(date.dayOfWeek), d(date), monthName(date.monthValue))

    /** "martes 3 de noviembre de 2025" */
    fun dayLongWithYear(date: LocalDate): String =
        str(R.string.format_day_long_year, weekday(date.dayOfWeek), d(date), monthName(date.monthValue), date.year.toString())

    /** "3 de noviembre" */
    fun dayMonth(date: LocalDate): String = str(R.string.format_day_month, d(date), monthName(date.monthValue))

    /** "3 de noviembre de 2026" */
    fun dayFull(date: LocalDate): String =
        str(R.string.format_day_full, d(date), monthName(date.monthValue), date.year.toString())

    /** "3 nov" */
    fun dayShort(date: LocalDate): String = str(R.string.format_day_short, d(date), monthShort(date.monthValue))

    /** "3 nov 2026" */
    fun dayShortWithYear(date: LocalDate): String =
        str(R.string.format_day_short_year, d(date), monthShort(date.monthValue), date.year.toString())

    /** "mar 3 nov" (día de la semana abreviado y fecha corta: tooltips, ejes). */
    fun weekdayDayShort(date: LocalDate): String =
        str(R.string.format_weekday_day_short, weekdayShort(date.dayOfWeek), dayShort(date))

    /** "octubre de 2026" */
    fun monthYear(date: LocalDate): String =
        str(R.string.format_month_year, monthStandalone(date.monthValue), date.year.toString())

    /** "octubre 2026" (para etiquetas, que luego van en mayúsculas: "OCTUBRE 2026"). */
    fun monthYearLabel(month: Int, year: Int): String =
        str(R.string.format_month_year_label, monthStandalone(month), year.toString())

    /** "27 oct – 2 nov", "3 – 9 nov", "29 dic 2025 – 4 ene 2026", "3 nov" si es el mismo día. */
    fun range(start: LocalDate, end: LocalDate): String = when {
        start == end -> dayShort(start)
        start.year != end.year -> str(R.string.format_range, dayShortWithYear(start), dayShortWithYear(end))
        start.month == end.month -> str(R.string.format_range_same_month, d(start), d(end), monthShort(end.monthValue))
        else -> str(R.string.format_range, dayShort(start), dayShort(end))
    }

    /**
     * Rango con el año solo cuando hace falta: sin año si es del año de [today] ("27 oct – 2 nov"),
     * con año si es de otro ("22 – 28 dic 2025") o si cruza de año ("29 dic 2025 – 4 ene 2026").
     */
    fun rangeInContext(start: LocalDate, end: LocalDate, today: LocalDate): String = when {
        start.year != end.year -> range(start, end)
        start.year == today.year -> range(start, end)
        else -> str(R.string.format_with_year, range(start, end), start.year.toString())
    }

    /** Rango siempre con año: "27 oct – 2 nov 2026", "29 dic 2025 – 4 ene 2026", "3 nov 2026". */
    fun rangeWithYear(start: LocalDate, end: LocalDate): String =
        if (start.year != end.year) range(start, end) else str(R.string.format_with_year, range(start, end), end.year.toString())

    /**
     * Nombre de un periodo de estadísticas:
     * "Esta semana" / "27 oct – 2 nov", "noviembre de 2026", "2026", "Desde el 3 mar 2025".
     *
     * @param span intervalo nominal del periodo ([PeriodRef.span]).
     */
    fun periodLabel(ref: PeriodRef, span: DateSpan, today: LocalDate): String = when (ref.kind) {
        PeriodKind.WEEK -> if (ref.offset == 0) str(R.string.format_period_this_week) else rangeInContext(span.start, span.end, today)
        PeriodKind.MONTH -> monthYear(span.start)
        PeriodKind.YEAR -> span.start.year.toString()
        PeriodKind.ALL -> str(R.string.format_period_since, dayShortWithYear(span.start))
    }

    /** Primera letra en mayúscula ("martes 3…" -> "Martes 3…"), con las reglas del idioma. */
    fun capitalize(text: String): String {
        val l = locale
        return text.replaceFirstChar { it.titlecase(l) }
    }

    /** Para etiquetas en versalitas: "Martes" -> "MARTES" con las reglas del idioma (turco: i -> İ). */
    fun upper(text: String): String = text.uppercase(locale)
}
