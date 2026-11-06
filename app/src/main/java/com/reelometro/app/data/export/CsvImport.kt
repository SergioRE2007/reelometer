package com.reelometro.app.data.export

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToLong

/** Cómo se importa: [ADD] suma lo nuevo sin duplicar; [REPLACE] borra antes todo lo que hay. */
enum class ImportMode { ADD, REPLACE }

/** Por qué no se puede importar un fichero (cada uno tiene su texto en `SettingsCopy`). */
enum class ImportError {
    /** La cabecera no es la de un CSV de Reelómetro. */
    NOT_REELOMETRO,

    /** No hay nada: ni vídeos ni envíos ni compartidos. */
    EMPTY,

    /** Más de [CsvImport.MAX_BYTES]. */
    TOO_LARGE,

    /** Más de [CsvImport.MAX_BAD_FRACTION] de las líneas no se entienden: no se importa nada. */
    TOO_MANY_BAD,

    /** No se ha podido leer o escribir (E/S, base de datos). */
    FAILED,
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
 * Un vídeo leído del CSV. [zone]: la de la columna `zona`, si venía. [exact]: el inicio viene de `inicio_ms` (al
 * milisegundo); si no, de la hora local, al segundo.
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
    val exact: Boolean = false,
)

/** Un compartido sin vídeo (bloque COMPARTIDOS SIN VÍDEO). */
data class ImportedShare(val platform: Platform, val at: Long, val zone: ZoneId?, val exact: Boolean = false)

/** Un envío de «A quién envías» (bloque ENVÍOS). */
data class ImportedRecipient(
    val platform: Platform,
    val at: Long,
    val recipient: String,
    val zone: ZoneId?,
    val exact: Boolean = false,
)

/** El fichero ya leído y entendido, listo para la vista previa y para importarlo. */
class ParsedCsv(
    /** En orden cronológico, sin filas repetidas idénticas (dos vídeos en el mismo segundo son dos vídeos). */
    val videos: List<ImportedVideo>,
    val orphanShares: List<ImportedShare>,
    val recipients: List<ImportedRecipient>,
    /** Los bloques que no son de vídeos ni de compartidos (PASAPORTE…), para cada `UserDataPart`. */
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

/**
 * Lee el CSV de exportación de cualquier versión (puro, sin Android ni base de datos):
 * - 1.0.0: solo los vídeos (`fecha_hora_local;plataforma;duración_s;creador;categoría;descripción;compartido`);
 * - 1.1.0+: detrás, el bloque PASAPORTE; 1.2.0+: el bloque ENVÍOS;
 * - 1.6.0+: las columnas `inicio_ms`, `zona` y `duración_ms` al final, el bloque COMPARTIDOS SIN VÍDEO y la
 *   columna `clave` del PASAPORTE.
 * Las columnas se buscan por nombre. Si están `inicio_ms`/`duración_ms`, mandan; si no, la hora local
 * se pasa a instante con [zone] (en un cambio de hora, el primer desfase) y la duración sale de `duración_s`.
 */
object CsvImport {
    /** Tope del fichero: una copia de varios años de uso intenso no llega a 30 MB. */
    const val MAX_BYTES = 50L * 1024 * 1024

    /** Si no se entiende más de esta parte de las líneas, no se importa nada. */
    const val MAX_BAD_FRACTION = 0.2

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

    fun parse(doc: CsvDocument, zone: ZoneId): CsvParse {
        val videoBlock = doc.videos ?: return CsvParse.Failed(ImportError.EMPTY)
        if (videoBlock.column(COL_TIME) == null || videoBlock.column(COL_PLATFORM) == null) {
            return CsvParse.Failed(ImportError.NOT_REELOMETRO)
        }
        var lines = 0
        var bad = 0

        val videos = ArrayList<ImportedVideo>(videoBlock.rows.size)
        for (row in videoBlock.rows) {
            lines++
            val video = video(videoBlock, row, zone)
            if (video == null) bad++ else videos += video
        }
        val recipients = ArrayList<ImportedRecipient>()
        doc.block(CsvExporter.RECIPIENTS_TITLE)?.let { block ->
            for (row in block.rows) {
                lines++
                val r = recipient(block, row, zone)
                if (r == null) bad++ else recipients += r
            }
        }
        val shares = ArrayList<ImportedShare>()
        doc.block(CsvExporter.ORPHAN_SHARES_TITLE)?.let { block ->
            for (row in block.rows) {
                lines++
                val s = orphanShare(block, row, zone)
                if (s == null) bad++ else shares += s
            }
        }
        if (lines > 0 && bad > lines * MAX_BAD_FRACTION) return CsvParse.Failed(ImportError.TOO_MANY_BAD)
        if (videos.isEmpty() && recipients.isEmpty() && shares.isEmpty()) return CsvParse.Failed(ImportError.EMPTY)

        // Solo se quitan las filas idénticas: al pasar un reel rápido, dos vídeos distintos empiezan en el mismo segundo.
        val unique = videos.distinct().sortedWith(compareBy<ImportedVideo> { it.startedAt }.thenBy { it.platform.ordinal })
        val known = setOf(CsvExporter.RECIPIENTS_TITLE, CsvExporter.ORPHAN_SHARES_TITLE).map(CsvReader::normalize)
        return CsvParse.Ok(
            ParsedCsv(
                videos = unique,
                orphanShares = shares.distinct(),
                recipients = recipients.distinct(),
                blocks = doc.blocks.filter { CsvReader.normalize(it.title) !in known },
                badLines = bad,
            ),
        )
    }

    /**
     * Separa lo nuevo de lo que ya está guardado ([existing]: plataforma, instante y un dato extra, como el nombre
     * del destinatario). Con la hora exacta (`inicio_ms`) se compara al milisegundo; sin ella (CSV viejos, al
     * segundo), por recuento: si en ese segundo ya hay dos guardados y el fichero trae dos, no entra ninguno; si trae
     * tres, entra uno. Así importar dos veces no duplica y dos vídeos en el mismo segundo no se pierden.
     * Devuelve lo nuevo y cuántos ya estaban.
     */
    fun <T> splitNew(
        items: List<T>,
        existing: List<TimeKey>,
        keyOf: (T) -> TimeKey,
        exact: (T) -> Boolean,
    ): Pair<List<T>, Int> {
        val byMs = HashMap<TimeKey, Int>()
        val bySecond = HashMap<TimeKey, Int>()
        existing.forEach { k ->
            byMs.merge(k, 1, Int::plus)
            bySecond.merge(k.toSecond(), 1, Int::plus)
        }
        val fresh = ArrayList<T>(items.size)
        var present = 0
        for (item in items) {
            val isExact = exact(item)
            val counts = if (isExact) byMs else bySecond
            val k = keyOf(item).let { if (isExact) it else it.toSecond() }
            val left = counts[k] ?: 0
            if (left > 0) {
                counts[k] = left - 1
                present++
            } else {
                fresh += item
            }
        }
        return fresh to present
    }

    private fun video(block: CsvBlock, row: List<String>, zone: ZoneId): ImportedVideo? {
        val platform = platform(block.field(row, COL_PLATFORM)) ?: return null
        val rowZone = zone(block.field(row, COL_ZONE))
        val start = instant(block.field(row, COL_START_MS), block.field(row, COL_TIME), rowZone ?: zone) ?: return null
        val duration = durationMs(block.field(row, COL_DURATION_MS), block.field(row, COL_DURATION_S)) ?: return null
        val shares = block.field(row, COL_SHARES).let { s ->
            if (s.isNullOrEmpty()) 0 else s.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        }
        return ImportedVideo(
            platform = platform,
            startedAt = start,
            durationMs = duration,
            creator = text(block.field(row, COL_CREATOR), CREATOR_MAX),
            caption = text(block.field(row, COL_CAPTION), CAPTION_MAX),
            category = category(block.field(row, COL_CATEGORY)),
            shares = shares.coerceAtMost(MAX_SHARES_PER_VIDEO),
            zone = rowZone,
            exact = block.field(row, COL_START_MS)?.toLongOrNull() != null,
        )
    }

    private fun recipient(block: CsvBlock, row: List<String>, zone: ZoneId): ImportedRecipient? {
        val platform = platform(block.field(row, COL_PLATFORM)) ?: return null
        val at = instant(block.field(row, COL_START_MS), block.field(row, COL_TIME), zone) ?: return null
        val name = text(block.field(row, COL_RECIPIENT), RECIPIENT_MAX) ?: return null
        return ImportedRecipient(platform, at, name, null, exact = block.field(row, COL_START_MS)?.toLongOrNull() != null)
    }

    private fun orphanShare(block: CsvBlock, row: List<String>, zone: ZoneId): ImportedShare? {
        val platform = platform(block.field(row, COL_PLATFORM)) ?: return null
        val at = instant(block.field(row, COL_START_MS), block.field(row, COL_TIME), zone) ?: return null
        return ImportedShare(platform, at, null, exact = block.field(row, COL_START_MS)?.toLongOrNull() != null)
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
        value?.takeIf { it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** `inicio_ms` si está y es un número; si no, la fecha y hora local en [zone]. */
    fun instant(startMs: String?, local: String?, zone: ZoneId): Long? {
        startMs?.toLongOrNull()?.let { return it }
        val time = localDateTime(local) ?: return null
        return try {
            ZonedDateTime.ofLocal(time, zone, null).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * La fecha y hora tal y como la escribe [CsvExporter] (`2026-11-03T14:37:05`) o como la deja una
     * hoja de cálculo (`2026-11-03 14:37:05`, `03/11/2026 14:37:05`, sin segundos…).
     */
    fun localDateTime(value: String?): LocalDateTime? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (format in DATE_TIMES) {
            try {
                return LocalDateTime.parse(v, format)
            } catch (_: DateTimeParseException) {
                // siguiente formato
            }
        }
        return null
    }

    /** `duración_ms` si está; si no, `duración_s` con coma o punto decimal. Vacío → 0; ilegible → `null`. */
    fun durationMs(ms: String?, seconds: String?): Long? {
        ms?.takeIf { it.isNotEmpty() }?.toLongOrNull()?.let { return it.coerceAtLeast(0) }
        val s = seconds?.replace(' ', ' ')?.replace(" ", "")?.takeIf { it.isNotEmpty() } ?: return 0
        val clean = if (s.contains(',') && s.contains('.')) {
            // «1.234,5» (millar con punto) o «1,234.5» (millar con coma): manda el último.
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        } else {
            s.replace(',', '.')
        }
        val value = clean.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        return (value * 1000).roundToLong()
    }

    private val DATE_TIMES: List<DateTimeFormatter> = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "d/M/yyyy H:mm:ss",
        "d/M/yyyy H:mm",
        "d-M-yyyy H:mm:ss",
        "d-M-yyyy H:mm",
        "d.M.yyyy H:mm:ss",
        "d.M.yyyy H:mm",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ROOT) }
}

/** Plataforma (nombre del enum), instante y un dato extra (el destinatario) para comparar con lo guardado. */
data class TimeKey(val platform: String, val at: Long, val extra: String = "") {
    /** La misma clave al segundo (para los CSV sin `inicio_ms`). */
    fun toSecond(): TimeKey = copy(at = Math.floorDiv(at, 1000L))
}
