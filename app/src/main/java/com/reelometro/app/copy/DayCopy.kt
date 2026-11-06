package com.reelometro.app.copy

import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import java.time.DayOfWeek
import java.time.LocalDate

/** Textos del **detalle de un día** (se abre al tocar el marcador o una barra/celda). */
object DayCopy {

    /** Título en frase: «Hoy, martes 3 de noviembre», «Ayer, lunes 2 de noviembre», «Martes 3 de noviembre de 2025». */
    fun title(date: LocalDate, today: LocalDate): String {
        val long = if (date.year != today.year) "${Format.dayLong(date)} de ${date.year}" else Format.dayLong(date)
        return when (date) {
            today -> "Hoy, $long"
            today.minusDays(1) -> "Ayer, $long"
            else -> Format.capitalize(long)
        }
    }

    /** Etiqueta: «HOY · MARTES 3 NOV», «AYER · LUNES 2 NOV», «MARTES 3 NOV», «MARTES 3 NOV 2025». */
    fun label(date: LocalDate, today: LocalDate): String {
        val short = if (date.year != today.year) Format.dayShortWithYear(date) else Format.dayShort(date)
        val base = Format.upper("${Format.weekday(date.dayOfWeek)} $short")
        return when (date) {
            today -> "${Labels.TODAY} · $base"
            today.minusDays(1) -> "${Labels.YESTERDAY} · $base"
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

        if (count <= 0) {
            return pick(
                seed, "day.zero",
                "Ese día, ni un vídeo.",
                "Cero. Un día de los buenos, o uno sin cobertura.",
                "Nada ese día. O no viste nada, o yo no estaba mirando.",
            )
        }

        if (goal != null && goal > 0) {
            val g = Format.integer(goal)
            when {
                count > goal -> {
                    val over = Words.videos(count - goal)
                    return pick(
                        seed, "day.goal.over",
                        "Ese día te pasaste en $over.",
                        "Objetivo: $g. Ese día: $n.",
                        "$over por encima del objetivo. Pasa.",
                    )
                }

                count == goal -> return pick(
                    seed, "day.goal.at",
                    "Justo en el límite: $g, ni uno más.",
                    "$g de $g. Clavado.",
                    "Objetivo clavado ese día.",
                )

                else -> {
                    val left = Words.videos(goal - count)
                    return pick(
                        seed, "day.goal.under",
                        "Dentro del objetivo: $n de $g.",
                        "Por debajo de $g, con $left de margen.",
                        "$n de $g. Día dentro del límite.",
                    )
                }
            }
        }

        val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive
        val fueron = if (count == 1) "fue" else "fueron"
        return when (Bands.of(count, totalMs)) {
            Band.ZERO, Band.FIRST -> pick(
                seed, "day.first",
                "Ese día $fueron $v. Casi nada.",
                if (hasTime) "$v y $t. Un día tranquilo." else "$v. Un día tranquilo.",
                "Poca cosa: $v.",
            )

            Band.NORMAL -> pick(
                seed, "day.normal",
                "$v y $t. Un día normal, que ya es decir.",
                "Ese día: $t de vídeos.",
                "$tCap. Ni fu ni fa.",
            )

            Band.HIGH -> pick(
                seed, "day.high",
                "$v. Ese día el algoritmo hizo horas extra.",
                if (eq != null) "$tCap de vídeos. Te daba para $eq." else "$tCap de vídeos. Un día largo.",
                "Ese día se te ${if (Words.isSingularDuration(totalMs)) "fue" else "fueron"} $t. Así, sin más.",
            )

            Band.EXTREME -> pick(
                seed, "day.extreme",
                "$v. Hay días y días, y este fue de los segundos.",
                "$tCap. Si alguien pregunta, tenías cosas que hacer.",
                "$v en un solo día. Ese día ganó el algoritmo.",
            )
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
        rank == 1 -> "TU RÉCORD: EL DÍA CON MÁS VÍDEOS"
        else -> "N.º ${Format.integer(rank)} DE ${Format.integer(totalDays)} DÍAS CONTADOS"
    }

    /**
     * Estado frente al objetivo de un día **ya cerrado** (para hoy usa [TodayCopy.goalStatus]):
     * «+62 PASADO», «EN EL LÍMITE», «38 POR DEBAJO».
     */
    fun goalStatus(count: Int, goal: Int): String = when {
        count > goal -> "+${Format.integer(count - goal)}${Words.NBSP}PASADO"
        count == goal -> "EN EL LÍMITE"
        else -> "${Format.integer(goal - count)}${Words.NBSP}POR DEBAJO"
    }

    /** Lo mismo para TalkBack: «38 vídeos por debajo del objetivo de 150». */
    fun goalStatusDescription(count: Int, goal: Int): String = when {
        count > goal -> "${Words.videos(count - goal)} por encima del objetivo de ${Format.integer(goal)}"
        count == goal -> "Justo en el objetivo de ${Format.integer(goal)}"
        else -> "${Words.videos(goal - count)} por debajo del objetivo de ${Format.integer(goal)}"
    }

    /** Cabecera de la sección por horas en un día pasado (para hoy, [TodayCopy.SECTION_HOURS]). */
    const val SECTION_HOURS = "ESE DÍA, HORA A HORA"

    /** «PRIMER VÍDEO 08:12 · ÚLTIMO 01:47». */
    fun firstLastLine(firstHour: Int, firstMinute: Int, lastHour: Int, lastMinute: Int): String =
        "${Labels.FIRST_VIDEO} ${Format.time(firstHour, firstMinute)} · ÚLTIMO ${Format.time(lastHour, lastMinute)}"
}
