package com.reelometro.app.data.game

import androidx.room.Transactor
import androidx.room.useReaderConnection
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.ReelView
import com.reelometro.app.game.GameHistory
import com.reelometro.app.game.GameHistoryLoader
import com.reelometro.app.game.PassportRules
import com.reelometro.app.game.StampId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Historial del pasaporte con SQL sobre `reel_views`, todo en **una transacción de lectura**
 * (cifras coherentes entre sí; no bloquea al servicio, que sigue escribiendo).
 *
 * Coste: los recuentos por día de todo el historial (índice estrecho de `day`, una fila por día) y,
 * solo mientras el sello no esté guardado, una consulta por sello irónico sobre el índice cubriente.
 */
internal class RoomGameHistory(private val database: AppDatabase) : GameHistoryLoader {

    private val stats = database.statsDao()
    private val game = database.gameDao()

    /** Cambios en `reel_views` (los compartidos no dan sellos). Room emite también al suscribirse. */
    override fun changes(): Flow<Unit> = database.invalidationTracker.createFlow(ReelView.TABLE).map { }

    override suspend fun load(today: LocalDate, skip: Set<StampId>): GameHistory = readTransaction {
        val first = stats.firstDay()
        if (first == null) {
            GameHistory()
        } else {
            val counts = stats.countsByDay(first, today.toEpochDay())
                .associate { LocalDate.ofEpochDay(it.day) to it.count }
            GameHistory(
                counts = counts,
                threePlatformsOn = if (StampId.TRES_PLATAFORMAS in skip) {
                    null
                } else {
                    game.firstDayWithPlatforms(PassportRules.PLATFORMS_IN_A_DAY)?.let { LocalDate.ofEpochDay(it) }
                },
                fiveCategoriesOn = if (StampId.CINCO_CATEGORIAS in skip) {
                    null
                } else {
                    PassportRules.varietyDay(game.categoryWeekFirstDays().map { LocalDate.ofEpochDay(it.firstDay) })
                },
                earlyBirdOn = if (StampId.MADRUGADOR in skip) {
                    null
                } else {
                    game.firstDayBetweenMinutes(PassportRules.EARLY_BIRD_FROM_MINUTE, PassportRules.EARLY_BIRD_TO_MINUTE)
                        ?.let { LocalDate.ofEpochDay(it) }
                },
                marathonOn = if (StampId.MARATON in skip) {
                    null
                } else {
                    game.firstDayWithBusyHour(PassportRules.MARATHON_PER_HOUR)?.let { LocalDate.ofEpochDay(it) }
                },
            )
        }
    }

    /** Varias lecturas en una transacción de lectura (instantánea coherente), como en `ReelRepository`. */
    private suspend fun <R> readTransaction(block: suspend () -> R): R =
        database.useReaderConnection { connection ->
            connection.withTransaction(Transactor.SQLiteTransactionType.DEFERRED) { block() }
        }
}
