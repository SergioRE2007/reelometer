package com.reelometro.app.detection

import com.reelometro.app.detection.ShortSignature.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortSignatureTest {

    private fun sig(creator: String?, caption: String?) = ShortSignature.of(creator, caption)!!

    @Test
    fun `normaliza arroba, mayusculas, acentos y signos`() {
        assertTrue(sig("@CocinaFácil", "La mejor tortilla de patatas!").sameAs(sig("cocina facil", "la mejor tortilla de patatas")))
        assertNull(ShortSignature.of(null, null))
        assertNull(ShortSignature.of("  ", "…"))
    }

    @Test
    fun `los titulos largos se comparan por el principio`() {
        val corto = sig("canal", "Esto es un título muy largo que la app corta de formas distintas")
        val largo = sig("canal", "Esto es un título muy largo que la app corta de formas distintas según el estado")
        assertTrue(corto.sameAs(largo))
    }

    @Test
    fun `mismo video exige autor y titulo`() {
        assertFalse(sig("canal", null).sameAs(sig("canal", null)))
        assertFalse(sig("canal", "uno dos tres cuatro").sameAs(sig("canal", "cinco seis siete ocho")))
    }

    @Test
    fun `cambio fuerte solo si todo lo comparable es distinto`() {
        assertEquals(Change.STRONG, sig("a_canal", "receta de croquetas").differsFrom(sig("b_canal", "golazo en el descuento")))
        // El autor sale distinto (nombre vs @handle) pero el título coincide: es el mismo vídeo.
        assertEquals(Change.NONE, sig("Cocina Fácil", "receta de croquetas").differsFrom(sig("cocinafacil_oficial", "receta de croquetas")))
        // Un solo campo comparable: débil.
        assertEquals(Change.WEAK, sig("a_canal", null).differsFrom(sig("b_canal", "golazo en el descuento")))
        // Solo el título comparable: no basta.
        assertEquals(Change.NONE, sig(null, "receta de croquetas").differsFrom(sig("b_canal", "golazo en el descuento")))
        // Nada comparable.
        assertEquals(Change.NONE, sig("a_canal", null).differsFrom(sig(null, "golazo en el descuento")))
    }

    @Test
    fun `enriquecer completa los campos que faltan`() {
        val s = sig("canal", null).enrichedWith(sig("otro", "título del vídeo"))
        assertEquals(sig("canal", "título del vídeo"), s)
    }
}
