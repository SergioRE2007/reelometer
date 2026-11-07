package com.reelometro.app.ui.components.flap

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.Locale

/** El separador de miles del marcador sigue al idioma: «7.052» en español, «7,052» en inglés, «7 052» en francés. */
class FlapGroupMarkTest {

    private fun of(tag: String) = FlapGroupMark.of(DecimalFormatSymbols.getInstance(Locale.forLanguageTag(tag)).groupingSeparator)

    @Test
    fun `marca por idioma`() {
        assertEquals(FlapGroupMark.DOT, FlapGroupMark.of('.'))
        assertEquals(FlapGroupMark.COMMA, of("en"))
        assertEquals(FlapGroupMark.SPACE, of("fr"))
        assertEquals(FlapGroupMark.DOT, of("pt-BR"))
        assertEquals(FlapGroupMark.DOT, of("it"))
        assertEquals(FlapGroupMark.DOT, FlapGroupMark.current) // los textos de los tests son los españoles
    }
}
