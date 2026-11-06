package com.reelometro.app.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Tipo de periodo de las estadísticas. */
enum class PeriodKind {
    /** Semana ISO, de lunes a domingo. */
    WEEK,

    /** Mes natural. */
    MONTH,

    /** Año natural. */
    YEAR,

    /** «Siempre»: desde el primer registro hasta hoy. */
    ALL,
}

/** Intervalo de fechas locales con ambos extremos incluidos (`start ≤ end`). */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "El final ($end) no puede ser anterior al inicio ($start)" }
    }

    /** Número de días del intervalo (≥ 1). */
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    val startEpochDay: Long get() = start.toEpochDay()
    val endEpochDay: Long get() = end.toEpochDay()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    /** Todas las fechas del intervalo, en orden. */
    fun dates(): List<LocalDate> = List(days) { start.plusDays(it.toLong()) }

    /** Parte común con [other], o `null` si no se solapan. */
    fun intersect(other: DateSpan): DateSpan? {
        val s = maxOf(start, other.start)
        val e = minOf(end, other.end)
        return if (e.isBefore(s)) null else DateSpan(s, e)
    }

    /** Recorta lo posterior a [date] (p. ej. hoy, para no tener días futuros); `null` si todo es posterior. */
    fun until(date: LocalDate): DateSpan? = if (start.isAfter(date)) null else DateSpan(start, minOf(end, date))

    /** Recorta lo anterior a [date] (p. ej. el primer registro); `null` si todo es anterior. */
    fun from(date: LocalDate): DateSpan? = if (end.isBefore(date)) null else DateSpan(maxOf(start, date), end)

    companion object {
        fun of(date: LocalDate): DateSpan = DateSpan(date, date)
    }
}

/**
 * Periodo relativo a hoy: `offset = 0` es el actual (esta semana, este mes, este año), `-1` el
 * anterior, etc. Nunca hay periodos futuros (`offset ≤ 0`) y [PeriodKind.ALL] solo admite `0`.
 *
 * Las fechas se calculan siempre a partir de `today` (lo pasa el repositorio), así que la
 * referencia sigue siendo válida al cambiar de día: `PeriodRef(WEEK)` es «esta semana» en cada momento.
 */
data class PeriodRef(val kind: PeriodKind, val offset: Int = 0) {
    init {
        require(offset <= 0) { "No hay periodos futuros (offset = $offset)" }
        require(kind != PeriodKind.ALL || offset == 0) { "«Siempre» no tiene periodos anteriores" }
    }

    /** Es el periodo en curso (contiene hoy). */
    val isCurrent: Boolean get() = offset == 0

    /** Hay un periodo siguiente (no se puede avanzar más allá del actual). */
    val hasNext: Boolean get() = offset < 0

    /** Periodo siguiente, o `null` si este es el actual. */
    fun next(): PeriodRef? = if (offset < 0) PeriodRef(kind, offset + 1) else null

    /** Periodo anterior del mismo tipo, o `null` para «siempre». No mira si tiene datos: ver [hasPrevious]. */
    fun previous(): PeriodRef? = if (kind == PeriodKind.ALL) null else PeriodRef(kind, offset - 1)

    /** Se puede ir atrás: el periodo anterior acaba en o después del primer registro. */
    fun hasPrevious(today: LocalDate, firstDay: LocalDate?): Boolean {
        val prev = previous() ?: return false
        if (firstDay == null) return false
        return !prev.span(today, firstDay).end.isBefore(firstDay)
    }

    /**
     * Intervalo nominal completo: lunes–domingo, 1–último del mes, 1 ene–31 dic, o primer registro–hoy.
     * El periodo actual acaba en el futuro; para la parte transcurrida usa [elapsed].
     */
    fun span(today: LocalDate, firstDay: LocalDate? = null): DateSpan = when (kind) {
        PeriodKind.WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
            DateSpan(monday, monday.plusDays(6))
        }
        PeriodKind.MONTH -> {
            val month = YearMonth.from(today).plusMonths(offset.toLong())
            DateSpan(month.atDay(1), month.atEndOfMonth())
        }
        PeriodKind.YEAR -> {
            val year = today.year + offset
            DateSpan(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        }
        PeriodKind.ALL -> DateSpan(minOf(firstDay ?: today, today), today)
    }

    /** Parte transcurrida del periodo: de su inicio a hoy como mucho (sin días futuros). */
    fun elapsed(today: LocalDate, firstDay: LocalDate? = null): DateSpan {
        val span = span(today, firstDay)
        return DateSpan(span.start, minOf(span.end, today))
    }

    /** «Esta semana», «27 oct – 2 nov», «noviembre de 2026», «2026», «Desde el 3 mar 2025». */
    fun label(today: LocalDate, firstDay: LocalDate? = null): String =
        Format.periodLabel(this, span(today, firstDay), today)

    /** Forma usada en la ruta `story:<KIND>:<offset>` («MONTH:-1»). */
    fun toRouteArg(): String = "${kind.name}:$offset"

    companion object {
        val THIS_WEEK = PeriodRef(PeriodKind.WEEK)
        val THIS_MONTH = PeriodRef(PeriodKind.MONTH)
        val LAST_MONTH = PeriodRef(PeriodKind.MONTH, -1)
        val THIS_YEAR = PeriodRef(PeriodKind.YEAR)
        val ALL_TIME = PeriodRef(PeriodKind.ALL)

        /** Lee «WEEK:0», «MONTH:-1»… (lo contrario de [toRouteArg]); `null` si no es válido. */
        fun parse(text: String): PeriodRef? {
            val parts = text.trim().split(':')
            if (parts.size != 2) return null
            val kind = PeriodKind.entries.firstOrNull { it.name.equals(parts[0], ignoreCase = true) } ?: return null
            val offset = parts[1].toIntOrNull() ?: return null
            if (offset > 0 || (kind == PeriodKind.ALL && offset != 0)) return null
            return PeriodRef(kind, offset)
        }
    }
}
