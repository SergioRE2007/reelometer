package com.reelometro.app.ui.stats

import androidx.lifecycle.SavedStateHandle
import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.StatsCopy
import com.reelometro.app.stats.DateSpan
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import com.reelometro.app.stats.Totals
import com.reelometro.app.stats.Trend
import com.reelometro.app.ui.components.charts.GhostBar
import com.reelometro.app.ui.today.MainDispatcherRule
import com.reelometro.app.ui.today.TODAY
import com.reelometro.app.ui.today.periodStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Estadísticas · SEMANA: «contra la semana anterior» (línea fantasma) y accesibilidad nueva. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsWeekCompareTest {

    @get:Rule
    val main = MainDispatcherRule()

    /** Semanas anteriores pedidas para la comparativa, en orden. */
    private val ghosts = mutableListOf<PeriodRef>()

    private fun TestScope.viewModel(
        source: (PeriodRef) -> Flow<PeriodStats> = { flowOf(periodStats(it)) },
        previous: ((PeriodRef) -> Flow<PeriodStats>)? = { ref -> ghosts += ref; flowOf(periodStats(ref)) },
    ): StatsViewModel {
        val vm = StatsViewModel(SavedStateHandle(), source, clock = { TODAY }, dispatcher = main.dispatcher, previousWeek = previous)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private val StatsViewModel.content: StatsUiState.Content get() = uiState.value as StatsUiState.Content

    private fun trend(change: Double) =
        Trend(DateSpan(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27)), 21 * 60, Totals(230, 0L), 2, change, null)

    @Test
    fun `en SEMANA cada día va frente al mismo de la semana anterior`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val week = vm.content.sections.week!!
        assertNotNull(week)
        assertEquals(listOf(PeriodRef(PeriodKind.WEEK, -1)), ghosts)
        assertEquals(7, week.bars.size)
        // Lunes 2 (102) frente al lunes 26 de octubre (126); el miércoles aún no ha llegado.
        assertEquals(GhostBar("L", 102, 126, StatsCopy.ghostTooltip(TODAY.minusDays(1), 102, 126, TODAY)), week.bars[0])
        assertNull(week.bars[2].count)
        assertEquals(128, week.bars[2].ghost)
        assertEquals(1, week.todayIndex)
        assertEquals(StatsCopy.GHOST_LEGEND, week.legend)
        // Sin tendencia (sin datos del mismo tramo): la semana y la anterior entera.
        assertEquals(listOf("week", "previousWhole"), week.lines.map { it.key })
        assertEquals("205", week.lines[0].value)
        assertEquals(StatsCopy.LINE_THIS_WEEK_SO_FAR, week.lines[0].label)
        assertEquals("872", week.lines[1].value)
        assertTrue(CopyRules.violations(week.description).isEmpty())
    }

    @Test
    fun `con tendencia - el mismo tramo de la anterior y la diferencia, en acento si es más`() = runTest(main.dispatcher) {
        val less = viewModel(source = { flowOf(periodStats(it, trend = trend(-0.1087))) })
        advanceUntilIdle()
        val lines = less.content.sections.week!!.lines
        assertEquals(listOf("week", "previous", "previousWhole", "difference"), lines.map { it.key })
        assertEquals(StatsCopy.LINE_PREVIOUS_SAME_STRETCH, lines[1].label)
        assertEquals("230", lines[1].value)
        assertEquals("−11 %", CopyRules.plain(lines[3].value))
        assertFalse(lines[3].alert)

        val more = viewModel(source = { flowOf(periodStats(it, trend = trend(0.12))) })
        advanceUntilIdle()
        val diff = more.content.sections.week!!.lines.last()
        assertEquals("+12 %", CopyRules.plain(diff.value))
        assertTrue(diff.alert)
    }

    @Test
    fun `fuera de SEMANA no se pide la semana anterior ni hay comparativa`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectTab(1) // MES
        advanceUntilIdle()
        assertNull(vm.content.sections.week)
        assertEquals(listOf(PeriodRef(PeriodKind.WEEK, -1)), ghosts)
    }

    @Test
    fun `en una semana pasada el fantasma es la de antes y no hay hoy`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.previous()
        advanceUntilIdle()
        assertEquals(listOf(PeriodRef(PeriodKind.WEEK, -1), PeriodRef(PeriodKind.WEEK, -2)), ghosts)
        val week = vm.content.sections.week!!
        assertNull(week.todayIndex)
        assertEquals(StatsCopy.LINE_THAT_WEEK, week.lines[0].label)
        assertTrue(week.bars.all { it.count != null && it.ghost != null })
    }

    @Test
    fun `si la semana anterior falla o no hay fuente, la pantalla sale sin la comparativa`() = runTest(main.dispatcher) {
        val failing = viewModel(previous = { flow { throw IllegalStateException("sin semana anterior") } })
        advanceUntilIdle()
        assertNull(failing.content.sections.week)

        val none = viewModel(previous = null)
        advanceUntilIdle()
        assertNull(none.content.sections.week)
    }

    @Test
    fun `si la semana anterior no tiene ningún registro, se omite`() = runTest(main.dispatcher) {
        val first = LocalDate.of(2026, 11, 2)
        val vm = viewModel(
            source = { flowOf(periodStats(it, firstDay = first)) },
            previous = { flowOf(periodStats(it, firstDay = first)) },
        )
        advanceUntilIdle()
        assertNull(vm.content.sections.week)
    }

    @Test
    fun `TalkBack - abrir cada día desde las barras en semana y mes, no en año`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val week = vm.content.sections.daily.actionLabels
        assertEquals(7, week.size)
        assertEquals(StatsCopy.openDayAction(TODAY.minusDays(1)), week[0])
        assertNull("el miércoles aún no se puede abrir", week[2])

        vm.selectTab(1)
        advanceUntilIdle()
        assertEquals(30, vm.content.sections.daily.actionLabels.size)

        vm.selectTab(2)
        advanceUntilIdle()
        assertTrue(vm.content.sections.daily.actionLabels.isEmpty())
    }

    @Test
    fun `TalkBack - la línea del resumen se dice sin abreviaturas`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val summary = vm.content.summary
        val spoken = summary.lineDescription!!
        assertTrue(spoken, CopyRules.plain(spoken).startsWith("205 vídeos, "))
        assertFalse(spoken.contains("·"))
        assertTrue(CopyRules.violations(spoken).isEmpty())
    }
}
