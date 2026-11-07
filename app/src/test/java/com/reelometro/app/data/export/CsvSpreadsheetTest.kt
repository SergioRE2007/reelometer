package com.reelometro.app.data.export

import com.reelometro.app.data.MADRID
import com.reelometro.app.game.ChallengeResult
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.StampId
import com.reelometro.app.game.TravelerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * El CSV que exporta la app después de pasar por una hoja de cálculo (Excel, LibreOffice o Google Sheets en
 * español, y alguna en inglés): cada variante o se importa igual que el original (con la precisión que le quede)
 * o se rechaza con un error; nunca con horas desplazadas, duraciones ×1000 ni duplicados.
 *
 * Con `REELOMETRO_CSV_OUT=<carpeta>` escribe además cada variante en esa carpeta (para probarlas en el emulador).
 */
class CsvSpreadsheetTest {

    // ------------------------------------------------------------------ el CSV original

    private fun ms(local: String, extraMs: Long = 0): Long =
        LocalDateTime.parse(local).atZone(MADRID).toInstant().toEpochMilli() + extraMs

    private class Row(val local: String, val platform: String, val seconds: String, val creator: String, val category: String,
        val caption: String, val shares: Int, val start: Long, val durationMs: Long)

    private val videoRows = listOf(
        Row("2026-11-03T14:37:05", "TikTok", "12,3", "michi", "Humor", "hola; qué tal", 2, ms("2026-11-03T14:37:05", 123), 12_345),
        // Un reel pasado rápido: empieza en el mismo segundo que el anterior.
        Row("2026-11-03T14:37:05", "TikTok", "0,7", "michi", "Humor", "", 0, ms("2026-11-03T14:37:05", 800), 700),
        Row("2026-11-25T21:30:00", "Instagram Reels", "1234,5", "'@cuenta_rara", "Comida", "Receta \"rápida\"\nsegunda línea", 0,
            ms("2026-11-25T21:30:00", 450), 1_234_500),
        Row("2026-11-07T09:05:59", "YouTube Shorts", "59,9", "", "Sin clasificar", "'=1+1", 1, ms("2026-11-07T09:05:59", 999), 59_900),
        Row("2026-11-12T23:59:30", "Instagram Reels", "8,0", "pepa", "Deporte", "gol", 0, ms("2026-11-12T23:59:30", 1), 8_000),
    )

    private val recipientStart = ms("2026-11-03T14:37:10", 250)
    private val orphanStart = ms("2026-11-07T09:10:00", 600)

    /** El fichero tal y como lo escribe [CsvExporter] y [PassportCsv] (BOM, `;`, CRLF). */
    private val original: String = buildString {
        append(Csv.BOM)
        append(Csv.row(CsvExporter.HEADER))
        for (r in videoRows) {
            append(
                Csv.row(
                    listOf(r.local, r.platform, r.seconds, r.creator, r.category, r.caption, r.shares.toString(), r.start.toString(),
                        "Europe/Madrid", r.durationMs.toString()),
                ),
            )
        }
        append(Csv.LINE_END)
        append(Csv.row(listOf(CsvExporter.RECIPIENTS_TITLE)))
        append(Csv.row(CsvExporter.RECIPIENTS_HEADER))
        append(Csv.row(listOf("2026-11-03T14:37:10", "Instagram Reels", "Grupo de ejemplo", recipientStart.toString())))
        append(Csv.LINE_END)
        append(Csv.row(listOf(CsvExporter.ORPHAN_SHARES_TITLE)))
        append(Csv.row(CsvExporter.ORPHAN_SHARES_HEADER))
        append(Csv.row(listOf("2026-11-07T09:10:00", "TikTok", orphanStart.toString())))
        append(Csv.LINE_END)
        append(Csv.row(listOf(PassportCsv.TITLE)))
        append(Csv.row(PassportCsv.HEADER))
        append(Csv.row(listOf("nivel", "PLATA", "", "", "", "420", "PLATA")))
        append(Csv.row(listOf("sello", "Racha de 3", "2026-11-05", "", "", "30", "RACHA_3")))
        append(Csv.row(listOf("reto", "cumplido", "2026-10-19", "1200", "980", "50", "")))
        append(Csv.row(listOf("evento", "Ticket guardado", "2026-11-13", "", "", "", "TICKET_SAVED")))
    }

    private val base: ParsedCsv by lazy { ok(original.toByteArray(Charsets.UTF_8)) }

    // ------------------------------------------------------------------ cómo la hoja de cálculo toca el fichero

    private val titles = setOf(CsvExporter.RECIPIENTS_TITLE, CsvExporter.ORPHAN_SHARES_TITLE, PassportCsv.TITLE)

    /** Las celdas del original (sin BOM), como las ve la hoja de cálculo. */
    private fun grid(): List<List<String>> = CsvReader.rows(original.removePrefix(Csv.BOM), ';')

    /** Cambia cada celda de datos sabiendo su bloque (`""` = vídeos) y su columna. */
    private fun List<List<String>>.cells(f: (block: String, column: String, value: String) -> String): List<List<String>> {
        var block = ""
        var header: List<String>? = null
        return map { row ->
            when {
                row.all { it.isBlank() } -> row
                row[0] in titles && row.drop(1).all { it.isBlank() } -> row.also { block = row[0]; header = null }
                header == null -> row.also { header = row }
                else -> row.mapIndexed { i, v -> f(block, header!!.getOrElse(i) { "" }, v) }
            }
        }
    }

    /** Quita las columnas [names] del bloque de vídeos (cabecera incluida). */
    private fun List<List<String>>.dropVideoColumns(vararg names: String): List<List<String>> {
        val header = first()
        val keep = header.indices.filter { header[it] !in names }
        var inVideos = true
        return map { row ->
            if (row.all { it.isBlank() }) inVideos = false
            if (inVideos) keep.map { row.getOrElse(it) { "" } } else row
        }
    }

    /** Texto con el [separator]: comillas solo si hacen falta o, con [quoteText], en todo lo que no es un número. */
    private fun List<List<String>>.write(
        separator: Char = ';',
        eol: String = "\r\n",
        quoteText: Boolean = false,
        pad: Boolean = false,
    ): String {
        val width = maxOf { it.size }
        return joinToString(eol, postfix = eol) { row ->
            val cells = if (pad) row + List(width - row.size) { "" } else row
            cells.joinToString(separator.toString()) { v ->
                val numeric = v.isNotEmpty() && v.all { it.isDigit() || it in ",.-+E" }
                val needs = v.any { it == separator || it == '"' || it == '\n' || it == '\r' }
                if (needs || (quoteText && v.isNotEmpty() && !numeric)) "\"" + v.replace("\"", "\"\"") + "\"" else v
            }
        }
    }

    private fun String.bytes(charset: Charset = Charsets.UTF_8, bom: Boolean = false): ByteArray =
        (if (bom) Csv.BOM + this else this).toByteArray(charset)

    private val cp1252: Charset = Charset.forName("windows-1252")

    /** Excel con formato General: 1762177025123 → `1,76218E+12` (seis cifras: se pierden los milisegundos y más). */
    private fun scientific(v: String, locale: Locale = ES): String =
        String.format(locale, "%.5E", v.toDouble())

    /** `2026-11-03T14:37:05` con otro [pattern] (lo que escribe la hoja al guardar una fecha que ha reconocido). */
    private fun redate(v: String, pattern: String, locale: Locale = ES): String =
        if (v.length == 19 && v[10] == 'T') LocalDateTime.parse(v).format(DateTimeFormatter.ofPattern(pattern, locale)) else v

    private fun reday(v: String, pattern: String): String =
        if (v.length == 10 && v[4] == '-') LocalDate.parse(v).format(DateTimeFormatter.ofPattern(pattern, ES)) else v

    /** `1234,5` → `1.234,5` y `1234500` → `1.234.500` (formato de número con separador de miles en español). */
    private fun thousands(v: String): String {
        val int = v.substringBefore(',')
        if (int.length <= 3 || !int.all(Char::isDigit)) return v
        val grouped = int.reversed().chunked(3).joinToString(".").reversed()
        return if (',' in v) grouped + "," + v.substringAfter(',') else grouped
    }

    // ------------------------------------------------------------------ comprobaciones

    private fun ok(bytes: ByteArray): ParsedCsv {
        val parse = CsvImport.parse(bytes, MADRID)
        if (parse !is CsvParse.Ok) fail("esperaba Ok y fue $parse")
        return (parse as CsvParse.Ok).csv
    }

    private fun failed(bytes: ByteArray): ImportError {
        val parse = CsvImport.parse(bytes, MADRID)
        if (parse !is CsvParse.Failed) fail("esperaba un error y se importó")
        return (parse as CsvParse.Failed).error
    }

    private data class Video(val platform: String, val at: Long, val durationMs: Long, val creator: String?, val caption: String?,
        val category: String, val shares: Int)

    private fun Long.floor(precision: Long) = Math.floorDiv(this, precision) * precision

    private fun videos(csv: ParsedCsv, precision: Long, durationStep: Long) = csv.videos.map {
        Video(it.platform.name, it.startedAt.floor(precision), (it.durationMs + durationStep / 2) / durationStep * durationStep,
            it.creator, it.caption, it.category.name, it.shares)
    }.sortedBy { it.toString() }

    private fun passport(csv: ParsedCsv) = csv.blocks.firstOrNull { it.title == PassportCsv.TITLE }?.let { PassportCsv.read(it) }

    /**
     * Lo importado es lo del original con la precisión [precision] (al ms, al segundo o al minuto) y las duraciones
     * redondeadas a [durationStep]; cada cosa trae esa precisión y, contra lo ya guardado, no entra nada dos veces.
     */
    private fun assertSame(name: String, bytes: ByteArray, precision: Long, durationStep: Long = 1, bad: Int = 0, zone: Boolean = true) {
        save(name, bytes)
        val csv = ok(bytes)
        assertEquals("$name: líneas rotas", bad, csv.badLines)
        assertEquals("$name: vídeos", videos(base, precision, durationStep), videos(csv, precision, durationStep))
        assertTrue("$name: precisión", csv.videos.all { it.precisionMs == precision })
        if (zone) assertEquals("$name: zona", base.videos.map { it.zone }.toSet(), csv.videos.map { it.zone }.toSet())
        assertEquals("$name: envíos", base.recipients.map { it.recipient to it.at.floor(precision) },
            csv.recipients.map { it.recipient to it.at.floor(precision) })
        assertEquals("$name: compartidos", base.orphanShares.map { it.at.floor(precision) }, csv.orphanShares.map { it.at.floor(precision) })
        assertEquals("$name: pasaporte", passport(base), passport(csv))
        assertNoDuplicates(name, csv)
    }

    /** Importar la variante encima de lo que dejó el original no añade nada. */
    private fun assertNoDuplicates(name: String, csv: ParsedCsv, reverse: Boolean = true) {
        val (fresh, present) = CsvImport.splitNew(csv.videos, base.videos.map { TimeKey(it.platform.name, it.startedAt) },
            keyOf = { TimeKey(it.platform.name, it.startedAt) }, precisionMs = { it.precisionMs })
        assertEquals("$name: vídeos nuevos", emptyList<ImportedVideo>(), fresh)
        assertEquals("$name: ya estaban", csv.videos.size, present)
        val (r, _) = CsvImport.splitNew(csv.recipients, base.recipients.map { TimeKey(it.platform.name, it.at, it.recipient) },
            keyOf = { TimeKey(it.platform.name, it.at, it.recipient) }, precisionMs = { it.precisionMs })
        assertEquals("$name: envíos nuevos", 0, r.size)
        val (s, _) = CsvImport.splitNew(csv.orphanShares, base.orphanShares.map { TimeKey(it.platform.name, it.at) },
            keyOf = { TimeKey(it.platform.name, it.at) }, precisionMs = { it.precisionMs })
        assertEquals("$name: compartidos nuevos", 0, s.size)
        // Y al revés: con la variante ya importada, el original tampoco añade nada.
        if (!reverse) return
        val (back, _) = CsvImport.splitNew(base.videos, csv.videos.map { TimeKey(it.platform.name, it.startedAt) },
            keyOf = { TimeKey(it.platform.name, it.startedAt) }, precisionMs = { it.precisionMs })
        assertEquals("$name: el original encima", emptyList<ImportedVideo>(), back)
    }

    /** Lo que se importe (si se importa) está en el original: nada inventado ni movido. */
    private fun assertNothingWrong(name: String, bytes: ByteArray) {
        save(name, bytes)
        val parse = CsvImport.parse(bytes, MADRID)
        if (parse !is CsvParse.Ok) return
        val good = videos(base, CsvImport.SECOND, 1).toSet()
        videos(parse.csv, CsvImport.SECOND, 1).forEach { assertTrue("$name: vídeo que no estaba: $it", it in good) }
        parse.csv.recipients.forEach { r -> assertTrue("$name: envío raro", base.recipients.any { it.recipient == r.recipient && it.at.floor(1000) == r.at.floor(1000) }) }
        parse.csv.orphanShares.forEach { s -> assertTrue("$name: compartido raro", base.orphanShares.any { it.at.floor(1000) == s.at.floor(1000) }) }
        assertNoDuplicates(name, parse.csv, reverse = false)
    }

    private fun save(name: String, bytes: ByteArray) {
        val dir = System.getenv("REELOMETRO_CSV_OUT")?.takeIf { it.isNotBlank() } ?: return
        File(dir).apply { mkdirs() }.resolve("$name.csv").writeBytes(bytes)
    }

    // ------------------------------------------------------------------ el original

    @Test
    fun `el original se lee entero y al milisegundo`() {
        assertEquals(videoRows.size, base.videos.size)
        assertEquals(videoRows.map { it.start }.sorted(), base.videos.map { it.startedAt })
        assertEquals(videoRows.map { it.durationMs }.sorted(), base.videos.map { it.durationMs }.sorted())
        assertTrue(base.videos.all { it.exact })
        assertEquals(listOf("Grupo de ejemplo"), base.recipients.map { it.recipient })
        assertEquals(listOf(orphanStart), base.orphanShares.map { it.at })
        val p = passport(base)!!
        assertEquals(TravelerLevel.PLATA, p.highestLevel)
        assertEquals(mapOf(StampId.RACHA_3 to LocalDate.of(2026, 11, 5)), p.stamps)
        assertEquals(listOf(ChallengeResult(LocalDate.of(2026, 10, 19), 1200, 980, won = true)), p.challenges)
        assertEquals(mapOf(GameEvent.TICKET_SAVED to LocalDate.of(2026, 11, 13)), p.events)
        assertSame("00_original", original.toByteArray(), CsvImport.MS)
    }

    // ------------------------------------------------------------------ se importan igual

    @Test
    fun `Excel en español, CSV UTF-8 - inicio_ms en notación científica y fechas del pasaporte con barras`() {
        val g = grid().cells { block, col, v ->
            when {
                col == "inicio_ms" -> scientific(v)
                block == PassportCsv.TITLE && col == "fecha" -> reday(v, "dd/MM/yyyy")
                else -> v
            }
        }
        assertSame("01_excel_utf8", g.write(pad = true).bytes(bom = true), CsvImport.SECOND)
    }

    @Test
    fun `Excel en español, CSV a secas - Windows-1252 sin BOM`() {
        val g = grid().cells { block, col, v ->
            when {
                col == "inicio_ms" -> scientific(v)
                block == PassportCsv.TITLE && col == "fecha" -> reday(v, "dd/MM/yyyy")
                else -> v
            }
        }
        assertSame("02_excel_ansi", g.write(pad = true).bytes(cp1252), CsvImport.SECOND)
        assertSame("02b_latin1", g.write().bytes(Charsets.ISO_8859_1), CsvImport.SECOND)
    }

    @Test
    fun `Excel que reconoce las fechas - sin segundos y sin milisegundos, al minuto y sin duplicar`() {
        val g = grid().cells { block, col, v ->
            when {
                col == "inicio_ms" -> scientific(v)
                col == "fecha_hora_local" -> redate(v, "dd/MM/yyyy H:mm")
                block == PassportCsv.TITLE && col == "fecha" -> reday(v, "dd/MM/yyyy")
                else -> v
            }
        }
        assertSame("03_excel_fechas", g.write(pad = true).bytes(bom = true), CsvImport.MINUTE)
    }

    @Test
    fun `LibreOffice en español - comillas en todo el texto, LF, miles con punto y fechas ISO con espacio`() {
        val g = grid().cells { _, col, v ->
            when (col) {
                "fecha_hora_local" -> redate(v, "yyyy-MM-dd HH:mm:ss")
                "duración_s", "duración_ms", "tope" -> thousands(v)
                else -> v
            }
        }
        assertSame("04_libreoffice", g.write(eol = "\n", quoteText = true).bytes(), CsvImport.MS)
        // Año con dos cifras (formato corto de LibreOffice).
        val short = grid().cells { _, col, v -> if (col == "fecha_hora_local") redate(v, "dd/MM/yy HH:mm:ss") else v }
        assertSame("04b_libreoffice_yy", short.write(eol = "\n").bytes(), CsvImport.MS)
    }

    @Test
    fun `Google Sheets en español - comas, fechas d-M-yyyy y decimales entre comillas`() {
        val g = grid().cells { block, col, v ->
            when {
                col == "fecha_hora_local" -> redate(v, "d/M/yyyy H:mm:ss")
                block == PassportCsv.TITLE && col == "fecha" -> reday(v, "d/M/yyyy")
                else -> v
            }
        }
        assertSame("05_google_sheets", g.write(separator = ',', pad = true).bytes(), CsvImport.MS)
    }

    @Test
    fun `tabuladores - Texto Unicode de Excel en UTF-16 y texto delimitado en Windows-1252`() {
        val g = grid().cells { _, col, v -> if (col == "inicio_ms") scientific(v) else v }
        assertSame("06_unicode_utf16", g.write(separator = '\t').bytes(Charsets.UTF_16LE).let { byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it },
            CsvImport.SECOND)
        assertSame("06b_tab_ansi", g.write(separator = '\t').bytes(cp1252), CsvImport.SECOND)
    }

    @Test
    fun `UTF-8 abierto como Windows-1252 y guardado otra vez en UTF-8`() {
        // Como Windows: los cinco huecos de Windows-1252 (0x81, 0x8D…; «Í» es C3 8D) pasan como su mismo código.
        val mojibake = original.removePrefix(Csv.BOM).toByteArray(Charsets.UTF_8).joinToString("") { b ->
            val c = String(byteArrayOf(b), cp1252)
            if (c == "�") (b.toInt() and 0xFF).toChar().toString() else c
        }
        assertTrue(mojibake.contains("duraciÃ³n_s"))
        assertSame("07_mojibake", mojibake.bytes(bom = true), CsvImport.MS)
    }

    @Test
    fun `hoja en inglés de EE UU - mes primero, AM-PM, punto decimal y coma`() {
        val us = Locale.US
        val g = grid().cells { block, col, v ->
            when {
                col == "inicio_ms" -> scientific(v, us)
                col == "fecha_hora_local" -> redate(v, "M/d/yyyy h:mm:ss a", us)
                col == "duración_s" -> v.replace(',', '.')
                block == PassportCsv.TITLE && col == "fecha" -> reday(v, "M/d/yyyy")
                else -> v
            }
        }
        assertSame("08_excel_us", g.write(separator = ',').bytes(), CsvImport.SECOND)
    }

    @Test
    fun `filas reordenadas, filas en blanco, columnas vacías y columnas añadidas por el usuario`() {
        val g = grid()
        val header = g.first() + listOf("", "notas", "")
        val videos = g.subList(1, 1 + videoRows.size).map { it + listOf("", "me gustó", "") }.sortedByDescending { it[1] }
        val rest = g.drop(1 + videoRows.size)
        val blank = List(header.size) { "" }
        val shuffled = listOf(header, videos[0], blank, videos[1]) + videos.drop(2) + listOf(blank, blank) + rest
        assertSame("09_reordenado", shuffled.write(pad = true).bytes(bom = true), CsvImport.MS)
    }

    @Test
    fun `sin las columnas de la 1_6_0 (el usuario las borra) - al segundo y con la duración de duración_s`() {
        val g = grid().dropVideoColumns("inicio_ms", "zona", "duración_ms")
        assertSame("10_sin_columnas_nuevas", g.write().bytes(bom = true), CsvImport.SECOND, durationStep = 100, zone = false)
    }

    @Test
    fun `quitar la protección contra fórmulas (Excel se come el apóstrofo) no cambia nada`() {
        val g = grid().cells { _, _, v -> if (v.length > 1 && v[0] == '\'') v.substring(1) else v }
        assertSame("11_sin_apostrofo", g.write().bytes(bom = true), CsvImport.MS)
    }

    @Test
    fun `la línea entera en una celda entrecomillada (hoja con otro separador)`() {
        // La cabecera se queda como está; cada fila de datos, en una sola celda entrecomillada.
        val g = grid()
        val text = g.mapIndexed { i, row ->
            val line = listOf(row).write(eol = "")
            if (i == 0 || ';' !in line) line else "\"" + line.replace("\"", "\"\"") + "\""
        }.joinToString("\r\n", postfix = "\r\n")
        assertSame("12_linea_en_una_celda", text.bytes(bom = true), CsvImport.MS)
    }

    // ------------------------------------------------------------------ se rechazan (o no entra nada malo)

    @Test
    fun `fechas como número de serie y sin inicio_ms - se rechaza diciendo que son las fechas`() {
        val g = grid().cells { _, col, v ->
            when (col) {
                "inicio_ms" -> scientific(v)
                "fecha_hora_local" -> if (v.length == 19) "46329,6090856" else v
                else -> v
            }
        }
        val bytes = g.write().bytes(bom = true)
        save("20_fechas_serie", bytes)
        assertEquals(ImportError.DATES_UNREADABLE, failed(bytes))
    }

    @Test
    fun `una descripción con punto y coma sin comillas descoloca su fila - se salta y el resto entra`() {
        val text = original.replace("\"hola; qué tal\"", "hola; qué tal")
        val csv = ok(text.toByteArray())
        assertEquals(1, csv.badLines)
        assertNothingWrong("21_sin_comillas", text.toByteArray())
    }

    @Test
    fun `toda la hoja ordenada por la primera columna - o se rechaza o no entra nada inventado`() {
        val g = grid()
        val sorted = listOf(g.first()) + g.drop(1).filter { r -> r.any { it.isNotBlank() } }.sortedBy { it[0].lowercase() }
        assertNothingWrong("22_todo_ordenado", sorted.write(pad = true).bytes(bom = true))
    }

    @Test
    fun `inicio_ms recortado o de otra columna no se toma como instante`() {
        assertEquals(null, CsvImport.epochMs("0"))
        assertEquals(null, CsvImport.epochMs("1,76218E+12"))
        assertEquals(1_762_177_025_123L, CsvImport.epochMs("1.762.177.025.123"))
        assertEquals(1_762_177_025_123L, CsvImport.epochMs("1762177025123,0"))
    }

    @Test
    fun `duraciones - miles, decimales y nunca por mil`() {
        assertEquals(1_234_500L, CsvImport.durationMs(null, "1.234,5"))
        assertEquals(1_234_000L, CsvImport.durationMs(null, "1.234"))
        assertEquals(12_300L, CsvImport.durationMs(null, "12,3"))
        assertEquals(12_345L, CsvImport.durationMs("12.345", "12,3"))
        assertEquals(12_345L, CsvImport.durationMs("12345,00", "12,3"))
        // «123,000» con tres decimales: duración_s dice que son 123 ms, no 123 s.
        assertEquals(123L, CsvImport.durationMs("123,000", "0,1"))
        // Un inicio_ms leído como segundos (columna movida) no es una duración.
        assertEquals(null, CsvImport.durationMs(null, "1762177025123"))
        assertEquals(null, CsvImport.durationMs("1762177025123", ""))
    }

    @Test
    fun `fechas - formatos de hoja de cálculo, orden día-mes deducido y AM-PM`() {
        val at = LocalDateTime.of(2026, 11, 3, 14, 37, 5)
        for (v in listOf("2026-11-03T14:37:05", "2026-11-03 14:37:05", "03/11/2026 14:37:05", "3/11/26 14:37:05", "03-11-2026 2:37:05 p. m.",
            "03.11.2026 14:37:05", "2026/11/03 14:37:05", " 03/11/2026 14:37:05 ")) {
            assertEquals(v, at, CsvImport.localDateTime(v))
        }
        assertEquals(at, CsvImport.localDateTime("11/3/2026 2:37:05 PM", DateOrder.MONTH_FIRST))
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 5), CsvImport.localDateTime("3/11/2026 12:05 AM"))
        assertEquals(CsvImport.MINUTE, CsvImport.localStamp("03/11/2026 14:37")!!.precisionMs)
        assertEquals(null, CsvImport.localDateTime("03/11/2026"))
        assertEquals(null, CsvImport.localDateTime("46329,6090856"))
        assertEquals(null, CsvImport.localDateTime("31/02/2026 10:00"))
        assertEquals(DateOrder.MONTH_FIRST, CsvImport.dateOrder(sequenceOf("11/3/2026 1:00", "11/25/2026 1:00")))
        assertEquals(DateOrder.DAY_FIRST, CsvImport.dateOrder(sequenceOf("11/3/2026 1:00", "25/11/2026 1:00", "11/25/2026 1:00")))
        assertEquals(DateOrder.DAY_FIRST, CsvImport.dateOrder(sequenceOf("01/02/2026 1:00", "2026-11-03T14:37:05")))
    }

    @Test
    fun `separador - el que más sale en la cabecera, fuera de comillas`() {
        assertEquals('\t', CsvReader.separatorOf("fecha_hora_local\tplataforma\tduración_s\n"))
        assertEquals(',', CsvReader.separatorOf("\"a;b\",c,d\n"))
        assertEquals(';', CsvReader.separatorOf("solo"))
    }

    @Test
    fun `un texto bueno con Ã no se toca`() {
        assertEquals("Ã© y ñ", CsvReader.repairMojibake("Ã© y ñ"))
        assertEquals("vídeo", CsvReader.repairMojibake("vÃ­deo"))
    }

    private companion object {
        val ES: Locale = Locale.forLanguageTag("es-ES")
    }
}
