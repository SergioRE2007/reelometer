package com.reelometro.app.game

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Historial en memoria; [beforeLoad] permite simular algo que pasa a mitad de un cálculo. */
private class LiveTestLoader(counts: Map<LocalDate, Int>) : GameHistoryLoader {
    val counts = MutableStateFlow(counts)
    val skips = mutableListOf<Set<StampId>>()
    var beforeLoad: suspend () -> Unit = {}

    override fun changes(): Flow<Unit> = counts.map { }

    override suspend fun load(today: LocalDate, skip: Set<StampId>): GameHistory {
        skips += skip
        beforeLoad()
        return GameHistory(counts = counts.value)
    }
}

/** Memoria en memoria, como el DataStore: solo escribe si cambia. */
private class LiveTestStore(initial: PassportMemory = PassportMemory()) : PassportMemoryStore {
    val state = MutableStateFlow(initial)
    var writes = 0

    override val memory: Flow<PassportMemory> = state

    override suspend fun update(transform: (PassportMemory) -> PassportMemory) {
        val next = transform(state.value)
        if (next != state.value) {
            writes++
            state.value = next
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LivePassportTest {

    private val today = dia(7)
    private val loader = LiveTestLoader(iguales(LUNES, 7, 60))
    private val store = LiveTestStore()
    private val goal = MutableStateFlow<Int?>(100)

    private fun TestScope.passport(): LivePassport = LivePassport(
        loader = loader,
        store = store,
        dailyGoal = goal,
        today = { today },
        ticks = flowOf(Unit),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        throttleMs = 0,
    )

    @Test
    fun `calcula y guarda en la memoria lo que no se puede recalcular`() = runTest {
        val passport = passport()
        val state = passport.observe().first()
        assertEquals(TravelerLevel.FRECUENTE, state.level.level)
        val memory = store.state.value
        assertEquals(today, memory.since)
        assertEquals(TravelerLevel.FRECUENTE, memory.highestLevel)
        assertEquals(dia(6), memory.stamps[StampId.RACHA_7])
        // Lo guardado no se vuelve a consultar.
        passport.snapshot()
        assertEquals(memory.stamps.keys, loader.skips.last())
    }

    @Test
    fun `guardar lo calculado no provoca más escrituras`() = runTest {
        val passport = passport()
        passport.snapshot()
        val writes = store.writes
        passport.snapshot()
        passport.observe().first()
        assertEquals(writes, store.writes)
    }

    @Test
    fun `las celebraciones vistas no vuelven a salir`() = runTest {
        val passport = passport()
        val first = passport.snapshot()
        assertTrue(first.pending.isNotEmpty())
        passport.markCelebrated(first.pending.map { it.key })
        assertTrue(passport.snapshot().pending.isEmpty())
        assertTrue(passport.observe().first().pending.isEmpty())
        val writes = store.writes
        passport.markCelebrated(emptyList())
        assertEquals(writes, store.writes)
    }

    @Test
    fun `el flujo sigue a los datos, al objetivo y a la memoria`() = runTest {
        val passport = passport()
        val states = mutableListOf<PassportState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { passport.observe().collect { states += it } }
        advanceUntilIdle()
        assertEquals("guardar la memoria no repite el estado", 1, states.size)
        assertEquals(7, states.last().streak)

        goal.value = 10
        advanceUntilIdle()
        assertEquals(0, states.last().streak)
        assertEquals("el nivel no baja", TravelerLevel.FRECUENTE, states.last().level.level)

        loader.counts.value = loader.counts.value + (today to 4)
        advanceUntilIdle()
        assertEquals(4, states.last().challenge?.total)

        val mood = states.last().flap.mood
        val other = if (mood == FlapMood.EN_FORMA) FlapMood.ZOMBI else FlapMood.EN_FORMA
        passport.markMoodSeen(other)
        advanceUntilIdle()
        assertEquals(other, states.last().flap.previousMood)
        passport.markMoodSeen(mood)
        advanceUntilIdle()
        assertNull(states.last().flap.previousMood)
    }

    @Test
    fun `un objetivo de 0 es no tener objetivo`() = runTest {
        goal.value = 0
        assertNull(passport().snapshot().goal)
    }

    @Test
    fun `los eventos dan su sello con la fecha de hoy`() = runTest {
        val passport = passport()
        passport.recordEvent(GameEvent.WRAPPED_SHARED)
        passport.recordEvent(GameEvent.WRAPPED_SHARED)
        assertEquals(mapOf(GameEvent.WRAPPED_SHARED to today), store.state.value.events)
        val state = passport.snapshot()
        assertEquals(today, state.fecha(StampId.WRAPPED_COMPARTIDO))
        assertTrue(Celebration.StampEarned(StampId.WRAPPED_COMPARTIDO, today) in state.pending)
    }

    @Test
    fun `borrar deja el pasaporte vacío y lo calculado antes no resucita`() = runTest {
        val passport = passport()
        passport.snapshot()
        passport.clear()
        assertEquals(PassportMemory(generation = 1), store.state.value)
        // Un borrado a mitad de cálculo: la escritura del cálculo viejo se descarta.
        loader.beforeLoad = { passport.clear() }
        passport.snapshot()
        assertEquals(PassportMemory(generation = 2), store.state.value)
    }

    @Test
    fun `la celebración de prueba queda pendiente`() = runTest {
        val passport = passport()
        val seen = passport.snapshot().pending.map { it.key }
        passport.markCelebrated(seen)
        passport.force(ForcedCelebration.LEVEL, StampId.PRIMER_DIA)
        assertEquals(listOf(Celebration.LevelUp(TravelerLevel.FRECUENTE)), passport.snapshot().pending)
        passport.force(ForcedCelebration.STAMP, StampId.MARATON)
        assertTrue(Celebration.StampEarned(StampId.MARATON, today) in passport.snapshot().pending)
    }
}
