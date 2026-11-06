package com.reelometro.app.ui.stats

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reelometro.app.ReelometroApp
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.sign

/**
 * Estadísticas: el periodo elegido (pestaña + desplazamiento) vive en [SavedStateHandle], así
 * sobrevive a la recreación y a la muerte del proceso. [period] es `ReelRepository::observePeriod`
 * (o una fuente falsa en los tests).
 *
 * En SEMANA se pide también la semana anterior a la mostrada con [previousWeek] (en la app, la misma
 * `observePeriod`) para la comparativa «contra la semana anterior»; si falla o no hay fuente, la
 * pantalla sale igual, sin ese bloque.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val savedState: SavedStateHandle,
    private val period: (PeriodRef) -> Flow<PeriodStats>,
    private val clock: () -> LocalDate = { LocalDate.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val previousWeek: ((PeriodRef) -> Flow<PeriodStats>)? = null,
) : ViewModel() {

    private val selection = MutableStateFlow(savedState.get<String>(KEY_PERIOD)?.let(PeriodRef::parse) ?: PeriodRef.THIS_WEEK)
    private val direction = MutableStateFlow(0)
    private val attempt = MutableStateFlow(0)

    /** Primer día con datos (del último resultado): limita la navegación hacia atrás. */
    @Volatile
    private var firstDay: LocalDate? = null

    private val results: Flow<PeriodResult?> = combine(selection, attempt) { ref, _ -> ref }
        .flatMapLatest { ref ->
            combine(period(ref), previousOf(ref)) { stats, previous -> PeriodResult.Data(ref, stats, previous) }
                .map<PeriodResult.Data, PeriodResult?> { it }
                .catch { emit(PeriodResult.Failed(ref)) }
        }
        .onEach { if (it is PeriodResult.Data) firstDay = it.stats.firstDay }
        .onStart { emit(null) }

    val uiState: StateFlow<StatsUiState> = combine(selection, direction, results) { ref, dir, result ->
        StatsMapper.map(ref, dir, result, clock())
    }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsMapper.loading(selection.value, clock()))

    /** Pestaña SEMANA/MES/AÑO/SIEMPRE: su periodo actual. Tocar la activa vuelve al actual. */
    fun selectTab(index: Int) {
        val kind = StatsMapper.KINDS.getOrNull(index) ?: return
        val current = selection.value
        if (kind == current.kind && current.offset == 0) return
        val dir = if (kind == current.kind) 1 else (index - StatsMapper.KINDS.indexOf(current.kind)).sign
        select(PeriodRef(kind), dir)
    }

    fun previous() {
        val current = selection.value
        if (!current.hasPrevious(clock(), firstDay)) return
        current.previous()?.let { select(it, -1) }
    }

    fun next() {
        selection.value.next()?.let { select(it, 1) }
    }

    fun retry() {
        attempt.value++
    }

    /** La semana anterior a [ref] (solo en SEMANA); un fallo deja la comparativa fuera, no la pantalla. */
    private fun previousOf(ref: PeriodRef): Flow<PeriodStats?> {
        val source = previousWeek
        val previous = ref.previous()
        if (source == null || ref.kind != PeriodKind.WEEK || previous == null) return flowOf(null)
        return source(previous)
            .map<PeriodStats, PeriodStats?> { it }
            .catch { emit(null) }
    }

    private fun select(ref: PeriodRef, dir: Int) {
        direction.value = dir
        selection.value = ref
        savedState[KEY_PERIOD] = ref.toRouteArg()
    }

    companion object {
        internal const val KEY_PERIOD = "period"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = ReelometroApp.from(context.applicationContext).repository
                StatsViewModel(
                    createSavedStateHandle(),
                    period = repository::observePeriod,
                    previousWeek = repository::observePeriod,
                )
            }
        }
    }
}
