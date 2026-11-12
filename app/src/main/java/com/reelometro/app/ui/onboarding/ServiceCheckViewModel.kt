package com.reelometro.app.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.ReelometroApp
import com.reelometro.app.data.Platform
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.detection.CheckFailure
import com.reelometro.app.detection.CheckInput
import com.reelometro.app.detection.CheckVerdict
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.ExitReasons
import com.reelometro.app.detection.ProcessExits
import com.reelometro.app.detection.ReelAccessibilityService
import com.reelometro.app.detection.ServiceCheckRules
import com.reelometro.app.detection.ServiceStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que mira la prueba de un minuto (interfaz para probar el ViewModel sin Android). */
interface ServiceCheckProbe {
    /** Activado en Ajustes › Accesibilidad. */
    fun isEnabled(): Boolean

    /** El sistema ha conectado el servicio en este proceso. */
    fun isConnected(): Boolean

    /** Eventos de las apps vigiladas desde que arrancó el proceso ([ServiceStatus.eventCount]). */
    fun eventCount(): Long

    /** Vídeos contados desde que arrancó el proceso ([ServiceStatus.videoCount]). */
    fun videoCount(): StateFlow<Long>

    /** Identificador del proceso: si cambia, Android ha cerrado la app durante la prueba. */
    fun pid(): Int

    /** Apps vigiladas instaladas en el móvil (para los botones ABRIR TIKTOK…). */
    fun installedApps(): List<Platform>

    /**
     * Por qué se cerró la app ([ProcessExits]): con [sinceMs], el cierre relevante posterior a esa hora (el de durante
     * la prueba); sin él, el último que no se había visto. null si Android no guarda nada.
     */
    fun exitReason(sinceMs: Long?): ExitCategory? = null

    /** La prueba ha pasado: los cierres de hasta ahora ya no explican un fallo posterior ([ProcessExits.markSeen]). */
    fun markExitsSeen() {}
}

/** Prueba en marcha, guardada para sobrevivir a que Android cierre la app mientras se usa TikTok. */
data class StoredCheck(
    val startedAt: Long,
    val pid: Int,
    val baseEvents: Long,
    val baseVideos: Long,
    /** App de vídeos elegida (null en pruebas guardadas por versiones anteriores). */
    val platform: Platform? = null,
)

interface ServiceCheckStore {
    fun load(): StoredCheck?

    fun save(check: StoredCheck)

    fun clear()
}

@Immutable
sealed interface ServiceCheckUiState {
    /** Sin empezar. [apps] vacía: no hay ninguna app vigilada instalada. */
    data class Idle(val apps: List<Platform>) : ServiceCheckUiState

    data class Running(val videos: Long, val target: Int, val apps: List<Platform>) : ServiceCheckUiState

    data class Passed(val videos: Long, val restarted: Boolean) : ServiceCheckUiState

    /** [exit]: por qué cerró Android la app, si lo guarda (solo con [CheckFailure.KILLED] y [CheckFailure.NOT_CONNECTED]). */
    data class Failed(val reason: CheckFailure, val apps: List<Platform>, val exit: ExitCategory? = null) : ServiceCheckUiState
}

/**
 * Prueba de un minuto tras conectar el servicio (pantalla de conectar y paso 3 del onboarding): ABRIR TIKTOK guarda
 * una base de los contadores de [ServiceStatus] ([start]) y, al volver a la app ([onReturned], en `ON_RESUME` después
 * de un `ON_STOP`), da un veredicto con [ServiceCheckRules]. Nunca bloquea: el pie de la pantalla sigue ahí.
 *
 * La prueba en marcha se guarda en [ServiceCheckStore]: si Android cierra la app mientras se pasan vídeos, el
 * ViewModel nuevo la recupera y, como el proceso ya no es el mismo, lo diagnostica ([CheckFailure.KILLED]).
 */
class ServiceCheckViewModel(
    private val settings: SettingsRepository,
    private val probe: ServiceCheckProbe,
    private val store: ServiceCheckStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private sealed interface Phase {
        data object Idle : Phase

        /** [restarted]: la prueba la empezó otro proceso (las bases no valen). [left]: se ha salido de la app. */
        data class Running(val check: StoredCheck, val restarted: Boolean, val left: Boolean) : Phase

        data class Done(val verdict: CheckVerdict, val exit: ExitCategory? = null) : Phase
    }

    private val phase = MutableStateFlow<Phase>(Phase.Idle)
    private val apps = MutableStateFlow<List<Platform>>(emptyList())

    /** [start] acaba de pedir abrir la app de vídeos: el primer `ON_STOP` es esa salida y solo ese. */
    private var launching = false

    val state: StateFlow<ServiceCheckUiState> =
        combine(phase, apps, probe.videoCount()) { p, a, videos ->
            when (p) {
                Phase.Idle -> ServiceCheckUiState.Idle(a)
                is Phase.Running -> ServiceCheckUiState.Running(
                    videos = (videos - p.baseVideos()).coerceAtLeast(0),
                    target = ServiceCheckRules.TARGET_VIDEOS,
                    apps = a,
                )
                is Phase.Done -> when (val v = p.verdict) {
                    is CheckVerdict.Passed -> ServiceCheckUiState.Passed(v.videos, v.restarted)
                    is CheckVerdict.Failed -> ServiceCheckUiState.Failed(v.reason, a, p.exit)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceCheckUiState.Idle(emptyList()))

    init {
        restore()
        viewModelScope.launch { apps.value = withContext(io) { probe.installedApps() } }
    }

    /** ABRIR TIKTOK…: guarda la base y pasa a «en marcha». Quien llama abre la app. */
    fun start(platform: Platform? = null) {
        val check = StoredCheck(
            startedAt = now(),
            pid = probe.pid(),
            baseEvents = probe.eventCount(),
            baseVideos = probe.videoCount().value,
            platform = platform,
        )
        store.save(check)
        launching = true
        phase.value = Phase.Running(check, restarted = false, left = false)
    }

    /**
     * `ON_STOP`: se ha ido a la app de vídeos, pero solo cuenta la salida que provoca [start]. Apagar la pantalla, abrir
     * Accesibilidad o pulsar un botón de la guía por marca no son «volver de TikTok».
     */
    fun onLeft() {
        if (!launching) return
        launching = false
        phase.update { p -> if (p is Phase.Running) p.copy(left = true) else p }
    }

    /** `ON_RESUME`: al volver de la app de vídeos (o con una prueba recuperada), da el veredicto. */
    fun onReturned() {
        val p = phase.value
        if (p is Phase.Running && p.left) {
            evaluate(p)
        } else if (p is Phase.Done) {
            // Un veredicto de «apagado» o «sin conectar» deja de valer en cuanto el servicio vuelve a funcionar.
            val reason = (p.verdict as? CheckVerdict.Failed)?.reason
            if ((reason == CheckFailure.OFF || reason == CheckFailure.NOT_CONNECTED) && probe.isEnabled() && probe.isConnected()) {
                phase.value = Phase.Idle
            }
        }
    }

    /** COMPROBAR AHORA: veredicto ya, sin esperar a volver. */
    fun verify() {
        (phase.value as? Phase.Running)?.let(::evaluate)
    }

    /** REPETIR LA PRUEBA: vuelve al principio (los botones ABRIR TIKTOK…). */
    fun retry() {
        launching = false
        store.clear()
        phase.value = Phase.Idle
    }

    /** CANCELAR LA PRUEBA, o no se ha podido abrir la app. */
    fun cancel() {
        launching = false
        store.clear()
        phase.value = Phase.Idle
    }

    private fun restore() {
        val saved = store.load() ?: return
        val age = now() - saved.startedAt
        if (age < 0 || age >= ServiceCheckRules.MAX_AGE_MS) {
            store.clear()
            return
        }
        // Si el proceso es otro, Android cerró la app durante la prueba: se diagnostica al volver. Si es el mismo, solo
        // se da por «salido» si ya hubo eventos (venimos de la app de vídeos); si no, queda COMPROBAR AHORA.
        val restarted = saved.pid != probe.pid()
        val left = restarted || probe.eventCount() > saved.baseEvents
        phase.value = Phase.Running(saved, restarted = restarted, left = left)
    }

    private fun evaluate(running: Phase.Running) {
        viewModelScope.launch {
            val input = withContext(io) {
                val baseEvents = if (running.restarted) 0L else running.check.baseEvents
                CheckInput(
                    enabled = probe.isEnabled(),
                    connected = probe.isConnected(),
                    processRestarted = running.restarted,
                    eventsSince = (probe.eventCount() - baseEvents).coerceAtLeast(0),
                    videosSince = (probe.videoCount().value - running.baseVideos()).coerceAtLeast(0),
                    anyPlatformEnabled = settings.settings.first().enabledPlatforms.let { enabled ->
                        // La app que se ha abierto, no «alguna»: con TikTok apagado e Instagram activo no llega nada.
                        running.check.platform?.let { it in enabled } ?: enabled.isNotEmpty()
                    },
                )
            }
            // Si mientras tanto se ha cancelado o repetido, no se pisa.
            if (phase.value != running) return@launch
            val verdict = ServiceCheckRules.verdict(input)
            // Cerrada durante la prueba o sin conectar: el motivo que guarda Android, si lo hay (limpiar recientes…).
            val exit = when ((verdict as? CheckVerdict.Failed)?.reason) {
                CheckFailure.KILLED -> withContext(io) { runCatching { probe.exitReason(running.check.startedAt) }.getOrNull() }
                CheckFailure.NOT_CONNECTED -> withContext(io) { runCatching { probe.exitReason(null) }.getOrNull() }
                else -> null
            }
            if (phase.value != running) return@launch
            store.clear()
            if (verdict is CheckVerdict.Passed) withContext(io) { runCatching { probe.markExitsSeen() } }
            phase.value = Phase.Done(verdict, exit)
        }
    }

    private fun Phase.Running.baseVideos(): Long = if (restarted) 0L else check.baseVideos

    companion object {
        fun create(context: Context): ServiceCheckViewModel {
            val app = ReelometroApp.from(context)
            return ServiceCheckViewModel(app.settings, AndroidServiceCheckProbe(app), PrefsServiceCheckStore(app))
        }
    }
}

/** [ServiceCheckProbe] real: [ServiceStatus] en memoria y el gestor de paquetes. */
class AndroidServiceCheckProbe(context: Context) : ServiceCheckProbe {
    private val app = context.applicationContext

    override fun isEnabled(): Boolean = ReelAccessibilityService.isEnabled(app)

    override fun isConnected(): Boolean = ServiceStatus.connected.value

    override fun eventCount(): Long = ServiceStatus.eventCount()

    override fun videoCount(): StateFlow<Long> = ServiceStatus.videoCount

    override fun pid(): Int = Process.myPid()

    override fun exitReason(sinceMs: Long?): ExitCategory? =
        if (sinceMs != null) {
            runCatching { ExitReasons.relevant(ProcessExits.read(app), sinceMs)?.category }.getOrNull()
        } else {
            ProcessExits.stallReason(app)?.category
        }

    override fun markExitsSeen() = ProcessExits.markSeen(app)

    override fun installedApps(): List<Platform> = Platform.entries.filter { platform ->
        platform.packages.any { pkg ->
            try {
                app.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}

/** [ServiceCheckStore] en preferencias propias («service_check»), aparte de los ajustes. */
class PrefsServiceCheckStore(context: Context) : ServiceCheckStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): StoredCheck? {
        if (!prefs.contains(KEY_STARTED)) return null
        return StoredCheck(
            startedAt = prefs.getLong(KEY_STARTED, 0),
            pid = prefs.getInt(KEY_PID, 0),
            baseEvents = prefs.getLong(KEY_EVENTS, 0),
            baseVideos = prefs.getLong(KEY_VIDEOS, 0),
            platform = prefs.getString(KEY_PLATFORM, null)?.let { name -> Platform.entries.firstOrNull { it.name == name } },
        )
    }

    override fun save(check: StoredCheck) = prefs.edit {
        putLong(KEY_STARTED, check.startedAt)
        putInt(KEY_PID, check.pid)
        putLong(KEY_EVENTS, check.baseEvents)
        putLong(KEY_VIDEOS, check.baseVideos)
        putString(KEY_PLATFORM, check.platform?.name)
    }

    override fun clear() = prefs.edit { clear() }

    private companion object {
        const val PREFS = "service_check"
        const val KEY_STARTED = "started_at"
        const val KEY_PID = "pid"
        const val KEY_EVENTS = "base_events"
        const val KEY_VIDEOS = "base_videos"
        const val KEY_PLATFORM = "platform"
    }
}
