package com.reelometro.app.ui.passport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reelometro.app.ReelometroApp
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Pestaña FLAP sobre [FlapSource] (en la app, `ReelometroApp.flap`; en tests, `FakeFlapSource`).
 * El formato y las frases los pone [FlapMapper] (puro).
 */
class FlapViewModel(
    private val source: FlapSource,
    private val clock: () -> LocalDate = { LocalDate.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val moodGate = MoodSeenGate()

    val uiState: StateFlow<FlapUiState> = FlapMapper.states(source::observe, attempt, clock)
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FlapMapper.loading())

    fun retry() {
        attempt.value++
    }

    /** Flap ya ha enseñado su estado (y su cambio, si lo había): no se vuelve a animar. */
    fun onMoodSeen(mood: FlapMood) {
        if (!moodGate.shouldMark(mood)) return
        viewModelScope.launch { runCatching { source.markMoodSeen(mood) } }
    }

    /**
     * La pestaña sale de la pantalla (p. ej. a Ajustes, donde BORRAR TODO o SUSTITUIR TODO olvidan lo
     * visto): al volver, el estado se vuelve a marcar aunque sea el mismo.
     */
    fun onScreenLeft() = moodGate.reset()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FlapViewModel(ReelometroApp.from(context.applicationContext).flap)
            }
        }
    }
}
