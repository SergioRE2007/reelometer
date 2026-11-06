package com.reelometro.app.data.game

import android.app.Application
import androidx.sqlite.db.SimpleSQLiteQuery
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.Category
import com.reelometro.app.data.LocalStamp
import com.reelometro.app.data.MADRID
import com.reelometro.app.data.ManualTicker
import com.reelometro.app.data.MutableClock
import com.reelometro.app.data.NativeSqliteRule
import com.reelometro.app.data.Platform
import com.reelometro.app.data.ROBOLECTRIC_SDK
import com.reelometro.app.data.ReelRepository
import com.reelometro.app.data.ReelRobolectricRunner
import com.reelometro.app.data.ReelView
import com.reelometro.app.data.at
import com.reelometro.app.data.inMemoryDatabase
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.GameHistory
import com.reelometro.app.game.PassportMemory
import com.reelometro.app.game.PassportMemoryStore
import com.reelometro.app.game.StampId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** Memoria del pasaporte en memoria para los tests con Room. */
private class RoomTestMemoryStore : PassportMemoryStore {
    val state = MutableStateFlow(PassportMemory())

    override val memory: Flow<PassportMemory> = state

    override suspend fun update(transform: (PassportMemory) -> PassportMemory) {
        state.value = transform(state.value)
    }
}

/** SQL del pasaporte sobre Room (consultas, planes) y su paso por el CSV y BORRAR TODO. */
@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class RoomGameHistoryTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()

    private lateinit var db: AppDatabase
    private val monday = LocalDate.of(2026, 11, 2)
    private var nextId = 1L

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun views(
        date: LocalDate,
        hour: Int,
        minute: Int,
        platform: Platform = Platform.TIKTOK,
        category: Category = Category.OTROS,
        n: Int = 1,
    ): List<ReelView> = List(n) {
        val startedAt = at(date, hour, minute)
        val stamp = LocalStamp.of(startedAt, MADRID)
        ReelView(nextId++, platform.name, startedAt, 1_000, null, null, category.name, stamp.day, stamp.minuteOfDay)
    }

    private fun day(n: Int): LocalDate = monday.plusDays(n.toLong())

    /** Como en la app (`LivePassport` calcula fuera del hilo principal): nunca en el hilo del test. */
    private suspend fun load(today: LocalDate, skip: Set<StampId>): GameHistory =
        withContext(Dispatchers.Default) { RoomGameHistory(db).load(today, skip) }

    private suspend fun seedIronicWeek() {
        val all = views(day(0), 10, 0, category = Category.HUMOR) +
            views(day(1), 10, 0, category = Category.DEPORTE) +
            views(day(1), 10, 5, Platform.INSTAGRAM) +
            views(day(2), 3, 20, category = Category.COMIDA) +
            views(day(3), 11, 0, Platform.YOUTUBE, Category.MUSICA) +
            views(day(3), 11, 0, category = Category.HUMOR) +
            views(day(3), 11, 1, Platform.INSTAGRAM, Category.HUMOR) +
            views(day(4), 13, 0, category = Category.BAILE) +
            views(day(5), 14, 0, n = 101) +
            views(day(6), 15, 0, n = 100)
        db.reelDao().insertAll(all)
    }

    @Test
    fun `sin vídeos el historial está vacío`() = runBlocking {
        assertEquals(GameHistory(), load(monday, emptySet()))
    }

    @Test
    fun `recuentos por día y sellos irónicos con SQL`() = runBlocking {
        seedIronicWeek()
        val history = load(day(6), emptySet())
        assertEquals(
            mapOf(day(0) to 1, day(1) to 2, day(2) to 1, day(3) to 3, day(4) to 1, day(5) to 101, day(6) to 100),
            history.counts,
        )
        assertEquals(day(3), history.threePlatformsOn)
        assertEquals(day(2), history.earlyBirdOn)
        assertEquals(day(4), history.fiveCategoriesOn)
        assertEquals(day(5), history.marathonOn)
        // Hasta hoy: lo de después no entra en los recuentos.
        assertEquals(setOf(day(0), day(1), day(2)), load(day(2), emptySet()).counts.keys)
    }

    @Test
    fun `los sellos ya guardados no se consultan`() = runBlocking {
        seedIronicWeek()
        val skip = setOf(StampId.MADRUGADOR, StampId.TRES_PLATAFORMAS, StampId.CINCO_CATEGORIAS, StampId.MARATON)
        val history = load(day(6), skip)
        assertNull(history.threePlatformsOn)
        assertNull(history.earlyBirdOn)
        assertNull(history.fiveCategoriesOn)
        assertNull(history.marathonOn)
        assertEquals(7, history.counts.size)
    }

    @Test
    fun `las consultas del pasaporte no recorren la tabla`() {
        val fullScan = Regex("^SCAN( TABLE)? reel_views$")
        mapOf(
            "FIRST_DAY_WITH_PLATFORMS" to plan(GameQueries.FIRST_DAY_WITH_PLATFORMS, 3),
            "FIRST_DAY_BETWEEN_MINUTES" to plan(GameQueries.FIRST_DAY_BETWEEN_MINUTES, 180, 299),
            "FIRST_DAY_WITH_BUSY_HOUR" to plan(GameQueries.FIRST_DAY_WITH_BUSY_HOUR, 100),
            "CATEGORY_WEEK_FIRST_DAYS" to plan(GameQueries.CATEGORY_WEEK_FIRST_DAYS),
        ).forEach { (name, details) ->
            assertTrue("$name recorre la tabla: $details", details.none { fullScan.matches(it.trim()) })
        }
    }

    @Test
    fun `el pasaporte real va en el CSV y se borra con BORRAR TODO`() = runBlocking {
        db.reelDao().insertAll((0..6).flatMap { views(day(it), 10, 0) })
        val clock = MutableClock(at(day(7), 20, 0))
        val store = RoomTestMemoryStore()
        val passport = PassportRepository(
            loader = RoomGameHistory(db),
            dailyGoal = flowOf(100),
            store = store,
            clock = clock,
            ticker = ManualTicker(),
            dispatcher = Dispatchers.Default,
            throttleMs = 0,
        )
        val repository = ReelRepository(db, clock = clock, ticker = ManualTicker(), userData = { listOf(passport) })
        passport.recordEvent(GameEvent.WRAPPED_SHARED)

        val out = ByteArrayOutputStream()
        assertEquals(7, repository.exportCsv(out))
        val lines = String(out.toByteArray(), Charsets.UTF_8).removePrefix("﻿").split("\r\n")
        val title = lines.indexOf("PASAPORTE")
        assertEquals("cabecera, 7 vídeos y una línea en blanco: $lines", 9, title)
        assertEquals("", lines[title - 1])
        assertEquals("tipo;elemento;fecha;tope;vídeos;experiencia;clave", lines[title + 1])
        val racha = "sello;${PassportCopy.stampName(StampId.RACHA_7)};2026-11-08;"
        assertTrue("falta $racha en $lines", lines.any { it.startsWith(racha) })
        assertTrue(lines.any { it.startsWith("evento;Resumen compartido;2026-11-09;") })

        repository.clearAll()
        assertEquals(PassportMemory(generation = 1), store.state.value)
        assertEquals(0, passport.snapshot().earnedStamps)
    }

    /** `EXPLAIN QUERY PLAN` con los parámetros en orden de aparición (como `QueryPlanTest`). */
    private fun plan(sql: String, vararg args: Any): List<String> {
        val details = mutableListOf<String>()
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql", args)).use { c ->
            val column = c.getColumnIndexOrThrow("detail")
            while (c.moveToNext()) details += c.getString(column)
        }
        return details
    }
}
