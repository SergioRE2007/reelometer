package com.reelometro.app.copy

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Los textos legales y de tienda (carpeta `store/` y `PRIVACY.md`, en la raíz del proyecto)
 * tienen que decir exactamente lo mismo que la app. Los de `store/` no están en todas las copias del código:
 * sin ellos, esos tests se saltan.
 */
class StoreDocsTest {

    /** Los tests se ejecutan desde `app/`; por si acaso, también desde la raíz. */
    private fun projectFile(path: String): File =
        listOf(File("../$path"), File(path)).firstOrNull { it.exists() }
            ?: throw AssertionError("No encuentro $path (directorio actual: ${File(".").absolutePath})")

    /** Como [projectFile], pero si no está el test se salta (copias del código sin la carpeta `store/`). */
    private fun optionalFile(path: String): File {
        val file = listOf(File("../$path"), File(path)).firstOrNull { it.exists() }
        assumeTrue("Sin $path en esta copia del código", file != null)
        return file!!
    }

    private fun normalized(text: String): String = CopyRules.plain(text).replace(Regex("\\s+"), " ")

    @Test
    fun `la divulgación de la tienda es la misma que la de la app`() {
        val doc = normalized(optionalFile("store/prominent-disclosure.md").readText())
        DisclosureCopy.fullText.forEach { part ->
            assertTrue("store/prominent-disclosure.md no contiene, tal cual: «${CopyRules.plain(part)}»", normalized(part) in doc)
        }
    }

    @Test
    fun `la traducción inglesa de la divulgación de la tienda es la de la app`() {
        // 1.10.0: la sección «Traducción al inglés» de store/prominent-disclosure.md copia values-en (se desfasó una vez).
        val doc = normalized(optionalFile("store/prominent-disclosure.md").readText().replace(Regex("(?m)^>\\s?"), ""))
        val english = AppTexts.using(JvmTexts.forQualifier("en")) { DisclosureCopy.fullText }
        english.drop(2).forEach { part -> // la etiqueta y el título van con otro formato en el documento
            assertTrue("store/prominent-disclosure.md no contiene, tal cual: «${CopyRules.plain(part)}»", normalized(part) in doc)
        }
    }

    @Test
    fun `la ficha respeta los límites de Google Play`() {
        val listing = optionalFile("store/listing-es-ES.md").readText().replace("\r\n", "\n")
        fun block(name: String): String {
            val m = Regex("<!-- $name -->\\s*```text\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL).find(listing)
                ?: throw AssertionError("Falta el bloque «$name» en store/listing-es-ES.md")
            return m.groupValues[1]
        }
        val title = block("TITULO")
        val short = block("DESCRIPCION_CORTA")
        val long = block("DESCRIPCION_LARGA")
        assertTrue("Título de ${title.length} caracteres", title.length <= 30)
        assertTrue("Descripción corta de ${short.length} caracteres", short.length <= 80)
        assertTrue("Descripción larga de ${long.length} caracteres", long.length <= 4_000)
        listOf(title, short).forEach { assertTrue("Sin emojis ni exclamaciones: «$it»", CopyRules.violations(it).isEmpty()) }
        assertTrue('!' !in long && '¡' !in long)
        val notes = optionalFile("store/release-notes-1.0.md").readText().replace("\r\n", "\n")
        val whatsNew = Regex("```text\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL).find(notes)?.groupValues?.get(1)
            ?: throw AssertionError("Falta el bloque de texto en store/release-notes-1.0.md")
        assertTrue("Novedades de ${whatsNew.length} caracteres", whatsNew.length <= 500)
    }

    @Test
    fun `la política de privacidad existe y tiene contacto`() {
        val privacy = projectFile("PRIVACY.md").readText()
        assertTrue("PRIVACY.md tiene huecos sin rellenar", "[NOMBRE" !in privacy && "[EMAIL" !in privacy)
        assertTrue("PRIVACY.md debería dar una forma de contacto", privacy.contains("github.com/SergioRE2007/reelometer/issues"))
        listOf("accesibilidad", "internet", "borrar", "menores", "CSV").forEach {
            assertTrue("PRIVACY.md debería hablar de «$it»", privacy.contains(it, ignoreCase = true))
        }
    }
}
