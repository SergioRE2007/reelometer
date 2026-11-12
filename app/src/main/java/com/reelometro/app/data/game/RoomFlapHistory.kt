package com.reelometro.app.data.game

import androidx.room.Transactor
import androidx.room.useReaderConnection
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.ReelView
import com.reelometro.app.game.FlapHistoryLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Vídeos por día para Flap, con el SQL que ya usan las rachas (`StatsDao.firstDay` y
 * `StatsDao.countsByDay`: índice estrecho de `day`, una fila por día) en **una transacción de lectura**
 * (cifras coherentes entre sí; no bloquea al servicio, que sigue escribiendo). Sin consultas propias:
 * no añade DAO, tablas ni índices.
 */
internal class RoomFlapHistory(private val database: AppDatabase) : FlapHistoryLoader {

    private val stats by lazy { database.statsDao() }

    /** Cambios en `reel_views` (los compartidos no cuentan). Room emite también al suscribirse. */
    override fun changes(): Flow<Unit> = database.invalidationTracker.createFlow(ReelView.TABLE).map { }

    override suspend fun counts(today: LocalDate): Map<LocalDate, Int> = readTransaction {
        val first = stats.firstDay()
        if (first == null) {
            emptyMap<LocalDate, Int>()
        } else {
            stats.countsByDay(first, today.toEpochDay()).associate { LocalDate.ofEpochDay(it.day) to it.count }
        }
    }

    /** Varias lecturas en una transacción de lectura (instantánea coherente), como en `ReelRepository`. */
    private suspend fun <R> readTransaction(block: suspend () -> R): R =
        database.useReaderConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) { block() }
        }
}
