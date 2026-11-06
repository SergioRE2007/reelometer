package com.reelometro.app.data

import androidx.room.Transactor
import androidx.room.useReaderConnection
import com.reelometro.app.data.export.CsvExporter
import com.reelometro.app.stats.AllTime
import com.reelometro.app.stats.CreatorRank
import com.reelometro.app.stats.DayRank
import com.reelometro.app.stats.DayStats
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import com.reelometro.app.stats.RecipientRank
import com.reelometro.app.stats.StatsMath
import com.reelometro.app.stats.Streak
import com.reelometro.app.stats.Totals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Única puerta a los datos de uso: la escritura la usa el servicio de accesibilidad y la lectura,
 * las pantallas, el widget y las notificaciones.
 *
 * Lectura: todas las agregaciones son SQL (GROUP BY día, hora, plataforma, categoría, creador) y
 * las de una misma vista se ejecutan juntas en **una transacción de lectura** (cifras coherentes
 * entre sí). Los `observe*` son flujos fríos que:
 * - calculan al suscribirse y se recalculan cuando cambia `reel_views` o `share_events`
 *   (InvalidationTracker), cuando cambia el objetivo diario y al pasar la medianoche;
 * - durante un scroll rápido no recalculan en cada inserción: como mucho una vez cada [throttleMs]
 *   y siempre con el último estado;
 * - solo emiten si el resultado cambia, y trabajan fuera del hilo principal.
 * En un ViewModel, conviértelos con `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`.
 *
 * Fechas: «hoy», `day` y `minuteOfDay` se calculan con la zona del sistema **actual** ([SystemZoneClock]).
 *
 * @param dailyGoal objetivo diario («como mucho N vídeos»; `null` o ≤ 0 = sin objetivo). En la app,
 *   `settings.settings.map { it.dailyGoal }`.
 * @param userData otros datos del usuario que van en el CSV y se borran con BORRAR TODO (el
 *   pasaporte). Es una lambda perezosa para evitar el ciclo repositorio ↔ pasaporte en `ReelometroApp`.
 */
class ReelRepository(
    private val database: AppDatabase,
    dailyGoal: Flow<Int?> = flowOf(null),
    private val clock: Clock = SystemZoneClock,
    private val ticker: Ticker = ClockTicker(clock),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val throttleMs: Long = THROTTLE_MS,
    private val userData: () -> List<UserDataPart> = { emptyList() },
) {
    private val dao = database.reelDao()
    private val statsDao = database.statsDao()
    private val goal: Flow<Int?> = dailyGoal.map { g -> g?.takeIf { it > 0 } }.distinctUntilChanged()
    private val exporter = CsvExporter(dao, zone = { clock.zone })

    // ---------------------------------------------------------------------------------------------
    // ESCRITURA — contrato estable que usa el servicio de accesibilidad.
    // No cambies estas firmas.
    // ---------------------------------------------------------------------------------------------

    /**
     * Empieza un vídeo. `id` lo genera el detector y es único. Guarda también el día local y el
     * minuto del día (zona actual del sistema), que son las claves de todas las agregaciones.
     */
    suspend fun recordStart(id: Long, platform: Platform, startedAt: Long) {
        val stamp = LocalStamp.of(startedAt, clock.zone)
        dao.insert(
            ReelView(
                id = id,
                platform = platform.name,
                startedAt = startedAt,
                day = stamp.day,
                minuteOfDay = stamp.minuteOfDay,
            ),
        )
    }

    /** Fija la duración final (ms) de un vídeo ya empezado. */
    suspend fun recordDuration(id: Long, durationMs: Long) {
        dao.updateDuration(id, durationMs.coerceAtLeast(0))
    }

    /**
     * Autor / descripción / categoría leídos de la pantalla (cualquiera puede ser null o desconocido).
     * Se fusiona con lo que ya hubiera: un `null` no borra un dato anterior y [Category.OTROS] no
     * pisa una categoría conocida. Se recortan espacios y la descripción se guarda como mucho con
     * [CAPTION_MAX] caracteres (la base de datos no crece sin control).
     */
    suspend fun recordInfo(id: Long, creator: String?, caption: String?, category: Category) {
        dao.updateInfo(
            id = id,
            creator = creator?.trim()?.takeIf { it.isNotEmpty() }?.take(CREATOR_MAX),
            caption = caption?.trim()?.takeIf { it.isNotEmpty() }?.take(CAPTION_MAX),
            category = category.name,
        )
    }

    /**
     * El usuario ha pulsado «compartir» en un vídeo corto. Aquí solo se guarda que ocurrió
     * (plataforma, instante y, si se sabe, en qué vídeo); a quién se envía va en [recordRecipients].
     */
    suspend fun recordShare(platform: Platform, at: Long, reelId: Long?) {
        val stamp = LocalStamp.of(at, clock.zone)
        dao.insertShare(
            ShareEvent(platform = platform.name, at = at, day = stamp.day, minuteOfDay = stamp.minuteOfDay, reelId = reelId),
        )
    }

    /**
     * Envíos desde la hoja de compartir de Instagram (solo con el ajuste «A quién envías» encendido):
     * una fila por persona o grupo. Se recortan espacios y el nombre a [RECIPIENT_MAX] caracteres; los
     * vacíos y los repetidos en la misma llamada se ignoran.
     */
    suspend fun recordRecipients(platform: Platform, at: Long, reelId: Long?, recipients: List<String>) {
        val names = recipients.mapNotNull { it.trim().takeIf(String::isNotEmpty)?.take(RECIPIENT_MAX) }.distinct()
        if (names.isEmpty()) return
        val stamp = LocalStamp.of(at, clock.zone)
        dao.insertRecipients(
            names.map {
                ShareRecipient(
                    platform = platform.name, at = at, day = stamp.day, minuteOfDay = stamp.minuteOfDay,
                    recipient = it, reelId = reelId,
                )
            },
        )
    }

    /** «Deshacer» en la hoja de compartir: borra el último envío a [recipient] desde [since]. */
    suspend fun undoRecipient(recipient: String, since: Long) {
        val name = recipient.trim().take(RECIPIENT_MAX).takeIf { it.isNotEmpty() } ?: return
        dao.undoRecipient(name, since)
    }

    /**
     * El detector descarta un vídeo que resultó no serlo (p. ej. estuvo en pantalla menos de lo
     * mínimo por un rebote del scroll). Borra la fila y los compartidos y envíos asociados a ella.
     */
    suspend fun discard(id: Long) {
        dao.discard(id)
    }

    // ---------------------------------------------------------------------------------------------
    // LECTURA
    // ---------------------------------------------------------------------------------------------

    /**
     * Hoy (para Hoy y el widget). Cambia de fecha sola al pasar la medianoche y se refresca cada
     * [TICK_MINUTES] minutos aunque no haya vídeos nuevos (la comparación es «a esta hora»).
     */
    fun observeToday(): Flow<DayStats> = observe(everyMinutes = TICK_MINUTES) { goal -> computeDay(null, goal) }

    /** Un día concreto (Detalle del día). Si es hoy, se compara hasta el minuto actual. */
    fun observeDay(date: LocalDate): Flow<DayStats> = observe(everyMinutes = TICK_MINUTES) { goal -> computeDay(date, goal) }

    /**
     * Puesto de un día entre todos los días contados (Detalle del día), en SQL: el recuento del día,
     * el primer registro y cuántos días tienen más vídeos, sin cargar el historial. Se recalcula con
     * cada vídeo y al pasar la medianoche (hay un día contado más).
     */
    fun observeDayRank(date: LocalDate): Flow<DayRank> = observe(everyMinutes = null) { _ -> computeDayRank(date) }

    /** Hoy, una sola vez (notificaciones, widget). */
    suspend fun today(): DayStats = withContext(dispatcher) { computeDay(null, goal.first()) }

    /** Un día, una sola vez. */
    suspend fun day(date: LocalDate): DayStats = withContext(dispatcher) { computeDay(date, goal.first()) }

    /**
     * Un periodo (Estadísticas, Wrapped). `PeriodRef(WEEK)` es siempre «esta semana», también tras
     * la medianoche del domingo. Si es el periodo en curso, se refresca cada [TICK_MINUTES] minutos.
     */
    fun observePeriod(ref: PeriodRef): Flow<PeriodStats> =
        observe(everyMinutes = if (ref.isCurrent) TICK_MINUTES else null) { goal -> computePeriod(ref, goal) }

    /** Un periodo, una sola vez (resumen mensual, imagen compartible). */
    suspend fun period(ref: PeriodRef): PeriodStats = withContext(dispatcher) { computePeriod(ref, goal.first()) }

    /** Totales desde el primer registro (banda «DESDE EL 3 MAR: …», Ajustes > Tus datos). */
    fun observeAllTime(): Flow<AllTime> = observe(everyMinutes = null) { computeAllTime() }

    suspend fun allTime(): AllTime = withContext(dispatcher) { computeAllTime() }

    /** Primer día con registros, o `null` si no hay ninguno. */
    suspend fun firstDay(): LocalDate? = withContext(dispatcher) { statsDao.firstDay()?.let(LocalDate::ofEpochDay) }

    /**
     * Vídeos de un día (solo el recuento, con el índice estrecho de `day`): barato para llamarlo en
     * cada vídeo (aviso en directo al pasar el objetivo).
     */
    suspend fun countOn(date: LocalDate): Int = withContext(dispatcher) {
        val d = date.toEpochDay()
        statsDao.countsByDay(d, d).firstOrNull()?.count ?: 0
    }

    /** Racha actual y mejor frente al objetivo diario; `null` si no hay objetivo. */
    suspend fun streak(): Streak? = withContext(dispatcher) {
        val goal = goal.first() ?: return@withContext null
        val today = LocalDate.now(clock)
        val (first, counts) = readTransaction {
            val first = statsDao.firstDay()
            first to if (first == null) emptyList() else statsDao.countsByDay(first, today.toEpochDay())
        }
        StatsMath.streak(goal, first?.let(LocalDate::ofEpochDay), today, counts.toDateCounts())
    }

    /** Los últimos [limit] vídeos, el más reciente primero (Calibración). Se emite en cada cambio. */
    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<List<RecentVideo>> =
        dao.observeRecent(limit.coerceIn(1, MAX_RECENT_LIMIT))
            .map { rows -> rows.mapNotNull(::toRecentVideo) }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    /**
     * A quién envías más en Instagram, de siempre (los nombres, el más frecuente primero): la lista para
     * fijar el envío rápido en Ajustes. Se emite al suscribirse y en cada cambio de `share_recipients`.
     */
    fun observeTopRecipients(limit: Int = TOP_RECIPIENTS_LIMIT): Flow<List<String>> =
        database.invalidationTracker.createFlow(ShareRecipient.TABLE)
            .map {
                statsDao.topRecipients(Long.MIN_VALUE, Long.MAX_VALUE, limit.coerceIn(1, MAX_RECENT_LIMIT))
                    .filter { it.platform == Platform.INSTAGRAM.name }
                    .map { it.recipient }
                    .distinct()
            }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    /**
     * Exporta todo a CSV (UTF-8 con BOM, `;`, para Excel en español; ver [CsvExporter]): los vídeos
     * y, detrás, el bloque de cada parte de [userData] (el pasaporte).
     * No cierra [out]. Devuelve el número de vídeos exportados.
     */
    suspend fun exportCsv(out: OutputStream): Int {
        val videos = exporter.export(out)
        userData().forEach { it.exportCsv(out) }
        return videos
    }

    /**
     * Borra todos los datos de uso (vídeos, compartidos y envíos) y los de [userData] (el pasaporte).
     * No toca los ajustes.
     */
    suspend fun clearAll() {
        dao.deleteAll()
        userData().forEach { it.clearAll() }
    }

    // ---------------------------------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------------------------------

    private fun <T> observe(everyMinutes: Int?, compute: suspend (goal: Int?) -> T): Flow<T> =
        combine(
            database.invalidationTracker.createFlow(ReelView.TABLE, ShareEvent.TABLE, ShareRecipient.TABLE),
            goal,
            ticker.ticks(everyMinutes),
        ) { _, goal, _ -> goal }
            .conflate()
            .transform { goal ->
                emit(compute(goal))
                if (throttleMs > 0) delay(throttleMs)
            }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    /** Ejecuta varias lecturas en una transacción de lectura (instantánea coherente; no bloquea al servicio). */
    private suspend fun <R> readTransaction(block: suspend () -> R): R =
        database.useReaderConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) { block() }
        }

    private suspend fun computeDay(requested: LocalDate?, goal: Int?): DayStats {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        val date = requested ?: today
        if (date.isAfter(today)) {
            return StatsMath.day(date, today, nowMinute, goal, emptyMap(), 0, emptyMap(), emptyMap(), emptyList(), emptyList())
        }
        val d = date.toEpochDay()
        val cut = if (date == today) nowMinute else StatsMath.LAST_MINUTE
        val candidates = StatsMath.baselineDays(date).map { it.toEpochDay() }
        val raw = readTransaction {
            DayRaw(
                hours = statsDao.viewsByHour(d, d),
                shares = statsDao.shareCount(d, d),
                platforms = statsDao.viewsByPlatform(d, d),
                categories = statsDao.viewsByCategory(d, d),
                creators = statsDao.topCreators(d, d, StatsMath.TOP_CREATORS),
                baseline = statsDao.sameWeekdayBaseline(candidates, cut, StatsMath.BASELINE_SAMPLES),
            )
        }
        return StatsMath.day(
            date = date,
            today = today,
            nowMinute = nowMinute,
            goal = goal,
            byHour = raw.hours.associate { it.hour to Totals(it.count, it.totalMs) },
            shares = raw.shares,
            byPlatform = raw.platforms.toPlatformTotals(),
            byCategory = raw.categories.toCategoryTotals(),
            creators = raw.creators.toRanks(),
            baseline = raw.baseline.map { Totals(it.count, it.totalMs) },
        )
    }

    private suspend fun computePeriod(ref: PeriodRef, goal: Int?): PeriodStats {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        val raw = readTransaction {
            val first = statsDao.firstDay()?.let(LocalDate::ofEpochDay)
            val elapsed = ref.elapsed(today, first)
            val from = elapsed.startEpochDay
            val to = elapsed.endEpochDay
            val byDay = statsDao.viewsByDay(from, to)
            val window = StatsMath.trendWindow(ref, today, nowMinute, first)
            val previous = if (window == null || first == null || window.span.end.isBefore(first)) {
                null
            } else {
                val a = window.span.startEpochDay
                val b = window.span.endEpochDay
                val cut = window.untilMinute ?: StatsMath.LAST_MINUTE
                val t = statsDao.totalsUntil(a, b, cut)
                Totals(t.count, t.totalMs, statsDao.sharesUntil(a, b, cut))
            }
            val history = when {
                goal == null || first == null -> emptyList()
                ref.kind == PeriodKind.ALL -> byDay.map { DayCountRow(it.day, it.count) }
                else -> statsDao.countsByDay(first.toEpochDay(), today.toEpochDay())
            }
            PeriodRaw(
                firstDay = first,
                byDay = byDay,
                sharesByDay = statsDao.sharesByDay(from, to),
                hours = statsDao.viewsByHour(from, to),
                platforms = statsDao.viewsByPlatform(from, to),
                categories = statsDao.viewsByCategory(from, to),
                creators = statsDao.topCreators(from, to, StatsMath.TOP_CREATORS),
                recipients = statsDao.topRecipients(from, to, StatsMath.TOP_RECIPIENTS),
                previous = previous,
                history = history,
            )
        }
        val shares = raw.sharesByDay.associate { it.day to it.count }
        val byDay = buildMap {
            raw.byDay.forEach { put(LocalDate.ofEpochDay(it.day), Totals(it.count, it.totalMs, shares[it.day] ?: 0)) }
            raw.sharesByDay.forEach { row ->
                val date = LocalDate.ofEpochDay(row.day)
                if (date !in this) put(date, Totals(0, 0, row.count))
            }
        }
        return StatsMath.period(
            ref = ref,
            today = today,
            nowMinute = nowMinute,
            firstDay = raw.firstDay,
            goal = goal,
            byDay = byDay,
            byHour = raw.hours.associate { it.hour to Totals(it.count, it.totalMs) },
            byPlatform = raw.platforms.toPlatformTotals(),
            byCategory = raw.categories.toCategoryTotals(),
            creators = raw.creators.toRanks(),
            previous = raw.previous,
            history = raw.history.toDateCounts(),
        ).copy(recipients = raw.recipients.toRecipientRanks())
    }

    private suspend fun computeDayRank(date: LocalDate): DayRank {
        val today = LocalDate.now(clock)
        val d = date.toEpochDay()
        val raw = readTransaction {
            val first = statsDao.firstDay()
            val count = statsDao.countsByDay(d, d).firstOrNull()?.count ?: 0
            val above = if (first == null || count == 0) 0 else statsDao.daysAbove(d, today.toEpochDay())
            Triple(first, count, above)
        }
        return DayRank.of(date, today, raw.first?.let(LocalDate::ofEpochDay), raw.second, raw.third)
    }

    private suspend fun computeAllTime(): AllTime {
        val today = LocalDate.now(clock)
        val (first, totals) = readTransaction {
            val t = statsDao.allTotals()
            statsDao.firstDay() to Totals(t.count, t.totalMs, statsDao.shareTotal())
        }
        return StatsMath.allTime(first?.let(LocalDate::ofEpochDay), today, totals)
    }

    private class DayRaw(
        val hours: List<HourRow>,
        val shares: Int,
        val platforms: List<GroupRow>,
        val categories: List<GroupRow>,
        val creators: List<CreatorRow>,
        val baseline: List<DayRow>,
    )

    private class PeriodRaw(
        val firstDay: LocalDate?,
        val byDay: List<DayRow>,
        val sharesByDay: List<DayCountRow>,
        val hours: List<HourRow>,
        val platforms: List<GroupRow>,
        val categories: List<GroupRow>,
        val creators: List<CreatorRow>,
        val recipients: List<RecipientRow>,
        val previous: Totals?,
        val history: List<DayCountRow>,
    )

    companion object {
        /** Ventana mínima entre dos recálculos de una vista observada (scroll rápido = muchas inserciones). */
        const val THROTTLE_MS = 300L

        /** Cada cuántos minutos se refrescan las vistas que dependen de la hora actual. */
        const val TICK_MINUTES = 5

        /** Longitud máxima guardada de la descripción de un vídeo. */
        const val CAPTION_MAX = 300

        /** Longitud máxima guardada del nombre de un creador. */
        const val CREATOR_MAX = 64

        /** Longitud máxima guardada del nombre de una persona o grupo al que envías. */
        const val RECIPIENT_MAX = 64

        const val RECENT_LIMIT = 100

        /** Personas y grupos que Ajustes ofrece para fijar el envío rápido. */
        const val TOP_RECIPIENTS_LIMIT = 8
        private const val MAX_RECENT_LIMIT = 1_000

        private fun List<GroupRow>.toPlatformTotals(): Map<Platform, Totals> =
            mapNotNull { row -> Platform.fromName(row.name)?.let { it to Totals(row.count, row.totalMs) } }.toMap()

        /** Nombres desconocidos (p. ej. categorías retiradas) se suman a «Sin clasificar». */
        private fun List<GroupRow>.toCategoryTotals(): Map<Category, Totals> =
            groupBy({ Category.fromName(it.name) }) { Totals(it.count, it.totalMs) }
                .mapValues { (_, list) -> list.reduce(Totals::plus) }

        private fun List<CreatorRow>.toRanks(): List<CreatorRank> =
            mapNotNull { row -> Platform.fromName(row.platform)?.let { row to it } }
                .mapIndexed { i, (row, platform) -> CreatorRank(i + 1, row.creator, platform, row.count, row.totalMs) }

        private fun List<RecipientRow>.toRecipientRanks(): List<RecipientRank> =
            mapNotNull { row -> Platform.fromName(row.platform)?.let { row to it } }
                .mapIndexed { i, (row, platform) -> RecipientRank(i + 1, row.recipient, platform, row.count) }

        private fun List<DayCountRow>.toDateCounts(): Map<LocalDate, Int> =
            associate { LocalDate.ofEpochDay(it.day) to it.count }

        private fun toRecentVideo(row: VideoRow): RecentVideo? {
            val platform = Platform.fromName(row.platform) ?: return null
            return RecentVideo(
                id = row.id,
                platform = platform,
                startedAt = row.startedAt,
                durationMs = row.durationMs,
                creator = row.creator,
                caption = row.caption,
                category = Category.fromName(row.category),
                shares = row.shares,
            )
        }
    }
}
