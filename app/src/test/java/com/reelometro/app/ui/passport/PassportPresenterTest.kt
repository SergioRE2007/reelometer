package com.reelometro.app.ui.passport

import com.reelometro.app.game.FakePassportSource
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.PassportFakes
import com.reelometro.app.game.PassportState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Flujo de la pestaña con `FakePassportSource` (JVM puro, sin ViewModel ni Android). */
@OptIn(ExperimentalCoroutinesApi::class)
class PassportPresenterTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `el estado del origen llega como contenido y se actualiza`() = runTest {
        val source = FakePassportSource(PassportFakes.fresh(today))
        val out = mutableListOf<PassportUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            PassportPresenter.states(source::observe, MutableStateFlow(0), { today }).collect { out += it }
        }
        assertEquals(1, out.size)
        assertEquals("0 DE 23", (out.last() as PassportUiState.Content).stampsCount)
        source.state.value = PassportFakes.sample(today)
        assertEquals(2, out.size)
        assertEquals("9 DE 23", (out.last() as PassportUiState.Content).stampsCount)
    }

    @Test
    fun `si el origen falla sale el error y al reintentar se recupera`() = runTest {
        var calls = 0
        val attempts = MutableStateFlow(0)
        val good = PassportFakes.sample(today)
        val out = mutableListOf<PassportUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            PassportPresenter.states(
                source = { if (calls++ == 0) flow<PassportState> { throw IllegalStateException("sin base de datos") } else flowOf(good) },
                attempts = attempts,
                clock = { today },
            ).collect { out += it }
        }
        assertTrue(out.single() is PassportUiState.Error)
        attempts.value = 1
        assertEquals(3, out.size)
        assertTrue(out[1] is PassportUiState.Loading)
        assertTrue(out[2] is PassportUiState.Content)
    }

    @Test
    fun `marcar el estado visto quita el cambio pendiente`() = runTest {
        val base = PassportFakes.sample(today, mood = FlapMood.EN_FORMA)
        val source = FakePassportSource(base.copy(flap = base.flap.copy(previousMood = FlapMood.CANSADO)))
        val first = PassportPresenter.states(source::observe, MutableStateFlow(0), { today }).first() as PassportUiState.Content
        assertEquals(FlapMood.CANSADO, first.flap.previousMood)
        source.markMoodSeen(FlapMood.EN_FORMA)
        val second = PassportPresenter.states(source::observe, MutableStateFlow(0), { today }).first() as PassportUiState.Content
        assertEquals(null, second.flap.previousMood)
        assertEquals(listOf(FlapMood.EN_FORMA), source.seenMoods)
    }

    @Test
    fun `el aviso de estado visto no se repite para el mismo estado`() {
        val gate = MoodSeenGate()
        assertTrue(gate.shouldMark(FlapMood.NORMAL))
        assertFalse(gate.shouldMark(FlapMood.NORMAL))
        assertTrue(gate.shouldMark(FlapMood.EN_FORMA))
        assertTrue(gate.shouldMark(FlapMood.NORMAL))
    }
}
