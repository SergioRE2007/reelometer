package com.reelometro.app.ui.stats

import androidx.lifecycle.SavedStateHandle
import com.reelometro.app.copy.CopyPeriod
import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.StatsCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.stats.DateSpan
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import com.reelometro.app.stats.Streak
import com.reelometro.app.stats.Totals
import com.reelometro.app.stats.Trend
import com.reelometro.app.ui.components.charts.HeatmapMode
import com.reelometro.app.ui.today.MainDispatcherRule
import com.reelometro.app.ui.today.TODAY
import com.reelometro.app.ui.today.periodStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

private const val WORD_JOINER = '\u2060'

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val seed = Variants.daySeed(TODAY)

    /** Periodos pedidos al repositorio falso, en orden. */
    private val requested = mutableListOf<PeriodRef>()

    private fun TestScope.viewModel(
        saved: SavedStateHandle = SavedStateHandle(),
        source: (PeriodRef) -> Flow<PeriodStats> = { flowOf(periodStats(it)) },
    ): StatsViewModel {
        val vm = StatsViewModel(saved, { ref -> requested += ref; source(ref) }, clock = { TODAY }, dispatcher = main.dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `empieza en esta semana con su cifra, resumen y tendencia`() = runTest(main.dispatcher) {
        val trend = Trend(DateSpan(TODAY.minusDays(8), TODAY.minusDays(7)), 21 * 60, Totals(200, 0), 2, 0.12, 0.1)
        val vm = viewModel(source = { flowOf(periodStats(it, trend = trend)) })
        advanceUntilIdle()
        val state = vm.uiState.value as StatsUiState.Content
        assertEquals(0, state.header.tabIndex)
        assertEquals(listOf("SEMANA", "MES", "AÑO", "SIEMPRE"), state.header.tabs)
        assertEquals("2 – 8 NOV 2026", CopyRules.plain(state.header.navigatorLabel))
        assertTrue(state.header.canGoPrevious)
        assertFalse(state.header.canGoNext)
        // Lunes 2 (102) + martes 3 (103).
        assertEquals("205", state.summary.count)
        assertEquals("WEEK:0", state.summary.key)
        assertEquals("+12 % QUE LA SEMANA ANTERIOR", CopyRules.plain(state.summary.trend!!.text))
        assertEquals(listOf(PeriodRef.THIS_WEEK), requested)
    }

    @Test
    fun `el resumen solo se parte por los separadores`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val line = (vm.uiState.value as StatsUiState.Content).summary.line
        // Solo se parte tras cada «·», que va pegado (espacio duro) al trozo de antes: nunca empieza una línea.
        line.split("\u00A0· ").forEach { segment -> assertFalse("«$segment» se podría partir", ' ' in segment) }
        // Tampoco tras la barra de «103/DÍA»: lleva detrás un WORD JOINER (U+2060), que no se ve.
        assertTrue("la barra se podría partir", line.contains("/$WORD_JOINER"))
        // El texto es el mismo que el de copy/: solo cambian los espacios de dentro de cada trozo.
        assertEquals(
            CopyRules.plain(StatsCopy.summaryLine(205, 205 * 15_000L, 102.5, 15_000)),
            CopyRules.plain(line.replace(WORD_JOINER.toString(), "")),
        )
    }

    @Test
    fun `cambiar de pestaña pide el periodo actual de ese tipo y anima hacia la derecha`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectTab(2)
        advanceUntilIdle()
        val state = vm.uiState.value as StatsUiState.Content
        assertEquals(2, state.header.tabIndex)
        assertEquals(1, state.header.direction)
        assertEquals("2026", state.header.navigatorLabel)
        assertEquals("YEAR:0", state.summary.key)
        assertEquals(PeriodRef(PeriodKind.YEAR), requested.last())

        vm.selectTab(0)
        advanceUntilIdle()
        assertEquals(-1, vm.uiState.value.header.direction)
    }

    @Test
    fun `atrás y adelante por periodos`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.previous()
        advanceUntilIdle()
        var header = vm.uiState.value.header
        assertEquals("26 OCT – 1 NOV 2026", CopyRules.plain(header.navigatorLabel))
        assertEquals(-1, header.direction)
        assertTrue(header.canGoNext)
        assertEquals(PeriodRef(PeriodKind.WEEK, -1), requested.last())

        vm.next()
        advanceUntilIdle()
        header = vm.uiState.value.header
        assertEquals(1, header.direction)
        assertFalse(header.canGoNext)
        assertEquals(PeriodRef.THIS_WEEK, requested.last())

        vm.next()
        advanceUntilIdle()
        assertEquals("no hay periodos futuros", PeriodRef.THIS_WEEK, requested.last())
    }

    @Test
    fun `no se puede ir antes del primer registro`() = runTest(main.dispatcher) {
        val monday = LocalDate.of(2026, 11, 2)
        val vm = viewModel(source = { flowOf(periodStats(it, firstDay = monday)) })
        advanceUntilIdle()
        assertFalse(vm.uiState.value.header.canGoPrevious)
        vm.previous()
        advanceUntilIdle()
        assertEquals(listOf(PeriodRef.THIS_WEEK), requested)
    }

    @Test
    fun `el periodo elegido sobrevive a la recreación`() = runTest(main.dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.selectTab(1)
        first.previous()
        advanceUntilIdle()

        val again = viewModel(saved)
        advanceUntilIdle()
        val state = again.uiState.value as StatsUiState.Content
        assertEquals(1, state.header.tabIndex)
        assertEquals("OCTUBRE 2026", state.header.navigatorLabel)
        assertEquals("MONTH:-1", state.summary.key)
    }

    @Test
    fun `mientras llega el periodo nuevo se mantiene la cifra del anterior`() = runTest(main.dispatcher) {
        val month = MutableSharedFlow<PeriodStats>()
        val vm = viewModel(source = { ref -> if (ref.kind == PeriodKind.MONTH) month else flowOf(periodStats(ref)) })
        advanceUntilIdle()
        vm.selectTab(1)
        advanceUntilIdle()
        var state = vm.uiState.value as StatsUiState.Content
        assertEquals("la cabecera ya es la del mes", 1, state.header.tabIndex)
        assertEquals("la cifra aún es la de la semana", "WEEK:0", state.summary.key)

        month.emit(periodStats(PeriodRef.THIS_MONTH))
        advanceUntilIdle()
        state = vm.uiState.value as StatsUiState.Content
        assertEquals("MONTH:0", state.summary.key)
    }

    @Test
    fun `periodo sin vídeos - cifra a cero y frase`() = runTest(main.dispatcher) {
        val vm = viewModel(source = { flowOf(periodStats(it, perDay = { 0 })) })
        advanceUntilIdle()
        val state = vm.uiState.value as StatsUiState.Empty
        assertEquals("0", state.summary.count)
        assertEquals(StatsCopy.emptyPeriod(CopyPeriod.WEEK, current = true, seed = seed), state.message)
    }

    @Test
    fun `un fallo es un error y reintentar vuelve a pedir el periodo`() = runTest(main.dispatcher) {
        var calls = 0
        val vm = viewModel(source = { ref ->
            flow {
                calls++
                if (calls == 1) throw IllegalStateException("roto") else emit(periodStats(ref))
            }
        })
        advanceUntilIdle()
        assertTrue(vm.uiState.value is StatsUiState.Error)
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is StatsUiState.Content)
    }

    // ------------------------------------------------------------------ mapeo de secciones

    @Test
    fun `la semana en curso tiene sus 7 barras y solo se abren los días pasados`() {
        val daily = StatsMapper.daily(periodStats(PeriodRef.THIS_WEEK))
        assertEquals(7, daily.values.size)
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), daily.labels)
        assertEquals(1, daily.highlightIndex)
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), daily.days[0])
        assertNull("el miércoles aún no ha llegado", daily.days[2])
        assertEquals(StatsCopy.futureTooltip(LocalDate.of(2026, 11, 4)), daily.tooltips[2])
        assertNotNull(daily.average)
    }

    @Test
    fun `el año va con los meses en el eje y sin resaltar hoy`() {
        val daily = StatsMapper.daily(periodStats(PeriodRef.THIS_YEAR))
        assertEquals(365, daily.values.size)
        assertEquals("ENE", daily.labels[0])
        assertEquals("FEB", daily.labels[31])
        assertEquals("", daily.labels[1])
        assertNull(daily.highlightIndex)
        assertNull("antes del primer registro no se abre nada", daily.days[0])
        assertEquals(StatsCopy.untrackedTooltip(LocalDate.of(2026, 1, 1)), daily.tooltips[0])
    }

    @Test
    fun `siempre con más de un año se agrupa por semanas y hay un calendario por año`() {
        val stats = periodStats(PeriodRef.ALL_TIME, firstDay = LocalDate.of(2025, 3, 3))
        val daily = StatsMapper.daily(stats)
        assertEquals(StatsCopy.BY_WEEK, daily.trailing)
        assertTrue(daily.days.all { it == null })
        assertTrue(daily.values.size in 80..90)

        val calendar = StatsMapper.calendar(stats)
        assertEquals(HeatmapMode.YEAR, calendar.mode)
        assertEquals(listOf("2026", "2025"), calendar.blocks.map { it.label })
        assertNotNull("el mapa puede dibujar todo el año: todos los días tienen tooltip", calendar.tooltips[LocalDate.of(2025, 1, 1)])
        assertTrue(LocalDate.of(2025, 3, 3) in calendar.openable)
        assertFalse(LocalDate.of(2025, 3, 2) in calendar.openable)
    }

    @Test
    fun `patrones sin objetivo llevan a ponerlo y con objetivo enseñan las rachas`() {
        val noGoal = StatsMapper.patterns(periodStats(PeriodRef.THIS_MONTH), seed)
        assertEquals(StatsCopy.noGoalStreak(seed), noGoal.noGoal)
        assertTrue(noGoal.lines.none { it.key == "streak" })
        val worst = noGoal.lines.first { it.key == "worst" }
        assertEquals(LocalDate.of(2026, 11, 3).toEpochDay(), worst.epochDay)
        assertTrue(worst.alert)
        assertTrue("día débil en el mes", noGoal.lines.any { it.key == "weak" })
        assertNotNull(noGoal.quote)

        val withGoal = StatsMapper.patterns(periodStats(PeriodRef.THIS_WEEK, streak = Streak(150, 4, 9, false)), seed)
        assertNull(withGoal.noGoal)
        assertEquals("4 DÍAS", CopyRules.plain(withGoal.lines.first { it.key == "streak" }.value))
        assertEquals("9 DÍAS", CopyRules.plain(withGoal.lines.first { it.key == "best" }.value))
        assertTrue("en una semana el día débil sería el peor día otra vez", withGoal.lines.none { it.key == "weak" })
    }

    @Test
    fun `tendencia - sin periodo anterior lo dice y en siempre no hay`() {
        val none = StatsMapper.trend(periodStats(PeriodRef.THIS_WEEK))
        assertEquals("SIN DATOS DE LA SEMANA ANTERIOR", none!!.text)
        assertNull(StatsMapper.trend(periodStats(PeriodRef.ALL_TIME)))
        val fromZero = Trend(DateSpan(TODAY.minusDays(8), TODAY.minusDays(7)), null, Totals.ZERO, 2, null, null)
        assertEquals("LA SEMANA ANTERIOR FUE A CERO", StatsMapper.trend(periodStats(PeriodRef.THIS_WEEK, trend = fromZero))!!.text)
    }
}
