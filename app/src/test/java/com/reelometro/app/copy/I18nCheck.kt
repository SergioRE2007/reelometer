package com.reelometro.app.copy

import org.w3c.dom.Element
import org.xml.sax.SAXParseException
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Comprueba una traducción (`res/values-xx/`) contra el español (`res/values/`). Lo usan
 * [I18nCheckTest] (que admite elegir idiomas y modo con variables de entorno) y la batería de tests normal (todas las
 * carpetas `values-xx` que haya tienen que pasar sin errores).
 *
 * Errores: XML mal formado, claves que faltan o sobran, huecos (`%1$s`) distintos, huecos mal
 * escritos, plurales sin las formas que pide el idioma, arrays vacíos o con frases vacías,
 * apóstrofos sin escapar, `locale_tag` ausente. Avisos: arrays con una sola frase, formas de plural
 * que el idioma no usa, emojis, espacios duros invisibles.
 */
object I18nCheck {

    class Resources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>,
        /** Claves con `translatable="false"` (no se traducen). */
        val untranslatable: Set<String>,
        /** Fichero de cada clave. */
        val fileOf: Map<String, String>,
        val parseErrors: List<String>,
        val rawErrors: List<String>,
        val rawWarnings: List<String>,
    ) {
        val keys: Set<String> get() = strings.keys + plurals.keys + arrays.keys
    }

    class Report(val qualifier: String, val errors: List<String>, val warnings: List<String>, val translated: Int, val total: Int)

    /**
     * Formas de plural (CLDR, números enteros) que Android pide en cada idioma. Si el idioma no
     * está, se piden `one` y `other` y se avisa.
     */
    private val PLURALS: Map<String, List<String>> = buildMap {
        listOf("ja", "zh", "ko", "vi", "th", "id", "ms", "my", "lo", "km").forEach { put(it, listOf("other")) }
        listOf("en", "de", "nl", "sv", "da", "nb", "no", "fi", "et", "el", "hu", "tr", "bg", "eu", "gl", "af", "sq", "hi", "bn", "ur", "fa", "sw", "ka", "az", "kk", "hy")
            .forEach { put(it, listOf("one", "other")) }
        listOf("es", "it", "pt", "ca", "fr").forEach { put(it, listOf("one", "many", "other")) }
        listOf("ru", "uk", "be", "pl", "lt").forEach { put(it, listOf("one", "few", "many", "other")) }
        listOf("cs", "sk").forEach { put(it, listOf("one", "few", "many", "other")) }
        listOf("hr", "sr", "bs", "ro").forEach { put(it, listOf("one", "few", "other")) }
        put("sl", listOf("one", "two", "few", "other"))
        put("he", listOf("one", "two", "other"))
        put("iw", listOf("one", "two", "other"))
        put("lv", listOf("zero", "one", "other"))
        put("ar", listOf("zero", "one", "two", "few", "many", "other"))
        put("cy", listOf("zero", "one", "two", "few", "many", "other"))
        put("ga", listOf("one", "two", "few", "many", "other"))
    }

    private val placeholder = Regex("%(\\d+)\\$[sd]")
    private val validPercent = Regex("%%|%\\d+\\$[sd]")

    /** Un «%» que no es hueco ni «%%». */
    private fun hasBadPlaceholder(text: String) = '%' in validPercent.replace(text, "")
    private val emoji = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]")

    fun resDir(): File = JvmTexts.resDir()

    /** Carpetas de traducción que hay (`en`, `pt-rBR`, `b+sr+Latn`). */
    fun qualifiers(): List<String> = resDir().listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { Regex("values-([a-z]{2,3}(-r[A-Z]{2})?|b\\+[A-Za-z0-9+]+)").matchEntire(it.name)?.groupValues?.get(1) }
        .sorted()

    /** Idioma (para las reglas de plural) de un calificador: `pt-rBR` → `pt`, `b+sr+Latn` → `sr`. */
    fun language(qualifier: String): String = qualifier.removePrefix("b+").split('-', '+').first()

    fun load(dir: File): Resources {
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val arrays = linkedMapOf<String, List<String>>()
        val untranslatable = mutableSetOf<String>()
        val fileOf = mutableMapOf<String, String>()
        val parseErrors = mutableListOf<String>()
        val rawErrors = mutableListOf<String>()
        val rawWarnings = mutableListOf<String>()
        dir.listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy { it.name }.forEach { file ->
            val text = file.readText()
            if (' ' in text) rawWarnings += "${file.name}: hay espacios duros invisibles; escríbelos como \\u00A0"
            val doc = try {
                DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(file)
            } catch (e: SAXParseException) {
                parseErrors += "${file.name}:${e.lineNumber}: XML mal formado (etiqueta sin cerrar, «&» o «<» sueltos…): ${e.message}"
                return@forEach
            }
            val children = doc.documentElement.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i) as? Element ?: continue
                val name = node.getAttribute("name")
                if (node.getAttribute("translatable") == "false") untranslatable += name
                fileOf[name] = file.name
                fun raw(e: Element) {
                    val t = e.textContent
                    if (hasBareApostrophe(t)) rawErrors += "$name: apóstrofo sin escapar (escribe \\' o usa ’)"
                }
                when (node.tagName) {
                    "string" -> {
                        raw(node)
                        strings[name] = JvmTexts.unescape(node.textContent)
                    }
                    "plurals" -> plurals[name] = node.items().onEach(::raw).associate { it.getAttribute("quantity") to JvmTexts.unescape(it.textContent) }
                    "string-array" -> arrays[name] = node.items().onEach(::raw).map { JvmTexts.unescape(it.textContent) }
                }
            }
        }
        return Resources(strings, plurals, arrays, untranslatable, fileOf, parseErrors, rawErrors, rawWarnings)
    }

    private fun Element.items(): List<Element> {
        val list = getElementsByTagName("item")
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun hasBareApostrophe(text: String): Boolean {
        var quoted = false
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                '"' -> quoted = !quoted
                '\'' -> if (!quoted) return true
            }
            i++
        }
        return false
    }

    private fun holes(text: String): Set<String> = placeholder.findAll(text).map { it.groupValues[1] }.toSet()

    fun check(qualifier: String): Report {
        val base = load(File(resDir(), "values"))
        val tr = load(File(resDir(), "values-$qualifier"))
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        errors += tr.parseErrors
        errors += tr.rawErrors
        warnings += tr.rawWarnings
        val language = language(qualifier)
        val required = PLURALS[language] ?: listOf("one", "other").also {
            warnings += "No conozco las formas de plural de «$language»: se piden one y other (revisa las reglas de CLDR)"
        }

        val toTranslate = base.keys - base.untranslatable
        val missing = toTranslate - tr.keys
        missing.groupBy { base.fileOf[it] }.forEach { (file, keys) ->
            errors += "Faltan ${keys.size} en $file: ${keys.sorted().joinToString(", ")}"
        }
        (tr.keys - base.keys).sorted().forEach { errors += "Sobra «$it» (no existe en values/: ¿nombre cambiado?)" }
        (tr.keys intersect base.untranslatable).sorted().forEach { warnings += "«$it» no se traduce (translatable=\"false\"): bórralo" }
        if ("locale_tag" !in tr.strings) errors += "Falta locale_tag (en format.xml): la etiqueta de tu idioma, p. ej. «en» o «pt-BR»"

        for ((name, value) in tr.strings) {
            val es = base.strings[name] ?: continue
            if (hasBadPlaceholder(value)) errors += "$name: hueco mal escrito («%» suelto: usa %% para el signo)"
            if (holes(value) != holes(es)) errors += "$name: huecos ${holes(value).sorted()} y en español ${holes(es).sorted()}"
            if (value.isBlank()) errors += "$name: vacío"
            if (emoji.containsMatchIn(value) && !name.startsWith("share_")) warnings += "$name: lleva emoji (DESIGN.md: sin emojis)"
        }
        for ((name, items) in tr.plurals) {
            val es = base.plurals[name] ?: continue
            val missingQ = required - items.keys
            if (missingQ.isNotEmpty()) errors += "$name: faltan las formas $missingQ (tu idioma pide $required)"
            (items.keys - required.toSet()).forEach { warnings += "$name: la forma «$it» no la usa tu idioma (se ignora)" }
            val esHoles = es.values.flatMap { holes(it) }.toSet()
            items.forEach { (q, v) ->
                if (hasBadPlaceholder(v)) errors += "$name[$q]: hueco mal escrito"
                if (!esHoles.containsAll(holes(v))) errors += "$name[$q]: usa huecos que el español no tiene (${holes(v) - esHoles})"
                if (v.isBlank()) errors += "$name[$q]: vacío"
            }
            val union = items.values.flatMap { holes(it) }.toSet()
            if (union != esHoles) errors += "$name: entre todas las formas, huecos ${union.sorted()} y en español ${esHoles.sorted()}"
        }
        for ((name, items) in tr.arrays) {
            val es = base.arrays[name] ?: continue
            val esHoles = es.flatMap { holes(it) }.toSet()
            if (items.isEmpty()) errors += "$name: array vacío (pon al menos 2 frases)"
            else if (items.size < 2 && es.size >= 2) warnings += "$name: una sola frase (mejor 2 o más, para no repetir)"
            items.forEachIndexed { i, v ->
                if (v.isBlank()) errors += "$name[$i]: frase vacía"
                if (hasBadPlaceholder(v)) errors += "$name[$i]: hueco mal escrito"
                if (!esHoles.containsAll(holes(v))) errors += "$name[$i]: usa huecos que el español no tiene (${holes(v) - esHoles})"
                if (emoji.containsMatchIn(v) && !name.startsWith("share_")) warnings += "$name[$i]: lleva emoji"
            }
        }
        val translated = (toTranslate intersect tr.keys).size
        return Report(qualifier, errors, warnings, translated, toTranslate.size)
    }

    fun format(report: Report): String = buildString {
        val pct = if (report.total == 0) 100 else report.translated * 100 / report.total
        appendLine("values-${report.qualifier}: ${report.translated}/${report.total} claves ($pct %), ${report.errors.size} errores, ${report.warnings.size} avisos")
        report.errors.forEach { appendLine("  ERROR  $it") }
        report.warnings.forEach { appendLine("  aviso  $it") }
    }
}
