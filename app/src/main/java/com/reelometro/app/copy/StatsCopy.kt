package com.reelometro.app.copy

import com.reelometro.app.stats.Format
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** Textos de **Estadísticas** (semana / mes / año / siempre). */
object StatsCopy {

    const val TITLE = "Estadísticas"
    const val SECTION_DAILY = "CADA DÍA"
    const val SECTION_CALENDAR = "CALENDARIO"
    const val SECTION_PATTERNS = "PATRONES"
    const val SECTION_PLATFORMS = "PLATAFORMAS"
    const val SECTION_CATEGORIES = "CATEGORÍAS"
    const val SECTION_CREATORS = "CREADORES"

    /** Ranking de personas y grupos a los que envías vídeos en Instagram (ajuste «A quién envías»). */
    const val SECTION_RECIPIENTS = "A QUIÉN ENVÍAS"

    /** Pestaña: «SEMANA», «MES», «AÑO», «SIEMPRE». */
    fun tab(period: CopyPeriod): String = when (period) {
        CopyPeriod.WEEK -> Labels.WEEK
        CopyPeriod.MONTH -> Labels.MONTH
        CopyPeriod.YEAR -> Labels.YEAR
        CopyPeriod.ALL -> Labels.ALL
    }

    // ------------------------------------------------------------------ navegador de periodo

    /** «27 OCT – 2 NOV 2026»; si cruza de año, «29 DIC 2025 – 4 ENE 2026». */
    fun weekLabel(start: LocalDate, end: LocalDate): String {
        val range = Format.range(start, end)
        return Format.upper(if (start.year == end.year) "$range ${end.year}" else range)
    }

    /** «OCTUBRE 2026». */
    fun monthLabel(month: YearMonth): String = Format.upper("${Format.monthName(month.monthValue)} ${month.year}")

    /** «2026» (los años nunca llevan punto de millar). */
    fun yearLabel(year: Int): String = year.toString()

    /** «DESDE EL 3 MAR 2026»; «SIEMPRE» si aún no hay datos. */
    fun allLabel(firstDay: LocalDate?): String =
        if (firstDay == null) Labels.ALL else Format.upper("desde el ${Format.dayShortWithYear(firstDay)}")

    // ------------------------------------------------------------------ cifra principal

    /**
     * Línea bajo la cifra grande: «VÍDEOS · 9 H 12 MIN · 172/DÍA · 27 S POR VÍDEO».
     * Con 0 vídeos, solo «VÍDEOS».
     */
    fun summaryLine(count: Long, totalMs: Long, averagePerDay: Double, averageMsPerVideo: Long): String {
        if (count <= 0) return Labels.VIDEOS
        val label = if (count == 1L) Labels.VIDEO else Labels.VIDEOS
        return "$label · ${Format.upper(Format.duration(totalMs))} · ${Format.decimal(averagePerDay)}/DÍA · " +
            "${perVideoValue(averageMsPerVideo)} ${Labels.PER_VIDEO}"
    }

    /**
     * Tendencia frente al periodo anterior: «+12 % QUE LA SEMANA ANTERIOR», «COMO EL MES ANTERIOR»,
     * «EL DOBLE QUE EL AÑO ANTERIOR», «SIN DATOS DE LA SEMANA ANTERIOR». `null` en [CopyPeriod.ALL].
     *
     * [current] y [previous] pueden ser totales o medias diarias (lo que compare la capa de datos);
     * [previous] = `null` si no hay periodo anterior con datos.
     */
    fun trend(current: Double, previous: Double?, period: CopyPeriod): DeltaLine? {
        val prev = period.previous ?: return null
        return when (val d = Deltas.of(current, previous)) {
            Delta.NoBase -> DeltaLine(
                Format.upper("sin datos ${de(prev)}"),
                "Sin datos ${de(prev)}.",
                Direction.NONE,
            )

            Delta.BothZero -> Deltas.ratioLine(1.0, prev)
            Delta.FromZero -> DeltaLine(Format.upper("$prev fue a cero"), "${Format.capitalize(prev)} fue a cero.", Direction.UP)
            is Delta.Ratio -> Deltas.ratioLine(d.ratio, prev)
        }
    }

    /** La tendencia en frase, con un remate. `null` en [CopyPeriod.ALL]. */
    fun trendQuote(current: Double, previous: Double?, period: CopyPeriod, seed: Long): String? {
        val prev = period.previous ?: return null
        val line = trend(current, previous, period) ?: return null
        val desc = line.description
        val esta = if (period.feminine) "Esta" else "Este"
        val un = if (period.feminine) "una" else "un"
        val primera = if (period.feminine) "primera" else "primer"
        val contada = if (period.feminine) "contada" else "contado"
        val proxima = if (period.feminine) "La próxima" else "El próximo"
        return when (Deltas.of(current, previous)) {
            Delta.NoBase -> pick(
                seed, "stats.trend.none",
                "Es tu $primera ${period.noun} $contada. $proxima ya tendrá con qué compararse.",
                "Todavía no hay ${period.noun} anterior para comparar.",
                "Sin ${period.noun} anterior con datos. Esto es el punto de partida.",
            )

            Delta.FromZero -> pick(
                seed, "stats.trend.fromZero",
                "${Format.capitalize(prev)}, cero. $esta, no.",
                "Venías de $un ${period.noun} en blanco.",
                "Partías de cero. Ya no.",
            )

            Delta.BothZero -> pick(
                seed, "stats.trend.bothZero",
                "Como ${prev}: nada.",
                "Cero ahora y cero antes. Constancia, se llama.",
                "Sin vídeos ni ahora ni antes.",
            )

            is Delta.Ratio -> when (line.direction) {
                Direction.UP -> pick(
                    seed, "stats.trend.up",
                    "$desc La tendencia es clara, y no va hacia abajo.",
                    "$desc En bolsa sería una buena noticia.",
                    "$desc El algoritmo toma nota.",
                )

                Direction.DOWN -> pick(
                    seed, "stats.trend.down",
                    "$desc Bien visto. O, mejor dicho, menos visto.",
                    "$desc El algoritmo lo ha notado.",
                    "$desc Hay tendencias peores.",
                )

                else -> pick(
                    seed, "stats.trend.same",
                    "$desc Constancia, se llama.",
                    "$desc Ni más ni menos.",
                    "$desc Lo tuyo es la regularidad.",
                )
            }
        }
    }

    // ------------------------------------------------------------------ patrones

    /** Valor de racha: «4 DÍAS», «1 DÍA», «0 DÍAS». */
    fun streakValue(days: Int): String = Format.upper(Words.days(days.coerceAtLeast(0)))

    /** Línea de racha sin objetivo (lleva a Ajustes): «Ponte un objetivo y empezamos a contar.». */
    fun noGoalStreak(seed: Long): String = pick(
        seed, "stats.streak.noGoal",
        "Ponte un objetivo y empezamos a contar.",
        "Sin objetivo no hay racha. Elige un número y empezamos a contar.",
        "Las rachas van contra un límite. Ponte uno y empezamos a contar.",
    )

    /**
     * Frase de racha (días seguidos sin pasar del objetivo).
     * @param current racha actual en días; @param best mejor racha (≥ actual); @param goal objetivo diario.
     */
    fun streakQuote(current: Int, best: Int, goal: Int, seed: Long): String {
        val g = Format.integer(goal)
        val cur = current.coerceAtLeast(0)
        val top = best.coerceAtLeast(cur)
        return when {
            cur == 0 && top == 0 -> pick(
                seed, "stats.streak.never",
                "Ningún día sin pasar de $g, todavía. El objetivo sigue ahí.",
                "Todavía sin racha. Basta un día sin pasar de $g para empezarla.",
                "Racha a cero. Empieza el primer día que no pases de $g.",
            )

            cur == 0 -> pick(
                seed, "stats.streak.broken",
                "Racha rota. La mejor sigue en ${Words.days(top)}; ahí está, esperando.",
                "Se acabó la racha. El récord, ${Words.days(top)}, no se mueve.",
                "Racha a cero. Ya sabes que puedes llegar a ${Words.days(top)}.",
            )

            cur >= top -> pick(
                seed, "stats.streak.record",
                if (cur == 1) "Un día sin pasar de $g. Así empiezan las rachas."
                else "${Words.days(cur)} seguidos sin pasar de $g. Tu mejor racha es esta.",
                "Racha de ${Words.days(cur)}, la mejor hasta ahora.",
                "${Words.days(cur)} y contando. Es tu récord.",
            )

            else -> {
                val left = top - cur
                val faltan = if (left == 1) "Falta" else "Faltan"
                pick(
                    seed, "stats.streak.ongoing",
                    "Racha de ${Words.days(cur)}. El récord está en ${Words.days(top)}.",
                    "Racha de ${Words.days(cur)}. $faltan ${Words.days(left)} para igualar tu mejor marca.",
                    "${Words.days(cur)} y contando. La mejor fue de ${Words.days(top)}.",
                )
            }
        }
    }

    /** Valor de día débil (el de mayor media): «DOMINGO · 240 DE MEDIA». */
    fun weakDayValue(weekday: DayOfWeek, average: Double): String =
        Format.upper("${Format.weekday(weekday)} · ${Format.decimal(average)} de media")

    /** Frase de día débil. */
    fun weakDayQuote(weekday: DayOfWeek, average: Double, seed: Long): String {
        val day = Format.weekday(weekday)
        val days = TodayCopy.weekdayPlural(day)
        val avg = averageVideos(average)
        return pick(
            seed, "stats.weakDay",
            "Tu día débil: el $day, con $avg de media.",
            "Los $days caes más: $avg de media.",
            "Si hay un día peligroso, es el $day.",
            "El $day es tu día débil. Todo el mundo tiene uno.",
        )
    }

    /** Valor de hora punta: «23:00 · 41 VÍDEOS». */
    fun peakHourValue(hour: Int, count: Int): String = "${Format.hour(hour)} · ${Format.upper(Words.videos(count))}"

    /** Frase de hora punta según la franja (madrugada, mañana, sobremesa, tarde, noche). */
    fun peakHourQuote(hour: Int, seed: Long): String {
        val h = Math.floorMod(hour, 24)
        val hh = Format.hour(h)
        val at = Words.atHour(h)
        return when (h) {
            in 0..5 -> pick(
                seed, "stats.peak.night",
                "$hh. A esa hora solo quedáis tú y el algoritmo.",
                "Tu hora punta, $at. De madrugada, el feed no cierra.",
                "$hh: la hora en la que todo parece buena idea, incluido otro vídeo.",
            )

            in 6..11 -> pick(
                seed, "stats.peak.morning",
                "Tu hora punta, $at. Hay quien lee el periódico.",
                "$hh: el feed, como desayuno.",
                "Donde más ves es $at. Antes que el café, el pulgar.",
            )

            in 12..16 -> pick(
                seed, "stats.peak.afternoon",
                "$hh. La sobremesa manda.",
                "Tu hora punta cae en la sobremesa: $hh.",
                "$hh: café, sofá y feed.",
            )

            in 17..20 -> pick(
                seed, "stats.peak.evening",
                "$hh. Esa hora en la que el día ya no da para mucho más.",
                "Tu hora punta, $at. La tarde se va de quince en quince segundos.",
                "$hh: entre lo que tocaba hacer y la cena, el feed.",
            )

            else -> pick(
                seed, "stats.peak.late",
                "$hh. El clásico «uno más y me duermo».",
                "Tu hora punta, $at. La cama es el sitio favorito del algoritmo.",
                "$hh: la hora del último vídeo. Y del siguiente.",
            )
        }
    }

    /** «27 S»; a partir de un minuto, «1 MIN», «1 H 2 MIN». */
    fun perVideoValue(averageMs: Long): String =
        Format.upper(if (averageMs < 59_500) Format.seconds(averageMs) else Format.duration(averageMs))

    /** «172». */
    fun dailyAverageValue(average: Double): String = Format.decimal(average)

    /** «26 DE 31». */
    fun activeDaysValue(active: Int, total: Int): String = "${Format.integer(active)} DE ${Format.integer(total)}"

    /** «34 %» (cuota de una plataforma, categoría o creador). */
    fun shareValue(fraction: Double): String = Format.percent(fraction)

    // ------------------------------------------------------------------ piezas de la pantalla

    /** Etiquetas de las líneas de PATRONES (`ReceiptLine`, en frase). */
    const val LINE_PEAK_HOUR = "Hora punta"
    const val LINE_WEAK_DAY = "Día débil"
    const val LINE_WORST_DAY = "Peor día"
    const val LINE_ACTIVE_DAYS = "Días con vídeos"
    const val LINE_PER_VIDEO = "Tiempo por vídeo"
    const val LINE_SHARES = "Compartidos"
    const val LINE_STREAK = "Racha actual"
    const val LINE_BEST_STREAK = "Mejor racha"
    const val LINE_STREAK_NO_GOAL = "Racha"

    /** Dato de la cabecera de CADA DÍA cuando «siempre» se agrupa por semanas. */
    const val BY_WEEK = "POR SEMANAS"

    /** Acción de la línea del peor día para TalkBack. */
    const val OPEN_WORST_DAY = "Abrir ese día"

    /** Acción de la línea de racha sin objetivo para TalkBack (lleva a Ajustes). */
    const val SET_GOAL_ACTION = "Poner un objetivo en Ajustes"

    /** Navegador: «27 OCT – 2 NOV 2026», «NOVIEMBRE 2026», «2026», «DESDE EL 3 MAR 2026». */
    fun navigatorLabel(period: CopyPeriod, start: LocalDate, end: LocalDate, firstDay: LocalDate?): String = when (period) {
        CopyPeriod.WEEK -> weekLabel(start, end)
        CopyPeriod.MONTH -> monthLabel(YearMonth.from(start))
        CopyPeriod.YEAR -> yearLabel(start.year)
        CopyPeriod.ALL -> allLabel(firstDay)
    }

    /** contentDescription de la cifra grande: «1.204 vídeos en este periodo». */
    fun countDescription(count: Long): String = "${Words.videos(count)} en este periodo"

    /** Tooltip de una barra o celda: «212 vídeos\nmar 3 nov». */
    fun dayTooltip(date: LocalDate, count: Int): String = "${Words.videos(count)}\n${tooltipDate(date)}"

    /** Tooltip de un día anterior al primer registro: «sin datos\nmar 3 nov». */
    fun untrackedTooltip(date: LocalDate): String = "sin datos\n${tooltipDate(date)}"

    /** Tooltip de un día que todavía no ha llegado: «por llegar\nmié 4 nov». */
    fun futureTooltip(date: LocalDate): String = "por llegar\n${tooltipDate(date)}"

    /** Tooltip de una barra semanal: «1.204 vídeos\nsemana del 27 oct». */
    fun weekTooltip(monday: LocalDate, count: Int): String = "${Words.videos(count)}\nsemana del ${Format.dayShort(monday)}"

    /** Valor del peor día: «MAR 3 NOV · 412» (con año si no es el de [today]). */
    fun worstDayValue(date: LocalDate, count: Int, today: LocalDate): String {
        val day = if (date.year != today.year) Format.dayShortWithYear(date) else Format.dayShort(date)
        return Format.upper("${Format.weekdayShort(date.dayOfWeek)} $day · ${Format.integer(count)}")
    }

    /** «1.204 · 34 %» (vídeos y cuota de una plataforma o categoría). */
    fun countShareValue(count: Int, fraction: Double): String = "${Format.integer(count)} · ${Format.percent(fraction)}"

    /** «4» (veces que se pulsó compartir). */
    fun sharesValue(shares: Int): String = Format.integer(shares)

    /** Valor de una fila de «A QUIÉN ENVÍAS»: los vídeos enviados a esa persona o grupo («12»). */
    fun recipientValue(count: Int): String = Format.integer(count)

    /** Resumen hablado del calendario: «Calendario: 26 días con vídeos de 31.». */
    fun calendarDescription(activeDays: Int, days: Int): String =
        "Calendario: ${Words.days(activeDays)} con vídeos de ${Format.integer(days)}."

    private fun tooltipDate(date: LocalDate): String = "${Format.weekdayShort(date.dayOfWeek)} ${Format.dayShort(date)}"

    // ------------------------------------------------------------------ vacíos

    /**
     * Periodo sin un solo vídeo. [current] = `true` si es el periodo en curso («esta semana»),
     * `false` si es uno pasado («esa semana»).
     */
    fun emptyPeriod(period: CopyPeriod, current: Boolean, seed: Long): String {
        if (period == CopyPeriod.ALL) {
            return pick(
                seed, "stats.empty.all",
                "Tu historial está vacío. Todo empieza con el primer vídeo.",
                "Todavía no hay nada que contar.",
                "Historial en blanco. De momento.",
            )
        }
        val which = if (current) period.thisOne else period.thatOne
        return if (current) {
            pick(
                seed, "stats.empty.current",
                "Ni un vídeo $which. Si es verdad, que conste en acta.",
                "Nada que contar $which, de momento.",
                "${Format.capitalize(which)}, en blanco. O no has visto nada, o el servicio estaba apagado.",
            )
        } else {
            pick(
                seed, "stats.empty.past",
                "Nada que contar $which.",
                "${Format.capitalize(which)}, en blanco. O no viste nada, o el servicio estaba apagado.",
                "Ni un vídeo $which. Que conste en acta.",
            )
        }
    }

    /** Periodo anterior al primer día con datos. */
    fun beforeHistory(firstDay: LocalDate, seed: Long): String = pick(
        seed, "stats.beforeHistory",
        "Antes del ${Format.dayFull(firstDay)} no contaba nada. Todavía no nos conocíamos.",
        "Esto es de antes de Reelómetro. Aquí no hay nada que ver, literalmente.",
        "Tu historial empieza el ${Format.dayFull(firstDay)}. Antes, silencio.",
    )

    // ------------------------------------------------------------------ accesibilidad de gráficas

    /** Barra diaria: «Martes 3 de noviembre: 212 vídeos, el día con más.». */
    fun barDescription(date: LocalDate, count: Int, isMax: Boolean): String =
        "${Format.capitalize(Format.dayLong(date))}: ${Words.videos(count)}${if (isMax && count > 0) ", el día con más" else ""}."

    /** Punto de la línea horaria: «23:00: 41 vídeos, la hora con más.». */
    fun hourDescription(hour: Int, count: Int, isMax: Boolean = false): String =
        "${Format.hour(hour)}: ${Words.videos(count)}${if (isMax && count > 0) ", la hora con más" else ""}."

    /** Celda del mapa de calor: «Martes 3 de noviembre: 212 vídeos.». */
    fun heatCellDescription(date: LocalDate, count: Int): String =
        "${Format.capitalize(Format.dayLong(date))}: ${Words.videos(count)}."

    /** Resumen hablado de la gráfica diaria: «1.204 vídeos. El día con más, martes 3 de noviembre, con 212.». */
    fun dailyBarsSummary(total: Long, maxDate: LocalDate?, maxCount: Int): String {
        val head = "${Words.videos(total)}."
        if (maxDate == null || maxCount <= 0) return head
        return "$head El día con más, ${Format.dayLong(maxDate)}, con ${Format.integer(maxCount)}."
    }

    /** «240 vídeos», «3,2 vídeos», «1 vídeo». */
    internal fun averageVideos(average: Double): String {
        val text = Format.decimal(average)
        return "$text${Words.NBSP}${if (text == "1") "vídeo" else "vídeos"}"
    }

    /** «de la semana anterior», «del mes anterior». */
    internal fun de(phrase: String): String = if (phrase.startsWith("el ")) "del ${phrase.removePrefix("el ")}" else "de $phrase"

    // ------------------------------------------------------------------ semana frente a la anterior

    /** Sección de la comparativa semanal (pestaña SEMANA). Corta: cabe en una línea al 130 %. */
    const val SECTION_WEEK_VS = "CONTRA LA SEMANA ANTERIOR"

    /** Leyenda de la línea fantasma («- - SEMANA ANTERIOR»). */
    const val GHOST_LEGEND = "SEMANA ANTERIOR"

    const val LINE_THIS_WEEK_SO_FAR = "Esta semana, hasta ahora"
    const val LINE_THAT_WEEK = "Esa semana"
    const val LINE_PREVIOUS_SAME_STRETCH = "La anterior, a estas alturas"
    const val LINE_PREVIOUS = "La anterior"
    const val LINE_PREVIOUS_WHOLE = "La anterior, entera"
    const val LINE_DIFFERENCE = "Diferencia"
    const val LINE_DIFFERENCE_PER_DAY = "Diferencia por día"

    /** «1.204». */
    fun weekTotalValue(count: Int): String = Format.integer(count)

    /** «+12 %», «−8 %», «±0 %». */
    fun weekDifferenceValue(change: Double): String = Format.signedPercent(change)

    /**
     * Tooltip de un día de la comparativa: «125 vídeos\nmar 3 nov · la anterior, 140»; sin dato,
     * «por llegar» o «sin datos».
     */
    fun ghostTooltip(date: LocalDate, count: Int?, previous: Int?, today: LocalDate): String {
        val head = when {
            count != null -> Words.videos(count)
            date.isAfter(today) -> "por llegar"
            else -> "sin datos"
        }
        val before = if (previous != null) Format.integer(previous) else "sin datos"
        return "$head\n${tooltipDate(date)} · la anterior, $before"
    }

    /**
     * Resumen hablado de la comparativa: «Esta semana llevas 205 vídeos; la anterior, a estas
     * alturas, 230. Un 11 % menos.».
     *
     * @param inProgress la semana mostrada es la actual (y sigue en curso).
     * @param perDay la diferencia compara medias por día registrado.
     */
    fun weekComparisonDescription(total: Int, previousTotal: Int?, change: Double?, inProgress: Boolean, perDay: Boolean): String {
        val head = if (inProgress) "Esta semana llevas ${Words.videos(total)}" else "Esa semana, ${Words.videos(total)}"
        val before = when {
            previousTotal == null -> "; de la anterior no hay datos de esos días."
            inProgress -> "; la anterior, a estas alturas, ${Format.integer(previousTotal)}."
            else -> "; la anterior, ${Format.integer(previousTotal)}."
        }
        val diff = change?.let { c ->
            val pct = (c * 100).roundToInt()
            val tail = if (perDay) " por día" else ""
            when {
                pct > 0 -> " Un ${Format.percent(abs(c))} más$tail."
                pct < 0 -> " Un ${Format.percent(abs(c))} menos$tail."
                else -> " Prácticamente igual."
            }
        }.orEmpty()
        return "$head$before$diff"
    }

    // ------------------------------------------------------------------ accesibilidad

    /** Acción de TalkBack para abrir un día desde la gráfica: «Abrir el martes 3 de noviembre». */
    fun openDayAction(date: LocalDate): String = "Abrir el ${Format.dayLong(date)}"

    /**
     * Línea bajo la cifra grande, dicha: «1.204 vídeos, 9 horas y 12 minutos, 172 al día, 27 segundos
     * por vídeo.» (la versión visible usa abreviaturas y «·»).
     */
    fun summaryDescription(count: Long, totalMs: Long, averagePerDay: Double, averageMsPerVideo: Long): String {
        if (count <= 0) return "${Words.videos(0)}."
        val perVideo = Words.duration(averageMsPerVideo)
        return "${Words.videos(count)}, ${Words.duration(totalMs)}, ${Format.decimal(averagePerDay)} al día, $perVideo por vídeo."
    }
}
