package com.reelometro.app.ui.calibration

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.CalibrationCopy
import com.reelometro.app.data.Platform
import com.reelometro.app.data.RecentVideo
import com.reelometro.app.detection.CalibrationEvent
import com.reelometro.app.detection.CalibrationLog
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.detection.ServiceStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** De dónde saca la consola sus datos (interfaz para poder probar sin el servicio ni Room). */
interface CalibrationSource {
    val events: Flow<List<CalibrationEvent>>
    val videos: Flow<List<RecentVideo>>
    val connected: Flow<Boolean>
    val lastEventAt: Flow<Long?>
    val lastVideoAt: Flow<Long?>
    val capturing: StateFlow<Boolean>

    fun setCapturing(on: Boolean)

    fun isServiceEnabled(): Boolean

    fun clear()

    fun exportText(): String
}

/** [CalibrationSource] real: [CalibrationLog], [ServiceStatus] y los últimos vídeos de la base de datos. */
class LiveCalibrationSource(context: Context) : CalibrationSource {
    private val app = ReelometroApp.from(context)

    override val events: Flow<List<CalibrationEvent>> = CalibrationLog.events
    override val videos: Flow<List<RecentVideo>> = app.repository.observeRecent()
    override val connected: Flow<Boolean> = ServiceStatus.connected
    override val lastEventAt: Flow<Long?> = ServiceStatus.lastEventAt
    override val lastVideoAt: Flow<Long?> = ServiceStatus.lastVideoAt
    override val capturing: StateFlow<Boolean> = CalibrationLog.enabled

    override fun setCapturing(on: Boolean) {
        CalibrationLog.enabled.value = on
    }

    override fun isServiceEnabled(): Boolean = ReelAccessibilityService.isEnabled(app)

    override fun clear() = CalibrationLog.clear()

    override fun exportText(): String = CalibrationLog.exportText()
}

/** Estado del servicio en la cabecera de la consola. */
enum class ServiceLine { CONNECTED, WAITING, OFF }

@Immutable
data class CalibrationState(
    val service: ServiceLine,
    val capturing: Boolean,
    val lastEvent: String,
    val lastVideo: String,
    /** Eventos en el búfer (sin filtrar). */
    val eventCount: Int,
    val platform: Platform?,
    val type: TypeFilter,
    val rows: List<CalibrationRow>,
    /** Hay algo capturado (aunque los filtros lo escondan todo). */
    val hasAnything: Boolean,
    val message: String?,
)

sealed interface CalibrationUiState {
    data object Loading : CalibrationUiState

    data class Ready(val calibration: CalibrationState) : CalibrationUiState
}

/**
 * Consola de calibración. La captura se enciende al entrar ([start]) y se apaga al salir ([stop]);
 * el interruptor CAPTURAR la pausa. La lista se recalcula fuera del hilo principal y como mucho cada
 * [THROTTLE_MS] (con el feed en marcha llegan decenas de eventos por segundo).
 */
class CalibrationViewModel(
    private val source: CalibrationSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private data class Filters(val platform: Platform? = null, val type: TypeFilter = TypeFilter.ALL)

    private val filters = MutableStateFlow(Filters())
    private val sessionStart = MutableStateFlow(clock())
    private val message = MutableStateFlow<String?>(null)
    private val serviceEnabled = MutableStateFlow(false)
    private val keys = EventKeys()

    /** Lista de eventos con un respiro entre emisiones (se queda siempre con la última). */
    private val throttledEvents: Flow<List<CalibrationEvent>> = source.events
        .conflate()
        .transform {
            emit(it)
            delay(THROTTLE_MS)
        }

    private val rows: Flow<Pair<List<CalibrationEvent>, List<CalibrationRow>>> =
        combine(
            throttledEvents,
            source.videos.catch { emit(emptyList()) }.onStart { emit(emptyList()) },
            filters,
            sessionStart,
        ) { events, videos, f, start ->
            val since = minOf(start, events.lastOrNull()?.time ?: start)
            val k = keys.assign(events)
            events to CalibrationRows.build(events, k, videos, since, f.platform, f.type, zone)
        }.flowOn(compute)

    private val status = combine(
        source.connected,
        serviceEnabled,
        source.lastEventAt,
        source.lastVideoAt,
        source.capturing,
    ) { connected, enabled, lastEvent, lastVideo, capturing ->
        Status(
            service = when {
                connected -> ServiceLine.CONNECTED
                enabled -> ServiceLine.WAITING
                else -> ServiceLine.OFF
            },
            lastEvent = CalibrationRows.clock(lastEvent, zone),
            lastVideo = CalibrationRows.clock(lastVideo, zone),
            capturing = capturing,
        )
    }

    private data class Status(val service: ServiceLine, val lastEvent: String, val lastVideo: String, val capturing: Boolean)

    val state: StateFlow<CalibrationUiState> =
        combine(rows, status, filters, message) { (events, list), st, f, msg ->
            CalibrationUiState.Ready(
                CalibrationState(
                    service = st.service,
                    capturing = st.capturing,
                    lastEvent = st.lastEvent,
                    lastVideo = st.lastVideo,
                    eventCount = events.size,
                    platform = f.platform,
                    type = f.type,
                    rows = list,
                    hasAnything = events.isNotEmpty() || list.isNotEmpty(),
                    message = msg,
                ),
            ) as CalibrationUiState
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalibrationUiState.Loading)

    /** Al entrar: enciende la captura. */
    fun start() {
        source.setCapturing(true)
        refresh()
    }

    /** Al salir: la apaga (no cuesta nada mientras está apagada). */
    fun stop() {
        source.setCapturing(false)
    }

    fun refresh() {
        viewModelScope.launch { serviceEnabled.value = withContext(compute) { source.isServiceEnabled() } }
    }

    fun setCapturing(on: Boolean) {
        source.setCapturing(on)
        message.value = if (on) null else CalibrationCopy.PAUSED
    }

    fun setPlatform(platform: Platform?) = filters.update { it.copy(platform = platform) }

    fun setType(type: TypeFilter) = filters.update { it.copy(type = type) }

    /** Texto para COPIAR / COMPARTIR LOG. */
    fun exportText(): String = source.exportText()

    fun onCopied() {
        message.value = CalibrationCopy.COPIED
    }

    fun clear() {
        source.clear()
        sessionStart.value = clock()
        message.value = CalibrationCopy.CLEARED
    }

    override fun onCleared() {
        source.setCapturing(false)
    }

    companion object {
        const val THROTTLE_MS = 200L

        fun create(context: Context): CalibrationViewModel = CalibrationViewModel(LiveCalibrationSource(context))
    }
}
