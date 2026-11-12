package com.reelometro.app.ui.onboarding

import com.reelometro.app.data.Platform
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.detection.CheckFailure
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.ServiceCheckRules
import com.reelometro.app.ui.settings.fakeSettings
import com.reelometro.app.ui.today.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Prueba de un minuto tras conectar el servicio (fuentes falsas: sin Android ni Room). */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceCheckViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class FakeProbe(
        var enabled: Boolean = true,
        var connected: Boolean = true,
        var events: Long = 100,
        var pid: Int = 42,
        var apps: List<Platform> = Platform.entries,
    ) : ServiceCheckProbe {
        val videos = MutableStateFlow(7L)
        override fun isEnabled() = enabled
        override fun isConnected() = connected
        override fun eventCount() = events
        override fun videoCount(): StateFlow<Long> = videos
        override fun pid() = pid
        override fun installedApps() = apps

        var exit: ExitCategory? = null
        var exitAskedSince: Long? = -1
        override fun exitReason(sinceMs: Long?): ExitCategory? {
            exitAskedSince = sinceMs
            return exit
        }
    }

    private class MemoryStore : ServiceCheckStore {
        var saved: StoredCheck? = null
        override fun load() = saved
        override fun save(check: StoredCheck) { saved = check }
        override fun clear() { saved = null }
    }

    private var clock = 1_000_000L
    private val store = MemoryStore()
    private val probe = FakeProbe()

    private fun TestScope.vm(settings: SettingsRepository = fakeSettings()): ServiceCheckViewModel {
        val vm = ServiceCheckViewModel(settings, probe, store, now = { clock }, io = main.dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        return vm
    }

    /** Empieza, se va a la app, pasa [videos] vídeos con [events] eventos y vuelve. */
    private fun TestScope.roundTrip(vm: ServiceCheckViewModel, videos: Long, events: Long) {
        vm.start()
        vm.onLeft()
        probe.videos.value += videos
        probe.events += events
        clock += 60_000
        vm.onReturned()
        advanceUntilIdle()
    }

    @Test
    fun `tres vídeos al volver es prueba superada`() = runTest(main.dispatcher) {
        val vm = vm()
        assertEquals(ServiceCheckUiState.Idle(Platform.entries), vm.state.value)
        vm.start()
        vm.onLeft()
        probe.videos.value += 2
        advanceUntilIdle()
        assertEquals("en vivo: 2 de 3", ServiceCheckUiState.Running(2, ServiceCheckRules.TARGET_VIDEOS, Platform.entries), vm.state.value)
        probe.videos.value += 1
        probe.events += 300
        vm.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Passed(3, restarted = false), vm.state.value)
        assertNull("con veredicto se borra lo guardado", store.saved)
    }

    @Test
    fun `conectado y sin eventos`() = runTest(main.dispatcher) {
        val vm = vm()
        roundTrip(vm, videos = 0, events = 0)
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.NO_EVENTS, Platform.entries), vm.state.value)
    }

    @Test
    fun `eventos sin vídeos, y con todas las apps apagadas`() = runTest(main.dispatcher) {
        val vm = vm()
        roundTrip(vm, videos = 0, events = 50)
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.NO_VIDEOS, Platform.entries), vm.state.value)

        val settings = fakeSettings()
        Platform.entries.forEach { settings.setPlatformEnabled(it, false) }
        val off = vm(settings)
        roundTrip(off, videos = 0, events = 50)
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.PLATFORMS_OFF, Platform.entries), off.state.value)
    }

    @Test
    fun `apagado en Ajustes`() = runTest(main.dispatcher) {
        val vm = vm()
        probe.enabled = false
        roundTrip(vm, videos = 0, events = 0)
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.OFF, Platform.entries), vm.state.value)
    }

    @Test
    fun `si Android cierra la app durante la prueba, el ViewModel nuevo lo diagnostica`() = runTest(main.dispatcher) {
        vm().start()
        assertNotNull(store.saved)

        // Proceso nuevo: otro pid, contadores a cero, sin conectar todavía.
        probe.pid = 99
        probe.videos.value = 0
        probe.events = 0
        probe.connected = false
        clock += 5 * 60_000
        val restored = vm()
        restored.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.KILLED, Platform.entries), restored.state.value)
    }

    @Test
    fun `cerrada al limpiar recientes durante la prueba lo dice`() = runTest(main.dispatcher) {
        vm().start()
        val startedAt = store.saved!!.startedAt
        probe.pid = 99
        probe.videos.value = 0
        probe.events = 0
        probe.connected = false
        probe.exit = ExitCategory.RECENTS_CLEAN
        clock += 60_000
        val restored = vm()
        restored.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.KILLED, Platform.entries, ExitCategory.RECENTS_CLEAN), restored.state.value)
        assertEquals("pide los cierres desde que empezó la prueba", startedAt, probe.exitAskedSince)
    }

    @Test
    fun `cerrada pero con vídeos en el proceso nuevo, superada avisando del reinicio`() = runTest(main.dispatcher) {
        vm().start()
        probe.pid = 99
        probe.videos.value = 2
        probe.events = 80
        clock += 2 * 60_000
        val restored = vm()
        assertEquals("se recupera en marcha", ServiceCheckUiState.Running(2, ServiceCheckRules.TARGET_VIDEOS, Platform.entries), restored.state.value)
        restored.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Passed(2, restarted = true), restored.state.value)
    }

    @Test
    fun `una prueba guardada de hace más de media hora se descarta`() = runTest(main.dispatcher) {
        vm().start()
        clock += 31 * 60_000
        val later = vm()
        assertEquals(ServiceCheckUiState.Idle(Platform.entries), later.state.value)
        assertNull(store.saved)
    }

    @Test
    fun `cancelar borra lo guardado`() = runTest(main.dispatcher) {
        val vm = vm()
        vm.start()
        vm.cancel()
        advanceUntilIdle()
        assertNull(store.saved)
        assertEquals(ServiceCheckUiState.Idle(Platform.entries), vm.state.value)
    }

    @Test
    fun `volver sin haber salido no evalúa, pero COMPROBAR AHORA sí`() = runTest(main.dispatcher) {
        val vm = vm()
        vm.start()
        vm.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Running(0, ServiceCheckRules.TARGET_VIDEOS, Platform.entries), vm.state.value)
        probe.videos.value += 1
        vm.verify()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Passed(1, restarted = false), vm.state.value)
    }

    @Test
    fun `repetir vuelve al principio`() = runTest(main.dispatcher) {
        val vm = vm()
        roundTrip(vm, videos = 0, events = 0)
        vm.retry()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Idle(Platform.entries), vm.state.value)
    }

    @Test
    fun `sin apps de vídeo instaladas`() = runTest(main.dispatcher) {
        probe.apps = emptyList()
        assertEquals(ServiceCheckUiState.Idle(emptyList()), vm().state.value)
    }

    @Test
    fun `con la app elegida desactivada es PLATFORMS_OFF aunque otra esté activa`() = runTest(main.dispatcher) {
        val settings = fakeSettings()
        settings.setPlatformEnabled(Platform.TIKTOK, false)
        val vm = vm(settings)
        vm.start(Platform.TIKTOK)
        vm.onLeft()
        probe.events += 50
        vm.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.PLATFORMS_OFF, Platform.entries), vm.state.value)
    }

    @Test
    fun `un ON_STOP que no viene de abrir la app de vídeos no cuenta`() = runTest(main.dispatcher) {
        // Prueba recuperada del mismo proceso y sin eventos: el usuario no viene de TikTok.
        store.saved = StoredCheck(clock - 1_000, probe.pid, probe.events, probe.videos.value, Platform.TIKTOK)
        val vm = vm()
        vm.onLeft() // abrir Accesibilidad, apagar la pantalla, pulsar un botón de la guía…
        vm.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Running(0, ServiceCheckRules.TARGET_VIDEOS, Platform.entries), vm.state.value)
    }

    @Test
    fun `una prueba guardada del mismo proceso sin eventos no se evalúa sola al entrar`() = runTest(main.dispatcher) {
        store.saved = StoredCheck(clock - 1_000, probe.pid, probe.events, probe.videos.value, Platform.TIKTOK)
        val vm = vm()
        vm.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Running(0, ServiceCheckRules.TARGET_VIDEOS, Platform.entries), vm.state.value)
        probe.events += 20
        val back = vm()
        back.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.NO_VIDEOS, Platform.entries), back.state.value)
    }

    @Test
    fun `el veredicto de apagado se descarta cuando el servicio vuelve a funcionar`() = runTest(main.dispatcher) {
        val vm = vm()
        probe.enabled = false
        roundTrip(vm, videos = 0, events = 0)
        assertEquals(ServiceCheckUiState.Failed(CheckFailure.OFF, Platform.entries), vm.state.value)
        probe.enabled = true
        vm.onReturned()
        advanceUntilIdle()
        assertEquals(ServiceCheckUiState.Idle(Platform.entries), vm.state.value)
    }

    @Test
    fun `con un fallo de la propia app no se muestra la guia de bateria`() {
        assertFalse(showsFailureFix(com.reelometro.app.detection.ExitCategory.APP_CRASH))
        assertTrue(showsFailureFix(com.reelometro.app.detection.ExitCategory.RECENTS_CLEAN))
        assertTrue(showsFailureFix(null))
    }
}
