package com.reelometro.app.data

import android.app.Application
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.Streak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections

@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class ReelRepositoryTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()


    private val today = LocalDate.of(2026, 11, 3) // martes
    private val clock = MutableClock(at(today, 14, 30))
    private val ticker = ManualTicker()
    private val goal = MutableStateFlow<Int?>(null)
    private lateinit var db: AppDatabase
    private lateinit var repo: ReelRepository
    private var nextId = 1L

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = ReelRepository(db, goal, clock, ticker, Dispatchers.Default, throttleMs = 0)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun view(
        startedAt: Long,
        platform: Platform = Platform.TIKTOK,
        durationMs: Long = 10_000,
        creator: String? = null,
        category: Category = Category.OTROS,
    ): Long {
        val id = nextId++
        repo.recordStart(id, platform, startedAt)
        repo.recordDuration(id, durationMs)
        if (creator != null || category != Category.OTROS) repo.recordInfo(id, creator, null, category)
        return id
    }

    /** [n] vídeos seguidos desde la hora local indicada (uno por segundo). */
    private suspend fun views(n: Int, date: LocalDate, hour: Int, minute: Int = 0, platform: Platform = Platform.TIKTOK) {
        repeat(n) { view(at(date, hour, minute) + it * 1_000L, platform) }
    }

    /** Recoge un flujo en segundo plano para comprobar que reacciona a los cambios. */
    private class Collector<T>(scope: CoroutineScope, flow: Flow<T>) {
        private val channel = Channel<T>(Channel.UNLIMITED)
        private val job = scope.launch(Dispatchers.Default) { flow.collect { channel.send(it) } }

        suspend fun next(predicate: (T) -> Boolean = { true }): T = withTimeout(10_000) {
            var value = channel.receive()
            while (!predicate(value)) value = channel.receive()
            value
        }

        fun cancel() = job.cancel()
    }

    // ------------------------------------------------------------------ escritura

    @Test
    fun `recordStart guarda día y minuto locales con la zona actual`() = runBlocking {
        val instant = LocalDateTime.of(2026, 11, 3, 23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        repo.recordStart(1, Platform.TIKTOK, instant) // Madrid: 4 nov 00:30
        ReelRepository(db, clock = MutableClock(clock.now, ZoneId.of("America/New_York")), ticker = ticker)
            .recordStart(2, Platform.TIKTOK, instant) // Nueva York: 3 nov 18:30

        val madrid = db.reelDao().find(1)!!
        assertEquals(LocalDate.of(2026, 11, 4).toEpochDay(), madrid.day)
        assertEquals(30, madrid.minuteOfDay)
        val newYork = db.reelDao().find(2)!!
        assertEquals(LocalDate.of(2026, 11, 3).toEpochDay(), newYork.day)
        assertEquals(18 * 60 + 30, newYork.minuteOfDay)
    }

    @Test
    fun `recordInfo fusiona lo leído sin borrar datos anteriores`() = runBlocking {
        val id = view(at(today, 10, 0))
        repo.recordInfo(id, "  michi_filosofo ", " Mi gato #gatos ", Category.ANIMALES)
        repo.recordInfo(id, null, null, Category.OTROS)
        var row = db.reelDao().find(id)!!
        assertEquals("michi_filosofo", row.creator)
        assertEquals("Mi gato #gatos", row.caption)
        assertEquals("ANIMALES", row.category)

        repo.recordInfo(id, "otro", "   ", Category.HUMOR)
        row = db.reelDao().find(id)!!
        assertEquals("otro", row.creator)
        assertEquals("Mi gato #gatos", row.caption) // en blanco = no leído
        assertEquals("HUMOR", row.category)

        repo.recordInfo(id, null, "x".repeat(1_000), Category.OTROS)
        assertEquals(ReelRepository.CAPTION_MAX, db.reelDao().find(id)!!.caption!!.length)
        repo.recordDuration(id, -5)
        assertEquals(0L, db.reelDao().find(id)!!.durationMs)
    }

    @Test
    fun `recordShare se guarda y discard borra el vídeo y sus compartidos`() = runBlocking {
        val keep = view(at(today, 9, 0))
        val bounce = view(at(today, 9, 1))
        repo.recordShare(Platform.TIKTOK, at(today, 9, 0, second = 5), keep)
        repo.recordShare(Platform.TIKTOK, at(today, 9, 1, second = 1), bounce)
        repo.recordShare(Platform.INSTAGRAM, at(today, 9, 2), null)
        assertEquals(3, repo.today().totals.shares)

        repo.discard(bounce)

        assertNull(db.reelDao().find(bounce))
        assertNotNull(db.reelDao().find(keep))
        val shares = db.reelDao().shareEvents()
        assertEquals(listOf(keep, null), shares.map { it.reelId })
        assertEquals(today.toEpochDay(), shares[0].day)
        assertEquals(9 * 60, shares[0].minuteOfDay)
        val stats = repo.today()
        assertEquals(1, stats.totals.count)
        assertEquals(2, stats.totals.shares)
    }

    @Test
    fun `a quién envías da el ranking del periodo, deshace, descarta y se borra`() = runBlocking {
        val reel = view(at(today, 9, 0), Platform.INSTAGRAM)
        val bounce = view(at(today, 9, 5), Platform.INSTAGRAM)
        repo.recordRecipients(Platform.INSTAGRAM, at(today, 9, 0, second = 10), reel, listOf(" Los del piso ", "Ana", "", "Ana"))
        repo.recordRecipients(Platform.INSTAGRAM, at(today, 9, 1), reel, listOf("Los del piso"))
        repo.recordRecipients(Platform.INSTAGRAM, at(today, 9, 2), null, listOf("Ana"))
        repo.recordRecipients(Platform.INSTAGRAM, at(today, 9, 5, second = 3), bounce, listOf("Marta"))
        repo.recordRecipients(Platform.INSTAGRAM, at(today, 9, 6), null, emptyList())

        var week = repo.period(PeriodRef(PeriodKind.WEEK))
        assertEquals(listOf("Ana" to 2, "Los del piso" to 2, "Marta" to 1), week.recipients.map { it.name to it.count })
        assertEquals(listOf(1, 2, 3), week.recipients.map { it.rank })
        assertTrue(week.recipients.all { it.platform == Platform.INSTAGRAM })

        // «Deshacer» quita solo el último envío a ese nombre desde el inicio de la hoja.
        repo.undoRecipient("Ana", since = at(today, 9, 1, second = 30))
        repo.discard(bounce)
        week = repo.period(PeriodRef(PeriodKind.WEEK))
        assertEquals(listOf("Los del piso" to 2, "Ana" to 1), week.recipients.map { it.name to it.count })
        // Un periodo sin envíos tiene el ranking vacío.
        assertTrue(repo.period(PeriodRef(PeriodKind.WEEK, -1)).recipients.isEmpty())

        repo.clearAll()
        assertTrue(db.reelDao().shareRecipients().isEmpty())
    }

    // ------------------------------------------------------------------ día

    @Test
    fun `hoy se compara con la media del mismo día de la semana hasta la hora actual`() = runBlocking {
        // Ahora son las 14:30 del martes 3 de noviembre.
        views(10, LocalDate.of(2026, 10, 27), 10); views(20, LocalDate.of(2026, 10, 27), 22)
        views(6, LocalDate.of(2026, 10, 20), 9); views(30, LocalDate.of(2026, 10, 20), 23)
        views(8, LocalDate.of(2026, 10, 13), 14, 30) // justo en el minuto actual: cuenta
        views(5, LocalDate.of(2026, 9, 29), 21) // con datos pero nada antes de esta hora: cuenta como 0
        views(7, LocalDate.of(2026, 10, 28), 10) // miércoles: no es referencia
        // 6 de octubre: sin datos, no cuenta.
        views(9, today, 8)

        val stats = repo.today()

        assertTrue(stats.isToday)
        assertEquals(14 * 60 + 30, stats.untilMinute)
        val comparison = stats.comparison!!
        assertEquals(DayOfWeek.TUESDAY, comparison.weekday)
        assertEquals(4, comparison.sampleDays)
        assertEquals(6.0, comparison.averageCount, 1e-9) // (10 + 6 + 8 + 0) / 4
        assertEquals(0.5, comparison.countChange!!, 1e-9) // +50 %
        assertEquals(9, stats.totals.count)
        assertEquals(8, stats.peakHour!!.hour)
        assertEquals(24, stats.hours.size)
    }

    @Test
    fun `un día pasado se compara entero y solo con los 8 más recientes con datos`() = runBlocking {
        // Diez martes anteriores con datos (1, 2, …, 10 vídeos): solo cuentan los 8 más recientes.
        for (week in 1..10) views(week, today.minusWeeks(week.toLong()), 20)
        val stats = repo.day(today.minusWeeks(1)) // 27 oct: 1 vídeo

        assertFalse(stats.isToday)
        assertNull(stats.untilMinute)
        val comparison = stats.comparison!!
        assertNull(comparison.untilMinute)
        assertEquals(8, comparison.sampleDays) // semanas 2..9
        assertEquals((2..9).average(), comparison.averageCount, 1e-9)
        assertEquals(1, comparison.count)
    }

    @Test
    fun `día sin referencias ni datos`() = runBlocking {
        val stats = repo.today()
        assertTrue(stats.isEmpty)
        assertNull(stats.comparison)
        assertNull(stats.peakHour)
        assertTrue(stats.platforms.isEmpty())
        val future = repo.day(today.plusDays(1))
        assertTrue(future.isEmpty)
        assertNull(future.comparison)
    }

    @Test
    fun `reparto del día por plataforma, categoría y creador`() = runBlocking {
        repeat(3) { view(at(today, 9, it), Platform.TIKTOK, creator = "michi_filosofo", category = Category.ANIMALES) }
        repeat(2) { view(at(today, 10, it), Platform.INSTAGRAM, creator = "la_tia_que_opina", category = Category.HUMOR) }
        view(at(today, 11, 0), Platform.YOUTUBE)

        val stats = repo.today()
        assertEquals(listOf(Platform.TIKTOK, Platform.INSTAGRAM, Platform.YOUTUBE), stats.platforms.map { it.platform })
        assertEquals(0.5, stats.platforms[0].fraction, 1e-9)
        assertEquals(listOf(Category.ANIMALES, Category.HUMOR, Category.OTROS), stats.categories.map { it.category })
        assertEquals(listOf("michi_filosofo", "la_tia_que_opina"), stats.creators.map { it.creator })
        assertEquals(listOf(1, 2), stats.creators.map { it.rank })
        assertEquals(Category.ANIMALES, stats.topCategory?.category)
    }

    @Test
    fun `objetivo diario y racha`() = runBlocking {
        goal.value = 5
        views(3, today.minusDays(3), 12) // sáb
        views(9, today.minusDays(2), 12) // dom: se pasa
        views(4, today.minusDays(1), 12) // lun
        views(6, today, 9) // hoy ya se ha pasado

        val day = repo.today()
        assertEquals(1, day.goal!!.exceededBy)
        assertTrue(day.goal!!.isExceeded)
        assertEquals(Streak(goal = 5, current = 0, best = 1, todayExceeded = true), repo.streak())

        goal.value = 10
        assertEquals(Streak(goal = 10, current = 3, best = 3, todayExceeded = false), repo.streak())
        assertEquals(Streak(10, 3, 3, false), repo.period(PeriodRef(PeriodKind.WEEK)).streak)
        goal.value = null
        assertNull(repo.streak())
        assertNull(repo.today().goal)
    }

    // ------------------------------------------------------------------ periodos

    @Test
    fun `la semana en curso tiene ceros, no tiene días futuros y se compara a la misma altura`() = runBlocking {
        val lastMonday = LocalDate.of(2026, 10, 26)
        views(4, lastMonday, 20) // lunes pasado
        views(10, lastMonday.plusDays(1), 10) // martes pasado, antes de las 14:30
        views(50, lastMonday.plusDays(1), 22) // martes pasado, después: no cuenta para la tendencia
        views(30, lastMonday.plusDays(5), 22) // sábado pasado: fuera del tramo
        views(7, today.minusDays(1), 21) // lunes
        views(9, today, 8) // hoy
        repo.recordShare(Platform.TIKTOK, at(today, 8, 1), null)

        val week = repo.period(PeriodRef(PeriodKind.WEEK))

        assertEquals("Esta semana", week.label)
        assertEquals(listOf(today.minusDays(1), today), week.days.map { it.date })
        assertEquals(listOf(7, 9), week.days.map { it.count })
        assertEquals(listOf(0, 1), week.days.map { it.shares })
        assertEquals(16, week.totals.count)
        assertEquals(1, week.totals.shares)
        assertEquals(2, week.trackedDays)
        assertEquals(8.0, week.avgPerDay, 1e-9)
        val trend = week.trend!!
        assertEquals(lastMonday, trend.previous.start)
        assertEquals(lastMonday.plusDays(1), trend.previous.end)
        assertEquals(14 * 60 + 30, trend.untilMinute)
        assertEquals(14, trend.previousTotals.count)
        assertEquals(16.0 / 14.0 - 1, trend.countChange!!, 1e-9)
        assertTrue(week.hasPrevious)
        assertFalse(week.hasNext)
        assertEquals(today, week.worstDay?.date)
        assertEquals(today.minusDays(1), week.quietestDay?.date)
    }

    @Test
    fun `un mes pasado con días a cero, día débil y creadores`() = runBlocking {
        val october = LocalDate.of(2026, 10, 1) // jueves
        views(5, october.plusDays(2), 21) // sáb 3
        views(15, october.plusDays(9), 21) // sáb 10
        views(3, october.plusDays(5), 13) // mar 6
        repeat(4) {
            view(at(october.plusDays(20), 23, it), Platform.INSTAGRAM, creator = "pesas_y_pesadillas", category = Category.FITNESS)
        }
        views(2, LocalDate.of(2026, 9, 15), 10) // septiembre: referencia de la tendencia

        val stats = repo.period(PeriodRef(PeriodKind.MONTH, -1))

        assertEquals("octubre de 2026", stats.label)
        assertEquals(31, stats.days.size)
        assertEquals(27, stats.totals.count)
        assertEquals(4, stats.activeDays)
        assertEquals(31, stats.trackedDays)
        val saturday = stats.weekdays.first { it.dayOfWeek == DayOfWeek.SATURDAY }
        assertEquals(5, saturday.days) // octubre de 2026 tiene cinco sábados
        assertEquals(4.0, saturday.averageCount, 1e-9)
        assertEquals(DayOfWeek.SATURDAY, stats.weakestWeekday?.dayOfWeek)
        assertEquals(october.plusDays(9), stats.worstDay?.date)
        assertEquals(0, stats.quietestDay?.count)
        assertEquals("pesas_y_pesadillas", stats.creators.single().creator)
        assertEquals(Platform.INSTAGRAM, stats.creators.single().platform)
        assertEquals(21, stats.peakHour?.hour)
        // Septiembre: 2 vídeos en 16 días registrados (del 15 al 30).
        val trend = stats.trend!!
        assertNull(trend.untilMinute)
        assertEquals(16, trend.previousTrackedDays)
        assertEquals((27.0 / 31) / (2.0 / 16) - 1, trend.countChange!!, 1e-9)
        assertTrue(stats.hasNext)
        assertTrue(stats.hasPrevious)
        assertFalse(repo.period(PeriodRef(PeriodKind.MONTH, -2)).hasPrevious)
    }

    @Test
    fun `siempre empieza en el primer registro`() = runBlocking {
        views(2, LocalDate.of(2025, 3, 3), 12)
        views(3, today, 12)
        val all = repo.period(PeriodRef(PeriodKind.ALL))
        assertEquals("Desde el 3 mar 2025", all.label)
        assertEquals(LocalDate.of(2025, 3, 3), all.days.first().date)
        assertEquals(today, all.days.last().date)
        assertEquals(5, all.totals.count)
        assertNull(all.trend)
        val allTime = repo.allTime()
        assertEquals(LocalDate.of(2025, 3, 3), allTime.firstDay)
        assertEquals(5, allTime.totals.count)
        assertEquals(all.days.size, allTime.trackedDays)
    }

    // ------------------------------------------------------------------ flujos

    @Test
    fun `observeToday se recalcula al insertar y al pasar la medianoche`() = runBlocking {
        clock.set(at(today, 23, 59, second = 30))
        val collector = Collector(this, repo.observeToday())

        assertEquals(0, collector.next().totals.count)
        view(at(today, 23, 59, second = 40))
        assertEquals(today, collector.next { it.totals.count == 1 }.date)

        clock.set(at(today.plusDays(1), 0, 0, second = 1))
        ticker.tick()
        val tomorrow = collector.next { it.date == today.plusDays(1) }
        assertEquals(0, tomorrow.totals.count)
        assertEquals(0, tomorrow.untilMinute)

        view(at(today.plusDays(1), 0, 0, second = 2))
        assertEquals(1, collector.next { it.totals.count == 1 }.totals.count)
        collector.cancel()
    }

    @Test
    fun `durante un scroll rápido no recalcula en cada inserción`() = runBlocking {
        val throttled = ReelRepository(db, goal, clock, ticker, Dispatchers.Default, throttleMs = 200)
        val counts = Collections.synchronizedList(mutableListOf<Int>())
        val job = launch(Dispatchers.Default) { throttled.observeToday().collect { counts += it.totals.count } }
        withTimeout(10_000) { while (counts.isEmpty()) delay(10) }

        repeat(60) { view(at(today, 12, 0) + it * 1_000L) }

        withTimeout(10_000) { while (counts.lastOrNull() != 60) delay(10) }
        job.cancel()
        assertTrue("emisiones: $counts", counts.size <= 15)
    }

    @Test
    fun `observeToday reacciona al cambio de objetivo`() = runBlocking {
        views(3, today, 9)
        val collector = Collector(this, repo.observeToday())
        assertNull(collector.next().goal)
        goal.value = 2
        assertEquals(1, collector.next { it.goal != null }.goal!!.exceededBy)
        collector.cancel()
    }

    @Test
    fun `observePeriod y observeAllTime siguen a los datos y clearAll lo vacía todo`() = runBlocking {
        views(2, today, 9)
        val week = Collector(this, repo.observePeriod(PeriodRef(PeriodKind.WEEK)))
        val all = Collector(this, repo.observeAllTime())
        assertEquals(2, week.next().totals.count)
        assertEquals(2, all.next().totals.count)

        views(1, today.minusDays(1), 9)
        assertEquals(3, week.next { it.totals.count == 3 }.totals.count)
        assertEquals(today.minusDays(1), all.next { it.totals.count == 3 }.firstDay)

        repo.clearAll()

        assertTrue(week.next { it.isEmpty }.days.all { it.count == 0 })
        assertNull(all.next { it.isEmpty }.firstDay)
        assertNull(repo.firstDay())
        week.cancel()
        all.cancel()
    }

    @Test
    fun `observeRecent devuelve los últimos con sus compartidos`() = runBlocking {
        val a = view(at(today, 9, 0), creator = "uno", category = Category.HUMOR)
        val b = view(at(today, 9, 1), Platform.YOUTUBE)
        repo.recordShare(Platform.TIKTOK, at(today, 9, 0, second = 3), a)
        repo.recordShare(Platform.TIKTOK, at(today, 9, 0, second = 4), a)

        val recent = Collector(this, repo.observeRecent(10))
        val list = recent.next { it.size == 2 && it[1].shares == 2 }
        assertEquals(listOf(b, a), list.map { it.id })
        assertEquals(listOf(0, 2), list.map { it.shares })
        assertEquals(Category.HUMOR, list[1].category)
        assertEquals("uno", list[1].creator)

        val c = view(at(today, 9, 2))
        assertEquals(listOf(c, b, a), recent.next { it.size == 3 }.map { it.id })
        recent.cancel()
        assertEquals(1, repo.observeRecent(1).awaitFirst { true }.size)
    }
}
