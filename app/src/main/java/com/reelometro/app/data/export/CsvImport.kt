package com.reelometro.app.data.export

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToLong

/** Cómo se importa: [ADD] suma lo nuevo sin duplicar; [REPLACE] borra antes todo lo que hay. */
enum class ImportMode { ADD, REPLACE }

/** Por qué no se puede importar un fichero (cada uno tiene su texto en `SettingsCopy`). */
enum class ImportError {
    /** La cabecera no es la de un CSV de Reelometer. */
    NOT_REELOMETRO,

    /** No hay nada: ni vídeos ni envíos ni compartidos. */
    EMPTY,

    /** Más de [CsvImport.MAX_BYTES]. */
    TOO_LARGE,

    /** Más de [CsvImport.MAX_BAD_FRACTION] de las líneas no se entienden: no se importa nada. */
    TOO_MANY_BAD,

    /** No se ha podido leer o escribir (E/S, base de datos). */
    FAILED,

    /**
     * Como [TOO_MANY_BAD], pero casi todas fallan por la fecha: una hoja de cálculo la ha reescrito en un
     * formato que no se entiende (número de serie, otro orden…) y `inicio_ms` tampoco sirve.
     */
    DATES_UNREADABLE,
}

/** Lo que se enseña antes de importar. [from]/[to]: primer y último día del fichero. */
data class ImportPreview(
    /** Vídeos del fichero (sin contar los repetidos dentro del propio fichero). */
    val videos: Int,
    /** De esos, los que ya están guardados (con [ImportMode.ADD] no se vuelven a añadir). */
    val alreadyPresent: Int,
    val from: LocalDate?,
    val to: LocalDate?,
    /** Líneas que no se entienden y se saltan. */
    val badLines: Int,
    /** Trae el bloque PASAPORTE. */
    val hasPassport: Boolean,
) {
    /** Vídeos que entrarían con [ImportMode.ADD]. */
    val newVideos: Int get() = videos - alreadyPresent
}

/** Resultado de una importación ya hecha. */
data class ImportResult(
    val mode: ImportMode,
    val imported: Int,
    val alreadyPresent: Int,
    val badLines: Int,
)

/**
 * Un vídeo leído del CSV. [zone]: la de la columna `zona`, si venía. [precisionMs]: de dónde sale el inicio:
 * [CsvImport.MS] de `inicio_ms`; [CsvImport.SECOND] de la hora local; [CsvImport.MINUTE] si una hoja de cálculo le
 * quitó los segundos. Con esa precisión se compara con lo guardado ([CsvImport.splitNew]).
 */
data class ImportedVideo(
    val platform: Platform,
    val startedAt: Long,
    val durationMs: Long,
    val creator: String?,
    val caption: String?,
    val category: Category,
    val shares: Int,
    val zone: ZoneId?,
    val precisionMs: Long = CsvImport.SECOND,
) {
    /** El inicio viene de `inicio_ms` (al milisegundo). */
    val exact: Boolean get() = precisionMs == CsvImport.MS
}

/** Un compartido sin vídeo (bloque COMPARTIDOS SIN VÍDEO). */
data class ImportedShare(val platform: Platform, val at: Long, val zone: ZoneId?, val precisionMs: Long = CsvImport.SECOND) {
    val exact: Boolean get() = precisionMs == CsvImport.MS
}

/** Un envío de «A quién envías» (bloque ENVÍOS). */
data class ImportedRecipient(
    val platform: Platform,
    val at: Long,
    val recipient: String,
    val zone: ZoneId?,
    val precisionMs: Long = CsvImport.SECOND,
) {
    val exact: Boolean get() = precisionMs == CsvImport.MS
}

/** El fichero ya leído y entendido, listo para la vista previa y para importarlo. */
class ParsedCsv(
    /** En orden cronológico, sin filas repetidas idénticas (dos vídeos en el mismo segundo son dos vídeos). */
    val videos: List<ImportedVideo>,
    val orphanShares: List<ImportedShare>,
    val recipients: List<ImportedRecipient>,
    /** Los bloques que no son de vídeos ni de compartidos (PASAPORTE…), con la columna `fecha` ya en ISO. */
    val blocks: List<CsvBlock>,
    val badLines: Int,
) {
    val from: LocalDate? get() = videos.firstOrNull()?.let { localDate(it.startedAt, it.zone) }
    val to: LocalDate? get() = videos.lastOrNull()?.let { localDate(it.startedAt, it.zone) }

    private fun localDate(at: Long, zone: ZoneId?): LocalDate =
        Instant.ofEpochMilli(at).atZone(zone ?: ZoneId.systemDefault()).toLocalDate()
}

/** O un [ParsedCsv] o el [ImportError] que impide importarlo. */
sealed interface CsvParse {
    data class Ok(val csv: ParsedCsv) : CsvParse

    data class Failed(val error: ImportError) : CsvParse
}

/** Cómo leer una fecha `03/04/2026`: día primero (España, por defecto) o mes primero (hoja en inglés de EE. UU.). */
enum class DateOrder { DAY_FIRST, MONTH_FIRST }

/** Fecha y hora local leída y su precisión ([CsvImport.SECOND] o [CsvImport.MINUTE]). */
data class LocalStamp(val time: LocalDateTime, val precisionMs: Long)

/**
 * Lee el CSV de exportación de cualquier versión (puro, sin Android ni base de datos):
 * - 1.0.0: solo los vídeos (`fecha_hora_local;plataforma;duración_s;creador;categoría;descripción;compartido`);
 * - 1.1.0+: detrás, el bloque PASAPORTE; 1.2.0+: el bloque ENVÍOS;
 * - 1.6.0+: las columnas `inicio_ms`, `zona` y `duración_ms` al final, el bloque COMPARTIDOS SIN VÍDEO y la
 *   columna `clave` del PASAPORTE.
 * Las columnas se buscan por nombre. Si están `inicio_ms`/`duración_ms` y se entienden, mandan; si no (Excel deja
 * `inicio_ms` como `1,76218E+12`), la hora local se pasa a instante con la `zona` de la fila o [zone] (en un
 * cambio de hora, el primer desfase) y la duración sale de `duración_s`.
 *
 * Tras una hoja de cálculo: cifras con separador de miles (`1.234`, `12.345`), coma o punto decimal, fechas
 * `03/11/2026 14:37[:05]`, `2026-11-03 14:37:05`, con año de dos cifras o con AM/PM, y el orden día/mes
 * deducido del fichero ([dateOrder]). Lo que no cuadra (inicio antes de 2000, duraciones de más de
 * [MAX_DURATION_MS]) es una línea rota, nunca un dato raro.
 */
object CsvImport {
    /** Tope del fichero: una copia de varios años de uso intenso no llega a 30 MB. */
    const val MAX_BYTES = 50L * 1024 * 1024

    /** Si no se entiende más de esta parte de las líneas, no se importa nada. */
    const val MAX_BAD_FRACTION = 0.2

    /** Precisión de un instante leído (ver [ImportedVideo.precisionMs]). */
    const val MS = 1L
    const val SECOND = 1_000L
    const val MINUTE = 60_000L

    /** Una duración mayor no es de un vídeo: es una columna movida (un `inicio_ms` leído como segundos). */
    const val MAX_DURATION_MS = 30L * 24 * 3_600_000

    /** `inicio_ms` fuera de [2000, 2100) no es un instante de verdad (otra columna, una cifra recortada). */
    private const val MIN_EPOCH_MS = 946_684_800_000L
    private const val MAX_EPOCH_MS = 4_102_444_800_000L

    const val COL_TIME = "fecha_hora_local"
    const val COL_PLATFORM = "plataforma"
    const val COL_DURATION_S = "duración_s"
    const val COL_CREATOR = "creador"
    const val COL_CATEGORY = "categoría"
    const val COL_CAPTION = "descripción"
    const val COL_SHARES = "compartido"
    const val COL_START_MS = "inicio_ms"
    const val COL_ZONE = "zona"
    const val COL_DURATION_MS = "duración_ms"
    const val COL_RECIPIENT = "destinatario"

    /** Columna de fecha (sin hora) de los bloques extra, como el PASAPORTE. */
    const val COL_DATE = "fecha"

    /** Límites de lo que se guarda (los mismos que `ReelRepository`). */
    private const val CREATOR_MAX = 64
    private const val CAPTION_MAX = 300
    private const val RECIPIENT_MAX = 64
    private const val MAX_SHARES_PER_VIDEO = 1_000

    private val FORMULA_START = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun parse(bytes: ByteArray, zone: ZoneId): CsvParse {
        if (bytes.size > MAX_BYTES) return CsvParse.Failed(ImportError.TOO_LARGE)
        val text = CsvReader.decode(bytes)
        if (text.isBlank()) return CsvParse.Failed(ImportError.EMPTY)
        return parse(CsvReader.document(text), zone)
    }

    /** Recuento de líneas leídas y rotas (y de esas, cuántas por la fecha). */
    private class Tally {
        var lines = 0
        var bad = 0
        var badDates = 0
    }

    fun parse(doc: CsvDocument, zone: ZoneId): CsvParse {
        val videoBlock = doc.videos ?: return CsvParse.Failed(ImportError.EMPTY)
        if (videoBlock.column(COL_TIME) == null || videoBlock.column(COL_PLATFORM) == null ||
            (videoBlock.column(COL_DURATION_S) == null && videoBlock.column(COL_DURATION_MS) == null)
        ) {
            return CsvParse.Failed(ImportError.NOT_REELOMETRO)
        }
        val recipientBlock = doc.block(CsvExporter.RECIPIENTS_TITLE)
        val sharesBlock = doc.block(CsvExporter.ORPHAN_SHARES_TITLE)
        val known = setOf(CsvExporter.RECIPIENTS_TITLE, CsvExporter.ORPHAN_SHARES_TITLE).map(CsvReader::normalize)
        val extra = doc.blocks.filter { CsvReader.normalize(it.title) !in known }
        val order = dateOrder(
            sequenceOf(videoBlock, recipientBlock, sharesBlock).filterNotNull().flatMap { b -> b.rows.asSequence().map { b.field(it, COL_TIME) } } +
                extra.asSequence().flatMap { b -> b.rows.asSequence().map { b.field(it, COL_DATE) } },
        )
        val tally = Tally()

        val videos = ArrayList<ImportedVideo>(videoBlock.rows.size)
        for (row in videoBlock.rows) {
            tally.lines++
            video(videoBlock, row, zone, order, tally)?.let { videos += it } ?: tally.bad++
        }
        val recipients = ArrayList<ImportedRecipient>()
        recipientBlock?.let { block ->
            for (row in block.rows) {
                tally.lines++
                recipient(block, row, zone, order, tally)?.let { recipients += it } ?: tally.bad++
            }
        }
        val shares = ArrayList<ImportedShare>()
        sharesBlock?.let { block ->
            for (row in block.rows) {
                tally.lines++
                orphanShare(block, row, zone, order, tally)?.let { shares += it } ?: tally.bad++
            }
        }
        if (tally.lines > 0 && tally.bad > tally.lines * MAX_BAD_FRACTION) {
            return CsvParse.Failed(if (tally.badDates * 2 > tally.bad) ImportError.DATES_UNREADABLE else ImportError.TOO_MANY_BAD)
        }
        if (videos.isEmpty() && recipients.isEmpty() && shares.isEmpty()) return CsvParse.Failed(ImportError.EMPTY)

        // Solo se quitan las filas idénticas: al pasar un reel rápido, dos vídeos distintos empiezan en el mismo segundo.
        val unique = videos.distinct().sortedWith(compareBy<ImportedVideo> { it.startedAt }.thenBy { it.platform.ordinal })
        return CsvParse.Ok(
            ParsedCsv(
                videos = unique,
                orphanShares = shares.distinct(),
                recipients = recipients.distinct(),
                blocks = extra.map { isoDates(it, order) },
                badLines = tally.bad,
            ),
        )
    }

    /** El bloque con su columna `fecha` reescrita en ISO (`2026-11-03`), para que cada `UserDataPart` la lea igual. */
    private fun isoDates(block: CsvBlock, order: DateOrder): CsvBlock {
        val i = block.column(COL_DATE) ?: return block
        val rows = block.rows.map { row ->
            val date = row.getOrNull(i)?.let { localDate(it, order) } ?: return@map row
            row.toMutableList().also { it[i] = date.toString() }
        }
        return CsvBlock(block.title, block.header, rows)
    }

    /**
     * Separa lo nuevo de lo que ya está guardado ([existing]: plataforma, instante y un dato extra, como el nombre
     * del destinatario). Cada cosa se compara con la precisión con la que se leyó ([precisionMs]): al milisegundo
     * con `inicio_ms`; sin él, por recuento al segundo (o al minuto, si la hoja de cálculo quitó los segundos): si en
     * ese segundo ya hay dos guardados y el fichero trae dos, no entra ninguno; si trae tres, entra uno. Así importar
     * dos veces no duplica y dos vídeos en el mismo segundo no se pierden. Devuelve lo nuevo y cuántos ya estaban.
     */
    fun <T> splitNew(
        items: List<T>,
        existing: List<TimeKey>,
        keyOf: (T) -> TimeKey,
        precisionMs: (T) -> Long,
    ): Pair<List<T>, Int> {
        val remaining = HashMap<TimeKey, Int>()
        existing.forEach { remaining.merge(it, 1, Int::plus) }
        val keys = items.map(keyOf)
        val matched = BooleanArray(items.size)
        var present = 0
        // 1) Iguales al milisegundo.
        for (i in items.indices) {
            val left = remaining[keys[i]] ?: 0
            if (left > 0) {
                remaining[keys[i]] = left - 1
                matched[i] = true
                present++
            }
        }
        // 2) Al segundo y luego al minuto, solo si uno de los dos se leyó con esa precisión. Lo guardado no dice
        // cómo se leyó: un instante en un segundo o un minuto justos (.000) viene de importar un CSV sin `inicio_ms`
        // (un vídeo de verdad casi nunca cae ahí), así que importar después el CSV original tampoco duplica.
        for (level in longArrayOf(SECOND, MINUTE)) {
            val coarse = HashMap<TimeKey, ArrayList<TimeKey>>() // guardados con esta precisión
            val fine = HashMap<TimeKey, ArrayList<TimeKey>>() // guardados más finos
            for ((k, n) in remaining) {
                val q = storedPrecision(k.at)
                if (n <= 0 || q > level) continue
                val list = (if (q == level) coarse else fine).getOrPut(k.truncated(level)) { ArrayList() }
                repeat(n) { list += k }
            }
            for (i in items.indices) {
                if (matched[i]) continue
                val p = precisionMs(items[i]).coerceAtLeast(MS)
                if (p > level) continue
                val g = keys[i].truncated(level)
                val from = coarse[g]?.takeIf { it.isNotEmpty() } ?: fine[g]?.takeIf { p == level && it.isNotEmpty() } ?: continue
                val k = from.removeAt(from.size - 1)
                remaining[k] = remaining.getValue(k) - 1
                matched[i] = true
                present++
            }
        }
        return items.filterIndexed { i, _ -> !matched[i] } to present
    }

    /** Precisión con la que parece guardado un instante: minuto o segundo justos, o al milisegundo. */
    private fun storedPrecision(at: Long): Long = when {
        at % MINUTE == 0L -> MINUTE
        at % SECOND == 0L -> SECOND
        else -> MS
    }

    /** [splitNew] con [exact] (al milisegundo) o al segundo. */
    @JvmName("splitNewExact")
    fun <T> splitNew(
        items: List<T>,
        existing: List<TimeKey>,
        keyOf: (T) -> TimeKey,
        exact: (T) -> Boolean,
    ): Pair<List<T>, Int> = splitNew(items, existing, keyOf, precisionMs = { if (exact(it)) MS else SECOND })

    private fun video(block: CsvBlock, row: List<String>, zone: ZoneId, order: DateOrder, tally: Tally): ImportedVideo? {
        val platform = platform(block.field(row, COL_PLATFORM)) ?: return null
        val rowZone = zone(block.field(row, COL_ZONE))
        val start = start(block, row, rowZone ?: zone, order, tally) ?: return null
        val duration = durationMs(block.field(row, COL_DURATION_MS), block.field(row, COL_DURATION_S)) ?: return null
        val shares = block.field(row, COL_SHARES).let { s ->
            if (s.isNullOrEmpty()) 0 else wholeNumber(s) ?: return null
        }
        return ImportedVideo(
            platform = platform,
            startedAt = start.first,
            durationMs = duration,
            creator = text(block.field(row, COL_CREATOR), CREATOR_MAX),
            caption = text(block.field(row, COL_CAPTION), CAPTION_MAX),
            category = category(block.field(row, COL_CATEGORY)),
            shares = shares.coerceAtMost(MAX_SHARES_PER_VIDEO.toLong()).toInt(),
            zone = rowZone,
            precisionMs = start.second,
        )
    }

    private fun recipient(block: CsvBlock, row: List<String>, zone: ZoneId, order: DateOrder, tally: Tally): ImportedRecipient? {
        val platform = platform(block.field(row, COL_PLATFORM)) ?: return null
        val name = text(block.field(row, COL_RECIPIENT), RECIPIENT_MAX) ?: return null
        val at = start(block, row, zone, order, tally) ?: return null
        return ImportedRecipient(platform, at.first, name, null, precisionMs = at.second)
    }

    private fun orphanShare(block: CsvBlock, row: List<String>, zone: ZoneId, order: DateOrder, tally: Tally): ImportedShare? {
        val platform = platform(block.field(row, COL_PLATFORM)) ?: return null
        val at = start(block, row, zone, order, tally) ?: return null
        return ImportedShare(platform, at.first, null, precisionMs = at.second)
    }

    /** Instante y precisión de la fila; si no hay manera, cuenta la fila como rota por la fecha. */
    private fun start(block: CsvBlock, row: List<String>, zone: ZoneId, order: DateOrder, tally: Tally): Pair<Long, Long>? {
        val at = instantOf(block.field(row, COL_START_MS), block.field(row, COL_TIME), zone, order)
        if (at == null) tally.badDates++
        return at
    }

    /** Por `displayName` («Instagram Reels»), nombre corto («Reels») o del enum (`INSTAGRAM`), sin mayúsculas. */
    fun platform(value: String?): Platform? {
        val v = value?.let(CsvReader::normalize)?.takeIf { it.isNotEmpty() } ?: return null
        return Platform.entries.firstOrNull { p ->
            v == CsvReader.normalize(p.displayName) || v == CsvReader.normalize(p.shortName) || v == CsvReader.normalize(p.name)
        }
    }

    /** Por el nombre del CSV («Moda y belleza»), el del idioma de la app o el del enum; si no, «Sin clasificar». */
    fun category(value: String?): Category {
        val v = value?.let(CsvReader::normalize)?.takeIf { it.isNotEmpty() } ?: return Category.OTROS
        return Category.entries.firstOrNull { c ->
            v == CsvReader.normalize(c.csvLabel) || v == CsvReader.normalize(c.name) ||
                runCatching { v == CsvReader.normalize(c.label) }.getOrDefault(false)
        } ?: Category.OTROS
    }

    /** Texto libre: deshace la protección contra fórmulas de [Csv.text] y recorta. Vacío → `null`. */
    fun text(value: String?, max: Int): String? {
        var v = value?.trim().orEmpty()
        if (v.length >= 2 && v[0] == '\'' && v[1] in FORMULA_START) v = v.substring(1)
        return v.takeIf { it.isNotEmpty() }?.take(max)
    }

    private fun zone(value: String?): ZoneId? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it.trim()) }.getOrNull() }

    /** `inicio_ms` si es un instante creíble; si no, la fecha y hora local en [zone]. */
    fun instant(startMs: String?, local: String?, zone: ZoneId, order: DateOrder = DateOrder.DAY_FIRST): Long? =
        instantOf(startMs, local, zone, order)?.first

    /** Instante y precisión: `inicio_ms` (al milisegundo) o la hora local (al segundo o al minuto). */
    private fun instantOf(startMs: String?, local: String?, zone: ZoneId, order: DateOrder): Pair<Long, Long>? {
        epochMs(startMs)?.let { return it to MS }
        val stamp = localStamp(local, order) ?: return null
        return try {
            ZonedDateTime.ofLocal(stamp.time, zone, null).toInstant().toEpochMilli() to stamp.precisionMs
        } catch (_: DateTimeException) {
            null
        }
    }

    /** `inicio_ms` entero (también con separador de miles) entre 2000 y 2100; `1,76218E+12` y lo demás, `null`. */
    fun epochMs(value: String?): Long? = wholeNumber(value)?.takeIf { it in MIN_EPOCH_MS until MAX_EPOCH_MS }

    /** La fecha y hora local (ver [localStamp]), sin la precisión. */
    fun localDateTime(value: String?, order: DateOrder = DateOrder.DAY_FIRST): LocalDateTime? = localStamp(value, order)?.time

    /**
     * La fecha y hora tal y como la escribe [CsvExporter] (`2026-11-03T14:37:05`) o como la deja una hoja de cálculo:
     * `2026-11-03 14:37:05`, `03/11/2026 14:37:05`, `3/11/26 9:07`, `03-11-2026 2:37 p. m.`, `11/3/2026 2:37:05 PM`
     * (con [DateOrder.MONTH_FIRST])… Sin segundos, la precisión es [MINUTE]. Sin hora, `null`.
     */
    fun localStamp(value: String?, order: DateOrder = DateOrder.DAY_FIRST): LocalStamp? {
        val parts = dateParts(value, order) ?: return null
        val hour = parts.hour ?: return null
        return try {
            LocalStamp(
                parts.date.atTime(hour, parts.minute, parts.second ?: 0, parts.nanos),
                if (parts.second == null) MINUTE else SECOND,
            )
        } catch (_: DateTimeException) {
            null
        }
    }

    /** Solo la fecha (`2026-11-03`, `03/11/2026`…; si trae hora, se ignora). */
    fun localDate(value: String?, order: DateOrder = DateOrder.DAY_FIRST): LocalDate? = dateParts(value, order)?.date

    /**
     * Orden día/mes de las fechas con barras del fichero: si alguna empieza por más de 12 (`25/11/2026`), día
     * primero; si alguna tiene más de 12 en medio (`11/25/2026`) y ninguna lo contrario, mes primero. Sin pistas,
     * día primero (España).
     */
    fun dateOrder(values: Sequence<String?>): DateOrder {
        var dayFirst = false
        var monthFirst = false
        for (value in values) {
            val m = value?.let { DMY.matchEntire(clean(it)) } ?: continue
            if (m.groupValues[1].toInt() > 12) dayFirst = true
            if (m.groupValues[3].toInt() > 12) monthFirst = true
            if (dayFirst) break
        }
        return if (monthFirst && !dayFirst) DateOrder.MONTH_FIRST else DateOrder.DAY_FIRST
    }

    private class DateParts(val date: LocalDate, val hour: Int?, val minute: Int, val second: Int?, val nanos: Int)

    private fun dateParts(value: String?, order: DateOrder): DateParts? {
        val v = value?.let(::clean)?.takeIf { it.isNotEmpty() } ?: return null
        YMD.matchEntire(v)?.let { m ->
            return dateParts(m.groupValues[1].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt(), m)
        }
        DMY.matchEntire(v)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[3].toInt()
            val year = m.groupValues[4].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            return if (order == DateOrder.DAY_FIRST) dateParts(year, b, a, m) else dateParts(year, a, b, m)
        }
        return null
    }

    /** Grupos de la hora a partir del 5: hora, minuto, segundo, fracción y a/p. */
    private fun dateParts(year: Int, month: Int, day: Int, m: MatchResult): DateParts? {
        val date = try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            return null
        }
        val g = m.groupValues
        if (g[5].isEmpty()) return DateParts(date, null, 0, null, 0)
        var hour = g[5].toInt()
        val minute = g[6].toInt()
        val second = g[7].takeIf { it.isNotEmpty() }?.toInt()
        val nanos = g[8].takeIf { it.isNotEmpty() }?.take(9)?.padEnd(9, '0')?.toInt() ?: 0
        if (g[9].isNotEmpty()) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (g[9].equals("p", ignoreCase = true)) 12 else 0
        }
        if (hour > 23 || minute > 59 || (second ?: 0) > 59) return null
        return DateParts(date, hour, minute, second, nanos)
    }

    /** Sin espacios en los bordes y con los espacios duros o repetidos como uno normal. */
    private fun clean(value: String): String = value.replace(SPACES, " ").trim()

    private val SPACES = Regex("[\\s\\u00A0\\u202F]+")
    private const val TIME = "(\\d{1,2}):(\\d{2})(?::(\\d{2})(?:[.,](\\d{1,9}))?)?(?: ?([ap])\\.? ?m\\.?)?"
    private val YMD = Regex("(\\d{4})([-/.])(\\d{1,2})\\2(\\d{1,2})(?:(?:T|,? )$TIME)?", RegexOption.IGNORE_CASE)
    private val DMY = Regex("(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{4}|\\d{2})(?:,? $TIME)?", RegexOption.IGNORE_CASE)

    /**
     * `duración_ms` si se entiende; si no, `duración_s` con coma o punto decimal (y separador de miles: `1.234,5`,
     * `1.234`). Vacío → 0; ilegible o de más de [MAX_DURATION_MS] → `null`.
     */
    fun durationMs(ms: String?, seconds: String?): Long? {
        val fromMs = wholeNumber(ms)
        if (fromMs != null) {
            // «123,000» puede ser 123.000 (miles) o 123 (decimales): si no son solo cifras, decide `duración_s`
            // (que va al décimo); si ninguna lectura cuadra con ella, manda ella.
            val raw = digitsOnly(ms!!)
            val fromSeconds = if (raw.all(Char::isDigit)) null else secondsMs(seconds)
            val best = if (fromSeconds == null) {
                fromMs
            } else {
                val asDecimal = raw.replace(',', '.').toDoubleOrNull()?.roundToLong()
                listOfNotNull(fromMs, asDecimal).firstOrNull { kotlin.math.abs(it - fromSeconds) <= 100 } ?: fromSeconds
            }
            return best.takeIf { it <= MAX_DURATION_MS }
        }
        if (seconds.isNullOrBlank()) return if (ms.isNullOrBlank()) 0 else null
        return secondsMs(seconds)?.takeIf { it <= MAX_DURATION_MS }
    }

    /** `duración_s` en milisegundos; vacío o ilegible → `null`. */
    private fun secondsMs(seconds: String?): Long? {
        val s = seconds?.let(::digitsOnly)?.takeIf { it.isNotEmpty() } ?: return null
        val clean = when {
            s.contains(',') && s.contains('.') ->
                // «1.234,5» (millar con punto) o «1,234.5» (millar con coma): manda el último.
                if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            // «1.234» o «12.345.678»: millares (la app nunca escribe tres decimales).
            GROUPED_DOT.matches(s) -> s.replace(".", "")
            GROUPED_COMMA_2.matches(s) -> s.replace(",", "")
            else -> s.replace(',', '.')
        }
        val value = clean.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE / 1000.0 } ?: return null
        return (value * 1000).roundToLong()
    }

    /**
     * Número entero como lo deja una hoja de cálculo: `12345`, `12.345`, `12,345`, `12 345`, `12345,0`. Ni negativos
     * ni notación científica (`1,76218E+12` ha perdido cifras). Vacío o ilegible → `null`.
     */
    fun wholeNumber(value: String?): Long? {
        val v = value?.let(::digitsOnly)?.takeIf { it.isNotEmpty() } ?: return null
        val digits = when {
            v.all(Char::isDigit) -> v
            GROUPED.matches(v) -> v.filter(Char::isDigit)
            ZERO_DECIMALS.matches(v) -> v.substringBefore(if ('.' in v) '.' else ',')
            else -> return null
        }
        return digits.toLongOrNull()
    }

    /** Sin espacios (también duros) ni apóstrofos de millar. */
    private fun digitsOnly(value: String): String = value.filterNot { it.isWhitespace() || it == ' ' || it == ' ' || it == '\'' }

    private val GROUPED = Regex("\\d{1,3}([.,])\\d{3}(?:\\1\\d{3})*")
    private val GROUPED_DOT = Regex("\\d{1,3}(?:\\.\\d{3})+")
    private val GROUPED_COMMA_2 = Regex("\\d{1,3}(?:,\\d{3}){2,}")
    private val ZERO_DECIMALS = Regex("\\d+[.,]0+")
}

/** Plataforma (nombre del enum), instante y un dato extra (el destinatario) para comparar con lo guardado. */
data class TimeKey(val platform: String, val at: Long, val extra: String = "") {
    /** La misma clave al segundo (para los CSV sin `inicio_ms`). */
    fun toSecond(): TimeKey = truncated(CsvImport.SECOND)

    /** La misma clave con el instante redondeado hacia abajo a [precisionMs]. */
    fun truncated(precisionMs: Long): TimeKey = if (precisionMs <= 1) this else copy(at = Math.floorDiv(at, precisionMs) * precisionMs)
}
