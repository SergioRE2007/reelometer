package com.reelometro.app.data.game

import androidx.room.Dao
import androidx.room.Query

/** Primer día en que apareció una categoría dentro de su semana (ver [GameQueries.CATEGORY_WEEK_FIRST_DAYS]). */
data class CategoryWeekRow(val name: String, val firstDay: Long)

/**
 * Consultas del modo Pasaporte (sellos irónicos). Solo lectura; no añade tablas ni cambia el esquema.
 * Los recuentos por día salen de `StatsDao.countsByDay`.
 */
@Dao
interface GameDao {

    @Query(GameQueries.FIRST_DAY_WITH_PLATFORMS)
    suspend fun firstDayWithPlatforms(platforms: Int): Long?

    @Query(GameQueries.FIRST_DAY_BETWEEN_MINUTES)
    suspend fun firstDayBetweenMinutes(fromMinute: Int, toMinute: Int): Long?

    @Query(GameQueries.FIRST_DAY_WITH_BUSY_HOUR)
    suspend fun firstDayWithBusyHour(perHour: Int): Long?

    @Query(GameQueries.CATEGORY_WEEK_FIRST_DAYS)
    suspend fun categoryWeekFirstDays(): List<CategoryWeekRow>
}
