package com.reelometro.app.detection

import com.reelometro.app.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CategoryClassifierTest {

    private fun classify(caption: String?, vararg tags: String) = CategoryClassifier.classify(caption, tags.toList())

    @Test
    fun `hashtags compuestos`() {
        assertEquals(Category.COMIDA, classify(null, "recetasfaciles"))
        assertEquals(Category.COMIDA, classify(null, "spanishfood"))
        assertEquals(Category.FITNESS, classify(null, "gymmotivation"))
        assertEquals(Category.DEPORTE, classify(null, "futbolEspañol"))
        assertEquals(Category.DEPORTE, classify(null, "realmadridcf"))
        assertEquals(Category.ANIMALES, classify(null, "dogsoftiktok"))
        assertEquals(Category.APRENDIZAJE, classify(null, "booktok"))
        assertEquals(Category.APRENDIZAJE, classify(null, "tiktokciencia"))
    }

    @Test
    fun `gol dentro de otras palabras no es deporte`() {
        assertEquals(Category.COMIDA, classify("Golosinas caseras para la merienda"))
        assertNotEquals(Category.DEPORTE, classify("Mi viaje a Mongolia"))
        assertEquals(Category.DEPORTE, classify("Qué gol en el último minuto"))
    }

    @Test
    fun `ia y ai sueltos no son tecnologia`() {
        assertEquals(Category.OTROS, classify("Ai que calor hace hoy en Sevilla"))
        assertEquals(Category.OTROS, classify("La IA de mi prima"))
        assertEquals(Category.TECNOLOGIA, classify("Así funciona la inteligencia artificial por dentro"))
        assertEquals(Category.TECNOLOGIA, classify(null, "ia"))
    }

    @Test
    fun `palabras ambiguas no clasifican solas`() {
        assertEquals(Category.VIAJES, classify("Unas vacaciones en la playa"))   // «unas» no es «uñas»
        assertEquals(Category.OTROS, classify("Mi abuela vino a verme"))           // «vino» de venir
        assertEquals(Category.OTROS, classify("Historias de terror para no dormir"))
        assertEquals(Category.TECNOLOGIA, classify(null, "motorola"))              // no es Motor
    }

    @Test
    fun `jerga y una sola palabra clara`() {
        assertEquals(Category.HUMOR, classify("jajajaja no puedo más"))
        assertEquals(Category.HUMOR, classify("xdd"))
        assertEquals(Category.ANIMALES, classify("Mi gato haciendo el tonto"))
        assertEquals(Category.COMIDA, classify("Receta de tarta para después del gym"))
        assertEquals(Category.OTROS, classify("Hoy no sé qué poner"))
    }
}
