package com.reelometro.app.ui.passport

import com.reelometro.app.game.FakeFlapSource
import com.reelometro.app.game.FlapFakes
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSource
import com.reelometro.app.game.FlapState
import com.reelometro.app.ui.today.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * `FlapViewModel` con `FakeFlapSource` (JVM con `androidx.lifecycle`). Nombres sin tildes: `runTest` genera clases con ellos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlapViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 4)

    private fun viewModel(source: FakeFlapSource) = FlapViewModel(source, clock = { today }, dispatcher = main.dispatcher)

    @Test
    fun `empieza cargando y llega el estado de Flap`() = runTest(main.dispatcher) {
        val vm = viewModel(FakeFlapSource(FlapFakes.sample(FlapMood.ENFERMO, today = today)))
        assertTrue(vm.uiState.value is FlapUiState.Loading)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val content = vm.uiState.value as FlapUiState.Content
        assertEquals(FlapMood.ENFERMO, content.mood)
        assertTrue(content.danger)
    }

    @Test
    fun `el estado visto se marca una sola vez por cambio`() = runTest(main.dispatcher) {
        val source = FakeFlapSource(FlapFakes.sample(FlapMood.EN_FORMA, today = today))
        val vm = viewModel(source)
        vm.onMoodSeen(FlapMood.EN_FORMA)
        vm.onMoodSeen(FlapMood.EN_FORMA)
        advanceUntilIdle()
        assertEquals(listOf(FlapMood.EN_FORMA), source.seen)
        vm.onMoodSeen(FlapMood.CANSADO)
        advanceUntilIdle()
        assertEquals(listOf(FlapMood.EN_FORMA, FlapMood.CANSADO), source.seen)
    }

    @Test
    fun `al volver a la pestana el mismo estado se vuelve a marcar`() = runTest(main.dispatcher) {
        val source = FakeFlapSource(FlapFakes.sample(FlapMood.EN_FORMA, today = today))
        val vm = viewModel(source)
        vm.onMoodSeen(FlapMood.EN_FORMA)
        advanceUntilIdle()
        // Entretanto, BORRAR TODO o SUSTITUIR TODO pueden haber olvidado lo visto.
        vm.onScreenLeft()
        vm.onMoodSeen(FlapMood.EN_FORMA)
        advanceUntilIdle()
        assertEquals(listOf(FlapMood.EN_FORMA, FlapMood.EN_FORMA), source.seen)
    }

    @Test
    fun `marcarlo visto tras animarlo quita el cambio pendiente`() = runTest(main.dispatcher) {
        val source = FakeFlapSource(FlapFakes.sample(FlapMood.EN_FORMA, today = today, previousMood = FlapMood.KO))
        val vm = viewModel(source)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val before = vm.uiState.value as FlapUiState.Content
        assertEquals(FlapMood.KO, before.previousMood)
        assertTrue(before.recovered)
        // La pantalla avisa al acabar el volteo y el salto.
        vm.onMoodSeen(before.mood)
        advanceUntilIdle()
        val after = vm.uiState.value as FlapUiState.Content
        assertNull(after.previousMood)
        assertEquals(listOf(FlapMood.EN_FORMA), source.seen)
    }

    @Test
    fun `reintentar vuelve a pedir los datos`() = runTest(main.dispatcher) {
        // El primer intento falla; solo un reintento de verdad (volver a suscribirse) trae el KO.
        var calls = 0
        val ko = FlapFakes.sample(FlapMood.KO, today = today)
        val source = object : FlapSource {
            override fun observe(): Flow<FlapState> =
                if (calls++ == 0) flow<FlapState> { throw IllegalStateException("sin base de datos") } else flowOf(ko)
            override suspend fun snapshot(): FlapState = ko
            override suspend fun markMoodSeen(mood: FlapMood) {}
        }
        val vm = FlapViewModel(source, clock = { today }, dispatcher = main.dispatcher)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value is FlapUiState.Error)
        vm.retry()
        advanceUntilIdle()
        assertEquals(FlapMood.KO, (vm.uiState.value as FlapUiState.Content).mood)
        assertEquals(2, calls)
    }
}
