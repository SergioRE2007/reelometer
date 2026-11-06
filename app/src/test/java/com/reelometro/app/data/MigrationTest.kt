package com.reelometro.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import com.reelometro.app.stats.PeriodKind
import com.reelometro.app.stats.PeriodRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class MigrationTest {

    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()

    @get:Rule(order = 1)
    val helper = MigrationTestHelper(SchemaAssetsInstrumentation.create(), AppDatabase::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** SQLite convierte con `'localtime'`, que usa la zona del sistema: la misma que la de Java aquí. */
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Instantes delicados: justo antes y después de medianoche, mediodía y un cambio de hora (marzo). */
    private val samples: List<Long> = listOf(
        at(LocalDate.of(2026, 11, 2), 23, 59, zone, second = 59),
        at(LocalDate.of(2026, 11, 3), 0, 0, zone),
        at(LocalDate.of(2026, 11, 3), 0, 1, zone),
        at(LocalDate.of(2026, 11, 3), 13, 47, zone, second = 31),
        at(LocalDate.of(2026, 3, 29), 3, 15, zone),
        at(LocalDate.of(2026, 7, 15), 23, 30, zone),
    )

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `de 1 a 2 conserva las filas y calcula día y minuto locales`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            samples.forEachIndexed { i, startedAt ->
                db.execSQL(
                    "INSERT INTO reel_views (id, platform, startedAt, durationMs, creator, caption, category) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(i + 1L, "TIKTOK", startedAt, 15_000L, "creador$i", "desc $i", "HUMOR"),
                )
            }
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT id, startedAt, day, minuteOfDay, durationMs, creator, category FROM reel_views ORDER BY id").use { c ->
            assertEquals(samples.size, c.count)
            while (c.moveToNext()) {
                val startedAt = c.getLong(1)
                val expected = LocalStamp.of(startedAt, zone)
                assertEquals("día de $startedAt", expected.day, c.getLong(2))
                assertEquals("minuto de $startedAt", expected.minuteOfDay, c.getInt(3))
                assertEquals(15_000L, c.getLong(4))
                assertEquals("HUMOR", c.getString(6))
            }
        }
        // La tabla nueva funciona.
        db.execSQL(
            "INSERT INTO share_events (platform, at, day, minuteOfDay, reelId) VALUES ('TIKTOK', 1, 20000, 10, 1)",
        )
        db.query("SELECT COUNT(*) FROM share_events").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        val indices = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%'").use { c ->
            while (c.moveToNext()) indices += c.getString(0)
        }
        assertTrue(
            indices.containsAll(
                listOf(
                    "index_reel_views_startedAt",
                    "index_reel_views_day",
                    "index_reel_views_day_platform_category_minuteOfDay_durationMs_creator",
                    "index_share_events_day",
                    "index_share_events_reelId",
                ),
            ),
        )
        db.close()
    }

    @Test
    fun `Room abre la base migrada y el repositorio lee los datos de antes`() = runBlocking {
        val first = at(LocalDate.of(2026, 11, 2), 22, 10, zone)
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO reel_views (id, platform, startedAt, durationMs, creator, caption, category) " +
                    "VALUES (1, 'INSTAGRAM', $first, 20000, 'michi_filosofo', NULL, 'ANIMALES')",
            )
            db.execSQL(
                "INSERT INTO reel_views (id, platform, startedAt, durationMs, creator, caption, category) " +
                    "VALUES (2, 'TIKTOK', ${first + 60_000}, 10000, NULL, NULL, 'OTROS')",
            )
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
        helper.closeWhenFinished(room)
        val clock = MutableClock(at(LocalDate.of(2026, 11, 3), 12, 0, zone), zone)
        val repository = ReelRepository(room, clock = clock, ticker = ManualTicker(), throttleMs = 0)

        assertEquals(LocalDate.of(2026, 11, 2), repository.firstDay())
        val day = repository.day(LocalDate.of(2026, 11, 2))
        assertEquals(2, day.totals.count)
        assertEquals(30_000L, day.totals.totalMs)
        assertEquals(22, day.peakHour?.hour)
        val week = repository.period(PeriodRef(PeriodKind.WEEK))
        assertEquals(2, week.totals.count)
        assertEquals("michi_filosofo", week.topCreator?.creator)
        // Y se puede seguir escribiendo con el contrato del servicio.
        repository.recordStart(3, Platform.YOUTUBE, clock.millis())
        repository.recordShare(Platform.YOUTUBE, clock.millis() + 1_000, 3)
        assertEquals(1, repository.today().totals.count)
        assertEquals(1, repository.today().totals.shares)
    }

    @Test
    fun `de 2 a 3 conserva los datos y crea la tabla de envíos`() = runBlocking {
        val first = at(LocalDate.of(2026, 11, 2), 22, 10, zone)
        val stamp = LocalStamp.of(first, zone)
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO reel_views (id, platform, startedAt, durationMs, creator, caption, category, day, minuteOfDay) " +
                    "VALUES (1, 'INSTAGRAM', $first, 20000, 'michi_filosofo', NULL, 'ANIMALES', ${stamp.day}, ${stamp.minuteOfDay})",
            )
            db.execSQL(
                "INSERT INTO share_events (platform, at, day, minuteOfDay, reelId) " +
                    "VALUES ('INSTAGRAM', ${first + 1_000}, ${stamp.day}, ${stamp.minuteOfDay}, 1)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3).close()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
        helper.closeWhenFinished(room)
        val clock = MutableClock(at(LocalDate.of(2026, 11, 3), 12, 0, zone), zone)
        val repository = ReelRepository(room, clock = clock, ticker = ManualTicker(), throttleMs = 0)
        val week = repository.period(PeriodRef(PeriodKind.WEEK))
        assertEquals(1, week.totals.count)
        assertEquals(1, week.totals.shares)
        assertTrue(week.recipients.isEmpty())
        repository.recordRecipients(Platform.INSTAGRAM, clock.millis(), 1, listOf("Los del piso"))
        assertEquals("Los del piso", repository.period(PeriodRef(PeriodKind.WEEK)).recipients.single().name)
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
