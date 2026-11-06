package com.reelometro.app.data

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Agregaciones SQL con datos conocidos (día y minuto explícitos, sin depender de zonas). */
@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class StatsDaoTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()


    private lateinit var db: AppDatabase
    private val dao get() = db.reelDao()
    private val stats get() = db.statsDao()
    private var nextId = 1L

    private val d = 20_000L // un epochDay cualquiera

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(
        day: Long,
        minute: Int,
        platform: Platform = Platform.TIKTOK,
        ms: Long = 10_000,
        creator: String? = null,
        category: Category = Category.OTROS,
    ) {
        dao.insert(
            ReelView(
                id = nextId++,
                platform = platform.name,
                startedAt = day * 86_400_000L + minute * 60_000L,
                durationMs = ms,
                creator = creator,
                category = category.name,
                day = day,
                minuteOfDay = minute,
            ),
        )
    }

    private suspend fun share(day: Long, minute: Int, reelId: Long? = null) {
        dao.insertShare(
            ShareEvent(platform = "TIKTOK", at = day * 86_400_000L + minute * 60_000L, day = day, minuteOfDay = minute, reelId = reelId),
        )
    }

    @Test
    fun `agrupa por día, hora, plataforma y categoría dentro del rango`() = runBlocking {
        insert(d - 1, 600) // fuera del rango
        insert(d, 0, Platform.TIKTOK, 1_000, category = Category.HUMOR)
        insert(d, 59, Platform.TIKTOK, 2_000, category = Category.HUMOR)
        insert(d, 60, Platform.INSTAGRAM, 3_000, category = Category.COMIDA)
        insert(d + 1, 1439, Platform.YOUTUBE, 4_000)
        insert(d + 3, 720, Platform.TIKTOK, 5_000, category = Category.COMIDA)
        insert(d + 4, 600) // fuera del rango

        assertEquals(
            listOf(DayRow(d, 3, 6_000), DayRow(d + 1, 1, 4_000), DayRow(d + 3, 1, 5_000)),
            stats.viewsByDay(d, d + 3),
        )
        assertEquals(
            listOf(DayCountRow(d, 3), DayCountRow(d + 1, 1), DayCountRow(d + 3, 1)),
            stats.countsByDay(d, d + 3),
        )
        assertEquals(
            listOf(HourRow(0, 2, 3_000), HourRow(1, 1, 3_000), HourRow(12, 1, 5_000), HourRow(23, 1, 4_000)),
            stats.viewsByHour(d, d + 3),
        )
        assertEquals(
            setOf(GroupRow("TIKTOK", 3, 8_000), GroupRow("INSTAGRAM", 1, 3_000), GroupRow("YOUTUBE", 1, 4_000)),
            stats.viewsByPlatform(d, d + 3).toSet(),
        )
        assertEquals(
            setOf(GroupRow("HUMOR", 2, 3_000), GroupRow("COMIDA", 2, 8_000), GroupRow("OTROS", 1, 4_000)),
            stats.viewsByCategory(d, d + 3).toSet(),
        )
        assertEquals(TotalsRow(7, 35_000), stats.allTotals())
        assertEquals(d - 1, stats.firstDay())
    }

    @Test
    fun `ranking de creadores con límite y desempates`() = runBlocking {
        repeat(5) { insert(d, it, creator = "ana") }
        repeat(5) { insert(d, 10 + it, ms = 20_000, creator = "bea") } // mismo número, más tiempo
        repeat(3) { insert(d, 20 + it, creator = "carla") }
        repeat(3) { insert(d, 30 + it, creator = "berta") } // empate total con carla: orden alfabético
        repeat(2) { insert(d, 40 + it, Platform.YOUTUBE, creator = "ana") } // otra plataforma: fila aparte
        repeat(9) { insert(d, 50 + it, creator = "") } // en blanco: no cuenta
        repeat(9) { insert(d, 60 + it) } // sin creador: no cuenta
        insert(d + 1, 0, creator = "fuera_de_rango")

        val top = stats.topCreators(d, d, 10)
        assertEquals(
            listOf("bea" to "TIKTOK", "ana" to "TIKTOK", "berta" to "TIKTOK", "carla" to "TIKTOK", "ana" to "YOUTUBE"),
            top.map { it.creator to it.platform },
        )
        assertEquals(listOf(5, 5, 3, 3, 2), top.map { it.count })
        assertEquals(listOf("bea", "ana"), stats.topCreators(d, d, 2).map { it.creator })
    }

    @Test
    fun `compartidos por día, en total y hasta un minuto`() = runBlocking {
        share(d, 100)
        share(d, 900, reelId = 1)
        share(d + 1, 10)
        share(d + 1, 700)
        share(d + 5, 0)

        assertEquals(listOf(DayCountRow(d, 2), DayCountRow(d + 1, 2)), stats.sharesByDay(d, d + 1))
        assertEquals(4, stats.shareCount(d, d + 1))
        assertEquals(3, stats.sharesUntil(d, d + 1, 500)) // el último día solo hasta las 8:20
        assertEquals(5, stats.shareTotal())
    }

    @Test
    fun `totales hasta un minuto cortan solo el último día`() = runBlocking {
        insert(d, 100)
        insert(d, 1000)
        insert(d + 1, 100)
        insert(d + 1, 500) // en el minuto de corte: cuenta
        insert(d + 1, 1000)

        assertEquals(TotalsRow(4, 40_000), stats.totalsUntil(d, d + 1, 500))
        assertEquals(TotalsRow(5, 50_000), stats.totalsUntil(d, d + 1, 1439))
    }

    @Test
    fun `referencia del mismo día de la semana`() = runBlocking {
        insert(d - 7, 100); insert(d - 7, 200); insert(d - 7, 900)
        insert(d - 14, 900) // con datos, pero nada antes del corte
        // d - 21: sin datos
        insert(d - 28, 50)
        insert(d - 6, 100) // otro día de la semana

        val rows = stats.sameWeekdayBaseline(listOf(d - 7, d - 14, d - 21, d - 28), untilMinute = 500, limit = 8)
        assertEquals(listOf(DayRow(d - 7, 2, 20_000), DayRow(d - 14, 0, 0), DayRow(d - 28, 1, 10_000)), rows)
        assertEquals(listOf(d - 7, d - 14), stats.sameWeekdayBaseline(listOf(d - 7, d - 14, d - 28), 500, 2).map { it.day })
    }

    @Test
    fun `primer día vacío y borrado total`() = runBlocking {
        assertNull(stats.firstDay())
        assertEquals(TotalsRow(0, 0), stats.allTotals())
        insert(d, 10)
        share(d, 11, 1)
        dao.deleteAll()
        assertNull(stats.firstDay())
        assertEquals(0, stats.shareTotal())
    }
}
