package com.reelometro.app.stats

import java.time.LocalDate

/**
 * Un día de la comparativa semanal: el de esta semana y el mismo día de la semana anterior.
 *
 * @property count vídeos del día; `null` si todavía no ha llegado o es anterior al primer registro.
 * @property previous vídeos del mismo día de la semana anterior; `null` si no hubo registro.
 */
data class WeekDayPair(
    val date: LocalDate,
    val count: Int?,
    val previousDate: LocalDate,
    val previous: Int?,
)

/**
 * Una semana frente a la anterior, día a día (Estadísticas · SEMANA, «yo fantasma»).
 *
 * Los totales y la diferencia salen de la **tendencia** del periodo ([Trend]), así que dicen lo
 * mismo que la línea de tendencia de la cifra grande: con la semana en curso se compara el mismo
 * tramo de la anterior (de lunes a hoy, y hoy solo hasta la misma hora); con una semana cerrada,
 * la anterior entera.
 *
 * @property days siempre 7, de lunes a domingo.
 * @property todayIndex posición de hoy en [days] (`null` en semanas pasadas).
 * @property total vídeos de la semana (hasta ahora, si está en curso).
 * @property previousTotal vídeos de la anterior en el mismo tramo; `null` si ese tramo no tiene días registrados.
 * @property previousWeekTotal vídeos de la semana anterior entera (sus días registrados).
 * @property change variación (0.12 = +12 %); `null` si no hay base con la que comparar.
 * @property perDay la variación compara **medias por día registrado** porque los dos tramos no tienen
 *   los mismos días contados (p. ej. la primera semana de uso).
 */
data class WeekComparison(
    val span: DateSpan,
    val inProgress: Boolean,
    val days: List<WeekDayPair>,
    val todayIndex: Int?,
    val total: Int,
    val previousTotal: Int?,
    val previousWeekTotal: Int,
    val change: Double?,
    val perDay: Boolean,
) {
    companion object {
        /**
         * Junta una semana ([current]) con la anterior ([previous]). `null` si [current] no es una
         * semana, si [previous] no es justo la semana anterior o si la anterior no tiene ningún día
         * registrado (no hay fantasma con el que compararse).
         */
        fun of(current: PeriodStats, previous: PeriodStats?): WeekComparison? {
            if (previous == null || current.ref.kind != PeriodKind.WEEK || previous.ref.kind != PeriodKind.WEEK) return null
            if (previous.span.start != current.span.start.minusWeeks(1)) return null
            val today = current.today
            val currentByDate = current.days.associateBy { it.date }
            val previousByDate = previous.days.associateBy { it.date }
            val days = current.span.dates().map { date ->
                val before = date.minusWeeks(1)
                WeekDayPair(
                    date = date,
                    count = currentByDate[date]?.takeIf { it.tracked && !date.isAfter(today) }?.count,
                    previousDate = before,
                    previous = previousByDate[before]?.takeIf { it.tracked }?.count,
                )
            }
            if (days.none { it.previous != null }) return null
            val trend = current.trend
            return WeekComparison(
                span = current.span,
                inProgress = current.inProgress,
                days = days,
                todayIndex = days.indexOfFirst { it.date == today }.takeIf { it >= 0 },
                total = current.totals.count,
                previousTotal = trend?.previousTotals?.count,
                previousWeekTotal = days.sumOf { it.previous ?: 0 },
                change = trend?.countChange,
                perDay = trend != null && trend.previousTrackedDays != current.trackedDays,
            )
        }
    }
}
