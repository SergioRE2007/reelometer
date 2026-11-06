package com.reelometro.app.stats

import java.time.LocalDate

/**
 * Puesto de un día entre **todos los días contados** (del primer registro a hoy, ambos incluidos;
 * los días sin vídeos también cuentan) por número de vídeos. Los empates comparten puesto: si dos
 * días tienen el máximo, los dos son «récord».
 *
 * Lo calcula SQL (`ReelRepository.observeDayRank`): solo hace falta el recuento del día, el primer
 * registro y cuántos días tienen más vídeos; no se carga el historial entero.
 *
 * @property count vídeos del día.
 * @property rank 1 = el día con más vídeos; `0` si el día no entra en el ranking (sin vídeos,
 *   anterior al primer registro o futuro).
 * @property totalDays días contados (0 si no hay ningún registro).
 * @property firstDay primer día con registros de toda la app; `null` si no hay ninguno.
 */
data class DayRank(
    val date: LocalDate,
    val count: Int,
    val rank: Int,
    val totalDays: Int,
    val firstDay: LocalDate?,
) {
    /** El día tiene puesto (tiene vídeos y está dentro del historial). */
    val isRanked: Boolean get() = rank > 0

    companion object {
        /**
         * @param count vídeos de [date].
         * @param daysAbove días contados (hasta [today]) con **más** vídeos que [date].
         */
        fun of(date: LocalDate, today: LocalDate, firstDay: LocalDate?, count: Int, daysAbove: Int): DayRank {
            val total = if (firstDay == null || firstDay.isAfter(today)) 0 else DateSpan(firstDay, today).days
            val inHistory = firstDay != null && !date.isBefore(firstDay) && !date.isAfter(today)
            val rank = if (inHistory && count > 0) daysAbove.coerceAtLeast(0) + 1 else 0
            return DayRank(date = date, count = count.coerceAtLeast(0), rank = rank, totalDays = total, firstDay = firstDay)
        }
    }
}
