package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationRedactorTest {

    private fun redact(text: String, vararg names: String) = CalibrationRedactor.redact(text, names.toList()) { "[#$it]" }

    @Test
    fun `cambia nombres enteros sin distinguir mayusculas`() {
        assertEquals("enviado a [#1] y a [#1]", redact("enviado a Marta y a MARTA", "marta"))
    }

    @Test
    fun `no toca palabras que solo empiezan igual`() {
        val text = "Analítica · clase=AnaView · id=ana_row · Ana2 · Ana."
        assertEquals("Analítica · clase=AnaView · id=ana_row · Ana2 · [#1].", redact(text, "Ana"))
    }

    @Test
    fun `el nombre mas largo va primero`() {
        assertEquals("[#2] y [#1]", redact("Ana María y Ana", "Ana", "Ana María"))
    }

    @Test
    fun `grupos con espacios, emojis y tildes`() {
        val text = "destino: Familia 🏠 · destino: Los de Cádiz · los de cádiz"
        assertEquals("destino: [#1] · destino: [#2] · [#2]", redact(text, "Familia 🏠", " Los de Cádiz "))
    }

    @Test
    fun `numeros estables por orden de entrada y sin repetidos ni cortos`() {
        val text = "Bea, Carlos, Bea, X"
        assertEquals("[#2], [#1], [#2], X", redact(text, "Carlos", "", "X", "Bea", "carlos"))
    }

    @Test
    fun `descarta nombres de menos de tres letras y los que no llevan letras`() {
        val text = "Al no aceptó 10 · v1.10.4 · ... · Al"
        assertEquals(text, redact(text, "Al", "No", "10", "1.10", "...", "  ", "😀"))
        assertEquals(null, CalibrationRedactor.compile(listOf("Al", "10")) { "[#$it]" })
    }

    @Test
    fun `sin nombres devuelve el mismo texto`() {
        val text = "Reelometer · registro de calibración\nSCROLL TikTok ✓"
        assertEquals(text, CalibrationRedactor.redact(text, emptyList()) { error("no se usa") })
    }

    @Test
    fun `nunca toca las marcas ni Reelometer`() {
        val text = "Reelometer · TikTok · Instagram · YouTube · Reels · Shorts · YouTube Shorts"
        assertEquals(text, redact(text, "TikTok", "instagram", "YouTube", "Reelometer", "Reels", "Shorts", "YouTube Shorts"))
    }

    @Test
    fun `lo que pone no se vuelve a cambiar`() {
        // Un contacto que se llama como el propio token no debe romper los ya puestos.
        assertEquals("[contacto 1] [contacto 2]", CalibrationRedactor.redact("Pepe contacto", listOf("Pepe", "contacto")) { "[contacto $it]" })
    }
}
