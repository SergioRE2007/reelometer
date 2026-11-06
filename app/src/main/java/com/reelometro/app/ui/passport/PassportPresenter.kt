package com.reelometro.app.ui.passport

import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.PassportState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.LocalDate

/**
 * Flujo de estados de la pestaña PASAPORTE (lógica PURA, sin Android: la prueba jvm-check).
 * `PassportViewModel` solo le pone el `stateIn`.
 */
object PassportPresenter {

    /**
     * Cada valor de [attempts] (0, 1, 2… al pulsar REINTENTAR) vuelve a suscribirse a [source].
     * Un fallo del origen (o del formato) da [PassportUiState.Error]; al reintentar se pasa por
     * [PassportUiState.Loading] hasta el siguiente dato.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun states(
        source: () -> Flow<PassportState>,
        attempts: Flow<Int>,
        clock: () -> LocalDate,
    ): Flow<PassportUiState> = attempts.flatMapLatest { attempt ->
        source()
            .map<PassportState, PassportUiState> { PassportMapper.map(it) }
            .onStart { if (attempt > 0) emit(PassportUiState.Loading) }
            .catch { emit(PassportMapper.error(clock())) }
    }
}

/**
 * Recuerda qué cambio de estado de Flap ya se ha marcado como visto, para no repetir la llamada
 * a `PassportSource.markMoodSeen` si la pantalla se recompone o vuelve antes de que llegue el
 * estado nuevo.
 */
class MoodSeenGate {
    private var last: FlapMood? = null

    /** `true` si hay que avisar de que se ha visto [mood]. */
    fun shouldMark(mood: FlapMood): Boolean {
        if (last == mood) return false
        last = mood
        return true
    }
}
