package com.reelometro.app.data

import android.app.Application
import androidx.sqlite.db.SimpleSQLiteQuery
import com.reelometro.app.stats.DayRank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Puesto del día en SQL (`Queries.DAYS_ABOVE`) y `ReelRepository.observeDayRank`. */
@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class DayRankQueryTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()

    private val today = LocalDate.of(2026, 11, 3) // martes
    private val clock = MutableClock(at(today, 14, 30))
    private lateinit var db: AppDatabase
    private lateinit var repo: ReelRepository
    private var nextId = 1L

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = ReelRepository(db, MutableStateFlow<Int?>(null), clock, ManualTicker(), Dispatchers.Default, throttleMs = 0)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** [n] vídeos el día [date] (minuto y día explícitos, sin depender de zonas). */
    private suspend fun views(date: LocalDate, n: Int) {
        val day = date.toEpochDay()
        repeat(n) {
            db.reelDao().insert(
                ReelView(
                    id = nextId++,
                    platform = Platform.TIKTOK.name,
                    startedAt = day * 86_400_000L + it * 1_000L,
                    durationMs = 10_000,
                    creator = null,
                    category = Category.OTROS.name,
                    day = day,
                    minuteOfDay = 600,
                ),
            )
        }
    }

    @Test
    fun `cuenta los días con más vídeos y los empates no suben el puesto`() = runBlocking {
        views(today.minusDays(5), 3)
        views(today.minusDays(4), 9)
        views(today.minusDays(3), 5)
        views(today.minusDays(2), 9)
        views(today.minusDays(1), 1)
        val stats = db.statsDao()
        val to = today.toEpochDay()
        assertEquals(0, stats.daysAbove(today.minusDays(4).toEpochDay(), to)) // récord compartido
        assertEquals(2, stats.daysAbove(today.minusDays(3).toEpochDay(), to))
        assertEquals(3, stats.daysAbove(today.minusDays(5).toEpochDay(), to))
        assertEquals(4, stats.daysAbove(today.minusDays(1).toEpochDay(), to))
        // Un día sin vídeos: todos los días con alguno están por encima.
        assertEquals(5, stats.daysAbove(today.toEpochDay(), to))
        // Lo posterior a `to` no cuenta.
        assertEquals(1, stats.daysAbove(today.minusDays(3).toEpochDay(), today.minusDays(3).toEpochDay()))
    }

    @Test
    fun `la consulta usa el índice de día y no recorre la tabla`() {
        val details = mutableListOf<String>()
        val sql = "EXPLAIN QUERY PLAN ${Queries.DAYS_ABOVE}"
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, arrayOf<Any>(20_000L, 20_000L))).use { c ->
            val column = c.getColumnIndexOrThrow("detail")
            while (c.moveToNext()) details += c.getString(column)
        }
        val fullScan = Regex("^SCAN( TABLE)? reel_views$")
        assertTrue("recorre la tabla: $details", details.none { fullScan.matches(it.trim()) })
        assertTrue("debería usar el índice de día: $details", details.any { "index_reel_views_day" in it })
    }

    @Test
    fun `observeDayRank da el puesto, los días contados y el primer registro`() = runBlocking {
        views(today.minusDays(9), 4)
        views(today.minusDays(1), 270)
        views(today.minusDays(3), 300)
        views(today.minusDays(5), 300)
        val rank = repo.observeDayRank(today.minusDays(1)).awaitFirst { true }
        assertEquals(DayRank(today.minusDays(1), 270, rank = 3, totalDays = 10, firstDay = today.minusDays(9)), rank)

        // Ayer sube a récord en cuanto pasa a los dos de 300.
        views(today.minusDays(1), 31)
        val updated = repo.observeDayRank(today.minusDays(1)).awaitFirst { it.count == 301 }
        assertEquals(1, updated.rank)
    }

    @Test
    fun `sin vídeos o antes del primer registro no hay puesto`() = runBlocking {
        views(today.minusDays(2), 10)
        assertFalse(repo.observeDayRank(today).awaitFirst { true }.isRanked)
        val before = repo.observeDayRank(today.minusDays(7)).awaitFirst { true }
        assertFalse(before.isRanked)
        assertEquals(today.minusDays(2), before.firstDay)
        assertEquals(3, before.totalDays)
    }
}
