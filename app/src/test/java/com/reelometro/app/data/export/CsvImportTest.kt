package com.reelometro.app.data.export

import com.reelometro.app.data.Category
import com.reelometro.app.data.MADRID
import com.reelometro.app.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Lectura del CSV de importación, sin Android ni base de datos. */
class CsvImportTest {

    private val header = "fecha_hora_local;plataforma;duración_s;creador;categoría;descripción;compartido"

    private fun ok(text: String, zone: ZoneId = MADRID): ParsedCsv {
        val parse = CsvImport.parse(text.toByteArray(Charsets.UTF_8), zone)
        assertTrue("esperaba Ok y fue $parse", parse is CsvParse.Ok)
        return (parse as CsvParse.Ok).csv
    }

    private fun failed(bytes: ByteArray): ImportError =
        (CsvImport.parse(bytes, MADRID) as CsvParse.Failed).error

    private fun local(text: String, zone: ZoneId = MADRID): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    // ------------------------------------------------------------------ CsvReader

    @Test
    fun `comillas con separador, comillas dobles y saltos de línea`() {
        val rows = CsvReader.rows("a;\"b;c\";\"d \"\"e\"\"\";\"f\r\ng\"\r\nx;y\n")
        assertEquals(listOf(listOf("a", "b;c", "d \"e\"", "f\r\ng"), listOf("x", "y")), rows)
    }

    @Test
    fun `fin de línea CR, LF o CRLF y última línea sin salto`() {
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("d")), CsvReader.rows("a\rb\nc\r\nd"))
    }

    @Test
    fun `separador coma si la cabecera no tiene punto y coma`() {
        assertEquals(',', CsvReader.separatorOf("fecha_hora_local,plataforma\n1;2"))
        assertEquals(';', CsvReader.separatorOf("fecha_hora_local;plataforma,x"))
    }

    @Test
    fun `BOM fuera y Windows-1252 si no es UTF-8`() {
        assertEquals("año", CsvReader.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "año".toByteArray()))
        assertEquals("año", CsvReader.decode(byteArrayOf('a'.code.toByte(), 0xF1.toByte(), 'o'.code.toByte())))
    }

    @Test
    fun `bloques con título, aunque la hoja de cálculo rellene las líneas en blanco`() {
        val doc = CsvReader.document(
            "$header\r\n2026-11-03T14:37:05;TikTok;1,0;;;;0\r\n;;;;;;\r\nENVÍOS;;;;;;\r\nfecha_hora_local;plataforma;destinatario\r\n" +
                "2026-11-03T14:37:06;Instagram Reels;Ana\r\n\r\nPASAPORTE\r\ntipo;elemento\r\nnivel;PLATA\r\n\r\n\r\n",
        )
        assertEquals(1, doc.videos!!.rows.size)
        assertEquals(listOf("ENVÍOS", "PASAPORTE"), doc.blocks.map { it.title })
        assertEquals(listOf("Ana"), doc.block("envios")!!.rows.map { it[2] })
        assertEquals("PLATA", doc.block("PASAPORTE")!!.field(listOf("nivel", "PLATA"), "elemento"))
    }

    @Test
    fun `una fila de vídeo rota con solo la fecha no se come los bloques`() {
        val doc = CsvReader.document("$header\r\n2026-11-03T14:37:05\r\n2026-11-03T14:37:06;TikTok;1,0;;;;0\r\n")
        assertEquals(2, doc.videos!!.rows.size)
        assertTrue(doc.blocks.isEmpty())
    }

    // ------------------------------------------------------------------ CsvImport

    @Test
    fun `formato actual con inicio_ms, zona y duración_ms exactos`() {
        val start = 1_762_177_025_123L
        val csv = ok(
            "${Csv.BOM}$header;inicio_ms;zona;duración_ms\r\n" +
                "2026-11-03T14:37:05;TikTok;12,3;michi;Humor;hola;2;$start;America/Bogota;12345\r\n",
        )
        val v = csv.videos.single()
        assertEquals(start, v.startedAt)
        assertEquals(12_345, v.durationMs)
        assertEquals(ZoneId.of("America/Bogota"), v.zone)
        assertEquals(2, v.shares)
        assertEquals(Category.HUMOR, v.category)
    }

    @Test
    fun `sin inicio_ms la hora local va con la zona dada, y coma o punto decimal`() {
        val csv = ok("$header\n2026-11-03T14:37:05;TikTok;12,3;;;;\n2026-11-03T14:37:06;TikTok;7.25;;;;\n")
        assertEquals(local("2026-11-03T14:37:05"), csv.videos[0].startedAt)
        assertEquals(12_300, csv.videos[0].durationMs)
        assertEquals(7_250, csv.videos[1].durationMs)
        assertEquals(0, csv.videos[0].shares)
        assertNull(csv.videos[0].zone)
    }

    @Test
    fun `fechas como las deja una hoja de cálculo`() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 37, 5), CsvImport.localDateTime("03/11/2026 14:37:05"))
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 7), CsvImport.localDateTime("3/11/2026 9:07"))
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 37), CsvImport.localDateTime("2026-11-03 14:37"))
        assertNull(CsvImport.localDateTime("ayer por la tarde"))
    }

    @Test
    fun `cambio de hora - la hora que no existe avanza y la repetida toma el primer desfase`() {
        // 29 mar 2026: en Madrid se salta de 02:00 a 03:00. 25 oct 2026: 02:30 ocurre dos veces.
        val gap = CsvImport.instant(null, "2026-03-29T02:30:00", MADRID)!!
        assertEquals(local("2026-03-29T03:30:00"), gap)
        val overlap = CsvImport.instant(null, "2026-10-25T02:30:00", MADRID)!!
        assertEquals(LocalDateTime.parse("2026-10-25T02:30:00").atOffset(java.time.ZoneOffset.ofHours(2)).toInstant().toEpochMilli(), overlap)
    }

    @Test
    fun `deshace la protección contra fórmulas y recorta`() {
        assertEquals("=SUMA(A1)", CsvImport.text("'=SUMA(A1)", 300))
        assertEquals("@raro", CsvImport.text("'@raro", 64))
        assertEquals("'hola", CsvImport.text("'hola", 64))
        assertNull(CsvImport.text("  ", 64))
        assertEquals("abc", CsvImport.text("abcdef", 3))
    }

    @Test
    fun `plataforma por nombre visible, corto o del enum, y categoría desconocida a Sin clasificar`() {
        assertEquals(Platform.INSTAGRAM, CsvImport.platform("instagram reels"))
        assertEquals(Platform.YOUTUBE, CsvImport.platform("Shorts"))
        assertEquals(Platform.TIKTOK, CsvImport.platform("TIKTOK"))
        assertNull(CsvImport.platform("Snapchat"))
        assertEquals(Category.BELLEZA, CsvImport.category("Moda y belleza"))
        assertEquals(Category.TECNOLOGIA, CsvImport.category("tecnologia"))
        assertEquals(Category.OTROS, CsvImport.category("Cosas raras"))
    }

    @Test
    fun `plataforma desconocida es una línea que se salta y se cuenta`() {
        val lines = (1..9).joinToString("") { "2026-11-03T14:37:0$it;TikTok;1,0;;;;0\n" }
        val csv = ok("$header\n${lines}2026-11-03T15:00:00;Snapchat;1,0;;;;0\n")
        assertEquals(9, csv.videos.size)
        assertEquals(1, csv.badLines)
    }

    @Test
    fun `más del 20 por ciento de líneas rotas no importa nada`() {
        val text = "$header\n2026-11-03T14:37:05;TikTok;1,0;;;;0\n2026-11-03T14:37:06;Snapchat;1,0;;;;0\n"
        assertEquals(ImportError.TOO_MANY_BAD, failed(text.toByteArray()))
    }

    @Test
    fun `vacío, cabecera ajena y demasiado grande`() {
        assertEquals(ImportError.EMPTY, failed(ByteArray(0)))
        assertEquals(ImportError.EMPTY, failed("${Csv.BOM}\r\n\r\n".toByteArray()))
        assertEquals(ImportError.EMPTY, failed("$header\r\n".toByteArray()))
        assertEquals(ImportError.NOT_REELOMETRO, failed("nombre;apellidos\nAna;Pérez\n".toByteArray()))
        assertEquals(ImportError.TOO_LARGE, failed(ByteArray((CsvImport.MAX_BYTES + 1).toInt())))
    }

    @Test
    fun `solo las filas idénticas se quitan - dos vídeos en el mismo segundo son dos, en orden cronológico`() {
        val csv = ok(
            "$header\n2026-11-03T14:37:09;TikTok;1,0;;;;0\n2026-11-03T14:37:05;TikTok;0,7;;;;0\n" +
                "2026-11-03T14:37:05;TikTok;9,6;;;;0\n2026-11-03T14:37:09;TikTok;1,0;;;;0\n",
        )
        assertEquals(
            listOf(local("2026-11-03T14:37:05"), local("2026-11-03T14:37:05"), local("2026-11-03T14:37:09")),
            csv.videos.map { it.startedAt },
        )
        assertEquals(LocalDate.of(2026, 11, 3), csv.from)
    }

    @Test
    fun `lo ya guardado se compara al milisegundo con inicio_ms y por recuento al segundo sin él`() {
        // Como en un móvil real: un reel pasado rápido (0,7 s) y el siguiente empiezan en el mismo segundo.
        val saved = listOf(TimeKey("TIKTOK", 1_000_100), TimeKey("TIKTOK", 1_000_800))
        val exact = listOf(1_000_100L, 1_000_800L, 1_000_900L)
        val (fresh, present) = CsvImport.splitNew(exact, saved, keyOf = { TimeKey("TIKTOK", it) }, exact = { true })
        assertEquals(listOf(1_000_900L), fresh)
        assertEquals(2, present)

        // Sin inicio_ms (al segundo): dos guardados en ese segundo cubren dos del fichero; el tercero es nuevo.
        val seconds = listOf(1_000_000L, 1_000_000L, 1_000_000L, 2_000_000L)
        val (fresh2, present2) = CsvImport.splitNew(seconds, saved, keyOf = { TimeKey("TIKTOK", it) }, exact = { false })
        assertEquals(listOf(1_000_000L, 2_000_000L), fresh2)
        assertEquals(2, present2)

        // Otra app u otro destinatario no cuentan.
        val (fresh3, _) = CsvImport.splitNew(listOf(1_000_100L), saved, keyOf = { TimeKey("YOUTUBE", it) }, exact = { true })
        assertEquals(1, fresh3.size)
    }

    @Test
    fun `separado por comas y con columnas en otro orden`() {
        val csv = ok("plataforma,fecha_hora_local,duración_s\nTikTok,2026-11-03T14:37:05,\"1,5\"\n")
        assertEquals(1_500, csv.videos.single().durationMs)
    }

    // ------------------------------------------------------------------ compatibilidad (ficheros de versiones viejas)

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/import/$name")) { "falta $name" }.use { it.readBytes() }

    private fun parseFixture(name: String): ParsedCsv = (CsvImport.parse(fixture(name), MADRID) as CsvParse.Ok).csv

    @Test
    fun `CSV de la 1_0_0`() {
        val csv = parseFixture("v1_0_0.csv")
        assertEquals(3, csv.videos.size)
        assertEquals(0, csv.badLines)
        val (a, b, c) = csv.videos
        assertEquals(local("2026-03-03T09:15:02"), a.startedAt)
        assertEquals("Un gato \"serio\"", a.caption)
        assertEquals(1, a.shares)
        assertEquals("@cuenta_rara", b.creator)
        assertEquals("=1+1", b.caption)
        assertEquals(Platform.YOUTUBE, c.platform)
        assertEquals("Receta; rápida\nsegunda línea", c.caption)
        assertEquals(59_900, c.durationMs)
        assertEquals(Category.COMIDA, c.category)
        assertTrue(csv.blocks.isEmpty())
    }

    @Test
    fun `CSV de la 1_1_0 con PASAPORTE`() {
        val csv = parseFixture("v1_1_0.csv")
        assertEquals(3, csv.videos.size)
        assertEquals(listOf(PassportCsv.TITLE), csv.blocks.map { it.title })
        assertEquals(5, csv.blocks.single().rows.size)
    }

    @Test
    fun `CSV de la 1_2_0 a la 1_5_0 con ENVÍOS y PASAPORTE`() {
        val csv = parseFixture("v1_2_0.csv")
        assertEquals(3, csv.videos.size)
        assertEquals(listOf("Grupo de ejemplo", "=Persona"), csv.recipients.map { it.recipient })
        assertEquals(local("2026-03-03T09:15:25"), csv.recipients.first().at)
        assertEquals(listOf(PassportCsv.TITLE), csv.blocks.map { it.title })
    }
}
