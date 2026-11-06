package com.reelometro.app.data.export

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/**
 * Lectura tolerante del CSV de Reelómetro (puro, sin Android): lo que escribe [CsvExporter] y lo que
 * deja una hoja de cálculo al volver a guardarlo.
 *
 * - Con o sin BOM; UTF-8 o, si los bytes no son UTF-8 válido, Windows-1252 (Excel «CSV» a secas).
 * - Separador `;` o `,` (se mira la primera línea), fin de línea CRLF, LF o CR.
 * - Campos entre comillas con separadores, comillas dobles (`""`) o saltos de línea dentro.
 * - Bloques ([CsvDocument]): los vídeos arriba y, detrás, `TÍTULO` + cabecera + filas.
 */
object CsvReader {

    /** Texto del fichero: quita el BOM y elige la codificación. */
    fun decode(bytes: ByteArray): String {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix(Csv.BOM)
    }

    /** `;` salvo que la primera línea solo tenga comas. */
    fun separatorOf(text: String): Char {
        val end = text.indexOfAny(charArrayOf('\n', '\r')).let { if (it < 0) text.length else it }
        val first = text.substring(0, end)
        return if (first.contains(Csv.SEPARATOR) || !first.contains(',')) Csv.SEPARATOR else ','
    }

    /** Todas las filas, con los campos ya sin comillas. Una línea vacía es una fila con un campo vacío. */
    fun rows(text: String, separator: Char = separatorOf(text)): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (field.isEmpty()) quoted = true else field.append(c)
                    separator -> {
                        fields += field.toString()
                        field.setLength(0)
                    }
                    '\r', '\n' -> {
                        if (c == '\r' && i + 1 < n && text[i + 1] == '\n') i++
                        fields += field.toString()
                        field.setLength(0)
                        rows += fields
                        fields = ArrayList()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields += field.toString()
            rows += fields
        }
        return rows
    }

    /** Fila sin nada (una hoja de cálculo rellena las líneas en blanco con `;;;`). */
    fun isBlank(row: List<String>): Boolean = row.all { it.isBlank() }

    /** Lee [text] y lo separa en bloques. */
    fun document(text: String): CsvDocument = CsvDocument.of(rows(text))

    /**
     * Nombre de columna o título comparable: sin espacios en los bordes, en minúsculas y sin tildes
     * (`Duración_s` = `duracion_s`), por si la hoja de cálculo o la codificación los ha tocado.
     */
    fun normalize(name: String): String =
        Normalizer.normalize(name.trim().removePrefix(Csv.BOM).lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")

    private val DIACRITICS = Regex("\\p{Mn}+")
}

/** Título, cabecera y filas de un bloque (las columnas se buscan por nombre: el orden da igual). */
data class CsvBlock(val title: String, val header: List<String>, val rows: List<List<String>>) {
    private val index: Map<String, Int> =
        header.withIndex().associate { (i, name) -> CsvReader.normalize(name) to i }

    /** Posición de la columna [name] (comparada con [CsvReader.normalize]), o `null` si no está. */
    fun column(name: String): Int? = index[CsvReader.normalize(name)]

    /** Campo [name] de [row] sin espacios en los bordes; `null` si la columna no está o la fila es corta. */
    fun field(row: List<String>, name: String): String? = column(name)?.let { row.getOrNull(it) }?.trim()
}

/**
 * El CSV en bloques: [videos] (la primera cabecera y sus filas; su título es vacío) y los bloques
 * con título detrás ([blocks]: ENVÍOS, COMPARTIDOS SIN VÍDEO, PASAPORTE…). Las filas en blanco no
 * entran en ningún bloque.
 *
 * Un bloque empieza con una fila de un solo campo (el título), normalmente tras una línea en blanco,
 * y su cabecera es la siguiente fila con algo. Una fila de vídeo nunca tiene un solo campo.
 */
class CsvDocument(val videos: CsvBlock?, val blocks: List<CsvBlock>) {

    /** El bloque con este título (sin distinguir mayúsculas ni tildes), si está. */
    fun block(title: String): CsvBlock? = blocks.firstOrNull { CsvReader.normalize(it.title) == CsvReader.normalize(title) }

    companion object {
        fun of(rows: List<List<String>>): CsvDocument {
            val blocks = ArrayList<CsvBlock>()
            var title = ""
            var header: List<String>? = null
            var body = ArrayList<List<String>>()
            var afterBlank = false
            fun close() {
                header?.let { blocks += CsvBlock(title, it, body) }
            }
            for (row in rows) {
                if (CsvReader.isBlank(row)) {
                    afterBlank = true
                    continue
                }
                // Título: un solo campo, tras una línea en blanco o sin cifras (una fila de vídeo rota
                // con solo la fecha no se come los bloques de detrás).
                val single = row.drop(1).all { it.isBlank() }
                val isTitle = header != null && single && (afterBlank || row[0].none(Char::isDigit))
                when {
                    header == null -> header = row
                    isTitle -> {
                        close()
                        title = row[0].trim()
                        header = null
                        body = ArrayList()
                    }
                    else -> body += row
                }
                afterBlank = false
            }
            close()
            val first = blocks.firstOrNull()?.takeIf { it.title.isEmpty() }
            return CsvDocument(first, if (first == null) blocks else blocks.drop(1))
        }
    }
}
