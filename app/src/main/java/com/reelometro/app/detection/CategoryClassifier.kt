package com.reelometro.app.detection

import com.reelometro.app.data.Category
import java.text.Normalizer

/**
 * Clasificador por palabras clave ([CategoryKeywords]). Funciona sin conexión y no envía nada a ningún sitio.
 *
 * Puntuación por categoría:
 *  - Palabra de la descripción: exacta +1; si no es exacta en ninguna categoría, raíz al principio +1.
 *  - Hashtag (los que vienen aparte y los que hay dentro de la descripción): exacto o «solo hashtag» +2;
 *    si no, compuesto +2: empieza o acaba por una raíz (#recetasfaciles, #spanishfood), contiene una raíz
 *    larga (≥ 7 letras) o empieza/acaba por una palabra de ≥ 5 letras (#realmadridcf, #tiktokciencia).
 *  - Frase de varias palabras («inteligencia artificial») +2.
 *  - Risas («jajaja», «jeje», «xd») +1 a Humor.
 * Gana la puntuación más alta (≥ [MIN_SCORE]); en empate, la categoría que va antes en [CategoryKeywords].
 */
object CategoryClassifier {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^a-z0-9]+")
    private val HASHTAG = Regex("#([\\p{L}\\p{N}_]+)")
    private val LAUGH = Regex("^a?(?:ja|je|ji|ha|he){2,}[jh]?$")
    private val XD = Regex("^x+d+$")

    const val MIN_SCORE = 1
    private const val CAPTION_WEIGHT = 1
    private const val TAG_WEIGHT = 2
    private const val PHRASE_WEIGHT = 2
    private const val LONG_STEM = 7
    private const val COMPOUND_WORD = 5

    /** Minúsculas y sin tildes (la ñ queda como n). */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    fun classify(caption: String?, hashtags: List<String>): Category {
        val scores = scores(caption, hashtags)
        var best = Category.OTROS
        var bestScore = MIN_SCORE - 1
        for ((category, _) in CategoryKeywords.BY_CATEGORY) {
            val score = scores[category] ?: 0
            if (score > bestScore) {
                best = category
                bestScore = score
            }
        }
        return best
    }

    /** Puntuación de cada categoría con alguna coincidencia (para tests y calibración). */
    internal fun scores(caption: String?, hashtags: List<String>): Map<Category, Int> {
        val scores = HashMap<Category, Int>()
        fun add(category: Category, points: Int) {
            scores[category] = (scores[category] ?: 0) + points
        }

        val rawCaption = caption.orEmpty()
        val tags = (hashtags + HASHTAG.findAll(rawCaption).map { it.groupValues[1] })
            .map { normalize(it).replace(NON_WORD, "") }
            .filter { it.isNotEmpty() }
            .distinct()
        val text = normalize(HASHTAG.replace(rawCaption, " "))
        val words = text.split(NON_WORD).filter { it.isNotEmpty() }

        for (word in words) {
            if (LAUGH.matches(word) || XD.matches(word)) {
                add(Category.HUMOR, CAPTION_WEIGHT)
                continue
            }
            val exact = CategoryKeywords.BY_CATEGORY.filterValues { word in it.words }.keys
            if (exact.isNotEmpty()) {
                exact.forEach { add(it, CAPTION_WEIGHT) }
                continue
            }
            if (isExactAnywhere(word, includeTagOnly = true)) continue // palabra ambigua: no se adivina por raíz
            for ((category, keywords) in CategoryKeywords.BY_CATEGORY) {
                if (keywords.stems.any { word.startsWith(it) }) add(category, CAPTION_WEIGHT)
            }
        }

        for (tag in tags) {
            if (LAUGH.matches(tag) || XD.matches(tag)) {
                add(Category.HUMOR, TAG_WEIGHT)
                continue
            }
            val exact = CategoryKeywords.BY_CATEGORY.filterValues { tag in it.words || tag in it.tagOnly }.keys
            if (exact.isNotEmpty()) {
                exact.forEach { add(it, TAG_WEIGHT) }
                continue
            }
            for ((category, keywords) in CategoryKeywords.BY_CATEGORY) {
                val byStem = keywords.stems.any { stem ->
                    tag.startsWith(stem) || tag.endsWith(stem) || (stem.length >= LONG_STEM && tag.contains(stem))
                }
                val byWord = !byStem && keywords.words.any { w ->
                    w.length >= COMPOUND_WORD && (tag.startsWith(w) || tag.endsWith(w))
                }
                if (byStem || byWord) add(category, TAG_WEIGHT)
            }
        }

        if (text.isNotBlank()) {
            val spaced = " " + words.joinToString(" ") + " "
            for ((category, keywords) in CategoryKeywords.BY_CATEGORY) {
                keywords.phrases.forEach { phrase -> if (spaced.contains(" $phrase ")) add(category, PHRASE_WEIGHT) }
            }
        }
        return scores
    }

    private fun isExactAnywhere(token: String, includeTagOnly: Boolean): Boolean =
        CategoryKeywords.BY_CATEGORY.values.any { token in it.words || (includeTagOnly && token in it.tagOnly) }
}
