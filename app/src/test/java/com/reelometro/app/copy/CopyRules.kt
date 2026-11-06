package com.reelometro.app.copy

import com.reelometro.app.stats.Format

/**
 * Reglas de estilo de DESIGN §8 que se pueden comprobar a máquina. Devuelve la lista de
 * incumplimientos de un texto (vacía si está bien).
 */
object CopyRules {

    private val BANNED = listOf(
        "genial", "enhorabuena", "increíble", "deberías", "usuario", "usted", "es importante que", "salud mental",
    )

    /** «1 vídeos», «1 DÍAS»… (en minúsculas para no depender de mayúsculas acentuadas). */
    private val ONE_WITH_PLURAL = Regex(
        "(?<![\\d.,])1[\\u00A0 ](vídeos|minutos|horas|días|veces|segundos|eventos|compartidos|libros|siestas)\\b",
    )

    /** «0 vídeo», «0 día»… */
    private val ZERO_WITH_SINGULAR = Regex("(?<![\\d.,])0[\\u00A0 ](vídeo|minuto|hora|día|vez|segundo)(?![\\p{L}])")

    /** Más de 4 cifras seguidas: faltaría el punto de millar. */
    private val UNGROUPED = Regex("\\d{5,}")

    /** 4 cifras seguidas que no son un año. */
    private val FOUR_DIGITS = Regex("(?<![\\d.,])\\d{4}(?![\\d.,])")

    /** Punto decimal («3.2») en vez de coma. Los grupos de millar tienen 3 cifras y no casan. */
    private val DECIMAL_POINT = Regex("\\d\\.\\d{1,2}(?!\\d)")

    /** «%» sin espacio duro delante. */
    private val BAD_PERCENT = Regex("(?<!\\u00A0)%")

    /** Espacio (normal o duro) antes de un signo de puntuación de cierre. */
    private val SPACE_BEFORE_PUNCT = Regex("[ \\u00A0][.,;:?)»]")

    fun violations(text: String, allowEmoji: Boolean = false, checkNumbers: Boolean = true): List<String> {
        val out = mutableListOf<String>()
        if (text.isBlank()) out += "vacío"
        if (text != text.trim()) out += "espacios al principio o al final"
        if ('¡' in text || '!' in text) out += "exclamación"
        val lower = text.lowercase(Format.locale)
        BANNED.filter { it in lower }.forEach { out += "palabra prohibida «$it»" }
        if (!allowEmoji && containsEmoji(text)) out += "emoji"
        if ("  " in text || "  " in text || "  " in text || "  " in text) out += "doble espacio"
        if ("..." in text || '…' in text) out += "puntos suspensivos"
        if ('?' in text && '¿' !in text) out += "falta «¿»"
        if (text.count { it == '¿' } != text.count { it == '?' }) out += "«¿» y «?» desparejados"
        if (SPACE_BEFORE_PUNCT.containsMatchIn(text)) out += "espacio antes de puntuación"
        if (ONE_WITH_PLURAL.containsMatchIn(lower)) out += "«1» con plural"
        if (ZERO_WITH_SINGULAR.containsMatchIn(lower)) out += "«0» con singular"
        if (checkNumbers) {
            if (UNGROUPED.containsMatchIn(text)) out += "cifra sin punto de millar"
            FOUR_DIGITS.findAll(text).map { it.value.toInt() }.filter { it !in 1900..2100 }
                .forEach { out += "cifra de 4 dígitos sin formato ($it)" }
            if (DECIMAL_POINT.containsMatchIn(text)) out += "punto decimal en vez de coma"
            if (BAD_PERCENT.containsMatchIn(text)) out += "«%» sin espacio duro"
        }
        return out
    }

    fun containsEmoji(text: String): Boolean = text.codePoints().anyMatch { cp ->
        cp in 0x1F000..0x1FAFF ||
            (cp in 0x2600..0x27BF && cp != 0x2713 && cp != 0x2717) ||
            cp in 0x2B00..0x2BFF ||
            cp in 0x231A..0x231B ||
            cp in 0x23E9..0x23FA ||
            cp == 0xFE0F || cp == 0x200D
    }

    /** Para comparar con textos escritos a mano: espacio duro → espacio normal. */
    fun plain(text: String): String = text.replace(Format.NBSP, ' ')
}
