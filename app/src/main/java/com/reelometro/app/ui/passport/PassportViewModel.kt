package com.reelometro.app.ui.passport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reelometro.app.ReelometroApp
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportSource
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
 * Pestaña PASAPORTE sobre [PassportSource] (en la app, `ReelometroApp.passport`; en tests,
 * `FakePassportSource`). El formato y las frases los pone [PassportPresenter] (puro).
 */
class PassportViewModel(
    private val source: PassportSource,
    private val clock: () -> LocalDate = { LocalDate.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val moodGate = MoodSeenGate()

    val uiState: StateFlow<PassportUiState> = PassportPresenter.states(source::observe, attempt, clock)
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PassportMapper.loading())

    fun retry() {
        attempt.value++
    }

    /** Flap ya ha enseñado su cambio de estado: no se vuelve a animar. */
    fun onMoodSeen(mood: FlapMood) {
        if (!moodGate.shouldMark(mood)) return
        viewModelScope.launch { runCatching { source.markMoodSeen(mood) } }
    }

    /** Datos del ticket del nivel actual (para compartirlo); `null` si no se pueden leer. */
    suspend fun levelTicket(): LevelTicketData? = runCatching { LevelTicketModel.build(source.snapshot()) }.getOrNull()

    /** El ticket de nivel se ha compartido (puede dar un sello). */
    fun onLevelShared() {
        viewModelScope.launch { runCatching { source.recordEvent(GameEvent.LEVEL_TICKET_SHARED) } }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PassportViewModel(ReelometroApp.from(context.applicationContext).passport)
            }
        }
    }
}
