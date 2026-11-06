package com.reelometro.app.ui.wrapped

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.copy.ShareCopy
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.copy.WrappedCopy
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.stats.PeriodStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Fuente de periodos que se actualizan solos (en la app, `ReelRepository::observePeriod`). */
fun interface PeriodObserver {
    fun observe(ref: PeriodRef): Flow<PeriodStats>
}

/** Un periodo, una sola vez (en la app, `ReelRepository::period`). */
fun interface PeriodLoader {
    suspend fun load(ref: PeriodRef): PeriodStats
}

// ====================================================================== hub

@Immutable
sealed interface WrappedHubUiState {
    data object Loading : WrappedHubUiState

    data class Error(val message: String) : WrappedHubUiState

    /** Ningún periodo tiene vídeos. */
    data class Empty(val message: String) : WrappedHubUiState

    data class Content(val headline: String, val strips: List<HubStrip>) : WrappedHubUiState
}

/** Hub del Resumen: las cuatro tiras (mes pasado, este mes, esta semana, este año), en vivo. */
@OptIn(ExperimentalCoroutinesApi::class)
class WrappedHubViewModel(
    private val periods: PeriodObserver,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val state: StateFlow<WrappedHubUiState> = attempt
        .flatMapLatest {
            combine(HubModel.PERIODS.map(periods::observe)) { stats -> hubState(stats.toList()) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(WrappedHubUiState.Error(StateCopy.error(Variants.daySeed(today()))))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WrappedHubUiState.Loading)

    fun retry() {
        attempt.value++
    }

    internal companion object {
        fun hubState(stats: List<PeriodStats>): WrappedHubUiState {
            val today = stats.firstOrNull()?.today ?: LocalDate.now()
            if (HubModel.isEmpty(stats)) return WrappedHubUiState.Empty(WrappedCopy.hubEmpty(Variants.daySeed(today)))
            val month = HubModel.headlineMonth(stats)
            return WrappedHubUiState.Content(
                headline = WrappedCopy.hubHeadline(month, Variants.daySeed(month.atDay(1))),
                strips = HubModel.strips(stats),
            )
        }
    }
}

// ====================================================================== historia

@Immutable
sealed interface StoryUiState {
    data object Loading : StoryUiState

    data class Error(val message: String) : StoryUiState

    /**
     * Historia lista. [ticket] llega después (se pinta fuera del hilo principal); si falla,
     * [ticketFailed]. Un periodo sin vídeos trae una sola diapositiva [Slide.Empty].
     */
    data class Ready(
        val label: String,
        val slides: List<Slide>,
        val fileName: String,
        val shareText: String,
        val ticket: RenderedTicket? = null,
        val ticketFailed: Boolean = false,
    ) : StoryUiState {
        val isEmpty: Boolean get() = slides.singleOrNull() is Slide.Empty
    }
}

/**
 * Historia de un periodo (Wrapped). Lee el periodo **una vez** (la historia es una foto: no cambia
 * mientras se ve) y prepara el ticket compartible en segundo plano.
 *
 * @param ref `null` si la ruta no es válida (se muestra el error).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryViewModel(
    private val ref: PeriodRef?,
    private val loader: PeriodLoader,
    private val tickets: TicketMaker,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /** Último resultado, por si la pantalla vuelve a suscribirse (p. ej. al volver de compartir). */
    private var last: Pair<PeriodStats, StoryUiState.Ready>? = null

    val state: StateFlow<StoryUiState> = attempt
        .flatMapLatest { n -> load(showLoading = n > 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoryUiState.Loading)

    fun retry() {
        attempt.value++
    }

    private fun load(showLoading: Boolean): Flow<StoryUiState> = flow {
        if (showLoading) emit(StoryUiState.Loading)
        val seed = Variants.daySeed(LocalDate.now())
        val period = ref ?: run {
            emit(StoryUiState.Error(StateCopy.error(seed)))
            return@flow
        }
        val stats = try {
            loader.load(period)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(StoryUiState.Error(StateCopy.error(seed)))
            return@flow
        }
        last?.let { (prev, ready) ->
            if (prev == stats && ready.ticket != null) {
                emit(ready)
                return@flow
            }
        }
        val ready = ready(stats)
        emit(ready)
        if (ready.isEmpty) return@flow
        val finished = try {
            ready.copy(ticket = tickets.render(TicketModel.build(stats), ready.fileName))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ready.copy(ticketFailed = true)
        }
        last = stats to finished
        emit(finished)
    }

    /** `content://` listo para el selector de compartir, o `null` si no se pudo preparar. */
    suspend fun shareUri(): Uri? {
        val ticket = (state.value as? StoryUiState.Ready)?.ticket ?: return null
        return try {
            tickets.shareUri(ticket)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Guarda el PNG en [target] (documento elegido con SAF). */
    suspend fun save(target: Uri): Boolean {
        val ticket = (state.value as? StoryUiState.Ready)?.ticket ?: return false
        return try {
            tickets.save(ticket, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    internal companion object {
        fun ready(stats: PeriodStats): StoryUiState.Ready {
            val period = stats.ref.kind.copyPeriod()
            return StoryUiState.Ready(
                label = stats.storyLabel(),
                slides = StorySlides.build(stats),
                fileName = ShareCopy.fileName(period, stats.span.start),
                shareText = ShareCopy.shareText(period, stats.span.start, stats.totals.count.toLong(), stats.totals.totalMs),
            )
        }
    }
}
