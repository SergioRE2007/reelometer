package com.reelometro.app.data.export

import android.app.Application
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
import com.reelometro.app.data.ShareEvent
import com.reelometro.app.data.ShareRecipient
import com.reelometro.app.data.at
import com.reelometro.app.data.inMemoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(ReelRobolectricRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class CsvExporterTest {
    @get:Rule(order = 0)
    val nativeSqlite = NativeSqliteRule()


    private lateinit var db: AppDatabase
    private val day = LocalDate.of(2026, 11, 3)
    private val header = "fecha_hora_local;plataforma;duración_s;creador;categoría;descripción;compartido;inicio_ms;zona;duración_ms"
    private val zone = MADRID.id

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(
        id: Long,
        startedAt: Long,
        platform: Platform,
        ms: Long,
        creator: String? = null,
        caption: String? = null,
        category: Category = Category.OTROS,
    ) {
        val stamp = LocalStamp.of(startedAt, MADRID)
        db.reelDao().insert(
            ReelView(id, platform.name, startedAt, ms, creator, caption, category.name, stamp.day, stamp.minuteOfDay),
        )
    }

    private suspend fun share(at: Long, reelId: Long?) {
        val stamp = LocalStamp.of(at, MADRID)
        db.reelDao().insertShare(ShareEvent(platform = "TIKTOK", at = at, day = stamp.day, minuteOfDay = stamp.minuteOfDay, reelId = reelId))
    }

    private fun lines(bytes: ByteArray): List<String> {
        val text = String(bytes, Charsets.UTF_8)
        assertTrue("falta el BOM", text.startsWith("﻿"))
        return text.removePrefix("﻿").removeSuffix("\r\n").split("\r\n")
    }

    @Test
    fun `exporta en orden cronológico, por páginas, con BOM, punto y coma y compartidos`() = runBlocking {
        val t = at(day, 14, 37, second = 5)
        insert(10, t, Platform.TIKTOK, 12_345, "michi_filosofo", "Hola; \"mundo\"\nadiós", Category.HUMOR)
        insert(11, t, Platform.INSTAGRAM, 8_000, "@raro", "=SUMA(A1)") // mismo instante: desempata el id
        insert(12, t + 1_000, Platform.YOUTUBE, 1_000)
        insert(13, t + 2_000, Platform.TIKTOK, 59_960, caption = "Tortilla #receta", category = Category.COMIDA)
        insert(9, t - 5_000, Platform.TIKTOK, 20_000) // insertado el último pero es el primero
        share(t + 1_000, 10)
        share(t + 2_000, 10)
        share(t + 3_000, null) // sin vídeo: va en su bloque

        val out = ByteArrayOutputStream()
        val count = CsvExporter(db.reelDao(), { MADRID }, pageSize = 2).export(out)

        assertEquals(5, count)
        val bytes = out.toByteArray()
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
        val rows = lines(bytes)
        assertEquals(
            listOf(
                header,
                "2026-11-03T14:37:00;TikTok;20,0;;Sin clasificar;;0;${t - 5_000};$zone;20000",
                "2026-11-03T14:37:05;TikTok;12,3;michi_filosofo;Humor;\"Hola; \"\"mundo\"\"\nadiós\";2;$t;$zone;12345",
                "2026-11-03T14:37:05;Instagram Reels;8,0;'@raro;Sin clasificar;'=SUMA(A1);0;$t;$zone;8000",
                "2026-11-03T14:37:06;YouTube Shorts;1,0;;Sin clasificar;;0;${t + 1_000};$zone;1000",
                "2026-11-03T14:37:07;TikTok;60,0;;Comida;Tortilla #receta;0;${t + 2_000};$zone;59960",
                "",
                "COMPARTIDOS SIN VÍDEO",
                "fecha_hora_local;plataforma;inicio_ms",
                "2026-11-03T14:37:08;TikTok;${t + 3_000}",
            ),
            rows,
        )
    }

    @Test
    fun `los envios de Instagram salen en su bloque detras de los videos`() = runBlocking {
        val t = at(day, 14, 37)
        insert(1, t, Platform.INSTAGRAM, 8_000)
        val dao = db.reelDao()
        val stamp = LocalStamp.of(t + 5_000, MADRID)
        dao.insertRecipients(
            listOf(
                ShareRecipient(platform = "INSTAGRAM", at = t + 5_000, day = stamp.day, minuteOfDay = stamp.minuteOfDay, recipient = "Los del piso", reelId = 1),
                ShareRecipient(platform = "INSTAGRAM", at = t + 6_000, day = stamp.day, minuteOfDay = stamp.minuteOfDay, recipient = "=Ana", reelId = 1),
            ),
        )
        val out = ByteArrayOutputStream()
        assertEquals(1, CsvExporter(dao, { MADRID }).export(out))
        assertEquals(
            listOf(
                header,
                "2026-11-03T14:37:00;Instagram Reels;8,0;;Sin clasificar;;0;$t;$zone;8000",
                "",
                "ENVÍOS",
                "fecha_hora_local;plataforma;destinatario;inicio_ms",
                "2026-11-03T14:37:05;Instagram Reels;Los del piso;${t + 5_000}",
                "2026-11-03T14:37:06;Instagram Reels;'=Ana;${t + 6_000}",
            ),
            lines(out.toByteArray()),
        )
    }

    @Test
    fun `sin datos queda solo la cabecera`() = runBlocking {
        val out = ByteArrayOutputStream()
        assertEquals(0, CsvExporter(db.reelDao(), { MADRID }).export(out))
        assertEquals(listOf(header), lines(out.toByteArray()))
    }

    @Test
    fun `una página exacta no repite ni pierde filas`() = runBlocking {
        val t = at(day, 9, 0)
        for (i in 1..6) insert(i.toLong(), t + (i / 2) * 1_000L, Platform.TIKTOK, 1_000)
        val out = ByteArrayOutputStream()
        assertEquals(6, CsvExporter(db.reelDao(), { MADRID }, pageSize = 3).export(out))
        assertEquals(7, lines(out.toByteArray()).size)
    }

    @Test
    fun `el repositorio exporta con la zona del reloj y no cierra el flujo`() = runBlocking {
        val repository = ReelRepository(db, clock = MutableClock(at(day, 20, 0)), ticker = ManualTicker())
        repository.recordStart(1, Platform.YOUTUBE, at(day, 0, 30))
        repository.recordDuration(1, 15_000)
        var closed = false
        val out = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
                super.close()
            }
        }
        assertEquals(1, repository.exportCsv(out))
        assertFalse(closed)
        assertEquals(
            "2026-11-03T00:30:00;YouTube Shorts;15,0;;Sin clasificar;;0;${at(day, 0, 30)};$zone;15000",
            lines(out.toByteArray())[1],
        )
    }
}
