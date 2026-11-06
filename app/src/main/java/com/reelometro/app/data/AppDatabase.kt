package com.reelometro.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.reelometro.app.data.game.GameDao

/**
 * Base de datos local (Room). Esquemas exportados en `app/schemas/`; migraciones en [Migrations].
 *
 * v2: `reel_views.day`/`minuteOfDay`, índices de agregación y tabla `share_events`.
 * v3: tabla `share_recipients` (a quién envías en Instagram, con el ajuste encendido).
 */
@Database(entities = [ReelView::class, ShareEvent::class, ShareRecipient::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao
    abstract fun statsDao(): StatsDao

    /** Consultas del modo Pasaporte (solo lectura; no cambia el esquema). */
    abstract fun gameDao(): GameDao

    companion object {
        const val NAME = "reelometro.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
