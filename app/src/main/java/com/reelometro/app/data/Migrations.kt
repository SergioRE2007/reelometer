package com.reelometro.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migraciones de [AppDatabase]. Cada una tiene su test en `MigrationTest`. */
object Migrations {

    /**
     * v1 → v2:
     * - `reel_views` gana `day` (epochDay local) y `minuteOfDay` (0–1439), rellenados para las filas
     *   existentes con la hora local de SQLite (`'localtime'`, la zona del sistema al migrar).
     * - Índices para las agregaciones (ver [ReelView]).
     * - Nueva tabla `share_events`.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `reel_views` ADD COLUMN `day` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `reel_views` ADD COLUMN `minuteOfDay` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                UPDATE `reel_views` SET
                    `day` = CAST(julianday(date(`startedAt` / 1000, 'unixepoch', 'localtime')) - 2440587.5 AS INTEGER),
                    `minuteOfDay` =
                        CAST(strftime('%H', `startedAt` / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60 +
                        CAST(strftime('%M', `startedAt` / 1000, 'unixepoch', 'localtime') AS INTEGER)
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reel_views_day` ON `reel_views` (`day`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reel_views_day_platform_category_minuteOfDay_durationMs_creator` " +
                    "ON `reel_views` (`day`, `platform`, `category`, `minuteOfDay`, `durationMs`, `creator`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `share_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`platform` TEXT NOT NULL, `at` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                    "`minuteOfDay` INTEGER NOT NULL, `reelId` INTEGER)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_share_events_day` ON `share_events` (`day`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_share_events_reelId` ON `share_events` (`reelId`)")
        }
    }

    /** v2 → v3: nueva tabla `share_recipients` (vacía; no toca nada de lo anterior). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `share_recipients` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`platform` TEXT NOT NULL, `at` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                    "`minuteOfDay` INTEGER NOT NULL, `recipient` TEXT NOT NULL, `reelId` INTEGER)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_share_recipients_day_recipient_platform` " +
                    "ON `share_recipients` (`day`, `recipient`, `platform`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_share_recipients_reelId` ON `share_recipients` (`reelId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
