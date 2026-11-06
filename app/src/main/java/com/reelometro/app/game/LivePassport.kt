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

/** De dónde saca el pasaporte el historial de vídeos (en la app, SQL en `data/game/`). */
interface GameHistoryLoader {
    /** Emite al suscribirse y cada vez que cambian los datos de uso. */
    fun changes(): Flow<Unit>

    /**
     * Historial hasta [today] incluido. Los sellos de [skip] ya están guardados: puede saltarse sus
     * consultas (dejando el campo a `null`).
     */
    suspend fun load(today: LocalDate, skip: Set<StampId>): GameHistory
}

/** Dónde se guarda la [PassportMemory] (en la app, DataStore en `data/game/`). */
interface PassportMemoryStore {
    /** Memoria actual; emite al suscribirse y en cada cambio. */
    val memory: Flow<PassportMemory>

    /** Cambia la memoria de forma atómica (las escrituras no se pisan). */
    suspend fun update(transform: (PassportMemory) -> PassportMemory)
}

/**
 * [PassportSource] sobre un [GameHistoryLoader] y un [PassportMemoryStore]: aplica [PassportEngine]
 * y guarda lo que el motor añade a la memoria. Puro (solo corrutinas), así se prueba sin Android.
 *
 * [observe] se recalcula cuando cambian los datos de uso, el objetivo, la memoria (celebraciones
 * vistas, eventos…) o el día ([ticks], que también emite al suscribirse); como mucho una vez cada
 * [throttleMs] y siempre con lo último, fuera del hilo principal y solo emite si el estado cambia.
 *
 * Guardar lo que añade el motor vuelve a disparar el cálculo, que ya no cambia nada (el motor es
 * idempotente), así que no hay bucles. Las escrituras se suman a lo que haya ([PassportMemory.absorb]):
 * una celebración marcada mientras tanto no se pierde, y lo calculado antes de un borrado
 * ([PassportMemory.generation]) se descarta.
 */
class LivePassport(
    private val loader: GameHistoryLoader,
    private val store: PassportMemoryStore,
    dailyGoal: Flow<Int?>,
    private val today: () -> LocalDate,
    private val ticks: Flow<Unit>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val throttleMs: Long = THROTTLE_MS,
) : PassportSource {

    private val goal: Flow<Int?> = dailyGoal.map { g -> g?.takeIf { it > 0 } }.distinctUntilChanged()

    override fun observe(): Flow<PassportState> =
        combine(loader.changes(), goal, store.memory, ticks) { _, currentGoal, memory, _ -> Inputs(currentGoal, memory) }
            .conflate()
            .transform { inputs ->
                emit(compute(inputs.goal, inputs.memory))
                if (throttleMs > 0) delay(throttleMs)
            }
            .distinctUntilChanged()
            .flowOn(dispatcher)

    override suspend fun snapshot(): PassportState = withContext(dispatcher) {
        compute(goal.first(), store.memory.first())
    }

    override suspend fun markCelebrated(keys: Collection<String>) {
        if (keys.isEmpty()) return
        store.update { it.celebrate(keys) }
    }

    override suspend fun markMoodSeen(mood: FlapMood) {
        store.update { it.seeing(mood) }
    }

    override suspend fun recordEvent(event: GameEvent) {
        val date = today()
        store.update { it.withEvent(event, date) }
    }

    /** Borra la memoria del pasaporte (BORRAR TODO, GAME_RESET). Los datos de uso no se tocan aquí. */
    suspend fun clear() {
        store.update { it.cleared() }
    }

    /** Tras importar un CSV: mezcla lo del fichero ([PassportMemory.imported]) y toma una base nueva ([PassportMemory.rebased]). */
    suspend fun import(data: PassportImport) {
        store.update { it.imported(data).rebased() }
    }

    /** Al encender el modo pasaporte: ver [PassportMemory.rebased]. */
    suspend fun rebase() {
        store.update { it.rebased() }
    }

    /** Memoria guardada ahora mismo (CSV). */
    suspend fun memory(): PassportMemory = store.memory.first()

    /** Solo QA (`GAME_CELEBRATE`): ver [PassportMemory.forcing]. */
    suspend fun force(kind: ForcedCelebration, stamp: StampId) {
        val date = today()
        store.update { it.forcing(kind, stamp, date) }
    }

    private suspend fun compute(goal: Int?, memory: PassportMemory): PassportState {
        val date = today()
        val history = loader.load(date, memory.stamps.keys)
        val result = PassportEngine.compute(GameInput(date, goal, history, memory))
        if (result.memory != memory) {
            store.update { current ->
                if (current.generation == memory.generation) current.absorb(result.memory) else current
            }
        }
        return result.state
    }

    private data class Inputs(val goal: Int?, val memory: PassportMemory)

    companion object {
        /** Ventana mínima entre dos recálculos (durante un scroll rápido llegan muchas inserciones). */
        const val THROTTLE_MS = 300L
    }
}
