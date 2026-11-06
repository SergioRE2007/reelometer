package com.reelometro.app.copy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Los textos de `res/values/` (el español, idioma por defecto) están bien formados: huecos
 * numerados, plurales con las formas del español, arrays con frases, espacios duros escritos como
 * `\u00A0` y las reglas de estilo de DESIGN §8. Lo mismo comprueba [I18nCheck] en cada
 * traducción.
 */
class ResourcesTest {

    private val texts = JvmTexts.spanish()

    /** Un hueco válido: `%1$s`, `%2$d`, o `%%`. */
    private val validPercent = Regex("%%|%\\d+\\$[sd]")

    /** Un «%» que no es hueco ni «%%». */
    private fun hasBadPlaceholder(text: String) = '%' in validPercent.replace(text, "")
    private val placeholder = Regex("%(\\d+)\\$[sd]")

    private fun all(): List<Pair<String, String>> = buildList {
        texts.stringNames.forEach { add(it to texts.rawString(it)!!) }
        texts.pluralNames.forEach { n -> texts.rawPlural(n)!!.forEach { (q, v) -> add("$n[$q]" to v) } }
        texts.arrayNames.forEach { n -> texts.rawArray(n)!!.forEachIndexed { i, v -> add("$n[$i]" to v) } }
    }

    @Test
    fun `huecos bien escritos`() {
        val bad = all().filter { (_, v) -> hasBadPlaceholder(v) }
        assertTrue("Huecos mal escritos (usa %1\$s, %2\$s… o %% para «%»):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `plurales con las formas del español y los mismos huecos`() {
        val problems = texts.pluralNames.mapNotNull { n ->
            val items = texts.rawPlural(n)!!
            val missing = listOf("one", "many", "other").filter { it !in items }
            val others = items.filterKeys { it != "one" }.values.map { v -> placeholder.findAll(v).map { it.value }.toSet() }.toSet()
            when {
                missing.isNotEmpty() -> "$n: faltan $missing"
                others.size > 1 -> "$n: las formas no tienen los mismos huecos"
                else -> null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `arrays sin frases vacias`() {
        val problems = texts.arrayNames.filter { n -> texts.rawArray(n)!!.let { it.isEmpty() || it.any(String::isBlank) } }
        assertTrue("Arrays vacíos o con frases vacías: $problems", problems.isEmpty())
    }

    @Test
    fun `espacios duros escritos como u00A0`() {
        val files = File(JvmTexts.resDir(), "values").listFiles { f -> f.extension == "xml" }.orEmpty()
        val bad = files.filter { '\u00A0' in it.readText() }.map { it.name }
        assertTrue("Escribe el espacio duro como \\u00A0 (no el carácter invisible) en: $bad", bad.isEmpty())
    }

    @Test
    fun `todos los textos cumplen las reglas de estilo`() {
        val problems = all().mapNotNull { (name, v) ->
            val sample = placeholder.replace(v, "X").replace("%%", "%")
            val allowEmoji = name.startsWith("share_")
            CopyRules.violations(sample, allowEmoji = allowEmoji, checkNumbers = false)
                // Los separadores («, ») son trozos con sus espacios a propósito.
                .filterNot { name.contains("_separator") && it == "espacios al principio o al final" }
                .takeIf { it.isNotEmpty() }?.let { "$name: «$sample» → $it" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
