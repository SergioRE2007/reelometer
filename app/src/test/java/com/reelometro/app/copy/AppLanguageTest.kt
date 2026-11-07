package com.reelometro.app.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Selector de idioma dentro de la app: nombres de las opciones y cuál está elegida. */
class AppLanguageTest {
    private val supported = listOf("es-ES", "en", "fr", "it", "pt-BR").map(Locale::forLanguageTag)
    private val tags = supported.map { it.toLanguageTag() }

    @Test
    fun optionsStartWithThePhoneAndUseEachLanguageOwnName() {
        val options = languageOptions(supported, baseTag = "es-ES", systemLabel = "El del móvil")
        assertEquals(LanguageOption(null, "El del móvil"), options.first())
        assertEquals(
            listOf("English", "Español", "Français", "Italiano", "Português (Brasil)"),
            options.drop(1).map { it.label },
        )
        assertEquals(listOf(null, "en", "es-ES", "fr", "it", "pt-BR"), options.map { it.tag })
    }

    @Test
    fun noRepeatedLanguages() {
        val options = languageOptions(supported + Locale.forLanguageTag("es-ES"), "es-ES", "x")
        assertEquals(6, options.size)
    }

    @Test
    fun selectedFollowsTheChosenAppLanguage() {
        assertNull(selectedLanguageTag(null, tags))
        assertEquals("pt-BR", selectedLanguageTag(Locale.forLanguageTag("pt-BR"), tags))
        assertEquals("es-ES", selectedLanguageTag(Locale.forLanguageTag("es-ES"), tags))
        // Elegido desde Android con otra región: la opción de ese idioma.
        assertEquals("es-ES", selectedLanguageTag(Locale.forLanguageTag("es-FR"), tags))
        assertEquals("en", selectedLanguageTag(Locale.forLanguageTag("en-ES"), tags))
        // Un idioma que la app no tiene: ninguna marcada.
        assertNull(selectedLanguageTag(Locale.forLanguageTag("de"), tags))
    }
}
