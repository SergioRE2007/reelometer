package com.reelometro.app.copy

import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Textos de la pantalla **Hoy** (y del detalle de hoy). Las frases con variantes reciben `seed`:
 * pasa `Variants.daySeed(hoy)` para que no cambien al recomponer y sí de un día a otro.
 */
object TodayCopy {

    const val SECTION_PLATFORMS = "PLATAFORMAS"
    const val SECTION_HOURS = "TU DÍA, HORA A HORA"
    const val SECTION_CATEGORIES = "LO QUE MÁS VES"
    const val SECTION_CREATORS = "QUIÉN TE LO PONE"

    const val EMPTY_PLATFORMS = "Ninguna app todavía."
    const val EMPTY_HOURS = "Sin actividad todavía."
    const val EMPTY_CATEGORIES = "Sin categorías todavía."
    const val EMPTY_CREATORS = "Sin creadores todavía."

    /** Etiqueta del marcador con el servicio desconectado (fichas en blanco «– – –»). */
    const val SERVICE_OFF_LABEL = Labels.NO_SIGNAL

    /** contentDescription del marcador sin servicio. */
    const val SERVICE_OFF_DESCRIPTION = "Sin señal: el servicio de Reelómetro está desconectado."

    /** Cabecera: «HOY · MARTES 3 NOV». */
    fun header(date: LocalDate): String =
        "${Labels.TODAY} · ${Format.upper("${Format.weekday(date.dayOfWeek)} ${Format.dayShort(date)}")}"

    /** Etiqueta sobre el marcador: «VÍDEOS» («VÍDEO» si es exactamente uno). */
    fun counterLabel(count: Int): String = if (count == 1) Labels.VIDEO else Labels.VIDEOS

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
        val son = if (Words.isSingularDuration(totalMs)) "es" else "son"
        val hasTime = totalMs >= 60_000

        if (count <= 0) {
            return if (firstDay) {
                pick(
                    seed, "today.zero.first",
                    "Todo listo. El primer vídeo que veas abre la cuenta.",
                    "Marcador a cero. A partir de aquí, todo cuenta.",
                    "Aquí aparecerá cada vídeo que veas. De momento, silencio.",
                )
            } else {
                pick(
                    seed, "today.zero",
                    "De momento, ganas tú.",
                    "Cero. El algoritmo todavía te está esperando.",
                    "Marcador a cero. Que dure lo que tenga que durar.",
                    "Ni un vídeo. Esto también se cuenta.",
                    "Hoy, de momento, nada que declarar.",
                )
            }
        }

        if (firstDay) {
            return pick(
                seed, "today.firstDay",
                if (hasTime) "Primer día contado: $v y $t. Mañana ya tendrás con qué compararte."
                else "Primer día contado: $v. Mañana ya tendrás con qué compararte.",
                "$v para empezar. Todavía no hay media; mañana, sí.",
                if (hasTime) "Así empieza tu historial: $v en $t." else "Así empieza tu historial: $v.",
                "Día uno. A partir de hoy, cada vídeo queda apuntado.",
            )
        }

        if (goal != null && goal > 0) {
            val goalN = Format.integer(goal)
            when (goalState(count, goal)) {
                GoalState.OVER -> {
                    val over = Words.videos(count - goal)
                    return pick(
                        seed, "today.goal.over",
                        "$over por encima de tu objetivo. El objetivo era otro.",
                        "Objetivo: $goalN. Realidad: $n.",
                        "Te has pasado en $over. Nadie lleva la cuenta. Bueno, yo sí.",
                        "Has superado el objetivo. En esto, superar no era la idea.",
                        "$n de $goalN. Las cuentas no salen, pero las llevo.",
                    )
                }

                GoalState.AT -> return pick(
                    seed, "today.goal.at",
                    "Justo en el límite: $goalN. Ni uno más, de momento.",
                    "$goalN de $goalN. Lo que venga ahora ya es de propina.",
                    "Objetivo clavado. A partir de aquí, cada vídeo va a números rojos.",
                )

                GoalState.NEAR -> {
                    val left = goal - count
                    val leftV = Words.videos(left)
                    val quedan = if (left == 1) "queda" else "quedan"
                    return pick(
                        seed, "today.goal.near",
                        "Te $quedan $leftV para el objetivo. Tú sabrás.",
                        "$n de $goalN. Queda poco margen.",
                        "A $leftV del límite. El marcador no juzga; solo cuenta.",
                        "Margen restante: $leftV. Gástalo como quieras.",
                    )
                }

                GoalState.UNDER, GoalState.NONE -> Unit
            }
        }

        val metres = Format.integer(Equivalences.swipeMetres(count).roundToLong())
        val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive

        return when (Bands.of(count, totalMs)) {
            Band.ZERO, Band.FIRST -> pick(
                seed, "today.first",
                "$v. Calentando.",
                if (hasTime) "Has empezado suave: $t." else "Has empezado suave.",
                if (hasTime) "$v y $t. Nadie lo notaría." else "$v. Nadie lo notaría.",
                "Poca cosa, por ahora: $v.",
                "Esto acaba de empezar. Por ahora, $v.",
            )

            Band.NORMAL -> pick(
                seed, "today.normal",
                "Eso $son $t que nadie te va a devolver.",
                "$tCap. El tiempo vuela, sobre todo a trozos de quince segundos.",
                "$v, uno detrás de otro. Como las pipas.",
                "Llevas $t. El día todavía no ha dicho su última palabra.",
                "$v. Ni tantos ni tan pocos, de momento.",
            )

            Band.HIGH -> pick(
                seed, "today.high",
                "Hoy llevas $n. Eso $son $t que nadie te va a devolver.",
                if (eq != null) "$tCap. Te daba para $eq." else "$tCap de pantalla. Lo que se dice un rato.",
                "$v. A estas alturas, el algoritmo ya sabe cómo te gusta el café.",
                "$v: unos $metres${Words.NBSP}metros de pulgar.",
                "$tCap de pantalla, en dosis de quince segundos.",
            )

            Band.EXTREME -> pick(
                seed, "today.extreme",
                "$v. El algoritmo te manda saludos.",
                "$tCap. Esto ya no es un rato; es un turno.",
                "Hoy llevas $v. Si esto fuera un trabajo, ya tocaría pedir horas extra.",
                if (eq != null) "$tCap de vídeos. Te daba para $eq." else "$tCap de vídeos. Y subiendo.",
                "$v. Tu pulgar ha recorrido $metres${Words.NBSP}metros. Tú, bastantes menos.",
            )
        }
    }

    /** Etiqueta de objetivo en la columna derecha: «OBJETIVO 150». */
    fun goalLabel(goal: Int): String = "${Labels.GOAL} ${Format.integer(goal)}"

    /** Estado frente al objetivo: «+62 PASADO», «EN EL LÍMITE», «QUEDAN 38», «QUEDA 1». */
    fun goalStatus(count: Int, goal: Int): String = when {
        count > goal -> "+${Format.integer(count - goal)}${Words.NBSP}PASADO"
        count == goal -> "EN EL LÍMITE"
        goal - count == 1 -> "QUEDA 1"
        else -> "QUEDAN ${Format.integer(goal - count)}"
    }

    /** Lo mismo para TalkBack: «62 vídeos por encima del objetivo». */
    fun goalStatusDescription(count: Int, goal: Int): String = when {
        count > goal -> "${Words.videos(count - goal)} por encima del objetivo de ${Format.integer(goal)}"
        count == goal -> "Justo en el objetivo de ${Format.integer(goal)}"
        goal - count == 1 -> "Queda 1${Words.NBSP}vídeo para el objetivo de ${Format.integer(goal)}"
        else -> "Quedan ${Words.videos(goal - count)} para el objetivo de ${Format.integer(goal)}"
    }

    /** Línea bajo el marcador: «47 MIN DE PANTALLA · 4 COMPARTIDOS» (sin compartidos si son 0). */
    fun screenTime(totalMs: Long, shares: Int = 0): String {
        val time = "${Words.durationLabel(totalMs)} DE PANTALLA"
        return if (shares > 0) "$time · ${Format.upper(Words.count(shares, "compartido", "compartidos"))}" else time
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
        val days = weekdayPlural(day)
        val ref = if (untilNow) "tu $day medio a esta hora" else "tu $day medio"
        return when (val d = Deltas.of(today, average)) {
            Delta.NoBase -> DeltaLine(
                "AÚN NO HAY ${Format.upper(days)} CON LOS QUE COMPARAR",
                "Aún no hay $days con los que comparar.",
                Direction.NONE,
            )

            Delta.BothZero -> Deltas.ratioLine(1.0, ref)

            Delta.FromZero -> if (untilNow) {
                DeltaLine(
                    "TUS ${Format.upper(days)}, A ESTA HORA, SUELEN IR A CERO",
                    "Tus $days, a esta hora, suelen ir a cero.",
                    Direction.UP,
                )
            } else {
                DeltaLine("TUS ${Format.upper(days)} SUELEN IR A CERO", "Tus $days suelen ir a cero.", Direction.UP)
            }

            is Delta.Ratio -> Deltas.ratioLine(d.ratio, ref)
        }
    }

    /** «HORA PUNTA 23:00 · 41 VÍDEOS». */
    fun peakHour(hour: Int, count: Int): String =
        "${Labels.PEAK_HOUR} ${Format.hour(hour)} · ${Format.upper(Words.videos(count))}"

    /** Valor de la línea de plataforma: «120 · 26 MIN». */
    fun platformValue(count: Int, totalMs: Long): String = "${Format.integer(count)} · ${Words.durationLabel(totalMs)}"

    /**
     * Banda a sangre «desde que usas Reelómetro»: «DESDE EL 3 MAR: 12.408 VÍDEOS · 61 H»,
     * «DESDE HOY: …», «DESDE AYER: …», «DESDE EL 3 MAR 2025: …».
     */
    fun sinceBand(firstDay: LocalDate, today: LocalDate, count: Long, totalMs: Long): String =
        "${Format.upper(sinceWhen(firstDay, today, short = true))}: ${Format.upper(Words.videos(count))} · ${Words.durationLabel(totalMs)}"

    /** La banda dicha en frase (TalkBack): «Desde el 3 de marzo: 12.408 vídeos y 61 horas.». */
    fun sinceBandDescription(firstDay: LocalDate, today: LocalDate, count: Long, totalMs: Long): String =
        "${Format.capitalize(sinceWhen(firstDay, today, short = false))}: ${Words.videos(count)} y ${Words.duration(totalMs)}."

    /** Aviso con el servicio desconectado (después de haber funcionado). Va con el botón «CONECTAR». */
    fun serviceOff(seed: Long): String = pick(
        seed, "today.serviceOff",
        "Sin el permiso de accesibilidad no veo nada. Que, por otra parte, es lo que esperas de una app así.",
        "El servicio está desconectado. Lo que veas ahora mismo no cuenta. Literalmente.",
        "Sin permiso, el marcador se queda a oscuras. Conéctalo y seguimos donde lo dejamos.",
        "Android ha desconectado el servicio. A veces lo hace. Un toque y vuelve.",
        "Ahora mismo podrías ver mil vídeos y aquí seguiría sin pasar nada.",
    )

    /** Aviso si el permiso nunca se ha concedido (se dijo «AHORA NO»). Va con «VER EL PERMISO». */
    fun serviceNeverEnabled(seed: Long): String = pick(
        seed, "today.serviceNever",
        "Para empezar a contar necesito un permiso. Te explico cuál y para qué, sin letra pequeña.",
        "Todavía no tengo permiso para mirar, así que no cuento nada. Tú decides.",
        "Sin el permiso de accesibilidad, este marcador es decoración. Bonita, pero decoración.",
    )

    /** Aviso si se han apagado todas las apps en Ajustes («Qué apps cuento»). */
    fun noPlatforms(seed: Long): String = pick(
        seed, "today.noPlatforms",
        "Has apagado todas las apps en Ajustes. Así no hay nada que contar.",
        "No estoy contando ninguna app. Así cualquiera.",
        "Ninguna app activada en Ajustes. El marcador, de vacaciones.",
    )

    // ------------------------------------------------------------------ piezas de la pantalla

    /** La frase bajo el marcador, entre comillas latinas: «Eso son 47 minutos…». */
    fun quoted(text: String): String = "«$text»"

    /** Cifra de la línea de tiempo: «47 MIN», «3,2 H». Con [screenTimeRest] forma [screenTime]. */
    fun screenTimeValue(totalMs: Long): String = Words.durationLabel(totalMs)

    /** Resto de la línea de tiempo: «DE PANTALLA», «DE PANTALLA · 4 COMPARTIDOS». */
    fun screenTimeRest(shares: Int = 0): String =
        if (shares > 0) "DE PANTALLA · ${Format.upper(Words.count(shares, "compartido", "compartidos"))}" else "DE PANTALLA"

    /** La línea de tiempo dicha (TalkBack): «47 minutos de pantalla. 4 vídeos compartidos.». */
    fun screenTimeDescription(totalMs: Long, shares: Int = 0): String {
        val time = "${Format.capitalize(Words.duration(totalMs))} de pantalla."
        return if (shares > 0) "$time ${Words.count(shares, "vídeo compartido", "vídeos compartidos")}." else time
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
        if (peakHour == null || peakCount <= 0) "Vídeos por hora: sin actividad todavía."
        else "Vídeos por hora. Hora punta: ${Format.hour(peakHour)}, con ${Words.videos(peakCount)}."

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
    const val SINCE_BAND_ACTION = "Ver tus estadísticas"

    private fun sinceWhen(firstDay: LocalDate, today: LocalDate, short: Boolean): String = when {
        !firstDay.isBefore(today) -> "desde hoy"
        firstDay == today.minusDays(1) -> "desde ayer"
        short && firstDay.year != today.year -> "desde el ${Format.dayShortWithYear(firstDay)}"
        short -> "desde el ${Format.dayShort(firstDay)}"
        firstDay.year != today.year -> "desde el ${Format.dayFull(firstDay)}"
        else -> "desde el ${firstDay.dayOfMonth} de ${Format.monthName(firstDay.monthValue)}"
    }

    /** «lunes» → «lunes», «sábado» → «sábados». */
    internal fun weekdayPlural(day: String): String = if (day.endsWith("s")) day else "${day}s"
}
