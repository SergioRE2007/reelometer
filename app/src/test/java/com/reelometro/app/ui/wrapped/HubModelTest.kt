package com.reelometro.app.ui.wrapped

import com.reelometro.app.copy.WrappedCopy
import com.reelometro.app.stats.PeriodRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HubModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun all(empty: Set<PeriodRef> = emptySet()) = HubModel.PERIODS.map { ref ->
        if (ref in empty) periodStats(ref = ref, perDay = { 0 }) else periodStats(ref = ref)
    }

    @Test
    fun `el mes pasado va primero y destacado`() {
        val strips = HubModel.strips(all())
        assertEquals(HubModel.PERIODS, strips.map { it.ref })
        assertTrue(strips.first().featured)
        assertEquals(1, strips.count { it.featured })
        assertEquals(WrappedCopy.HUB_LAST_MONTH, strips.first().title)
        assertEquals("OCTUBRE 2026", strips.first().range)
    }

    @Test
    fun `los periodos en curso lo dicen y los cerrados no`() {
        val strips = HubModel.strips(all()).associateBy { it.ref }
        assertFalse(strips.getValue(PeriodRef.LAST_MONTH).detail.contains(WrappedCopy.HUB_IN_PROGRESS))
        assertTrue(strips.getValue(PeriodRef.THIS_MONTH).detail.endsWith(WrappedCopy.HUB_IN_PROGRESS))
        assertTrue(strips.getValue(PeriodRef.THIS_WEEK).detail.endsWith(WrappedCopy.HUB_IN_PROGRESS))
    }

    @Test
    fun `sin datos del mes pasado se destaca el primero que tenga y el vacío va al final atenuado`() {
        val strips = HubModel.strips(all(empty = setOf(PeriodRef.LAST_MONTH)))
        assertEquals(PeriodRef.THIS_MONTH, strips.first().ref)
        assertTrue(strips.first().featured)
        val last = strips.last()
        assertEquals(PeriodRef.LAST_MONTH, last.ref)
        assertFalse(last.hasData)
        assertFalse(last.featured)
        assertEquals(WrappedCopy.HUB_NO_DATA, last.detail)
    }

    @Test
    fun `el titular habla del mes pasado o, si no hay, del actual`() {
        assertEquals(YearMonth.of(2026, 10), HubModel.headlineMonth(all()))
        assertEquals(YearMonth.of(2026, 11), HubModel.headlineMonth(all(empty = setOf(PeriodRef.LAST_MONTH))))
    }

    @Test
    fun `sin ningún dato el hub está vacío`() {
        val state = WrappedHubViewModel.hubState(all(empty = HubModel.PERIODS.toSet()))
        assertTrue(state is WrappedHubUiState.Empty)
    }

    @Test
    fun `el ViewModel combina los cuatro periodos y se recupera de un error`() = runTest {
        var fail = true
        val source = PeriodObserver { ref ->
            if (fail) kotlinx.coroutines.flow.flow { throw IllegalStateException("sin base de datos") } else flowOf(periodStats(ref = ref))
        }
        val vm = WrappedHubViewModel(source, today = { TODAY })
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        assertTrue(vm.state.value is WrappedHubUiState.Error)
        fail = false
        vm.retry()
        val content = vm.state.first { it is WrappedHubUiState.Content } as WrappedHubUiState.Content
        assertEquals(4, content.strips.size)
        assertTrue(content.headline.isNotBlank())
        collector.cancel()
    }

    @Test
    fun `el ViewModel empieza cargando`() {
        val vm = WrappedHubViewModel({ MutableStateFlow(periodStats(ref = it)) }, today = { TODAY })
        assertTrue(vm.state.value is WrappedHubUiState.Loading)
    }
}
