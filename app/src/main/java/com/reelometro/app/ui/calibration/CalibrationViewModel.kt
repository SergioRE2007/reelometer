package com.reelometro.app.ui.calibration

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.AppTexts
import com.reelometro.app.copy.CalibrationCopy
import com.reelometro.app.copy.ExitCopy
import com.reelometro.app.copy.ResourceTexts
import com.reelometro.app.copy.SystemStateCopy
import com.reelometro.app.copy.CalibrationMetricsCopy
import com.reelometro.app.data.Platform
import com.reelometro.app.data.RecentVideo
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.detection.CalibrationEvent
import com.reelometro.app.detection.CalibrationLog
import com.reelometro.app.detection.ExitAccess
import com.reelometro.app.detection.ProcessExits
import com.reelometro.app.detection.RecentExits
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.detection.ServiceFailures
import com.reelometro.app.detection.ServiceHealth
import com.reelometro.app.detection.ServiceJournal
import com.reelometro.app.detection.ServiceMetrics
import com.reelometro.app.detection.ServiceStatus
import com.reelometro.app.detection.SystemState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

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

    /** Vacía el registro y pone a cero el rendimiento: el tester vacía, prueba un rato y comparte. */
    fun clear()

    /** Texto para Copiar y Compartir (sin nombres de contactos ni grupos). Puede leer la base de datos: suspende. */
    suspend fun exportText(): String

    /** Rendimiento del servicio en este momento; null si no hay (tests). */
    fun metrics(): ServiceMetrics.Snapshot? = null

    /**
     * Últimos cierres de la app que guarda Android ([ProcessExits.recent]), del más reciente al más antiguo, y si se han
     * podido leer (Android antiguo o un error no son «ninguno»). Por defecto (tests), leídos y ninguno.
     */
    fun exits(): RecentExits = RecentExits(ExitAccess.READ)
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

    override fun clear() {
        CalibrationLog.clear()
        ServiceMetrics.live.reset()
        ServiceFailures.live.reset()
    }

    override suspend fun exportText(): String = withContext(Dispatchers.IO) {
        val names = privateNames()
        val snapshot = metrics()
        // Se lee antes de cambiar de idioma: consulta la base de datos (suspende).
        val system = runCatching { systemInput() }.getOrNull()
        // Lo que se comparte va a desarrollo y el resto de la cabecera es español: las líneas de rendimiento (y el
        // token de los nombres) también, sea cual sea el idioma del móvil del tester. La pantalla sigue su idioma.
        val spanish = runCatching { ResourceTexts(app, EXPORT_LOCALE) }.getOrNull()
        fun <T> inSpanish(block: () -> T): T = if (spanish != null) AppTexts.using(spanish, block) else block()
        inSpanish {
            CalibrationLog.exportText(
                fallbackEnvironment = runCatching { ReelAccessibilityService.describeEnvironment(app) }.getOrNull(),
                service = runCatching { ServiceHealth.current(app) }.getOrNull(),
                performance = runCatching { CalibrationMetricsCopy.exportLines(snapshot) }.getOrDefault(emptyList()),
                exits = runCatching {
                    val recent = exits()
                    val lines = ExitCopy.logLines(recent, ZoneId.systemDefault())
                    // El título dice que Android los guarda: solo va encima de los cierres que se han leído.
                    if (recent.access == ExitAccess.READ) listOf(ExitCopy.LOG_TITLE) + lines else lines
                }.getOrDefault(emptyList()),
                system = runCatching { system?.let { SystemStateCopy.lines(it, ZoneId.systemDefault()) } }.getOrNull().orEmpty(),
                privateNames = names,
                redactToken = CalibrationMetricsCopy::redacted,
            )
        }
    }

    /**
     * Lo que explica que el servicio deje de contar tras un rato sin usar el móvil: lo que Android dice de la app, el
     * diario del servicio, el último vídeo guardado y los fallos contenidos. Cada parte, si falla, sale vacía.
     */
    private suspend fun systemInput(): SystemStateCopy.Input = SystemStateCopy.Input(
        system = runCatching { SystemState.read(app) }.getOrDefault(SystemState()),
        journal = ServiceJournal(app).read(),
        failures = ServiceFailures.live.snapshot(),
        memory = ServiceStatus.memoryWarnings.value,
        lastVideoAt = runCatching { app.repository.observeRecent(1).first().firstOrNull()?.startedAt }.getOrNull(),
    )

    override fun metrics(): ServiceMetrics.Snapshot = ServiceMetrics.live.snapshot()

    override fun exits(): RecentExits = ProcessExits.recent(app)

    /**
     * Nombres de personas y grupos que la app conoce (los fijados en el envío rápido y a quién se ha enviado): se quitan
     * del texto que sale del móvil. Si algo falla, se exporta igual (hoy el registro no los lleva: es una red).
     */
    private suspend fun privateNames(): List<String> {
        val pinned = runCatching { app.settings.current().allQuickSendTargets().values.flatten() }.getOrDefault(emptyList())
        val sent = AppSettings.QUICK_SEND_PLATFORMS.flatMap { p ->
            runCatching { app.repository.observeTopRecipients(p, limit = PRIVATE_NAMES_LIMIT).first() }.getOrDefault(emptyList())
        }
        return pinned + sent
    }

    private companion object {
        const val PRIVATE_NAMES_LIMIT = 100

        /** Idioma del registro que se comparte (el de `values/`). */
        val EXPORT_LOCALE: Locale = Locale.forLanguageTag("es-ES")
    }
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
    /** Rendimiento del servicio en una línea ([CalibrationMetricsCopy.summary]); null si no hay datos. */
    val performance: String? = null,
    /**
     * Últimos cierres de la app, una línea cada uno, o una que dice por qué no hay ([ExitCopy.logLines]); vacía mientras
     * no se han leído.
     */
    val exits: List<String> = emptyList(),
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

    /** Cierres de la app en texto: se leen al entrar y en cada `refresh` (no cambian mientras la app está abierta). */
    private val exitLines = MutableStateFlow<List<String>>(emptyList())
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

    /** Rendimiento del servicio, cada [METRICS_MS] y solo con la pantalla abierta (son unos contadores en memoria). */
    private val performance: Flow<String?> = flow {
        while (true) {
            emit(runCatching { source.metrics()?.let(CalibrationMetricsCopy::summary) }.getOrNull())
            delay(METRICS_MS)
        }
    }.flowOn(compute)

    val state: StateFlow<CalibrationUiState> =
        combine(rows, status, filters, message, combine(performance, exitLines, ::Pair)) { (events, list), st, f, msg, (perf, exits) ->
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
                    performance = perf,
                    exits = exits,
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
        viewModelScope.launch {
            exitLines.value = withContext(compute) {
                runCatching { ExitCopy.logLines(source.exits(), zone) }.getOrDefault(emptyList())
            }
        }
    }

    fun setCapturing(on: Boolean) {
        source.setCapturing(on)
        message.value = if (on) null else CalibrationCopy.PAUSED
    }

    fun setPlatform(platform: Platform?) = filters.update { it.copy(platform = platform) }

    fun setType(type: TypeFilter) = filters.update { it.copy(type = type) }

    /** Texto para COPIAR / COMPARTIR LOG (sin nombres de contactos ni grupos). */
    suspend fun exportText(): String = source.exportText()

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
        const val METRICS_MS = 2_000L

        fun create(context: Context): CalibrationViewModel = CalibrationViewModel(LiveCalibrationSource(context))
    }
}
