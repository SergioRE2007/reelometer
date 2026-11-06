package com.reelometro.app.ui.passport

import com.reelometro.app.game.FakePassportSource
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportFakes
import com.reelometro.app.ui.today.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * `PassportViewModel` con `FakePassportSource` (JVM con `androidx.lifecycle`). Nombres sin tildes: `runTest` genera clases con ellos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PassportViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 4)

    private fun viewModel(source: FakePassportSource) =
        PassportViewModel(source, clock = { today }, dispatcher = main.dispatcher)

    @Test
    fun `empieza cargando y llega el contenido del pasaporte`() = runTest(main.dispatcher) {
        val vm = viewModel(FakePassportSource(PassportFakes.sample(today)))
        assertTrue(vm.uiState.value is PassportUiState.Loading)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val content = vm.uiState.value as PassportUiState.Content
        assertEquals("9 DE 23", content.stampsCount)
    }

    @Test
    fun `el estado visto se marca una sola vez por cambio`() = runTest(main.dispatcher) {
        val source = FakePassportSource(PassportFakes.sample(today))
        val vm = viewModel(source)
        vm.onMoodSeen(FlapMood.EN_FORMA)
        vm.onMoodSeen(FlapMood.EN_FORMA)
        advanceUntilIdle()
        assertEquals(listOf(FlapMood.EN_FORMA), source.seenMoods)
        vm.onMoodSeen(FlapMood.CANSADO)
        advanceUntilIdle()
        assertEquals(listOf(FlapMood.EN_FORMA, FlapMood.CANSADO), source.seenMoods)
    }

    @Test
    fun `el ticket de nivel sale del estado actual y compartirlo se registra`() = runTest(main.dispatcher) {
        val source = FakePassportSource(PassportFakes.sample(today, xp = 520))
        val vm = viewModel(source)
        assertEquals("PLATA", vm.levelTicket()?.levelName)
        vm.onLevelShared()
        advanceUntilIdle()
        assertEquals(listOf(GameEvent.LEVEL_TICKET_SHARED), source.events)
    }

    @Test
    fun `reintentar vuelve a pedir los datos`() = runTest(main.dispatcher) {
        val source = FakePassportSource(PassportFakes.fresh(today))
        val vm = viewModel(source)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        source.state.value = PassportFakes.sample(today)
        vm.retry()
        advanceUntilIdle()
        assertEquals("9 DE 23", (vm.uiState.value as PassportUiState.Content).stampsCount)
    }
}
