package com.reelometro.app.ui.calibration

import android.view.accessibility.AccessibilityEvent
import com.reelometro.app.copy.CalibrationMetricsCopy
import com.reelometro.app.copy.ExitCopy
import com.reelometro.app.data.Platform
import com.reelometro.app.data.RecentVideo
import com.reelometro.app.detection.CalibrationEvent
import com.reelometro.app.detection.ExitAccess
import com.reelometro.app.detection.RecentExits
import com.reelometro.app.detection.ServiceMetrics
import com.reelometro.app.ui.today.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset

/** Las líneas RENDIMIENTO y CIERRES de la consola y la exportación (con una fuente falsa: sin servicio ni Room). */
@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationMetricsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class FakeSource(var snapshot: ServiceMetrics.Snapshot?) : CalibrationSource {
        var cleared = 0
        var recent = RecentExits(ExitAccess.READ)
        override val events: Flow<List<CalibrationEvent>> = MutableStateFlow(emptyList())
        override val videos: Flow<List<RecentVideo>> = flowOf(emptyList())
        override val connected: Flow<Boolean> = flowOf(true)
        override val lastEventAt: Flow<Long?> = flowOf(null)
        override val lastVideoAt: Flow<Long?> = flowOf(null)
        override val capturing: StateFlow<Boolean> = MutableStateFlow(false)
        override fun setCapturing(on: Boolean) = Unit
        override fun isServiceEnabled(): Boolean = true
        override fun clear() {
            cleared++
        }
        override suspend fun exportText(): String = "texto exportado"
        override fun metrics(): ServiceMetrics.Snapshot? = snapshot
        override fun exits(): RecentExits = recent
    }

    private var now = 0L
    private val metrics = ServiceMetrics(clockMs = { now })

    private fun vm(source: CalibrationSource) =
        CalibrationViewModel(source, clock = { 0L }, zone = ZoneOffset.UTC, compute = main.dispatcher)

    private fun CalibrationViewModel.current(): CalibrationState? =
        (state.value as? CalibrationUiState.Ready)?.calibration

    @Test
    fun `la consola enseña el rendimiento y lo refresca cada dos segundos`() = runTest(main.dispatcher) {
        // 61 eventos, uno por segundo: 60 s con actividad.
        repeat(61) {
            metrics.onEvent(Platform.TIKTOK, AccessibilityEvent.TYPE_VIEW_SCROLLED, 500_000)
            now += 1_000
        }
        val source = FakeSource(metrics.snapshot())
        val vm = vm(source)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        val first = vm.current()!!.performance
        assertEquals(CalibrationMetricsCopy.summary(source.snapshot!!), first)
        assertTrue(first, first!!.startsWith("61 ev/min"))

        // Una pausa de 5 min (no cuenta) y 31 eventos más, uno cada 2 s: 92 eventos en 120 s con actividad.
        now += 5 * 60_000
        repeat(31) {
            metrics.onEvent(Platform.TIKTOK, AccessibilityEvent.TYPE_VIEW_SCROLLED, 500_000)
            now += 2_000
        }
        source.snapshot = metrics.snapshot()
        advanceTimeBy(CalibrationViewModel.METRICS_MS + 1)
        runCurrent()
        val second = vm.current()!!.performance!!
        assertTrue(second, second.startsWith("46 ev/min"))
    }

    @Test
    fun `sin datos de rendimiento no hay linea`() = runTest(main.dispatcher) {
        val vm = vm(FakeSource(null))
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertNull(vm.current()!!.performance)
    }

    @Test
    fun `la consola de cierres distingue ninguno, Android sin registro y error al leer`() = runTest(main.dispatcher) {
        val cases = listOf(
            ExitAccess.READ to ExitCopy.LOG_NONE,
            ExitAccess.UNAVAILABLE to ExitCopy.LOG_UNAVAILABLE,
            ExitAccess.FAILED to ExitCopy.LOG_FAILED,
        )
        for ((access, expected) in cases) {
            val vm = vm(FakeSource(null).apply { recent = RecentExits(access) })
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertEquals(emptyList<String>(), vm.current()!!.exits) // Aún sin leer.
            vm.refresh()
            runCurrent()
            assertEquals(access.name, listOf(expected), vm.current()!!.exits)
        }
    }

    @Test
    fun `exportar y vaciar van a la fuente`() = runTest(main.dispatcher) {
        val source = FakeSource(null)
        val vm = vm(source)
        assertEquals("texto exportado", vm.exportText())
        vm.clear()
        assertEquals(1, source.cleared)
    }
}
