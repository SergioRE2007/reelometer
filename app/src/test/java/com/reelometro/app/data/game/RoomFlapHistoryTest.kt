package com.reelometro.app.data.game

import android.app.Application
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.Category
import com.reelometro.app.data.LocalStamp
import com.reelometro.app.data.MADRID
import com.reelometro.app.data.ManualTicker
import com.reelometro.app.data.MutableClock
import com.reelometro.app.data.NativeSqliteRule
import com.reelometro.app.data.Platform
import com.reelometro.app.data.ROBOLECTRIC_SDK
import com.reelometro.app.data.ReelRepository
import com.reelometro.app.data.ReelRobolectricRunner
import com.reelometro.app.data.ReelView
import com.reelometro.app.data.at
import com.reelometro.app.data.inMemoryDatabase
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSeen
import com.reelometro.app.game.FlapSeenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Lo visto de Flap en memoria para los tests con Room. */
private class RoomTestSeenStore : FlapSeenStore {
    val state = MutableStateFlow<FlapSeen?>(null)

    override val seen: Flow<FlapSeen?> = state

    override suspend fun save(seen: FlapSeen) {
        state.value = seen
    }

    override suspend fun clear() {
        state.value = null
    }
}

/** Flap sobre Room: vídeos por día con el SQL de las rachas y su paso por BORRAR TODO. */
@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class RoomFlapHistoryTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()

    private lateinit var db: AppDatabase
    private val monday = LocalDate.of(2026, 11, 2)
    private var nextId = 1L

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun views(date: LocalDate, hour: Int, minute: Int, n: Int = 1, platform: Platform = Platform.TIKTOK): List<ReelView> =
        List(n) {
            val startedAt = at(date, hour, minute)
            val stamp = LocalStamp.of(startedAt, MADRID)
            ReelView(nextId++, platform.name, startedAt, 1_000, null, null, Category.OTROS.name, stamp.day, stamp.minuteOfDay)
        }

    private fun day(n: Int): LocalDate = monday.plusDays(n.toLong())

    /** Como en la app (`LiveFlap` calcula fuera del hilo principal): nunca en el hilo del test. */
    private suspend fun counts(today: LocalDate): Map<LocalDate, Int> =
        withContext(Dispatchers.Default) { RoomFlapHistory(db).counts(today) }

    @Test
    fun `sin vídeos no hay recuentos`() = runBlocking {
        assertTrue(counts(monday).isEmpty())
    }

    @Test
    fun `recuentos por día de todo el historial hasta hoy, sin los días vacíos`() = runBlocking {
        db.reelDao().insertAll(
            views(day(0), 10, 0, n = 3) + views(day(0), 23, 59) + views(day(2), 9, 0, n = 2, platform = Platform.INSTAGRAM) +
                views(day(6), 15, 0, n = 41) + views(day(9), 8, 0),
        )
        assertEquals(mapOf(day(0) to 4, day(2) to 2, day(6) to 41), counts(day(6)))
        assertEquals(setOf(day(0), day(2)), counts(day(5)).keys)
        assertEquals(mapOf(day(0) to 4, day(2) to 2, day(6) to 41, day(9) to 1), counts(day(9)))
    }

    @Test
    fun `Flap real - estado, semana y racha con Room, y BORRAR TODO olvida lo visto`() = runBlocking {
        // Una semana a 30 vídeos y hoy 45: con objetivo 40, enfermo.
        db.reelDao().insertAll((0..6).flatMap { views(day(it), 10, 0, n = 30) } + views(day(7), 20, 0, n = 45))
        val clock = MutableClock(at(day(7), 21, 0))
        val seen = RoomTestSeenStore()
        val flap = FlapRepository(
            loader = RoomFlapHistory(db),
            dailyGoal = flowOf(40),
            store = seen,
            clock = clock,
            ticker = ManualTicker(),
            dispatcher = Dispatchers.Default,
            throttleMs = 0,
        )
        val state = flap.snapshot()
        assertEquals(day(7), state.today)
        assertEquals(45, state.count)
        assertEquals(FlapMood.ENFERMO, state.mood)
        assertEquals(List(7) { 30 }, state.week.map { it.count })
        assertEquals(7, state.healthyStreak)
        assertEquals(8, state.trackedDays)

        flap.markMoodSeen(FlapMood.CANSADO)
        assertEquals(FlapSeen(FlapMood.CANSADO, day(7)), seen.state.value)
        assertEquals(FlapMood.CANSADO, flap.snapshot().previousMood)

        val repository = ReelRepository(db, clock = clock, ticker = ManualTicker(), userData = { listOf(flap) })
        repository.clearAll()
        assertNull(seen.state.value)
        val empty = flap.snapshot()
        assertEquals(0, empty.trackedDays)
        assertEquals(FlapMood.EN_FORMA, empty.mood)
        assertNull(empty.previousMood)
    }
}
