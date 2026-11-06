package com.reelometro.app.data.game

import android.content.Context
import android.util.Log
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.ClockTicker
import com.reelometro.app.data.SystemZoneClock
import com.reelometro.app.data.Ticker
import com.reelometro.app.data.UserDataPart
import com.reelometro.app.copy.ResourceTexts
import com.reelometro.app.copy.Texts
import com.reelometro.app.data.export.CsvBlock
import com.reelometro.app.data.export.PassportCsv
import com.reelometro.app.game.PassportImport
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.ForcedCelebration
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.GameHistoryLoader
import com.reelometro.app.game.LivePassport
import com.reelometro.app.game.PassportMemoryStore
import com.reelometro.app.game.PassportSource
import com.reelometro.app.game.PassportState
import com.reelometro.app.game.StampId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlin.math.min

/**
 * Modo Pasaporte sobre los datos reales: lee el historial con SQL ([RoomGameHistory]), aplica las
 * reglas puras de `game/` ([LivePassport] y `PassportEngine`) y guarda en el móvil solo lo que no se
 * puede recalcular ([PassportStore]: fechas de los sellos, celebraciones vistas, nivel más alto,
 * retos cerrados, eventos).
 *
 * También es una [UserDataPart]: `ReelRepository` lo usa para añadir el bloque PASAPORTE al CSV y
 * para borrarlo con BORRAR TODO.
 *
 * [observe] se recalcula al cambiar `reel_views`, el objetivo, la memoria y a medianoche, con límite
 * de frecuencia y fuera del hilo principal (ver [LivePassport]).
 */
class PassportRepository internal constructor(
    loader: GameHistoryLoader,
    dailyGoal: Flow<Int?>,
    store: PassportMemoryStore,
    private val clock: Clock = SystemZoneClock,
    ticker: Ticker = ClockTicker(clock),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    throttleMs: Long = LivePassport.THROTTLE_MS,
    /** Textos de otros idiomas con los que leer el bloque PASAPORTE de un CSV viejo (el español). */
    private val legacyTexts: () -> List<Texts> = { emptyList() },
) : PassportSource, UserDataPart {

    /** La de la app: objetivo de Ajustes y memoria en `passport.preferences_pb`. */
    constructor(context: Context, database: AppDatabase, settings: SettingsRepository) : this(
        loader = RoomGameHistory(database),
        dailyGoal = settings.settings.map { it.dailyGoal },
        store = PassportStore(context),
        legacyTexts = { listOf(ResourceTexts(context, SPANISH)) },
    )

    private val live = LivePassport(
        loader = loader,
        store = store,
        dailyGoal = dailyGoal,
        today = { LocalDate.now(clock) },
        ticks = ticker.ticks(null),
        dispatcher = dispatcher,
        throttleMs = throttleMs,
    )

    /**
     * Un único cálculo para todos los que miran (pestaña Pasaporte y `CelebrationHost` a la vez).
     * Se para a los 5 s sin nadie y entonces olvida el último valor, para no repetir una
     * celebración ya marcada al volver.
     *
     * Un error no puede escaparse del `scope` compartido (cerraría la app): se entrega como
     * `Result.failure` a quien mira (la pantalla enseña el error y REINTENTAR) y el cálculo se
     * reintenta solo, con espera creciente.
     */
    private val shared: Flow<Result<PassportState>> by lazy {
        live.observe()
            .map { Result.success(it) }
            .retryWhen { cause, attempt ->
                Log.w(LOG, "Pasaporte: cálculo fallido", cause)
                emit(Result.failure(cause))
                delay(min(MAX_RETRY_MS, RETRY_MS * (attempt + 1)))
                true
            }
            .shareIn(
                CoroutineScope(SupervisorJob() + dispatcher),
                SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MS, replayExpirationMillis = 0),
                replay = 1,
            )
    }

    override fun observe(): Flow<PassportState> = shared.map { it.getOrThrow() }

    override suspend fun snapshot(): PassportState = live.snapshot()

    override suspend fun markCelebrated(keys: Collection<String>) = live.markCelebrated(keys)

    override suspend fun markMoodSeen(mood: FlapMood) = live.markMoodSeen(mood)

    override suspend fun recordEvent(event: GameEvent) = live.recordEvent(event)

    /** Bloque PASAPORTE del CSV (nivel, sellos con fecha, retos cerrados y eventos). No cierra [out]. */
    override suspend fun exportCsv(out: OutputStream) {
        val state = live.snapshot()
        val memory = live.memory()
        withContext(Dispatchers.IO) { PassportCsv.write(out, state, memory.events) }
    }

    override val blockTitle: String get() = PassportCsv.TITLE

    /**
     * Tras importar un CSV: recupera del bloque PASAPORTE lo que no se puede recalcular (fechas de sellos y
     * eventos, nivel más alto, retos cerrados) y, siempre, toma una base nueva: lo conseguido con el
     * historial importado no llega de golpe como celebraciones.
     */
    override suspend fun importBlock(block: CsvBlock?) {
        val data = if (block == null) PassportImport() else withContext(dispatcher) { PassportCsv.read(block, legacyTexts()) }
        live.import(data)
    }

    /** BORRAR TODO (y el gancho de QA `GAME_RESET`): el pasaporte vuelve a empezar. */
    override suspend fun clearAll() = live.clear()

    /** Al volver a encender el modo pasaporte: lo conseguido con el modo oculto no llega de golpe. */
    suspend fun rebase() = live.rebase()

    /**
     * Solo QA (`GAME_CELEBRATE`): deja pendiente una celebración de prueba. [kind] es `stamp`
     * (por defecto), `level` o `challenge`; [stamp], el nombre de un [StampId] (por defecto
     * PRIMER_DIA). Devuelve qué ha hecho, para el log.
     */
    suspend fun forceCelebration(kind: String?, stamp: String?): String {
        val forced = ForcedCelebration.parse(kind)
            ?: throw IllegalArgumentException("tipo desconocido «$kind»; usa stamp|level|challenge")
        val name = stamp?.trim()?.uppercase().orEmpty()
        val id = if (name.isEmpty()) {
            StampId.PRIMER_DIA
        } else {
            StampId.entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("sello desconocido «$stamp»")
        }
        live.force(forced, id)
        return if (forced == ForcedCelebration.STAMP) "$forced $id" else forced.name
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RETRY_MS = 2_000L
        const val MAX_RETRY_MS = 60_000L
        const val LOG = "Reelometro"
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    }
}
