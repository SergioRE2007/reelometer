package com.reelometro.app.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Formato de cifras y fechas en español de España. Es determinista: no depende de los datos de
 * localización del dispositivo (ICU agrupa "1234" sin punto; aquí siempre "1.234").
 *
 *  1.234 · 3,2 · 34 % · +12 % · 1 h 12 min · 3,2 h · 3 nov · martes 3 de noviembre
 */
object Format {
    val locale: Locale = Locale.forLanguageTag("es-ES")

    /** Espacio duro entre cifra y unidad ("34 %", "12 min"): nunca se parten en dos líneas. */
    const val NBSP = ' '

    /** Signo menos tipográfico (no el guion). */
    const val MINUS = '−'

    // ------------------------------------------------------------------ números

    /** 7 -> "7", 1234 -> "1.234", 1234567 -> "1.234.567". */
    fun integer(value: Long): String {
        val digits = abs(value).toString()
        val sb = StringBuilder(digits.length + digits.length / 3)
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return if (value < 0) "$MINUS$sb" else sb.toString()
    }

    fun integer(value: Int): String = integer(value.toLong())

    /**
     * Decimal con coma. Por defecto, un decimal por debajo de 10 y ninguno a partir de 10:
     * 3.0 -> "3", 3.24 -> "3,2", 12.7 -> "13", 1234.5 -> "1.235".
     */
    fun decimal(value: Double, maxFractionDigits: Int = if (abs(value) >= 10) 0 else 1): String {
        if (value.isNaN() || value.isInfinite()) return "–"
        val factor = 10.0.pow(maxFractionDigits).toLong()
        val rounded = (abs(value) * factor).roundToLong()
        val intPart = rounded / factor
        val frac = rounded % factor
        val sign = if (value < 0 && rounded != 0L) MINUS.toString() else ""
        val fracText = if (maxFractionDigits == 0 || frac == 0L) "" else
            "," + frac.toString().padStart(maxFractionDigits, '0').trimEnd('0')
        return sign + integer(intPart) + fracText
    }

    /** 0.34 -> "34 %", 0.004 -> "0,4 %", 0.0 -> "0 %". */
    fun percent(fraction: Double): String {
        val pct = fraction * 100
        val text = if (pct == 0.0 || abs(pct) >= 1) integer(pct.roundToInt()) else decimal(pct, 1)
        return "$text$NBSP%"
    }

    /** 0.34 -> "+34 %", -0.12 -> "−12 %", 0.001 -> "±0 %". */
    fun signedPercent(fraction: Double): String {
        val pct = (fraction * 100).roundToInt()
        return when {
            pct > 0 -> "+${integer(pct)}$NBSP%"
            pct < 0 -> "$MINUS${integer(-pct)}$NBSP%"
            else -> "±0$NBSP%"
        }
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
            h >= 100 -> "${integer(h)}${NBSP}h"
            h > 0 && m > 0 -> "$h${NBSP}h $m${NBSP}min"
            h > 0 -> "$h${NBSP}h"
            m > 0 -> "$m${NBSP}min"
            else -> "$s${NBSP}s"
        }
    }

    /** Por debajo de una hora, minutos ("47 min"); a partir de ahí, horas con un decimal ("3,2 h"). */
    fun durationCompact(ms: Long): String {
        val minutes = (ms / 60_000.0).roundToInt()
        return if (minutes < 60) "$minutes${NBSP}min" else "${decimal(ms / 3_600_000.0)}${NBSP}h"
    }

    /** Segundos redondeados: "27 s". */
    fun seconds(ms: Long): String = "${integer((ms / 1000.0).roundToLong())}${NBSP}s"

    fun hours(ms: Long): Double = ms / 3_600_000.0

    /** "21:30" */
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

    /** 1..12 -> "enero".."diciembre" */
    fun monthName(month: Int): String = MONTHS[month - 1]

    /** 1..12 -> "ene".."dic" */
    fun monthShort(month: Int): String = MONTHS_SHORT[month - 1]

    /** "martes" */
    fun weekday(day: DayOfWeek): String = WEEKDAYS[day.value - 1]

    /** "mar" */
    fun weekdayShort(day: DayOfWeek): String = WEEKDAYS_SHORT[day.value - 1]

    /** L M X J V S D */
    fun weekdayInitial(day: DayOfWeek): String = WEEKDAY_INITIALS[day.value - 1]

    /** "martes 3 de noviembre" */
    fun dayLong(date: LocalDate): String = "${weekday(date.dayOfWeek)} ${date.dayOfMonth} de ${monthName(date.monthValue)}"

    /** "3 de noviembre de 2026" */
    fun dayFull(date: LocalDate): String = "${date.dayOfMonth} de ${monthName(date.monthValue)} de ${date.year}"

    /** "3 nov" */
    fun dayShort(date: LocalDate): String = "${date.dayOfMonth} ${monthShort(date.monthValue)}"

    /** "3 nov 2026" */
    fun dayShortWithYear(date: LocalDate): String = "${dayShort(date)} ${date.year}"

    /** "octubre de 2026" */
    fun monthYear(date: LocalDate): String = "${monthName(date.monthValue)} de ${date.year}"

    /** "27 oct – 2 nov", "3 – 9 nov", "29 dic 2025 – 4 ene 2026", "3 nov" si es el mismo día. */
    fun range(start: LocalDate, end: LocalDate): String = when {
        start == end -> dayShort(start)
        start.year != end.year -> "${dayShortWithYear(start)} – ${dayShortWithYear(end)}"
        start.month == end.month -> "${start.dayOfMonth} – ${dayShort(end)}"
        else -> "${dayShort(start)} – ${dayShort(end)}"
    }

    /**
     * Rango con el año solo cuando hace falta: sin año si es del año de [today] ("27 oct – 2 nov"),
     * con año si es de otro ("22 – 28 dic 2025") o si cruza de año ("29 dic 2025 – 4 ene 2026").
     */
    fun rangeInContext(start: LocalDate, end: LocalDate, today: LocalDate): String = when {
        start.year != end.year -> range(start, end)
        start.year == today.year -> range(start, end)
        else -> "${range(start, end)} ${start.year}"
    }

    /** Rango siempre con año: "27 oct – 2 nov 2026", "29 dic 2025 – 4 ene 2026", "3 nov 2026". */
    fun rangeWithYear(start: LocalDate, end: LocalDate): String =
        if (start.year != end.year) range(start, end) else "${range(start, end)} ${end.year}"

    /**
     * Nombre de un periodo de estadísticas:
     * "Esta semana" / "27 oct – 2 nov", "noviembre de 2026", "2026", "Desde el 3 mar 2025".
     *
     * @param span intervalo nominal del periodo ([PeriodRef.span]).
     */
    fun periodLabel(ref: PeriodRef, span: DateSpan, today: LocalDate): String = when (ref.kind) {
        PeriodKind.WEEK -> if (ref.offset == 0) "Esta semana" else rangeInContext(span.start, span.end, today)
        PeriodKind.MONTH -> monthYear(span.start)
        PeriodKind.YEAR -> span.start.year.toString()
        PeriodKind.ALL -> "Desde el ${dayShortWithYear(span.start)}"
    }

    /** Primera letra en mayúscula ("martes 3…" -> "Martes 3…"). */
    fun capitalize(text: String): String = text.replaceFirstChar { it.titlecase(locale) }

    /** Para etiquetas en versalitas: "Martes" -> "MARTES" con reglas del español. */
    fun upper(text: String): String = text.uppercase(locale)
}
