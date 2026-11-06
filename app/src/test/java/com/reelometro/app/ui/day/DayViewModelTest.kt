package com.reelometro.app.ui.day

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.DayCopy
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.StatsCopy
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.stats.DayRank
import com.reelometro.app.stats.DayStats
import com.reelometro.app.ui.today.MIN
import com.reelometro.app.ui.today.MainDispatcherRule
import com.reelometro.app.ui.today.TODAY
import com.reelometro.app.ui.today.dayStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val yesterday = TODAY.minusDays(1)

    /** 10 días contados (desde hace 9 días hasta hoy). */
    private val firstDay = TODAY.minusDays(9)

    /** Puesto como lo daría SQL: [above] días con más vídeos que [date]. */
    private fun rankOf(date: LocalDate, count: Int = 270, above: Int = 2) = DayRank.of(date, TODAY, firstDay, count, above)

    private fun TestScope.viewModel(
        epochDay: Long = yesterday.toEpochDay(),
        day: Flow<DayStats> = flowOf(dayStats(date = yesterday, count = 270, totalMs = 78 * MIN)),
        rank: Flow<DayRank> = flowOf(rankOf(LocalDate.ofEpochDay(epochDay))),
    ): DayViewModel {
        val vm = DayViewModel(epochDay, day, rank, clock = { TODAY }, dispatcher = main.dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `mientras carga, el marcador ya está en blanco para recibir la cifra`() = runTest(main.dispatcher) {
        val vm = viewModel(day = flow { awaitCancellation() })
        advanceUntilIdle()
        val state = vm.uiState.value as DayUiState.Loading
        assertNull(state.hero.count)
        assertEquals(yesterday.toEpochDay(), state.hero.epochDay)
        assertEquals("AYER · LUNES 2 NOV", CopyRules.plain(state.top.label))
        assertEquals("Ayer, lunes 2 de noviembre", state.top.title)
    }

    @Test
    fun `un día pasado - frase de día pasado, comparación del día entero y puesto`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value as DayUiState.Content
        val seed = Variants.daySeed(yesterday)
        assertEquals(270L, state.hero.count)
        assertEquals(Labels.VIDEOS, state.hero.label)
        assertEquals(TodayCopy.quoted(DayCopy.quote(270, 78 * MIN, seed)), state.hero.quote)
        assertTrue(state.hero.comparison!!.text.endsWith("QUE TU LUNES MEDIO"))
        assertEquals(DayCopy.SECTION_HOURS, state.sections.hoursTitle)
        assertNull("un día pasado no tiene «ahora»", state.sections.hours.currentHour)
        assertEquals(DayCopy.rankLine(3, 10), state.rank)
    }

    @Test
    fun `el puesto no retiene el día - sin él, no hay puesto`() = runTest(main.dispatcher) {
        val vm = viewModel(rank = flow { awaitCancellation() })
        advanceUntilIdle()
        val state = vm.uiState.value as DayUiState.Content
        assertEquals(270L, state.hero.count)
        assertNull(state.rank)
    }

    @Test
    fun `si el puesto falla, el día se ve igual`() = runTest(main.dispatcher) {
        val vm = viewModel(rank = flow { throw IllegalStateException("sin puesto") })
        advanceUntilIdle()
        val state = vm.uiState.value as DayUiState.Content
        assertNull(state.rank)
    }

    @Test
    fun `el puesto sigue a la consulta - si cambia, cambia la banda`() = runTest(main.dispatcher) {
        val ranks = MutableStateFlow(rankOf(yesterday, above = 2))
        val vm = viewModel(rank = ranks)
        advanceUntilIdle()
        assertEquals(DayCopy.rankLine(3, 10), (vm.uiState.value as DayUiState.Content).rank)
        ranks.value = rankOf(yesterday, above = 0)
        advanceUntilIdle()
        assertEquals(DayCopy.rankLine(1, 10), (vm.uiState.value as DayUiState.Content).rank)
    }

    @Test
    fun `día sin vídeos es el estado vacío`() = runTest(main.dispatcher) {
        val vm = viewModel(day = flowOf(dayStats(date = yesterday, count = 0, totalMs = 0, shares = 0)))
        advanceUntilIdle()
        val state = vm.uiState.value as DayUiState.Empty
        assertEquals(0L, state.hero.count)
        assertEquals(TodayCopy.quoted(DayCopy.quote(0, 0, Variants.daySeed(yesterday))), state.hero.quote)
    }

    @Test
    fun `un día anterior al primer registro lo dice`() = runTest(main.dispatcher) {
        val before = TODAY.minusDays(30)
        val vm = viewModel(
            epochDay = before.toEpochDay(),
            day = flowOf(dayStats(date = before, count = 0, totalMs = 0, shares = 0, average = null)),
        )
        advanceUntilIdle()
        val state = vm.uiState.value as DayUiState.Empty
        val firstDay = TODAY.minusDays(9)
        assertEquals(TodayCopy.quoted(StatsCopy.beforeHistory(firstDay, Variants.daySeed(before))), state.hero.quote)
        assertNull("sin comparación antes de empezar a contar", state.hero.comparison)
    }

    @Test
    fun `hoy usa la frase de hoy, la comparación a esta hora y la hora actual`() = runTest(main.dispatcher) {
        val vm = viewModel(epochDay = TODAY.toEpochDay(), day = flowOf(dayStats()))
        advanceUntilIdle()
        val state = vm.uiState.value as DayUiState.Content
        assertEquals("HOY · MARTES 3 NOV", CopyRules.plain(state.top.label))
        assertEquals(TodayCopy.quoted(TodayCopy.headline(212, 47 * MIN, Variants.daySeed(TODAY))), state.hero.quote)
        assertTrue(state.hero.comparison!!.text.endsWith("A ESTA HORA"))
        assertEquals(TodayCopy.SECTION_HOURS, state.sections.hoursTitle)
        assertEquals(21, state.sections.hours.currentHour)
    }

    @Test
    fun `con objetivo, un día cerrado dice cuánto quedó por debajo`() = runTest(main.dispatcher) {
        val vm = viewModel(day = flowOf(dayStats(date = yesterday, count = 112, goal = 150)))
        advanceUntilIdle()
        val goal = (vm.uiState.value as DayUiState.Content).hero.goal!!
        assertEquals("38 POR DEBAJO", CopyRules.plain(goal.status))
    }

    @Test
    fun `un fallo es un error y reintentar lo arregla`() = runTest(main.dispatcher) {
        var calls = 0
        val day = flow {
            calls++
            if (calls == 1) throw IllegalStateException("roto") else emit(dayStats(date = yesterday, count = 270))
        }
        val vm = viewModel(day = day)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DayUiState.Error)
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DayUiState.Content)
    }

    @Test
    fun `la banda del puesto sale del puesto calculado en SQL`() {
        val day = dayStats(date = yesterday, count = 300)
        // Empate en el máximo: ningún día tiene más, así que ayer es récord (compartido).
        assertEquals(DayCopy.rankLine(1, 10), DayMapper.rankLine(day, rankOf(yesterday, count = 300, above = 0)))
        assertEquals(DayCopy.rankLine(4, 10), DayMapper.rankLine(day, rankOf(yesterday, count = 300, above = 3)))
        // Sin vídeos, sin puesto que llegar o con el puesto de otro día: no hay banda.
        assertNull(DayMapper.rankLine(dayStats(date = yesterday, count = 0), rankOf(yesterday, count = 0, above = 0)))
        assertNull(DayMapper.rankLine(day, null))
        assertNull(DayMapper.rankLine(day, rankOf(TODAY.minusDays(2))))
    }
}
