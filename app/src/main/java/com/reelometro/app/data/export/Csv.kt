package com.reelometro.app.data.export

/**
 * Formato CSV para Excel en español: separador `;`, CRLF, comillas solo cuando hacen falta.
 * Puro (sin Android): lo usan [CsvExporter] y los bloques extra del CSV ([PassportCsv]).
 */
object Csv {
    const val SEPARATOR = ';'
    const val LINE_END = "\r\n"

    /** Marca de orden de bytes: Excel la necesita para leer UTF-8 (tildes, eñes, emojis). */
    const val BOM = "\uFEFF"

    private val FORMULA_START = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /** Una línea completa (con su fin de línea). */
    fun row(fields: List<String>): String =
        fields.joinToString(SEPARATOR.toString(), postfix = LINE_END) { escape(it) }

    /** Entrecomilla si el campo lleva separador, comillas, saltos de línea o espacios en los bordes. */
    fun escape(field: String): String {
        val needsQuotes = field.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' } ||
            field.startsWith(' ') || field.endsWith(' ')
        if (!needsQuotes) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    /**
     * Texto libre (creador, descripción): `null` → vacío, y si empieza como una fórmula
     * (`=`, `+`, `-`, `@`…) se antepone `'` para que la hoja de cálculo no la ejecute.
     */
    fun text(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return if (value[0] in FORMULA_START) "'$value" else value
    }

    /** Segundos con un decimal y coma decimal, sin separador de millar: 12345 ms → "12,3". */
    fun seconds(ms: Long): String {
        val tenths = (ms.coerceAtLeast(0) + 50) / 100
        return "${tenths / 10},${tenths % 10}"
    }
}
