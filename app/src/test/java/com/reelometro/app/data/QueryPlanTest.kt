package com.reelometro.app.data

import android.app.Application
import androidx.sqlite.db.SimpleSQLiteQuery
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * `EXPLAIN QUERY PLAN` de las consultas reales ([Queries]): las agregaciones no leen la tabla
 * (índice cubriente) y nada recorre una tabla entera sin índice.
 */
@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class QueryPlanTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()


    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Parámetros en orden de primera aparición (SQLite numera así los `:nombre`). */
    private fun plan(sql: String, vararg args: Any): List<String> {
        val details = mutableListOf<String>()
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql", args)).use { c ->
            val column = c.getColumnIndexOrThrow("detail")
            while (c.moveToNext()) details += c.getString(column)
        }
        return details
    }

    private fun assertPlan(name: String, details: List<String>, vararg expected: String) {
        val fullScan = Regex("^SCAN( TABLE)? (reel_views|share_events|share_recipients|r|s)$")
        assertTrue("$name recorre una tabla sin índice: $details", details.none { fullScan.matches(it.trim()) })
        for (e in expected) assertTrue("$name debería usar «$e»: $details", details.any { e in it })
    }

    @Test
    fun `las agregaciones por rango de días solo leen el índice cubriente`() {
        val cover = "USING COVERING INDEX $COVER"
        assertPlan("VIEWS_BY_DAY", plan(Queries.VIEWS_BY_DAY, 1, 2), cover)
        assertPlan("VIEWS_BY_HOUR", plan(Queries.VIEWS_BY_HOUR, 1, 2), cover)
        assertPlan("VIEWS_BY_PLATFORM", plan(Queries.VIEWS_BY_PLATFORM, 1, 2), cover)
        assertPlan("VIEWS_BY_CATEGORY", plan(Queries.VIEWS_BY_CATEGORY, 1, 2), cover)
        assertPlan("TOP_CREATORS", plan(Queries.TOP_CREATORS, 1, 2, 10), cover)
        assertPlan("TOTALS_UNTIL", plan(Queries.TOTALS_UNTIL, 1, 2, 870), cover)
        assertPlan(
            "SAME_WEEKDAY_BASELINE",
            plan(Queries.SAME_WEEKDAY_BASELINE.replace("(:days)", "(10, 17, 24)"), 870, 8),
            cover,
        )
        assertPlan("ALL_TOTALS", plan(Queries.ALL_TOTALS), "USING COVERING INDEX")
    }

    @Test
    fun `los recuentos por día usan el índice estrecho de día`() {
        assertPlan("COUNTS_BY_DAY", plan(Queries.COUNTS_BY_DAY, 1, 2), "USING COVERING INDEX index_reel_views_day (")
        assertPlan("FIRST_DAY", plan(Queries.FIRST_DAY), "USING COVERING INDEX index_reel_views_day")
    }

    @Test
    fun `los compartidos usan sus índices`() {
        assertPlan("SHARES_BY_DAY", plan(Queries.SHARES_BY_DAY, 1, 2), "index_share_events_day")
        assertPlan("SHARE_COUNT", plan(Queries.SHARE_COUNT, 1, 2), "index_share_events_day")
        assertPlan("SHARES_UNTIL", plan(Queries.SHARES_UNTIL, 1, 2, 870), "index_share_events_day")
    }

    @Test
    fun `a quién envías solo lee su índice cubriente`() {
        assertPlan(
            "TOP_RECIPIENTS",
            plan(Queries.TOP_RECIPIENTS, 1, 2, 10),
            "USING COVERING INDEX index_share_recipients_day_recipient_platform",
        )
    }

    @Test
    fun `recientes y exportación van por startedAt sin ordenar en memoria`() {
        val recent = plan(Queries.RECENT, 100)
        assertPlan("RECENT", recent, "USING INDEX index_reel_views_startedAt", "index_share_events_reelId")
        assertTrue("RECENT no debería ordenar aparte: $recent", recent.none { "TEMP B-TREE" in it })
        val export = plan(Queries.EXPORT_PAGE, 0L, 0L, 500)
        assertPlan("EXPORT_PAGE", export, "USING INDEX index_reel_views_startedAt (startedAt>?)", "index_share_events_reelId")
        assertTrue("EXPORT_PAGE no debería ordenar aparte: $export", export.none { "TEMP B-TREE" in it })
    }

    private companion object {
        const val COVER = "index_reel_views_day_platform_category_minuteOfDay_durationMs_creator"
    }
}
