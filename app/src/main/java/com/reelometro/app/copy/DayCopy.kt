package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Textos del **detalle de un día** (se abre al tocar el marcador o una barra/celda).
 *
 * Patrón de los textos con recursos (`res/values/copy_day.xml`): aquí se decide **qué** frase toca
 * (tramos, objetivo…) y con qué huecos; las palabras están en el XML y cada idioma tiene las suyas.
 */
object DayCopy {

    /** Título en frase: «Hoy, martes 3 de noviembre», «Ayer, lunes 2 de noviembre», «Martes 3 de noviembre de 2025». */
    fun title(date: LocalDate, today: LocalDate): String {
        val long = if (date.year != today.year) Format.dayLongWithYear(date) else Format.dayLong(date)
        return when (date) {
            today -> str(R.string.day_title_today, long)
            today.minusDays(1) -> str(R.string.day_title_yesterday, long)
            else -> Format.capitalize(long)
        }
    }

    /** Etiqueta: «HOY · MARTES 3 NOV», «AYER · LUNES 2 NOV», «MARTES 3 NOV», «MARTES 3 NOV 2025». */
    fun label(date: LocalDate, today: LocalDate): String {
        val short = if (date.year != today.year) Format.dayShortWithYear(date) else Format.dayShort(date)
        val base = Format.upper(str(R.string.day_label_date, Format.weekday(date.dayOfWeek), short))
        return when (date) {
            today -> str(R.string.day_label_today, base)
            today.minusDays(1) -> str(R.string.day_label_yesterday, base)
            else -> base
        }
    }

    /**
     * Frase de un día **pasado** (para hoy usa [TodayCopy.headline]). Con objetivo, se cuenta
     * frente al objetivo; sin él, por tramos.
     */
    fun quote(count: Int, totalMs: Long, seed: Long, goal: Int? = null): String {
        val v = Words.videos(count)
        val n = Format.integer(count)
        val t = Words.duration(totalMs)
        val tCap = Format.capitalize(t)
        val hasTime = totalMs >= 60_000

        if (count <= 0) return pick(seed, "day.zero", R.array.day_zero)

        if (goal != null && goal > 0) {
            val g = Format.integer(goal)
            return when {
                count > goal -> pick(seed, "day.goal.over", R.array.day_goal_over, Words.videos(count - goal), g, n)
                count == goal -> pick(seed, "day.goal.at", R.array.day_goal_at, g)
                else -> pick(seed, "day.goal.under", R.array.day_goal_under, n, g, Words.videos(goal - count))
            }
        }

        return when (Bands.of(count, totalMs)) {
            Band.ZERO, Band.FIRST -> {
                val array = when {
                    count == 1 && hasTime -> R.array.day_first_one
                    count == 1 -> R.array.day_first_one_notime
                    hasTime -> R.array.day_first
                    else -> R.array.day_first_notime
                }
                pick(seed, "day.first", array, v, t)
            }

            Band.NORMAL -> pick(seed, "day.normal", R.array.day_normal, v, t, tCap)

            Band.HIGH -> {
                val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive
                val singular = Words.isSingularDuration(totalMs)
                val array = when {
                    singular && eq != null -> R.array.day_high_one
                    singular -> R.array.day_high_one_noeq
                    eq != null -> R.array.day_high
                    else -> R.array.day_high_noeq
                }
                pick(seed, "day.high", array, v, tCap, t, eq.orEmpty())
            }

            Band.EXTREME -> pick(seed, "day.extreme", R.array.day_extreme, v, tCap)
        }
    }

    /** Comparación con la media de ese día de la semana (día completo): «+34 % QUE TU MARTES MEDIO». */
    fun comparison(count: Int, average: Double?, weekday: DayOfWeek): DeltaLine =
        TodayCopy.comparison(count.toDouble(), average, weekday, untilNow = false)

    /**
     * Puesto del día entre todos los contados: «TU RÉCORD: EL DÍA CON MÁS VÍDEOS»,
     * «N.º 3 DE 64 DÍAS CONTADOS». `null` si no tiene sentido (un solo día o puesto desconocido).
     */
    fun rankLine(rank: Int, totalDays: Int): String? = when {
        rank <= 0 || totalDays <= 1 -> null
        rank == 1 -> str(R.string.day_rank_record)
        else -> plural(R.plurals.day_rank_line, totalDays, Format.integer(rank), Format.integer(totalDays))
    }

    /**
     * Estado frente al objetivo de un día **ya cerrado** (para hoy usa [TodayCopy.goalStatus]):
     * «+62 PASADO», «EN EL LÍMITE», «38 POR DEBAJO».
     */
    fun goalStatus(count: Int, goal: Int): String = when {
        count > goal -> str(R.string.day_goal_status_over, Format.integer(count - goal))
        count == goal -> str(R.string.day_goal_status_at)
        else -> str(R.string.day_goal_status_under, Format.integer(goal - count))
    }

    /** Lo mismo para TalkBack: «38 vídeos por debajo del objetivo de 150». */
    fun goalStatusDescription(count: Int, goal: Int): String = when {
        count > goal -> str(R.string.day_goal_status_over_description, Words.videos(count - goal), Format.integer(goal))
        count == goal -> str(R.string.day_goal_status_at_description, Format.integer(goal))
        else -> str(R.string.day_goal_status_under_description, Words.videos(goal - count), Format.integer(goal))
    }

    /** Cabecera de la sección por horas en un día pasado (para hoy, [TodayCopy.SECTION_HOURS]). */
    val SECTION_HOURS: String get() = str(R.string.day_section_hours)

    /** «PRIMER VÍDEO 08:12 · ÚLTIMO 01:47». */
    fun firstLastLine(firstHour: Int, firstMinute: Int, lastHour: Int, lastMinute: Int): String =
        str(R.string.day_first_last, Format.time(firstHour, firstMinute), Format.time(lastHour, lastMinute))
}
