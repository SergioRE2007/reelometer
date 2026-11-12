package com.reelometro.app.data.game

import android.content.Context
import android.util.Log
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.ClockTicker
import com.reelometro.app.data.SystemZoneClock
import com.reelometro.app.data.Ticker
import com.reelometro.app.data.UserDataPart
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.game.FlapHistoryLoader
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSeenStore
import com.reelometro.app.game.FlapSource
import com.reelometro.app.game.FlapState
import com.reelometro.app.game.LiveFlap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import java.time.Clock
import java.time.LocalDate
import kotlin.math.min

/**
 * Flap sobre los datos reales: los vídeos por día con SQL ([RoomFlapHistory]), el objetivo de Ajustes,
 * las reglas puras de `game/` ([LiveFlap], [com.reelometro.app.game.FlapEngine]) y, guardado en el
 * móvil, solo lo último que el usuario vio ([FlapStore]).
 *
 * También es una [UserDataPart]: BORRAR TODO (y SUSTITUIR TODO al importar) borra lo visto. Flap no
 * va en el CSV: todo lo demás se recalcula del historial.
 *
 * [observe] se recalcula al cambiar `reel_views`, el objetivo, lo visto y a medianoche, con límite
 * de frecuencia y fuera del hilo principal (ver [LiveFlap]); es un único cálculo compartido.
 */
class FlapRepository internal constructor(
    loader: FlapHistoryLoader,
    dailyGoal: Flow<Int?>,
    private val store: FlapSeenStore,
    clock: Clock = SystemZoneClock,
    ticker: Ticker = ClockTicker(clock),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    throttleMs: Long = LiveFlap.THROTTLE_MS,
) : FlapSource, UserDataPart {

    /** La de la app: objetivo de Ajustes y lo visto en `passport.preferences_pb`. */
    constructor(context: Context, database: AppDatabase, settings: SettingsRepository) : this(
        loader = RoomFlapHistory(database),
        dailyGoal = settings.settings.map { it.dailyGoal },
        store = FlapStore(context),
    )

    private val live = LiveFlap(
        loader = loader,
        store = store,
        dailyGoal = dailyGoal,
        today = { LocalDate.now(clock) },
        ticks = ticker.ticks(null),
        dispatcher = dispatcher,
        throttleMs = throttleMs,
    )

    /**
     * Un único cálculo para todos los que miran (la pestaña y quien más lo observe a la vez). Se para
     * a los 5 s sin nadie y entonces olvida el último valor: al volver, nada de un `previousMood` viejo.
     *
     * Un error no puede escaparse del `scope` compartido (cerraría la app): se entrega como
     * `Result.failure` a quien mira (la pantalla enseña el error y REINTENTAR) y el cálculo se
     * reintenta solo, con espera creciente. REINTENTAR recalcula al momento (ver [observe]).
     */
    private val shared: Flow<Result<FlapState>> by lazy {
        live.observe()
            .map { Result.success(it) }
            .retryWhen { cause, attempt ->
                Log.w(LOG, "Flap: cálculo fallido", cause)
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

    override fun observe(): Flow<FlapState> = flow {
        var first = true
        shared.collect { result ->
            // Lo primero puede ser un fallo de la caché (REINTENTAR mientras el cálculo espera su
            // reintento): se recalcula ya; si vuelve a fallar, la pantalla enseña otra vez el error.
            val recompute = first && result.isFailure
            first = false
            emit(if (recompute) live.snapshot() else result.getOrThrow())
        }
    }

    override suspend fun snapshot(): FlapState = live.snapshot()

    override suspend fun markMoodSeen(mood: FlapMood) = live.markMoodSeen(mood)

    /** BORRAR TODO, SUSTITUIR TODO y el gancho de QA `GAME_RESET`: Flap olvida lo último visto. */
    override suspend fun clearAll() = live.clear()

    /** Al arrancar: borra la memoria del pasaporte de antes de Flap (fechas de sellos), si sigue ahí. */
    suspend fun dropLegacy() = store.dropLegacy()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RETRY_MS = 2_000L
        const val MAX_RETRY_MS = 60_000L
        const val LOG = "Reelometro"
    }
}
