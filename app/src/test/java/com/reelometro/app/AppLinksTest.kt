package com.reelometro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class AppLinksTest {

    @Test
    fun `la politica sale en espanol solo con los textos en espanol`() {
        assertEquals(AppLinks.PRIVACY_URL_ES, AppLinks.privacyUrl(Locale.forLanguageTag("es-ES")))
        assertEquals(AppLinks.PRIVACY_URL_ES, AppLinks.privacyUrl(Locale.forLanguageTag("es")))
        for (tag in listOf("en", "pt-BR", "fr", "it", "de")) {
            assertEquals(tag, AppLinks.PRIVACY_URL_EN, AppLinks.privacyUrl(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun `las urls de la politica son https y de la web del proyecto`() {
        for (url in listOf(AppLinks.PRIVACY_URL_ES, AppLinks.PRIVACY_URL_EN)) {
            assertTrue(url, url.startsWith("https://sergiore2007.github.io/reelometer/"))
            assertTrue(url, url.endsWith("/"))
        }
    }

    /** Si la web del proyecto está en este árbol, comprueba que sus rutas coinciden con las del enlace de Ajustes. */
    @Test
    fun `las rutas coinciden con las de la web`() {
        val build = listOf(File("../web/build.mjs"), File("web/build.mjs")).firstOrNull { it.exists() }
        assumeTrue("Sin la web en este árbol", build != null)
        val text = build!!.readText()
        val base = Regex("""const BASE = '([^']+)'""").find(text)?.groupValues?.get(1)
        val origin = Regex("""const ORIGIN = '([^']+)'""").find(text)?.groupValues?.get(1)
        val privacy = Regex("""privacy: \{[^}]*path: \{ es: '([^']+)', en: '([^']+)' \}""").find(text)
        assertTrue("No encuentro BASE, ORIGIN o la ruta de privacy en web/build.mjs", base != null && origin != null && privacy != null)
        assertEquals(origin + base + privacy!!.groupValues[1], AppLinks.PRIVACY_URL_ES)
        assertEquals(origin + base + privacy.groupValues[2], AppLinks.PRIVACY_URL_EN)
    }
}
