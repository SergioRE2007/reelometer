package com.reelometro.app.data.export

import android.app.Application
import com.reelometro.app.data.AppDatabase
import com.reelometro.app.data.Category
import com.reelometro.app.data.CsvImportOutcome
import com.reelometro.app.data.CsvRead
import com.reelometro.app.data.MADRID
import com.reelometro.app.data.ManualTicker
import com.reelometro.app.data.MutableClock
import com.reelometro.app.data.NativeSqliteRule
import com.reelometro.app.data.Platform
import com.reelometro.app.data.ROBOLECTRIC_SDK
import com.reelometro.app.data.ReelRepository
import com.reelometro.app.data.ReelRobolectricRunner
import com.reelometro.app.data.UserDataPart
import com.reelometro.app.data.at
import com.reelometro.app.data.inMemoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate

/**
 * Exportar → BORRAR TODO → importar, con Room de verdad: los totales por día, plataforma y categoría, los
 * creadores, los compartidos y los envíos vuelven igual. Y AÑADIR dos veces no duplica, SUSTITUIR TODO
 * deja solo lo del fichero y un fallo a mitad no deja nada a medias.
 */
@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class CsvImportRoundTripTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: ReelRepository
    private val part = FakePart()
    private val day = LocalDate.of(2026, 11, 3)

    /** Una parte de los datos del usuario que apunta lo que recibe (como el pasaporte). */
    private class FakePart : UserDataPart {
        override val blockTitle = "FALSO"
        val imported = mutableListOf<CsvBlock?>()
        var cleared = 0

        override suspend fun exportCsv(out: OutputStream) {
            out.write("\r\nFALSO\r\nclave;valor\r\nnivel;3\r\n".toByteArray())
        }

        override suspend fun importBlock(block: CsvBlock?) {
            imported += block
        }

        override suspend fun clearAll() {
            cleared++
        }
    }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = ReelRepository(
            db, clock = MutableClock(at(day, 23, 0)), ticker = ManualTicker(), throttleMs = 0, userData = { listOf(part) },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        var id = 1L
        suspend fun video(d: LocalDate, h: Int, m: Int, s: Int, p: Platform, ms: Long, creator: String?, cat: Category, shares: Int = 0, extraMs: Long = 0) {
            val start = at(d, h, m, second = s) + 123 + extraMs // con milisegundos: inicio_ms los conserva
            val vid = id++
            repository.recordStart(vid, p, start)
            repository.recordDuration(vid, ms)
            repository.recordInfo(vid, creator, "descripción de $creator", cat)
            repeat(shares) { repository.recordShare(p, start + 500, vid) }
        }
        video(day.minusDays(2), 9, 0, 1, Platform.TIKTOK, 12_345, "=gato", Category.HUMOR, shares = 2)
        video(day.minusDays(2), 23, 59, 59, Platform.INSTAGRAM, 8_017, "@cuenta", Category.COMIDA)
        video(day.minusDays(1), 0, 0, 3, Platform.YOUTUBE, 61_999, null, Category.OTROS)
        video(day, 14, 37, 5, Platform.TIKTOK, 1_049, "michi", Category.BELLEZA, shares = 1)
        video(day, 14, 37, 5, Platform.INSTAGRAM, 2_000, "michi", Category.BELLEZA) // mismo segundo, otra app
        // Como en un móvil real: un reel pasado rápido y el siguiente, en la misma app y el mismo segundo.
        video(day, 18, 0, 0, Platform.INSTAGRAM, 700, "rapido", Category.OTROS)
        video(day, 18, 0, 0, Platform.INSTAGRAM, 9_636, "siguiente", Category.OTROS, extraMs = 700)
        repository.recordShare(Platform.TIKTOK, at(day, 15, 0), null) // compartido sin vídeo
        repository.recordRecipients(Platform.INSTAGRAM, at(day, 14, 37, second = 9), 5, listOf("Los del piso", "Ana"))
    }

    /** Todo lo que tiene que volver igual. */
    private suspend fun snapshot(): List<Any> {
        val stats = db.statsDao()
        val all = Long.MIN_VALUE to Long.MAX_VALUE
        return listOf(
            stats.viewsByDay(all.first, all.second),
            stats.viewsByPlatform(all.first, all.second),
            stats.viewsByCategory(all.first, all.second),
            stats.topCreators(all.first, all.second, 50),
            stats.sharesByDay(all.first, all.second),
            stats.shareTotal(),
            stats.topRecipients(all.first, all.second, 50),
            db.reelDao().exportPage(Long.MIN_VALUE, Long.MIN_VALUE, 100).map { listOf(it.platform, it.startedAt, it.creator, it.caption, it.shares) },
        )
    }

    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { repository.exportCsv(it) }.toByteArray()

    private suspend fun import(bytes: ByteArray, mode: ImportMode): ImportResult {
        val outcome = repository.importCsv(ByteArrayInputStream(bytes), mode)
        assertTrue("$outcome", outcome is CsvImportOutcome.Done)
        return (outcome as CsvImportOutcome.Done).result
    }

    @Test
    fun `ida y vuelta exacta con inicio_ms`() = runBlocking {
        seed()
        val before = snapshot()
        val durations = db.reelDao().exportPage(Long.MIN_VALUE, Long.MIN_VALUE, 100).map { it.durationMs }
        val bytes = export()
        repository.clearAll()
        assertEquals(0, db.statsDao().allTotals().count)

        val result = import(bytes, ImportMode.ADD)

        assertEquals(7, result.imported)
        assertEquals(0, result.alreadyPresent)
        assertEquals(0, result.badLines)
        assertEquals(before, snapshot())
        assertEquals(durations, db.reelDao().exportPage(Long.MIN_VALUE, Long.MIN_VALUE, 100).map { it.durationMs })
        assertEquals("nivel", part.imported.single()!!.rows.single()[0])
    }

    @Test
    fun `ida y vuelta sin inicio_ms (CSV de antes de la 1_6_0), duraciones con margen de 50 ms`() = runBlocking {
        seed()
        val before = snapshot()
        val durations = db.reelDao().exportPage(Long.MIN_VALUE, Long.MIN_VALUE, 100).map { it.durationMs }
        val old = stripNewColumns(String(export(), Charsets.UTF_8))
        repository.clearAll()

        val result = import(old.toByteArray(Charsets.UTF_8), ImportMode.ADD)

        assertEquals(7, result.imported)
        // Sin `inicio_ms` ni `duración_ms` se pierden los milisegundos (del inicio y de la duración, que va en
        // décimas); los recuentos por día, app, categoría y creador, los compartidos y los envíos cuadran.
        assertEquals(withoutMs(before), withoutMs(snapshot()))
        val restored = db.reelDao().exportPage(Long.MIN_VALUE, Long.MIN_VALUE, 100).map { it.durationMs }
        durations.zip(restored).forEach { (a, b) -> assertTrue("$a vs $b", kotlin.math.abs(a - b) <= 50) }

        // Otra vez el mismo CSV viejo (al segundo): por recuento, ya estaban todos, también los dos del mismo segundo.
        val again = import(old.toByteArray(Charsets.UTF_8), ImportMode.ADD)
        assertEquals(0, again.imported)
        assertEquals(7, again.alreadyPresent)
    }

    /** La foto de [snapshot] sin tiempos ni instantes: solo recuentos, nombres y compartidos. */
    private fun withoutMs(snapshot: List<Any>): List<Any> = snapshot.dropLast(1).map { part ->
        if (part !is List<*>) {
            part
        } else {
            part.map { row ->
                when (row) {
                    is com.reelometro.app.data.DayRow -> row.copy(totalMs = 0)
                    is com.reelometro.app.data.GroupRow -> row.copy(totalMs = 0)
                    is com.reelometro.app.data.CreatorRow -> row.copy(totalMs = 0)
                    else -> row
                }
            }
        }
    } + listOf((snapshot.last() as List<*>).map { (it as List<*>).filterIndexed { i, _ -> i != 1 } })

    /** Quita `inicio_ms`, `zona` y `duración_ms` de los vídeos e `inicio_ms` de los bloques, como los CSV viejos. */
    private fun stripNewColumns(text: String): String = text.split("\r\n").joinToString("\r\n") { line ->
        when {
            line.endsWith(";inicio_ms;zona;duración_ms") -> line.removeSuffix(";inicio_ms;zona;duración_ms")
            line.startsWith("20") && line.count { it == ';' } >= 9 -> line.substringBeforeLast(';').substringBeforeLast(';').substringBeforeLast(';')
            line.endsWith(";inicio_ms") -> line.removeSuffix(";inicio_ms")
            line.startsWith("20") -> line.substringBeforeLast(';')
            else -> line
        }
    }

    @Test
    fun `importar dos veces el mismo fichero no añade nada`() = runBlocking {
        seed()
        val bytes = export()
        val before = snapshot()

        val result = import(bytes, ImportMode.ADD)

        assertEquals(0, result.imported)
        assertEquals(7, result.alreadyPresent)
        assertEquals(before, snapshot())
        val preview = (repository.readCsv(ByteArrayInputStream(bytes)) as CsvRead.Ready).preview
        assertEquals(7, preview.alreadyPresent)
        assertEquals(0, preview.newVideos)
    }

    @Test
    fun `SUSTITUIR TODO deja solo lo del fichero y borra antes las partes`() = runBlocking {
        seed()
        val bytes = export()
        val before = snapshot()
        repository.recordStart(99, Platform.TIKTOK, at(day, 22, 0))
        repository.recordShare(Platform.TIKTOK, at(day, 22, 1), 99)

        val result = import(bytes, ImportMode.REPLACE)

        assertEquals(7, result.imported)
        assertEquals(before, snapshot())
        assertEquals(1, part.cleared)
    }

    @Test
    fun `un fallo a mitad no deja nada a medias`() = runBlocking {
        seed()
        val bytes = export()
        val before = snapshot()
        repository.importFault = { throw IllegalStateException("fallo simulado") }

        for (mode in ImportMode.entries) {
            try {
                repository.importCsv(ByteArrayInputStream(bytes), mode)
                fail("tenía que fallar ($mode)")
            } catch (_: IllegalStateException) {
                // esperado
            }
            assertEquals(before, snapshot())
        }
        repository.clearAll()
        try {
            repository.importCsv(ByteArrayInputStream(bytes), ImportMode.ADD)
            fail("tenía que fallar")
        } catch (_: IllegalStateException) {
            // esperado
        }
        assertEquals(0, db.statsDao().allTotals().count)
        assertEquals(0, db.statsDao().shareTotal())
        assertTrue(part.imported.isEmpty())
    }

    @Test
    fun `los ids no chocan con los que hay y siguen el esquema del detector`() = runBlocking {
        val start = at(day, 10, 0)
        repository.recordStart(start * 1000, Platform.TIKTOK, start)
        val csv = "fecha_hora_local;plataforma;duración_s;creador;categoría;descripción;compartido;inicio_ms\r\n" +
            "2026-11-03T10:00:00;YouTube Shorts;1,0;;;;0;$start\r\n"
        val result = import(csv.toByteArray(), ImportMode.ADD)
        assertEquals(1, result.imported)
        val ids = db.reelDao().exportPage(Long.MIN_VALUE, Long.MIN_VALUE, 10).map { it.id }
        assertEquals(listOf(start * 1000, start * 1000 + 1), ids.sorted())
    }

    @Test
    fun `los CSV de versiones viejas se importan`() = runBlocking {
        for (name in listOf("v1_0_0.csv", "v1_1_0.csv", "v1_2_0.csv")) {
            repository.clearAll()
            val bytes = requireNotNull(javaClass.getResourceAsStream("/import/$name")).use { it.readBytes() }
            val result = import(bytes, ImportMode.ADD)
            assertEquals(name, 3, result.imported)
            assertEquals(name, 3, db.statsDao().shareTotal())
        }
        assertEquals(2, db.statsDao().topRecipients(Long.MIN_VALUE, Long.MAX_VALUE, 10).size)
    }
}
