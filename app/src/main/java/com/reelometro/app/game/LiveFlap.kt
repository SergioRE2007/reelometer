package com.reelometro.app.game

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** De dónde saca Flap los vídeos por día (en la app, SQL en `data/game/`). */
interface FlapHistoryLoader {
    /** Emite al suscribirse y cada vez que cambian los datos de uso. */
    fun changes(): Flow<Unit>

    /** Vídeos por día de todo el historial, hasta [today] incluido (los días sin vídeos pueden faltar). */
    suspend fun counts(today: LocalDate): Map<LocalDate, Int>
}

/** Último estado de Flap que el usuario vio en la pestaña y el día en que lo vio. */
data class FlapSeen(val mood: FlapMood, val date: LocalDate) {

    /** `MOOD|yyyy-MM-dd`, como se guarda en el móvil. */
    fun encode(): String = "${mood.name}$SEPARATOR$date"

    companion object {
        private const val SEPARATOR = '|'

        /** Lo contrario de [encode]; `null` si no hay nada o no se entiende (nunca falla). */
        fun decode(raw: String?): FlapSeen? {
            val parts = raw?.trim()?.split(SEPARATOR) ?: return null
            if (parts.size != 2) return null
            val mood = FlapMood.entries.firstOrNull { it.name == parts[0].trim() } ?: return null
            val date = runCatching { LocalDate.parse(parts[1].trim()) }.getOrNull() ?: return null
            return FlapSeen(mood, date)
        }
    }
}

/** Dónde se guarda lo último visto de Flap (en la app, DataStore en `data/game/`). */
interface FlapSeenStore {
    /** Lo guardado ahora; emite al suscribirse y en cada cambio. `null` si nunca se ha visto. */
    val seen: Flow<FlapSeen?>

    /** Guarda [seen] (si ya estaba guardado tal cual, no escribe). */
    suspend fun save(seen: FlapSeen)

    /** Lo borra (BORRAR TODO, `GAME_RESET`). */
    suspend fun clear()

    /** Borra lo que quede del pasaporte de antes de Flap (en la app, la memoria de sellos). */
    suspend fun dropLegacy() {}
}

/**
 * [FlapSource] sobre un [FlapHistoryLoader] y un [FlapSeenStore]: aplica [FlapEngine]. Puro (solo
 * corrutinas), así se prueba sin Android.
 *
 * [observe] se recalcula cuando cambian los datos de uso, el objetivo, lo último visto o el día
 * ([ticks], que también emite al suscribirse: en la app, a cada medianoche); como mucho una vez cada
 * [throttleMs] y siempre con lo último, fuera del hilo principal y solo emite si el estado cambia.
 *
 * El cálculo no escribe nada (solo [markMoodSeen] guarda), así que no hay bucles: guardar lo visto
 * recalcula una vez y, como mucho, cambia [FlapState.previousMood].
 */
class LiveFlap(
    private val loader: FlapHistoryLoader,
    private val store: FlapSeenStore,
    dailyGoal: Flow<Int?>,
    private val today: () -> LocalDate,
    private val ticks: Flow<Unit>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val throttleMs: Long = THROTTLE_MS,
) : FlapSource {

    private val goal: Flow<Int?> = dailyGoal.map { g -> g?.takeIf { it > 0 } }.distinctUntilChanged()

    /** Solo el estado: la fecha de lo visto no cambia nada de lo que se pinta. */
    private val seenMood: Flow<FlapMood?> = store.seen.map { it?.mood }.distinctUntilChanged()

    override fun observe(): Flow<FlapState> =
        combine(loader.changes(), goal, seenMood, ticks) { _, currentGoal, seen, _ -> Inputs(currentGoal, seen) }
            .conflate()
            .transform { inputs ->
                emit(compute(inputs.goal, inputs.seen))
                if (throttleMs > 0) delay(throttleMs)
            }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    override suspend fun snapshot(): FlapState = withContext(dispatcher) {
        compute(goal.first(), seenMood.first())
    }

    override suspend fun markMoodSeen(mood: FlapMood) {
        store.save(FlapSeen(mood, today()))
    }

    /** Olvida lo último visto (BORRAR TODO, `GAME_RESET`). Los datos de uso no se tocan aquí. */
    suspend fun clear() {
        store.clear()
    }

    private suspend fun compute(goal: Int?, seen: FlapMood?): FlapState {
        val date = today()
        return FlapEngine.compute(date, goal, loader.counts(date), seen)
    }

    private data class Inputs(val goal: Int?, val seen: FlapMood?)

    companion object {
        /** Ventana mínima entre dos recálculos (durante un scroll rápido llegan muchas inserciones). */
        const val THROTTLE_MS = 300L
    }
}
