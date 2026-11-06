package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Textos de la pantalla **Hoy** (y del detalle de hoy). Las frases con variantes reciben `seed`:
 * pasa `Variants.daySeed(hoy)` para que no cambien al recomponer y sí de un día a otro.
 *
 * Las palabras están en `res/values/copy_today.xml`; aquí se decide qué frase toca y con qué huecos.
 */
object TodayCopy {

    val SECTION_PLATFORMS: String get() = str(R.string.today_section_platforms)
    val SECTION_HOURS: String get() = str(R.string.today_section_hours)
    val SECTION_CATEGORIES: String get() = str(R.string.today_section_categories)
    val SECTION_CREATORS: String get() = str(R.string.today_section_creators)

    val EMPTY_PLATFORMS: String get() = str(R.string.today_empty_platforms)
    val EMPTY_HOURS: String get() = str(R.string.today_empty_hours)
    val EMPTY_CATEGORIES: String get() = str(R.string.today_empty_categories)
    val EMPTY_CREATORS: String get() = str(R.string.today_empty_creators)

    /** Etiqueta del marcador con el servicio desconectado (fichas en blanco «– – –»). */
    val SERVICE_OFF_LABEL: String get() = Labels.NO_SIGNAL

    /** contentDescription del marcador sin servicio. */
    val SERVICE_OFF_DESCRIPTION: String get() = str(R.string.today_service_off_description)

    /** Cabecera: «HOY · MARTES 3 NOV». */
    fun header(date: LocalDate): String =
        Format.upper(str(R.string.today_header, Format.weekday(date.dayOfWeek), Format.dayShort(date)))

    /** Etiqueta sobre el marcador: «VÍDEOS» («VÍDEO» si es exactamente uno). */
    fun counterLabel(count: Int): String = plural(R.plurals.today_counter_label, count)

    /** contentDescription del marcador: «212 vídeos». */
    fun counterDescription(count: Int): String = Words.videos(count)

    /**
     * Frase principal bajo el marcador (estilo `quote`). Elige la situación así:
     *  1. 0 vídeos (con o sin historial);
     *  2. primer día sin historial ([firstDay]);
     *  3. objetivo superado, clavado o a menos del 20 % ([goal]);
     *  4. tramo por vídeos y minutos: primeros vídeos, normal, mucho, brutal.
     *
     * Referencia de tono: «Hoy llevas 212. Eso son 47 minutos que nadie te va a devolver.»
     *
     * @param count vídeos de hoy.
     * @param totalMs tiempo de hoy en ms.
     * @param seed normalmente `Variants.daySeed(hoy)`.
     * @param goal objetivo diario («como mucho N»); `null` sin objetivo.
     * @param firstDay `true` si hoy es el primer día con datos (no hay medias todavía).
     */
    fun headline(count: Int, totalMs: Long, seed: Long, goal: Int? = null, firstDay: Boolean = false): String {
        val v = Words.videos(count)
        val n = Format.integer(count)
        val t = Words.duration(totalMs)
        val tCap = Format.capitalize(t)
        val singular = Words.isSingularDuration(totalMs)
        val hasTime = totalMs >= 60_000

        if (count <= 0) {
            return if (firstDay) {
                pick(seed, "today.zero.first", R.array.today_zero_first)
            } else {
                pick(seed, "today.zero", R.array.today_zero)
            }
        }

        if (firstDay) {
            val array = if (hasTime) R.array.today_first_day else R.array.today_first_day_notime
            return pick(seed, "today.firstDay", array, v, t)
        }

        if (goal != null && goal > 0) {
            val goalN = Format.integer(goal)
            when (goalState(count, goal)) {
                GoalState.OVER -> return pick(seed, "today.goal.over", R.array.today_goal_over, Words.videos(count - goal), goalN, n)

                GoalState.AT -> return pick(seed, "today.goal.at", R.array.today_goal_at, goalN)

                GoalState.NEAR -> {
                    val left = goal - count
                    val array = if (left == 1) R.array.today_goal_near_one else R.array.today_goal_near
                    return pick(seed, "today.goal.near", array, Words.videos(left), n, goalN)
                }

                GoalState.UNDER, GoalState.NONE -> Unit
            }
        }

        val metresValue = Equivalences.swipeMetres(count).roundToLong()
        val metres = plural(R.plurals.today_metres, metresValue, Format.integer(metresValue))
        val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive

        return when (Bands.of(count, totalMs)) {
            Band.ZERO, Band.FIRST -> {
                val array = if (hasTime) R.array.today_first else R.array.today_first_notime
                pick(seed, "today.first", array, v, t)
            }

            Band.NORMAL -> {
                val array = if (singular) R.array.today_normal_one else R.array.today_normal
                pick(seed, "today.normal", array, v, t, tCap)
            }

            Band.HIGH -> {
                val array = when {
                    singular && eq != null -> R.array.today_high_one
                    singular -> R.array.today_high_one_noeq
                    eq != null -> R.array.today_high
                    else -> R.array.today_high_noeq
                }
                pick(seed, "today.high", array, n, t, tCap, eq.orEmpty(), v, metres)
            }

            Band.EXTREME -> {
                val array = if (eq != null) R.array.today_extreme else R.array.today_extreme_noeq
                pick(seed, "today.extreme", array, v, tCap, eq.orEmpty(), metres)
            }
        }
    }

    /** Etiqueta de objetivo en la columna derecha: «OBJETIVO 150». */
    fun goalLabel(goal: Int): String = str(R.string.today_goal_label, Format.integer(goal))

    /** Estado frente al objetivo: «+62 PASADO», «EN EL LÍMITE», «QUEDAN 38», «QUEDA 1». */
    fun goalStatus(count: Int, goal: Int): String = when {
        count > goal -> str(R.string.today_goal_status_over, Format.integer(count - goal))
        count == goal -> str(R.string.today_goal_status_at)
        else -> plural(R.plurals.today_goal_status_left, goal - count, Format.integer(goal - count))
    }

    /** Lo mismo para TalkBack: «62 vídeos por encima del objetivo». */
    fun goalStatusDescription(count: Int, goal: Int): String = when {
        count > goal -> str(R.string.today_goal_status_over_description, Words.videos(count - goal), Format.integer(goal))
        count == goal -> str(R.string.today_goal_status_at_description, Format.integer(goal))
        else -> plural(R.plurals.today_goal_status_left_description, goal - count, Words.videos(goal - count), Format.integer(goal))
    }

    /** Línea bajo el marcador: «47 MIN DE PANTALLA · 4 COMPARTIDOS» (sin compartidos si son 0). */
    fun screenTime(totalMs: Long, shares: Int = 0): String =
        if (shares > 0) {
            str(R.string.today_screen_time_shares, Words.durationLabel(totalMs), sharesLabel(shares))
        } else {
            str(R.string.today_screen_time, Words.durationLabel(totalMs))
        }

    /**
     * Comparación con la media del mismo día de la semana. Para hoy, [untilNow] = `true` (hasta la
     * misma hora): «+34 % QUE TU MARTES MEDIO A ESTA HORA», «−12 % …», «COMO TU MARTES MEDIO…»,
     * «EL DOBLE QUE…», «AÚN NO HAY MARTES CON LOS QUE COMPARAR» (si [average] es `null`).
     *
     * @param today vídeos de hoy (hasta ahora).
     * @param average media de ese día de la semana (hasta la misma hora si [untilNow]); `null` si no hay datos suficientes.
     */
    fun comparison(today: Double, average: Double?, weekday: DayOfWeek, untilNow: Boolean = true): DeltaLine {
        val day = Format.weekday(weekday)
        val days = weekdayPlural(weekday)
        val daysUpper = Format.upper(days)
        val ref = str(if (untilNow) R.string.today_comparison_ref_now else R.string.today_comparison_ref, day)
        return when (val d = Deltas.of(today, average)) {
            Delta.NoBase -> DeltaLine(
                str(R.string.today_comparison_no_base, daysUpper),
                str(R.string.today_comparison_no_base_description, days),
                Direction.NONE,
            )

            Delta.BothZero -> Deltas.ratioLine(1.0, ref)

            Delta.FromZero -> if (untilNow) {
                DeltaLine(
                    str(R.string.today_comparison_from_zero_now, daysUpper),
                    str(R.string.today_comparison_from_zero_now_description, days),
                    Direction.UP,
                )
            } else {
                DeltaLine(
                    str(R.string.today_comparison_from_zero, daysUpper),
                    str(R.string.today_comparison_from_zero_description, days),
                    Direction.UP,
                )
            }

            is Delta.Ratio -> Deltas.ratioLine(d.ratio, ref)
        }
    }

    /** «HORA PUNTA 23:00 · 41 VÍDEOS». */
    fun peakHour(hour: Int, count: Int): String =
        Format.upper(str(R.string.today_peak_hour, Format.hour(hour), Words.videos(count)))

    /** Valor de la línea de plataforma: «120 · 26 MIN» (solo cifras y separador, sin palabras). */
    fun platformValue(count: Int, totalMs: Long): String = "${Format.integer(count)} · ${Words.durationLabel(totalMs)}"

    /**
     * Banda a sangre «desde que usas Reelómetro»: «DESDE EL 3 MAR: 12.408 VÍDEOS · 61 H»,
     * «DESDE HOY: …», «DESDE AYER: …», «DESDE EL 3 MAR 2025: …».
     */
    fun sinceBand(firstDay: LocalDate, today: LocalDate, count: Long, totalMs: Long): String =
        Format.upper(
            str(R.string.today_since_band, sinceWhen(firstDay, today, short = true), Words.videos(count), Words.durationLabel(totalMs)),
        )

    /** La banda dicha en frase (TalkBack): «Desde el 3 de marzo: 12.408 vídeos y 61 horas.». */
    fun sinceBandDescription(firstDay: LocalDate, today: LocalDate, count: Long, totalMs: Long): String =
        str(
            R.string.today_since_band_description,
            Format.capitalize(sinceWhen(firstDay, today, short = false)),
            Words.videos(count),
            Words.duration(totalMs),
        )

    /** Aviso con el servicio desconectado (después de haber funcionado). Va con el botón «CONECTAR». */
    fun serviceOff(seed: Long): String = pick(seed, "today.serviceOff", R.array.today_service_off)

    /** Aviso si el permiso nunca se ha concedido (se dijo «AHORA NO»). Va con «VER EL PERMISO». */
    fun serviceNeverEnabled(seed: Long): String = pick(seed, "today.serviceNever", R.array.today_service_never)

    /** Aviso si se han apagado todas las apps en Ajustes («Qué apps cuento»). */
    fun noPlatforms(seed: Long): String = pick(seed, "today.noPlatforms", R.array.today_no_platforms)

    // ------------------------------------------------------------------ piezas de la pantalla

    /** La frase bajo el marcador, entre comillas latinas: «Eso son 47 minutos…». */
    fun quoted(text: String): String = str(R.string.today_quoted, text)

    /** Cifra de la línea de tiempo: «47 MIN», «3,2 H». Con [screenTimeRest] forma [screenTime]. */
    fun screenTimeValue(totalMs: Long): String = Words.durationLabel(totalMs)

    /** Resto de la línea de tiempo: «DE PANTALLA», «DE PANTALLA · 4 COMPARTIDOS». */
    fun screenTimeRest(shares: Int = 0): String =
        if (shares > 0) str(R.string.today_screen_time_rest_shares, sharesLabel(shares)) else str(R.string.today_screen_time_rest)

    /** La línea de tiempo dicha (TalkBack): «47 minutos de pantalla. 4 vídeos compartidos.». */
    fun screenTimeDescription(totalMs: Long, shares: Int = 0): String {
        val time = Format.capitalize(Words.duration(totalMs))
        return if (shares > 0) {
            str(
                R.string.today_screen_time_description_shares,
                time,
                plural(R.plurals.today_shares_videos, shares, Format.integer(shares)),
            )
        } else {
            str(R.string.today_screen_time_description, time)
        }
    }

    /** Flecha de una comparación: «↑» si hay más, «↓» si hay menos; nada si es igual o no hay base. */
    fun arrow(direction: Direction): String? = when (direction) {
        Direction.UP -> "↑"
        Direction.DOWN -> "↓"
        Direction.SAME, Direction.NONE -> null
    }

    /** Tooltip de una hora de la línea: «41 vídeos\n23:00–24:00». */
    fun hourTooltip(hour: Int, count: Int): String = "${Words.videos(count)}\n${Format.hourRange(hour)}"

    /** Resumen hablado de la línea por horas. */
    fun hoursDescription(peakHour: Int?, peakCount: Int): String =
        if (peakHour == null || peakCount <= 0) str(R.string.today_hours_description_empty)
        else str(R.string.today_hours_description, Format.hour(peakHour), Words.videos(peakCount))

    /** Reparto por plataformas dicho: «TikTok, 120 vídeos; Reels, 64 vídeos.». */
    fun platformsDescription(items: List<Pair<String, Int>>): String =
        if (items.isEmpty()) EMPTY_PLATFORMS
        else items.joinToString("; ", postfix = ".") { (name, count) -> "$name, ${Words.videos(count)}" }

    /** Valor de una fila de categoría o creador: «120 · 26 MIN» (como [platformValue]). */
    fun rankValue(count: Int, totalMs: Long): String = platformValue(count, totalMs)

    /** Etiqueta pequeña de la banda: «DESDE EL 3 MAR», «DESDE AYER». Con [sinceBandValue] forma [sinceBand]. */
    fun sinceBandKicker(firstDay: LocalDate, today: LocalDate): String =
        Format.upper(sinceWhen(firstDay, today, short = true))

    /** Cifras de la banda: «12.408 VÍDEOS · 61 H». */
    fun sinceBandValue(count: Long, totalMs: Long): String =
        "${Format.upper(Words.videos(count))} · ${Words.durationLabel(totalMs)}"

    /** Acción de la banda para TalkBack. */
    val SINCE_BAND_ACTION: String get() = str(R.string.today_since_band_action)

    /** «4 COMPARTIDOS». */
    private fun sharesLabel(shares: Int): String = Format.upper(plural(R.plurals.today_shares, shares, Format.integer(shares)))

    private fun sinceWhen(firstDay: LocalDate, today: LocalDate, short: Boolean): String = when {
        !firstDay.isBefore(today) -> str(R.string.today_since_today)
        firstDay == today.minusDays(1) -> str(R.string.today_since_yesterday)
        short && firstDay.year != today.year -> str(R.string.today_since_date, Format.dayShortWithYear(firstDay))
        short -> str(R.string.today_since_date, Format.dayShort(firstDay))
        firstDay.year != today.year -> str(R.string.today_since_date, Format.dayFull(firstDay))
        else -> str(R.string.today_since_date, Format.dayMonth(firstDay))
    }

    /** Día de la semana en plural: «los lunes», «los sábados» (sin artículo: «lunes», «sábados»). */
    internal fun weekdayPlural(day: DayOfWeek): String = strArray(R.array.today_weekdays_plural)[day.value - 1]

    /** Lo mismo a partir del nombre del día ya escrito (`Format.weekday`): «lunes» → «lunes», «sábado» → «sábados». */
    internal fun weekdayPlural(day: String): String =
        DayOfWeek.entries.firstOrNull { Format.weekday(it) == day }?.let { weekdayPlural(it) } ?: day
}
