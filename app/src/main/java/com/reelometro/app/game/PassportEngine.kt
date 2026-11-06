package com.reelometro.app.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Motor PURO del modo Pasaporte: historial + objetivo + hoy + memoria → [PassportState] y memoria
 * actualizada. Sin Android, sin E/S y determinista (mismo [GameInput], mismo [GameResult]); calcular
 * otra vez con la memoria que devuelve no cambia nada (idempotente).
 *
 * ## Días
 * - **Primer registro**: el primer día con vídeos. Desde ese día, los días sin vídeos cuentan como 0
 *   (igual que las rachas de `stats/StatsMath`): la app no puede saber si el servicio estaba apagado.
 * - **Día cerrado**: del primer registro a ayer. Hoy no cuenta hasta que acaba, salvo para empeorar
 *   (si ya se ha pasado de la referencia).
 *
 * ## Referencia diaria
 * - Con objetivo: el objetivo, para todos los días.
 * - Sin objetivo: la media de vídeos de los 28 días que acaban 8 días antes (las 4 semanas anteriores
 *   a la ventana de 7 días que termina ayer), redondeada y como mínimo 1, contando solo días con
 *   registro. Si en esos 28 días hay menos de [PassportRules.MIN_BASELINE_DAYS] con registro, no hay
 *   referencia: Flap está «calentando» y ese día no da experiencia ni rachas. Cada día se juzga con
 *   **su** referencia (media móvil), así lo ganado en un día cerrado no cambia al cambiar la media.
 *
 * ## Flap
 * Ventana = los 7 días cerrados anteriores (si hoy ya se pasó de la referencia, la ventana es de hace
 * 6 días a hoy, con hoy en contra). Se cuentan los días de la ventana con registro que se pasaron de
 * la referencia de hoy: 0–1 EN_FORMA, 2–3 NORMAL, 4–5 CANSADO, 6–7 ZOMBI (con la ventana entera,
 * lo mismo que 6–7 / 4–5 / 2–3 / 0–1 días en la referencia o por debajo). Los días anteriores al
 * primer registro no cuentan en contra. Nunca muere.
 *
 * ## Experiencia ([XpBreakdown]) — nunca por vídeos vistos
 * - +[PassportRules.DAY_XP] por día cerrado en su referencia o por debajo.
 * - Rachas: al llegar a 3, 7, 14 y 30 días seguidos, +15/+30/+60/+120; después, +120 cada 30 días.
 *   Una racha rota no quita nada; la siguiente vuelve a dar bonificaciones.
 * - +[PassportRules.CHALLENGE_XP] por reto semanal cumplido.
 * - Lo que dé cada sello conseguido ([StampId.xp]; los irónicos, 0).
 * - Nivel = el más alto entre el de la experiencia actual y el más alto guardado: si cambias el
 *   objetivo y la experiencia baja, el nivel no baja ([LevelProgress.xp] no baja del mínimo del nivel).
 *
 * ## Sellos (fecha = el día en que se cumplió por primera vez; una vez guardada, no cambia)
 * - PRIMER_DIA: primer día cerrado en su referencia o por debajo.
 * - MEDIO_DIA: día cerrado por debajo de la mitad de su referencia (un día a 0 solo si es «en blanco»).
 * - DIA_EN_BLANCO: día cerrado a 0 con vídeos algún día antes **y** algún día después (sin eso no se
 *   distingue de un servicio apagado; por eso puede aparecer con retraso, con la fecha de aquel día).
 * - FIN_DE_SEMANA: sábado y domingo de la misma semana bajo su referencia (fecha: el domingo).
 * - RACHA_3/7/14/30: la racha llega a esa longitud (fecha: el día que la completa).
 * - SEMANA_MENOS_20: semana (lunes–domingo) cerrada con un 20 % menos o más que la anterior, que
 *   tiene que tener registro los 7 días y algún vídeo (fecha: el domingo).
 * - MES_A_LA_BAJA: mes natural cerrado con menos vídeos **por día** que el anterior, que necesita
 *   [PassportRules.MONTH_MIN_TRACKED_DAYS] días con registro (fecha: el último día del mes).
 * - PRIMER_RETO / CUATRO_RETOS: primer y cuarto reto cumplidos (fecha: el domingo de ese reto).
 * - REGRESO: Flap pasa de ZOMBI a NORMAL o EN_FORMA (con el estado de cada día al empezar).
 * - PRIMERA_SEMANA: 7 días con registro desde el primero (fecha: el séptimo, ya cerrado).
 * - WRAPPED_COMPARTIDO / TICKET_GUARDADO: primera vez del [GameEvent] correspondiente.
 * - PRIMER_ASCENSO: el día en que la experiencia acumulada llega a FRECUENTE (hoy si el nivel guardado
 *   ya lo era y la experiencia actual no llega).
 * - Irónicos, sin experiencia ni barra de progreso (no animan a ver más): MADRUGADOR, MIL_VIDEOS,
 *   DIEZ_MIL_VIDEOS (total acumulado), TRES_PLATAFORMAS, CINCO_CATEGORIAS, MARATON.
 * - Progreso de los pendientes: rachas (racha actual), PRIMERA_SEMANA y CUATRO_RETOS.
 *
 * ## Reto semanal ([WeeklyChallenge])
 * - Fantasma = la semana anterior entera. Hace falta que tenga [PassportRules.MIN_GHOST_DAYS] días
 *   con registro y algún vídeo; si le faltan días, su total se estima con su media diaria.
 * - Dificultad según los retos anteriores ([PassportRules.reductionPercent]): 0 % («menos que la
 *   anterior»: tope = fantasma − 1), −10 % si ganaste el último, −20 % si ganaste los 3 últimos. Si
 *   la semana fantasma ya estaba dentro del objetivo (≤ 7 × objetivo), no se aprieta más: 0 %.
 * - EN_CURSO hasta el domingo; FALLADO en cuanto el total pasa del tope; al cerrar la semana pasa a
 *   [PassportState.pastChallenges] como [ChallengeResult] (cumplido si total ≤ tope) y se guarda.
 *   Las semanas pasadas sin reto guardado se cierran en orden, aunque no se abriera la app.
 *
 * ## Celebraciones
 * Pendientes, en orden: sellos sin celebrar (por fecha), retos ganados sin celebrar (por semana) y,
 * al final, el nivel más alto si no se ha celebrado. En el primer cálculo (memoria sin `since`), lo
 * conseguido antes de ayer se da por celebrado: al estrenar el pasaporte con historial no sale una
 * avalancha (el nivel sí se celebra una vez).
 */
object PassportEngine {
    fun compute(input: GameInput): GameResult = Calculation(input).calculate()
}

private enum class XpSource { DAYS, STREAKS, CHALLENGES, STAMPS }

private class XpEntry(val date: LocalDate, val source: XpSource, val amount: Int)

/** Vídeos y días con registro de un tramo. */
private class Tally(val videos: Long, val days: Int)

private class ChallengeSetup(
    val percent: Int,
    val ghostTotal: Int,
    val target: Int,
    /** Vídeos reales de los días con registro de la semana fantasma. */
    val ghostVideos: Long,
    /** Días con registro de la semana fantasma. */
    val ghostDays: Int,
)

private class Calculation(input: GameInput) {
    private val today: LocalDate = input.today
    private val goal: Int? = input.goal?.takeIf { it > 0 }
    private val memory: PassportMemory = input.memory
    private val history: GameHistory = input.history
    private val counts: Map<LocalDate, Int> =
        input.history.counts.filter { (date, videos) -> videos > 0 && !date.isAfter(input.today) }
    private val first: LocalDate? = counts.keys.minOrNull()
    private val lastVideoDay: LocalDate? = counts.keys.maxOrNull()
    private val firstEpoch: Long = first?.toEpochDay() ?: 0L
    private val todayEpoch: Long = today.toEpochDay()

    /** Días del primer registro a hoy, ambos incluidos (0 sin registros). */
    private val span: Int = if (first == null) 0 else (todayEpoch - firstEpoch + 1).toInt()

    /** `prefix[i]` = vídeos del primer registro hasta el día `i − 1` desde él. */
    private val prefix: LongArray = LongArray(span + 1).also { p ->
        for (i in 0 until span) p[i + 1] = p[i] + (counts[LocalDate.ofEpochDay(firstEpoch + i)] ?: 0)
    }

    /** Sellos que se cumplen según el historial (antes de mirar la memoria). */
    private val computed = HashMap<StampId, LocalDate>()

    fun calculate(): GameResult {
        val ledger = mutableListOf<XpEntry>()

        // Días cerrados: experiencia por día, rachas y sellos de día.
        var streakRun = 0
        val f = first
        if (f != null) {
            var day: LocalDate = f
            while (day.isBefore(today)) {
                val ref = reference(day)
                val videos = videosOn(day)
                if (ref != null && videos <= ref) {
                    streakRun++
                    ledger += XpEntry(day, XpSource.DAYS, PassportRules.DAY_XP)
                    val bonus = PassportRules.streakBonus(streakRun)
                    if (bonus > 0) ledger += XpEntry(day, XpSource.STREAKS, bonus)
                    val streakStamp = PassportRules.STREAK_STAMPS[streakRun]
                    if (streakStamp != null) mark(streakStamp, day)
                    mark(StampId.PRIMER_DIA, day)
                    if (day.dayOfWeek == DayOfWeek.SUNDAY) {
                        val saturday = day.minusDays(1)
                        if (isTracked(saturday) && isUnder(saturday)) mark(StampId.FIN_DE_SEMANA, day)
                    }
                } else {
                    streakRun = 0
                }
                if (ref != null && videos.toLong() * 2 < ref && (videos > 0 || isVerifiedBlank(day))) {
                    mark(StampId.MEDIO_DIA, day)
                }
                if (isVerifiedBlank(day)) mark(StampId.DIA_EN_BLANCO, day)
                day = day.plusDays(1)
            }
            mark(StampId.PRIMERA_SEMANA, f.plusDays(6).takeIf { it.isBefore(today) })
            markTotals(f)
        }

        val todayRef = reference(today)
        val todayExceeded = todayRef != null && videosOn(today) > todayRef
        val streak = if (todayExceeded) 0 else streakRun
        val flap = flapState(todayRef, todayExceeded)

        mark(StampId.REGRESO, comebackDay(flap))
        mark(StampId.SEMANA_MENOS_20, weekTwentyLess())
        mark(StampId.MES_A_LA_BAJA, monthDown())

        // Retos.
        val closed = closeChallenges()
        val currentMonday = PassportRules.weekStart(today)
        val challenge = challengeSetup(currentMonday, newestFirstBefore(closed, currentMonday))
            ?.let { currentChallenge(currentMonday, it) }
        val won = closed.filter { it.won }
        mark(StampId.PRIMER_RETO, won.getOrNull(0)?.weekStart?.plusDays(6))
        mark(StampId.CUATRO_RETOS, won.getOrNull(3)?.weekStart?.plusDays(6))

        // Eventos e irónicos.
        mark(StampId.WRAPPED_COMPARTIDO, memory.events[GameEvent.WRAPPED_SHARED])
        mark(StampId.TICKET_GUARDADO, memory.events[GameEvent.TICKET_SAVED])
        mark(StampId.MADRUGADOR, history.earlyBirdOn)
        mark(StampId.TRES_PLATAFORMAS, history.threePlatformsOn)
        mark(StampId.CINCO_CATEGORIAS, history.fiveCategoriesOn)
        mark(StampId.MARATON, history.marathonOn)

        // Lo guardado manda sobre lo calculado.
        val earned = LinkedHashMap<StampId, LocalDate>()
        for (id in StampId.entries) {
            if (id == StampId.PRIMER_ASCENSO) continue
            val date = memory.stamps[id] ?: computed[id]
            if (date != null) earned[id] = date
        }
        for ((id, date) in earned) {
            if (id.xp > 0) ledger += XpEntry(date, XpSource.STAMPS, id.xp)
        }
        for (result in won) {
            ledger += XpEntry(result.weekStart.plusDays(6), XpSource.CHALLENGES, PassportRules.CHALLENGE_XP)
        }

        val xp = XpBreakdown(
            days = ledger.filter { it.source == XpSource.DAYS }.sumOf { it.amount },
            streaks = ledger.filter { it.source == XpSource.STREAKS }.sumOf { it.amount },
            challenges = ledger.filter { it.source == XpSource.CHALLENGES }.sumOf { it.amount },
            stamps = ledger.filter { it.source == XpSource.STAMPS }.sumOf { it.amount },
        )
        val highest = maxOf(memory.highestLevel, TravelerLevel.forXp(xp.total))
        val ascent = memory.stamps[StampId.PRIMER_ASCENSO]
            ?: dateReaching(ledger, TravelerLevel.FRECUENTE.minXp)
            ?: if (highest >= TravelerLevel.FRECUENTE) today else null
        if (ascent != null) earned[StampId.PRIMER_ASCENSO] = ascent

        // Celebraciones: en el primer cálculo, lo de antes de ayer se da por visto.
        val celebrated = memory.celebrated.toMutableSet()
        if (memory.since == null) {
            val cutoff = today.minusDays(1)
            for ((id, date) in earned) {
                if (date.isBefore(cutoff)) celebrated += Celebration.StampEarned(id, date).key
            }
            for (result in won) {
                if (result.weekStart.plusDays(6).isBefore(cutoff)) celebrated += Celebration.ChallengeWon(result).key
            }
        }

        val closedTracked = (span - 1).coerceAtLeast(0)
        val stamps = StampId.entries.map { id ->
            val date = earned[id]
            StampState(
                id = id,
                earnedOn = date,
                celebrated = date != null && Celebration.StampEarned(id, date).key in celebrated,
                progress = if (date == null) progress(id, streak, closedTracked, won.size) else null,
            )
        }

        val pending = mutableListOf<Celebration>()
        earned.map { (id, date) -> Celebration.StampEarned(id, date) }
            .filter { it.key !in celebrated }
            .sortedWith(compareBy<Celebration.StampEarned> { it.on }.thenBy { it.stamp.ordinal })
            .forEach { pending += it }
        won.map { Celebration.ChallengeWon(it) }
            .filter { it.key !in celebrated }
            .forEach { pending += it }
        if (highest > TravelerLevel.PASAJERO) {
            val levelUp = Celebration.LevelUp(highest)
            if (levelUp.key !in celebrated) pending += levelUp
        }

        val state = PassportState(
            today = today,
            goal = goal,
            flap = flap,
            level = LevelProgress(highest, maxOf(xp.total, highest.minXp)),
            xp = xp,
            streak = streak,
            stamps = stamps,
            challenge = challenge,
            pastChallenges = closed.sortedByDescending { it.weekStart },
            pending = pending,
            trackedDays = span,
        )
        val updated = memory.copy(
            since = memory.since ?: today,
            stamps = memory.stamps + earned.filterKeys { it !in memory.stamps },
            celebrated = celebrated.toSet(),
            highestLevel = highest,
            challenges = closed,
        )
        return GameResult(state, updated)
    }

    // ------------------------------------------------------------------ días

    private fun videosOn(date: LocalDate): Int = counts[date] ?: 0

    /** Día con registro: del primer registro a hoy. */
    private fun isTracked(date: LocalDate): Boolean {
        val f = first ?: return false
        return !date.isBefore(f) && !date.isAfter(today)
    }

    /** Día a 0 con vídeos antes y después (no puede ser un servicio apagado hasta hoy). */
    private fun isVerifiedBlank(date: LocalDate): Boolean {
        val f = first ?: return false
        val last = lastVideoDay ?: return false
        return videosOn(date) == 0 && date.isAfter(f) && last.isAfter(date)
    }

    private fun tally(from: LocalDate, to: LocalDate): Tally {
        if (first == null) return Tally(0, 0)
        val lo = maxOf(from.toEpochDay(), firstEpoch)
        val hi = minOf(to.toEpochDay(), todayEpoch)
        if (lo > hi) return Tally(0, 0)
        val i = (lo - firstEpoch).toInt()
        val j = (hi - firstEpoch).toInt()
        return Tally(prefix[j + 1] - prefix[i], j - i + 1)
    }

    /** Referencia de [date]: el objetivo o la media de los 28 días que acaban 8 días antes. */
    private fun reference(date: LocalDate): Int? {
        if (goal != null) return goal
        val base = tally(
            date.minusDays((PassportRules.WINDOW_DAYS + PassportRules.BASELINE_DAYS).toLong()),
            date.minusDays((PassportRules.WINDOW_DAYS + 1).toLong()),
        )
        if (base.days < PassportRules.MIN_BASELINE_DAYS) return null
        return ((base.videos * 2 + base.days) / (2L * base.days)).toInt().coerceAtLeast(1)
    }

    private fun isUnder(date: LocalDate): Boolean {
        val ref = reference(date) ?: return false
        return videosOn(date) <= ref
    }

    private fun mark(id: StampId, date: LocalDate?) {
        if (date != null && !date.isAfter(today) && id !in computed) computed[id] = date
    }

    private fun markTotals(f: LocalDate) {
        for (i in 0 until span) {
            val total = prefix[i + 1]
            if (total >= PassportRules.THOUSAND_VIDEOS) mark(StampId.MIL_VIDEOS, f.plusDays(i.toLong()))
            if (total >= PassportRules.TEN_THOUSAND_VIDEOS) {
                mark(StampId.DIEZ_MIL_VIDEOS, f.plusDays(i.toLong()))
                return
            }
        }
    }

    // ------------------------------------------------------------------ Flap

    private fun flapState(ref: Int?, todayExceeded: Boolean): FlapState {
        if (ref == null) {
            val tracked = (1..PassportRules.WINDOW_DAYS).count { isTracked(today.minusDays(it.toLong())) }
            val mood = FlapMood.NORMAL
            return FlapState(mood, previousMood(mood), daysUnder = 0, daysTracked = tracked, reference = null, referenceIsGoal = false)
        }
        val start = if (todayExceeded) {
            today.minusDays(PassportRules.WINDOW_DAYS - 1L)
        } else {
            today.minusDays(PassportRules.WINDOW_DAYS.toLong())
        }
        var tracked = 0
        var under = 0
        for (i in 0 until PassportRules.WINDOW_DAYS) {
            val day = start.plusDays(i.toLong())
            if (!isTracked(day)) continue
            tracked++
            if (videosOn(day) <= ref) under++
        }
        val mood = PassportRules.moodFor(tracked - under, tracked)
        return FlapState(
            mood = mood,
            previousMood = previousMood(mood),
            daysUnder = under,
            daysTracked = tracked,
            reference = ref,
            referenceIsGoal = goal != null,
        )
    }

    private fun previousMood(mood: FlapMood): FlapMood? = memory.lastSeenMood?.takeIf { it != mood }

    /** Estado de Flap al empezar [day] (ventana de los 7 días anteriores); `null` si no se puede juzgar. */
    private fun moodAtStartOf(day: LocalDate): FlapMood? {
        val ref = reference(day) ?: return null
        var tracked = 0
        var under = 0
        for (i in 1..PassportRules.WINDOW_DAYS) {
            val d = day.minusDays(i.toLong())
            if (!isTracked(d)) continue
            tracked++
            if (videosOn(d) <= ref) under++
        }
        if (tracked == 0) return null
        return PassportRules.moodFor(tracked - under, tracked)
    }

    /** REGRESO: primer día en que Flap, habiendo estado ZOMBI, vuelve a NORMAL o EN_FORMA. */
    private fun comebackDay(flap: FlapState): LocalDate? {
        val f = first ?: return null
        var wasZombie = false
        var day: LocalDate = f.plusDays(1)
        while (!day.isAfter(today)) {
            val mood = if (day == today) {
                flap.mood.takeIf { flap.reference != null && flap.daysTracked > 0 }
            } else {
                moodAtStartOf(day)
            }
            if (mood == FlapMood.ZOMBI) {
                wasZombie = true
            } else if (wasZombie && (mood == FlapMood.NORMAL || mood == FlapMood.EN_FORMA)) {
                return day
            }
            day = day.plusDays(1)
        }
        return null
    }

    // ------------------------------------------------------------------ semanas y meses

    private fun weekTwentyLess(): LocalDate? {
        val f = first ?: return null
        var monday = PassportRules.weekStart(f).plusWeeks(1)
        while (monday.plusDays(6).isBefore(today)) {
            val previousStart = monday.minusWeeks(1)
            if (!previousStart.isBefore(f)) {
                val previous = tally(previousStart, monday.minusDays(1)).videos
                val current = tally(monday, monday.plusDays(6)).videos
                if (previous > 0 && current * 5 <= previous * 4) return monday.plusDays(6)
            }
            monday = monday.plusWeeks(1)
        }
        return null
    }

    private fun monthDown(): LocalDate? {
        val f = first ?: return null
        var month = YearMonth.from(f).plusMonths(1)
        while (month.atEndOfMonth().isBefore(today)) {
            val previous = month.minusMonths(1)
            val before = tally(previous.atDay(1), previous.atEndOfMonth())
            val now = tally(month.atDay(1), month.atEndOfMonth())
            val fewerPerDay = now.videos * before.days < before.videos * now.days
            if (before.days >= PassportRules.MONTH_MIN_TRACKED_DAYS && before.videos > 0 && now.days > 0 && fewerPerDay) {
                return month.atEndOfMonth()
            }
            month = month.plusMonths(1)
        }
        return null
    }

    // ------------------------------------------------------------------ retos

    /** Retos cerrados: los guardados y los de las semanas pasadas que faltan, en orden. */
    private fun closeChallenges(): List<ChallengeResult> {
        val results = HashMap<LocalDate, ChallengeResult>()
        memory.challenges.forEach { results[it.weekStart] = it }
        val f = first ?: return results.values.sortedBy { it.weekStart }
        val currentMonday = PassportRules.weekStart(today)
        var monday = PassportRules.weekStart(f).plusWeeks(1)
        while (monday.isBefore(currentMonday)) {
            if (monday !in results) {
                val setup = challengeSetup(monday, newestFirstBefore(results.values, monday))
                if (setup != null) {
                    val total = tally(monday, monday.plusDays(6)).videos.toInt()
                    results[monday] = ChallengeResult(monday, setup.target, total, won = total <= setup.target)
                }
            }
            monday = monday.plusWeeks(1)
        }
        return results.values.sortedBy { it.weekStart }
    }

    private fun newestFirstBefore(results: Collection<ChallengeResult>, monday: LocalDate): List<ChallengeResult> =
        results.filter { it.weekStart.isBefore(monday) }.sortedByDescending { it.weekStart }

    private fun challengeSetup(monday: LocalDate, previousNewestFirst: List<ChallengeResult>): ChallengeSetup? {
        val ghost = tally(monday.minusWeeks(1), monday.minusDays(1))
        if (ghost.days < PassportRules.MIN_GHOST_DAYS || ghost.videos <= 0) return null
        val week = PassportRules.WINDOW_DAYS
        val ghostTotal = if (ghost.days >= week) {
            ghost.videos.toInt()
        } else {
            ((ghost.videos * week * 2 + ghost.days) / (2L * ghost.days)).toInt()
        }
        val withinGoal = goal != null && ghostTotal.toLong() <= goal.toLong() * week
        val percent = if (withinGoal) 0 else PassportRules.reductionPercent(previousNewestFirst)
        return ChallengeSetup(
            percent = percent,
            ghostTotal = ghostTotal,
            target = PassportRules.challengeTarget(ghostTotal, percent),
            ghostVideos = ghost.videos,
            ghostDays = ghost.days,
        )
    }

    private fun currentChallenge(monday: LocalDate, setup: ChallengeSetup): WeeklyChallenge {
        val days = (0 until PassportRules.WINDOW_DAYS).map { i ->
            val date = monday.plusDays(i.toLong())
            val ghostDate = date.minusWeeks(1)
            ChallengeDay(
                date = date,
                count = if (date.isAfter(today)) null else videosOn(date),
                ghost = if (isTracked(ghostDate)) videosOn(ghostDate) else null,
            )
        }
        val elapsed = days.filter { it.count != null }
        val total = elapsed.sumOf { it.count ?: 0 }
        val knownGhost = elapsed.sumOf { it.ghost ?: 0 }
        val missingGhost = elapsed.count { it.ghost == null }
        val estimated = (missingGhost * setup.ghostVideos * 2 + setup.ghostDays) / (2L * setup.ghostDays)
        return WeeklyChallenge(
            weekStart = monday,
            reductionPercent = setup.percent,
            ghostTotal = setup.ghostTotal,
            target = setup.target,
            total = total,
            ghostSoFar = knownGhost + (if (missingGhost == 0) 0 else estimated.toInt()),
            days = days,
            status = if (total > setup.target) ChallengeStatus.FALLADO else ChallengeStatus.EN_CURSO,
        )
    }

    // ------------------------------------------------------------------ experiencia y progreso

    /** Día en que la experiencia acumulada (por fechas) llega a [threshold]; `null` si no llega. */
    private fun dateReaching(ledger: List<XpEntry>, threshold: Int): LocalDate? {
        var total = 0
        for (entry in ledger.sortedBy { it.date }) {
            total += entry.amount
            if (total >= threshold) return entry.date
        }
        return null
    }

    private fun progress(id: StampId, streak: Int, closedTracked: Int, wins: Int): Float? = when (id) {
        StampId.RACHA_3 -> fraction(streak, 3)
        StampId.RACHA_7 -> fraction(streak, 7)
        StampId.RACHA_14 -> fraction(streak, 14)
        StampId.RACHA_30 -> fraction(streak, 30)
        StampId.PRIMERA_SEMANA -> fraction(closedTracked, PassportRules.WINDOW_DAYS)
        StampId.CUATRO_RETOS -> fraction(wins, 4)
        else -> null
    }

    private fun fraction(part: Int, whole: Int): Float = (part.toFloat() / whole).coerceIn(0f, 1f)
}
