package com.reelometro.app.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.Discovery
import com.reelometro.app.data.settings.DiscoveryFeature
import com.reelometro.app.data.settings.DiscoveryState
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.ProcessExits
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.detection.ServiceHealth
import com.reelometro.app.detection.ServiceJournal
import com.reelometro.app.detection.ServiceJournalRules
import com.reelometro.app.stats.AllTime
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

/** Guarda cambios en el estado del descubrimiento progresivo (lee y escribe en una sola transacción). */
fun interface DiscoveryWriter {
    suspend fun update(change: (DiscoveryState) -> DiscoveryState)

    companion object {
        val NONE = DiscoveryWriter { }
    }
}

/**
 * Hoy: combina el día en curso, el total desde el primer registro, los ajustes (divulgación
 * aceptada, apps activas) y el estado del servicio de accesibilidad.
 *
 * Las fuentes llegan como flujos y funciones (no el repositorio entero) para probarlo con datos
 * falsos. El estado del servicio no tiene flujo en el sistema: la pantalla llama a
 * [refreshService] en cada `ON_RESUME` (al volver de los ajustes de accesibilidad, por ejemplo).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val today: Flow<DayStats>,
    private val allTime: Flow<AllTime>,
    private val settings: Flow<AppSettings>,
    private val serviceEnabled: () -> Boolean,
    /** Activado pero sin funcionar ([ServiceHealth.STALLED]). */
    private val serviceStalled: () -> Boolean = { false },
    /** Por qué se cerró la app la última vez (solo se mira si está sin funcionar; [ProcessExits.stallReason]). */
    private val stallReason: () -> ExitCategory? = { null },
    /** Apagado, pero no por el usuario: lo quitó el sistema ([ServiceJournalRules.lostBySystem]). */
    private val serviceLost: () -> Boolean = { false },
    /** Cuándo volver a mirar el servicio sin esperar a `ON_RESUME` ([ServiceHealth.changes]). */
    serviceChanges: Flow<Unit> = emptyFlow(),
    /** Descubrimiento progresivo: sube las pestañas con los días y marca la novedad como vista. */
    private val discovery: DiscoveryWriter = DiscoveryWriter.NONE,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val service = MutableStateFlow(readService())
    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<TodayUiState> = attempt
        .flatMapLatest {
            combine(today, allTime, settings, service) { day, all, prefs, s ->
                maybeAdvance(prefs.discovery, all)
                TodayMapper.map(
                    day, all, prefs,
                    serviceOn = s.on, serviceStalled = s.stalled, stallReason = s.reason, serviceLost = s.lost,
                )
            }
                .onStart { emit(TodayMapper.loading(clock())) }
                .catch { emit(TodayMapper.error(clock())) }
        }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayMapper.loading(clock()))

    /** Vuelve a mirar si el servicio está activado (en cada `ON_RESUME`). */
    fun refreshService() {
        val read = readService()
        // Apagado y ya se sabía que lo quitó el sistema: el aviso no parpadea mientras se vuelve a leer.
        service.update { old -> if (!read.on && old.lost) read.copy(lost = true, reason = old.reason) else read }
        if (read.stalled || !read.on) loadDetails()
    }

    /**
     * Lo que no se lee en el hilo principal y se añade cuando llega: si el servicio apagado lo quitó el sistema (unas
     * preferencias) y el motivo del último cierre (IPC a Android), que solo se mira sin funcionar o perdido.
     */
    private fun loadDetails() {
        viewModelScope.launch {
            val now = service.value
            val (lost, reason) = withContext(dispatcher) {
                val lost = !now.on && runCatching { serviceLost() }.getOrDefault(false)
                val reason = if (now.stalled || lost) runCatching { stallReason() }.getOrNull() else null
                lost to reason
            }
            service.update {
                when {
                    it.stalled -> it.copy(reason = reason)
                    !it.on -> it.copy(lost = lost, reason = reason.takeIf { lost })
                    else -> it
                }
            }
        }
    }

    init {
        if (service.value.stalled || !service.value.on) loadDetails()
        viewModelScope.launch { serviceChanges.collect { refreshService() } }
    }

    private data class ServiceRead(val on: Boolean, val stalled: Boolean, val lost: Boolean = false, val reason: ExitCategory? = null)

    /** Activado y activado pero sin funcionar; lo demás llega después ([loadDetails]). */
    private fun readService(): ServiceRead {
        val on = serviceEnabled()
        return ServiceRead(on, on && serviceStalled())
    }

    /** Último avance pedido: las emisiones repetidas (servicio, día en curso) no repiten la escritura. */
    private val lastRequested = AtomicReference<DiscoveryState?>(null)

    /**
     * Con un día más de historial puede aparecer una pestaña: se calcula aquí (sin flujos nuevos) y se
     * guarda volviendo a aplicar la regla dentro de la transacción (por si el estado ha cambiado entretanto).
     */
    private fun maybeAdvance(state: DiscoveryState, all: AllTime) {
        val next = Discovery.advance(state, all.firstDay, all.today)
        if (next == state) return
        if (lastRequested.getAndSet(next) == next) return
        viewModelScope.launch { runCatching { discovery.update { Discovery.advance(it, all.firstDay, all.today) } } }
    }

    /** VER… en la novedad: se da por presentada (la pantalla abre la pestaña). */
    fun openNews(feature: DiscoveryFeature) = updateDiscovery { Discovery.announce(it, feature) }

    /** ENTENDIDO en la novedad. */
    fun dismissNews(feature: DiscoveryFeature) = updateDiscovery { Discovery.announce(it, feature) }

    /** ENSEÑARLO TODO YA: todas las pestañas, sin anuncios. */
    fun showAll() = updateDiscovery(Discovery::showAll)

    private fun updateDiscovery(change: (DiscoveryState) -> DiscoveryState) {
        viewModelScope.launch { runCatching { discovery.update(change) } }
    }

    /** Reintenta tras un error. */
    fun retry() {
        attempt.value++
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val app = ReelometroApp.from(appContext)
                TodayViewModel(
                    today = app.repository.observeToday(),
                    allTime = app.repository.observeAllTime(),
                    settings = app.settings.settings,
                    serviceEnabled = { ReelAccessibilityService.isEnabled(appContext) },
                    serviceStalled = { ServiceHealth.current(appContext) == ServiceHealth.STALLED },
                    stallReason = { ProcessExits.stallReason(appContext)?.category },
                    serviceLost = { ServiceJournalRules.lostBySystem(enabled = false, ServiceJournal(appContext).read()) },
                    serviceChanges = ServiceHealth.changes(),
                    discovery = DiscoveryWriter(app.settings::updateDiscovery),
                )
            }
        }
    }
}
