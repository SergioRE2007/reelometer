package com.reelometro.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Escrituras y lecturas fila a fila. Las agregaciones están en [StatsDao]. */
@Dao
interface ReelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(view: ReelView)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(views: List<ReelView>)

    @Query("UPDATE reel_views SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long)

    @Query(Queries.UPDATE_INFO)
    suspend fun updateInfo(id: Long, creator: String?, caption: String?, category: String)

    @Insert
    suspend fun insertShare(event: ShareEvent): Long

    @Insert
    suspend fun insertShares(events: List<ShareEvent>)

    @Insert
    suspend fun insertRecipients(rows: List<ShareRecipient>)

    @Query(Queries.UNDO_RECIPIENT)
    suspend fun undoRecipient(recipient: String, since: Long): Int

    @Query("DELETE FROM share_recipients WHERE reelId = :id")
    suspend fun deleteRecipientsOf(id: Long): Int

    @Query("DELETE FROM reel_views WHERE id = :id")
    suspend fun deleteView(id: Long): Int

    @Query("DELETE FROM share_events WHERE reelId = :id")
    suspend fun deleteSharesOf(id: Long): Int

    /** Borra un vídeo y los compartidos (y envíos) que cuelgan de él, en una sola transacción. */
    @Transaction
    suspend fun discard(id: Long) {
        deleteView(id)
        deleteSharesOf(id)
        deleteRecipientsOf(id)
    }

    @Query("DELETE FROM reel_views")
    suspend fun deleteAllViews(): Int

    @Query("DELETE FROM share_events")
    suspend fun deleteAllShares(): Int

    @Query("DELETE FROM share_recipients")
    suspend fun deleteAllRecipients(): Int

    /** Borra todos los datos de uso. */
    @Transaction
    suspend fun deleteAll() {
        deleteAllViews()
        deleteAllShares()
        deleteAllRecipients()
    }

    @Query("SELECT * FROM reel_views WHERE id = :id")
    suspend fun find(id: Long): ReelView?

    @Query("SELECT * FROM share_events ORDER BY at, id")
    suspend fun shareEvents(): List<ShareEvent>

    @Query("SELECT * FROM share_recipients ORDER BY at, id")
    suspend fun shareRecipients(): List<ShareRecipient>

    /** Los últimos vídeos, el más reciente primero. Room lo repite al cambiar cualquiera de las dos tablas. */
    @Query(Queries.RECENT)
    fun observeRecent(limit: Int): Flow<List<VideoRow>>

    @Query(Queries.EXPORT_PAGE)
    suspend fun exportPage(afterStartedAt: Long, afterId: Long, limit: Int): List<VideoRow>

    /** Compartidos que no cuelgan de ningún vídeo guardado (bloque COMPARTIDOS SIN VÍDEO del CSV). */
    @Query(Queries.ORPHAN_SHARES)
    suspend fun orphanShares(): List<ShareEvent>

    /** Id, plataforma e inicio de todos los vídeos (importar sin duplicar). */
    @Query("SELECT id, platform, startedAt FROM reel_views")
    suspend fun viewKeys(): List<ViewKey>
}

/** Agregaciones en SQL (ver [Queries]). Rangos en epochDay local, ambos incluidos. */
@Dao
interface StatsDao {

    @Query(Queries.VIEWS_BY_DAY)
    suspend fun viewsByDay(from: Long, to: Long): List<DayRow>

    @Query(Queries.COUNTS_BY_DAY)
    suspend fun countsByDay(from: Long, to: Long): List<DayCountRow>

    @Query(Queries.SHARES_BY_DAY)
    suspend fun sharesByDay(from: Long, to: Long): List<DayCountRow>

    @Query(Queries.VIEWS_BY_HOUR)
    suspend fun viewsByHour(from: Long, to: Long): List<HourRow>

    @Query(Queries.VIEWS_BY_PLATFORM)
    suspend fun viewsByPlatform(from: Long, to: Long): List<GroupRow>

    @Query(Queries.VIEWS_BY_CATEGORY)
    suspend fun viewsByCategory(from: Long, to: Long): List<GroupRow>

    @Query(Queries.TOP_CREATORS)
    suspend fun topCreators(from: Long, to: Long, limit: Int): List<CreatorRow>

    @Query(Queries.SHARE_COUNT)
    suspend fun shareCount(from: Long, to: Long): Int

    @Query(Queries.TOP_RECIPIENTS)
    suspend fun topRecipients(from: Long, to: Long, limit: Int): List<RecipientRow>

    @Query(Queries.TOTALS_UNTIL)
    suspend fun totalsUntil(from: Long, to: Long, untilMinute: Int): TotalsRow

    @Query(Queries.SHARES_UNTIL)
    suspend fun sharesUntil(from: Long, to: Long, untilMinute: Int): Int

    @Query(Queries.SAME_WEEKDAY_BASELINE)
    suspend fun sameWeekdayBaseline(days: List<Long>, untilMinute: Int, limit: Int): List<DayRow>

    @Query(Queries.FIRST_DAY)
    suspend fun firstDay(): Long?

    /** Días hasta [to] (incluido) con más vídeos que [day] (puesto del día = esto + 1). */
    @Query(Queries.DAYS_ABOVE)
    suspend fun daysAbove(day: Long, to: Long): Int

    @Query(Queries.ALL_TOTALS)
    suspend fun allTotals(): TotalsRow

    @Query(Queries.ALL_SHARES)
    suspend fun shareTotal(): Int
}
