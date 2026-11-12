package com.reelometro.app.game

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Vídeos por día en memoria; cuenta las lecturas (cada una es un recálculo). */
private class LiveFlapLoader(initial: Map<LocalDate, Int>) : FlapHistoryLoader {
    val data = MutableStateFlow(initial)
    var loads = 0

    override fun changes(): Flow<Unit> = data.map { }

    override suspend fun counts(today: LocalDate): Map<LocalDate, Int> {
        loads++
        return data.value
    }
}

/** Lo visto en memoria, como el DataStore: solo escribe si cambia. */
private class LiveFlapStore(initial: FlapSeen? = null) : FlapSeenStore {
    val state = MutableStateFlow(initial)
    var writes = 0

    override val seen: Flow<FlapSeen?> = state

    override suspend fun save(seen: FlapSeen) {
        if (state.value != seen) {
            writes++
            state.value = seen
        }
    }

    override suspend fun clear() {
        if (state.value != null) {
            writes++
            state.value = null
        }
    }
}

/** [LiveFlap]: recalcula con los datos, el objetivo, lo visto y la medianoche, sin bucles ni prisas. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveFlapTest {

    private var now: LocalDate = LocalDate.of(2026, 11, 16)
    private val loader = LiveFlapLoader(mapOf(now to 31, now.minusDays(1) to 10))
    private val store = LiveFlapStore()
    private val goal = MutableStateFlow<Int?>(40)

    /** Como `ClockTicker`: emite al suscribirse y cada vez que el test pasa la medianoche ([midnight]). */
    private val ticks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16).apply { tryEmit(Unit) }

    private fun midnight() {
        now = now.plusDays(1)
        ticks.tryEmit(Unit)
    }

    private fun TestScope.live(throttleMs: Long = 0): LiveFlap = LiveFlap(
        loader = loader,
        store = store,
        dailyGoal = goal,
        today = { now },
        ticks = ticks,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        throttleMs = throttleMs,
    )

    /** Estados que emite [LiveFlap.observe] mientras dura el test. */
    private fun TestScope.record(live: LiveFlap): MutableList<FlapState> {
        val states = mutableListOf<FlapState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { live.observe().collect { states += it } }
        return states
    }

    @Test
    fun `calcula con los vídeos de hoy y el objetivo`() = runTest {
        val live = live()
        val state = live.observe().first()
        assertEquals(now, state.today)
        assertEquals(31, state.count)
        assertEquals(40, state.limit)
        assertEquals(FlapMood.NORMAL, state.mood)
        assertEquals(2, state.trackedDays)
        assertEquals(state, live.snapshot())
        // Calcular no escribe nada.
        assertEquals(0, store.writes)
    }

    @Test
    fun `el flujo sigue a los datos, al objetivo y a lo visto`() = runTest {
        val live = live()
        val states = record(live)
        advanceUntilIdle()
        assertEquals(1, states.size)
        assertEquals(FlapMood.NORMAL, states.last().mood)

        loader.data.value = loader.data.value + (now to 45)
        advanceUntilIdle()
        assertEquals(FlapMood.ENFERMO, states.last().mood)

        goal.value = 100
        advanceUntilIdle()
        assertEquals(100, states.last().limit)
        assertEquals(FlapMood.EN_FORMA, states.last().mood)

        live.markMoodSeen(FlapMood.ENFERMO)
        advanceUntilIdle()
        assertEquals(FlapMood.ENFERMO, states.last().previousMood)
        assertTrue(states.last().recovered)

        live.markMoodSeen(FlapMood.EN_FORMA)
        advanceUntilIdle()
        assertNull(states.last().previousMood)
    }

    @Test
    fun `a medianoche revive y recuerda que estaba KO`() = runTest {
        loader.data.value = mapOf(now to 200)
        val live = live()
        val states = record(live)
        advanceUntilIdle()
        assertEquals(FlapMood.KO, states.last().mood)
        live.markMoodSeen(FlapMood.KO)
        advanceUntilIdle()
        assertNull(states.last().previousMood)

        val yesterday = now
        midnight()
        advanceUntilIdle()
        val morning = states.last()
        assertEquals(yesterday.plusDays(1), morning.today)
        assertEquals(0, morning.count)
        assertEquals(FlapMood.EN_FORMA, morning.mood)
        assertEquals(100, morning.life)
        assertEquals(FlapMood.KO, morning.previousMood)
        assertTrue(morning.recovered)
        assertEquals(200, morning.week.last().count)
        assertEquals(yesterday, morning.week.last().date)
    }

    @Test
    fun `sin bucles - guardar lo visto recalcula una vez y no vuelve a escribir`() = runTest {
        val live = live()
        val states = record(live)
        advanceUntilIdle()
        val loads = loader.loads
        assertEquals(1, states.size)

        live.markMoodSeen(FlapMood.NORMAL)
        advanceUntilIdle()
        assertEquals(1, store.writes)
        assertEquals("lo visto cambia: un recálculo", loads + 1, loader.loads)
        assertEquals("el estado no cambia: no se repite", 1, states.size)

        live.markMoodSeen(FlapMood.NORMAL)
        live.snapshot()
        advanceUntilIdle()
        assertEquals("lo mismo no se escribe otra vez", 1, store.writes)
        assertEquals("snapshot lee una vez y nada más", loads + 2, loader.loads)
        assertEquals(1, states.size)
    }

    @Test
    fun `con prisa recalcula como mucho una vez por ventana y con lo último`() = runTest {
        val live = live(throttleMs = LiveFlap.THROTTLE_MS)
        val states = record(live)
        runCurrent()
        val loads = loader.loads
        repeat(20) { i -> loader.data.value = mapOf(now to i + 1) }
        // Se recoge en backgroundScope: advanceUntilIdle no espera sus delay(), así que el reloj se avanza a mano.
        advanceTimeBy(LiveFlap.THROTTLE_MS * 3)
        runCurrent()
        assertEquals(20, states.last().count)
        assertTrue("recálculos: ${loader.loads - loads}", loader.loads - loads <= 2)
    }

    @Test
    fun `un objetivo de 0 es no tener objetivo`() = runTest {
        goal.value = 0
        val state = live().snapshot()
        assertNull(state.goal)
        assertEquals(FlapRules.DEFAULT_LIMIT, state.limit)
        assertEquals(FlapMood.EN_FORMA, state.mood)
    }

    @Test
    fun `lo visto se guarda con su día y clear lo olvida`() = runTest {
        val live = live()
        live.markMoodSeen(FlapMood.CANSADO)
        assertEquals(FlapSeen(FlapMood.CANSADO, now), store.state.value)
        assertEquals(FlapMood.CANSADO, live.snapshot().previousMood)
        live.clear()
        assertNull(store.state.value)
        assertNull(live.snapshot().previousMood)
    }

    @Test
    fun `FlapSeen se guarda como MOOD y fecha y lo que no se entiende es nunca visto`() {
        val seen = FlapSeen(FlapMood.ENFERMO, LocalDate.of(2026, 11, 4))
        assertEquals("ENFERMO|2026-11-04", seen.encode())
        assertEquals(seen, FlapSeen.decode(seen.encode()))
        assertEquals(seen, FlapSeen.decode(" ENFERMO | 2026-11-04 "))
        FlapMood.entries.forEach { mood ->
            val s = FlapSeen(mood, LocalDate.of(2026, 1, 31))
            assertEquals(s, FlapSeen.decode(s.encode()))
        }
        for (garbage in listOf(null, "", "|", "ZOMBI|2026-11-04", "KO", "KO|", "KO|ayer", "KO|2026-02-30", "KO|2026-11-04|x", "{\"stamps\":{}}")) {
            assertNull("«$garbage»", FlapSeen.decode(garbage))
        }
    }
}
