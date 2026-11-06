package com.reelometro.app.copy

import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [Texts] para los tests JVM: lee `src/main/res/values/` (y, si se pide, una traducción encima,
 * como haría Android con `values-xx/`) a mano, sin dispositivo ni Robolectric, y casa los ids de
 * `R` por reflexión. Sigue las reglas de aapt2 para los textos: espacios agrupados, comillas que
 * conservan espacios, `\n`, `\'`, `\"`, `\@`, `\?`, `\\` y `\uXXXX`.
 *
 * `AppTexts` la carga sola si nadie ha instalado textos ([spanish]).
 */
class JvmTexts private constructor(
    private val strings: Map<String, String>,
    private val plurals: Map<String, Map<String, String>>,
    private val arrays: Map<String, List<String>>,
) : Texts {

    override val locale: Locale = Locale.forLanguageTag(strings["locale_tag"] ?: "es-ES")

    private val pluralRule: (Int) -> String = rule(locale.language)

    override fun string(id: Int, vararg args: Any): String {
        val name = Ids.name("string", id)
        val raw = strings[name] ?: error("Falta el texto «$name» en res/values")
        return if (args.isEmpty()) raw else format(raw, *args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val name = Ids.name("plurals", id)
        val items = plurals[name] ?: error("Falta el plural «$name» en res/values")
        val raw = items[pluralRule(count)] ?: items["other"] ?: error("El plural «$name» no tiene «other»")
        return if (args.isEmpty()) raw else format(raw, *args)
    }

    override fun array(id: Int): List<String> {
        val name = Ids.name("array", id)
        return arrays[name] ?: error("Falta el array «$name» en res/values")
    }

    /** Nombres de todo lo que hay (para comprobar estilo o huecos en los tests). */
    val stringNames: Set<String> get() = strings.keys
    val pluralNames: Set<String> get() = plurals.keys
    val arrayNames: Set<String> get() = arrays.keys
    fun rawString(name: String): String? = strings[name]
    fun rawPlural(name: String): Map<String, String>? = plurals[name]
    fun rawArray(name: String): List<String>? = arrays[name]

    companion object {

        private val cache = mutableMapOf<String, JvmTexts>()

        /** Los textos por defecto (español, `values/`). */
        @JvmStatic
        fun spanish(): JvmTexts = forQualifier(null)

        /**
         * `values/` con `values-[qualifier]/` encima (lo que falte sale en español, como en el móvil).
         * Ej.: `forQualifier("en")`, `forQualifier("pt-rBR")`.
         */
        @JvmStatic
        @Synchronized
        fun forQualifier(qualifier: String?): JvmTexts = cache.getOrPut(qualifier ?: "") {
            val res = resDir()
            val strings = linkedMapOf<String, String>()
            val plurals = linkedMapOf<String, Map<String, String>>()
            val arrays = linkedMapOf<String, List<String>>()
            val dirs = listOfNotNull(File(res, "values"), qualifier?.let { File(res, "values-$it") })
            for (dir in dirs) {
                dir.listFiles { f -> f.extension == "xml" }?.sortedBy { it.name }?.forEach { file ->
                    parse(file, strings, plurals, arrays)
                }
            }
            JvmTexts(strings, plurals, arrays)
        }

        fun resDir(): File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

        private fun parse(
            file: File,
            strings: MutableMap<String, String>,
            plurals: MutableMap<String, Map<String, String>>,
            arrays: MutableMap<String, List<String>>,
        ) {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            val doc = factory.newDocumentBuilder().parse(file)
            val children = doc.documentElement.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i) as? Element ?: continue
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "string" -> strings[name] = unescape(node.textContent)
                    "plurals" -> plurals[name] = node.elements("item").associate { it.getAttribute("quantity") to unescape(it.textContent) }
                    "string-array" -> arrays[name] = node.elements("item").map { unescape(it.textContent) }
                }
            }
        }

        private fun Element.elements(tag: String): List<Element> {
            val list = getElementsByTagName(tag)
            return (0 until list.length).map { list.item(it) as Element }
        }

        /** Reglas de aapt2 para el texto de un recurso. */
        fun unescape(raw: String): String {
            val sb = StringBuilder()
            var quoted = false
            var pendingSpace = false
            var i = 0
            fun put(c: Char) {
                if (pendingSpace) {
                    sb.append(' ')
                    pendingSpace = false
                }
                sb.append(c)
            }
            while (i < raw.length) {
                val c = raw[i]
                when {
                    c == '\\' && i + 1 < raw.length -> {
                        val n = raw[i + 1]
                        when (n) {
                            'n' -> put('\n')
                            't' -> put('\t')
                            'u' -> {
                                put(raw.substring(i + 2, i + 6).toInt(16).toChar())
                                i += 4
                            }
                            else -> put(n)
                        }
                        i += 2
                    }
                    c == '"' -> {
                        quoted = !quoted
                        i++
                    }
                    !quoted && (c == ' ' || c == '\n' || c == '\t' || c == '\r') -> {
                        if (sb.isNotEmpty()) pendingSpace = true
                        i++
                    }
                    else -> {
                        if (quoted && pendingSpace) {
                            sb.append(' ')
                            pendingSpace = false
                        }
                        put(c)
                        i++
                    }
                }
            }
            return sb.toString()
        }

        /** Categoría de plural de CLDR para los idiomas más habituales (lo justo para los tests). */
        private fun rule(language: String): (Int) -> String = when (language) {
            "es", "it", "pt", "ca" -> { n -> if (n == 1) "one" else if (n != 0 && n % 1_000_000 == 0) "many" else "other" }
            "fr" -> { n -> if (n == 0 || n == 1) "one" else if (n != 0 && n % 1_000_000 == 0) "many" else "other" }
            "ja", "zh", "ko" -> { _ -> "other" }
            else -> { n -> if (n == 1) "one" else "other" }
        }
    }

    /** Ids de `R` → nombres (por reflexión, sirve con el `R` de Android y con uno generado). */
    private object Ids {
        private val maps = mutableMapOf<String, Map<Int, String>>()

        @Synchronized
        fun name(type: String, id: Int): String = maps.getOrPut(type) {
            val cls = Class.forName("com.reelometro.app.R\$$type")
            cls.declaredFields.filter { it.type == Int::class.javaPrimitiveType }
                .associate { f -> f.isAccessible = true; f.getInt(null) to f.name }
        }[id] ?: error("Id $id sin nombre en R.$type")
    }
}
