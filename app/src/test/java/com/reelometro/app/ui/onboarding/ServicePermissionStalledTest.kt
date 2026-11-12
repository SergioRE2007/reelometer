package com.reelometro.app.ui.onboarding

import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.ui.settings.fakeSettings
import com.reelometro.app.ui.today.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pantalla de conectar con el servicio activado pero sin funcionar (Xiaomi y compañía). */
@OptIn(ExperimentalCoroutinesApi::class)
class ServicePermissionStalledTest {

    @get:Rule
    val main = MainDispatcherRule()

    private class Probe(
        var enabled: Boolean,
        var stalled: Boolean,
        var error: String? = null,
        var exit: ExitCategory? = null,
        var lost: Boolean = false,
    ) : ServiceProbe {
        val events = MutableSharedFlow<Unit>()
        override fun isServiceEnabled() = enabled
        override fun isRestricted() = false
        override fun isServiceStalled() = stalled
        override fun startError() = error
        override fun stallReason() = exit
        override fun isServiceLost() = lost
        override fun changes(): Flow<Unit> = events
    }

    private fun ServicePermissionViewModel.ready() = (state.value as ServicePermissionUiState.Ready).permission

    @Test
    fun `activado pero sin conectar enseña los pasos y el fallo, y al conectarse pasa a OK`() = runTest(main.dispatcher) {
        val probe = Probe(enabled = true, stalled = true, error = "IllegalStateException: boom")
        val vm = ServicePermissionViewModel(fakeSettings(), probe, io = main.dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        with(vm.ready()) {
            assertTrue(serviceEnabled)
            assertTrue(serviceStalled)
            assertEquals("IllegalStateException: boom", startError)
            assertFalse(connectedHere)
        }

        probe.stalled = false
        probe.error = null
        probe.events.emit(Unit)
        advanceUntilIdle()
        with(vm.ready()) {
            assertFalse(serviceStalled)
            assertNull(startError)
            assertTrue("voltea a OK con háptico", connectedHere)
        }
    }

    @Test
    fun `sin conectar lleva el motivo del cierre y al conectarse lo quita`() = runTest(main.dispatcher) {
        val probe = Probe(enabled = true, stalled = true, exit = ExitCategory.RECENTS_CLEAN)
        val vm = ServicePermissionViewModel(fakeSettings(), probe, io = main.dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(ExitCategory.RECENTS_CLEAN, vm.ready().stallReason)

        probe.stalled = false
        probe.events.emit(Unit)
        advanceUntilIdle()
        assertNull(vm.ready().stallReason)
    }

    @Test
    fun `apagado no cuenta como sin conectar`() = runTest(main.dispatcher) {
        val vm = ServicePermissionViewModel(fakeSettings(), Probe(enabled = false, stalled = true), io = main.dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertFalse(vm.ready().serviceStalled)
    }

    @Test
    fun `apagado por el sistema lo explica con el motivo y, al volver a activarlo, deja abierta la guia`() = runTest(main.dispatcher) {
        val probe = Probe(enabled = false, stalled = false, exit = ExitCategory.USER_STOPPED, lost = true)
        val vm = ServicePermissionViewModel(fakeSettings(), probe, io = main.dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        with(vm.ready()) {
            assertFalse(serviceEnabled)
            assertTrue(serviceLost)
            assertEquals(ExitCategory.USER_STOPPED, stallReason)
            assertTrue(recoveredFromLoss)
        }

        // Lo vuelve a activar: ya no está perdido, pero la pantalla recuerda que lo estuvo.
        probe.enabled = true
        probe.events.emit(Unit)
        advanceUntilIdle()
        with(vm.ready()) {
            assertTrue(serviceEnabled)
            assertFalse(serviceLost)
            assertNull(stallReason)
            assertTrue("la guía para que no se repita sale abierta", recoveredFromLoss)
            assertTrue(connectedHere)
        }
    }

    @Test
    fun `apagado por el usuario no es una perdida ni mira el motivo del cierre`() = runTest(main.dispatcher) {
        val probe = Probe(enabled = false, stalled = false, exit = ExitCategory.RECENTS_CLEAN, lost = false)
        val vm = ServicePermissionViewModel(fakeSettings(), probe, io = main.dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        with(vm.ready()) {
            assertFalse(serviceLost)
            assertNull(stallReason)
            assertFalse(recoveredFromLoss)
        }
    }

    @Test
    fun `activado nunca cuenta como perdido aunque el diario lo diga`() = runTest(main.dispatcher) {
        val vm = ServicePermissionViewModel(fakeSettings(), Probe(enabled = true, stalled = false, lost = true), io = main.dispatcher)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertFalse(vm.ready().serviceLost)
        assertFalse(vm.ready().recoveredFromLoss)
    }
}
