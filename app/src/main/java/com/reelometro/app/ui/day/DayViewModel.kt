package com.reelometro.app.ui.day

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reelometro.app.ReelometroApp
import com.reelometro.app.stats.DayRank
import com.reelometro.app.stats.DayStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Detalle de un día: sus estadísticas y, aparte, su puesto entre todos los días contados
 * ([rank], `ReelRepository::observeDayRank`: SQL, sin cargar el historial). El puesto no retiene
 * la pantalla: si tarda o falla, el día se ve sin puesto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val epochDay: Long,
    private val day: Flow<DayStats>,
    private val rank: Flow<DayRank>,
    private val clock: () -> LocalDate = { LocalDate.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<DayUiState> = attempt
        .flatMapLatest {
            val ranking = rank
                .map<DayRank, DayRank?> { it }
                .onStart { emit(null) }
                .catch { emit(null) }
            combine(day, ranking) { d, r -> DayMapper.map(epochDay, clock(), d, r) }
                .onStart { emit(DayMapper.loading(epochDay, clock())) }
                .catch { emit(DayMapper.error(epochDay, clock())) }
        }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayMapper.loading(epochDay, clock()))

    fun retry() {
        attempt.value++
    }

    companion object {
        fun factory(context: Context, epochDay: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = ReelometroApp.from(context.applicationContext).repository
                val date = LocalDate.ofEpochDay(epochDay)
                DayViewModel(
                    epochDay = epochDay,
                    day = repository.observeDay(date),
                    rank = repository.observeDayRank(date),
                )
            }
        }
    }
}
