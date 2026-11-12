package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El arte de Flap: rejillas válidas, parpadeo solo en los ojos y estados distinguibles (también sin color). */
class FlapSpriteTest {

    private fun grid(mood: FlapMood, frame: FlapFrame = FlapFrame.OPEN) = FlapSprite.frame(mood, frame)

    private fun accent(rows: List<String>) = rows.sumOf { row -> row.count { it == 'o' } }

    /** Solo la tinta (lo que se ve en un widget monocromo o sin distinguir colores). */
    private fun inkMask(rows: List<String>) = rows.joinToString("") { row -> row.map { if (it == 'X') 'X' else '.' }.joinToString("") }

    @Test
    fun `todas las rejillas son de SIZE por SIZE con píxeles documentados`() {
        for (mood in FlapMood.entries) for (frame in FlapFrame.entries) {
            val rows = grid(mood, frame)
            assertEquals(FlapSprite.SIZE, rows.size)
            rows.forEach { row ->
                assertEquals("$mood/$frame «$row»", FlapSprite.SIZE, row.length)
                row.forEach { c -> assertTrue("$mood/$frame carácter «$c»", c in FlapSprite.PIXELS) }
            }
        }
    }

    @Test
    fun `cada carácter tiene su papel y solo el punto es vacío`() {
        val roles = FlapSprite.PIXELS.map { FlapSprite.pixel(it) }
        assertEquals(FlapPixel.EMPTY, FlapSprite.pixel('.'))
        assertEquals(FlapSprite.PIXELS.length, roles.toSet().size)
        assertEquals(FlapPixel.entries.toSet(), roles.toSet())
        assertEquals(FlapPixel.EMPTY, FlapSprite.pixel('?'))
    }

    @Test
    fun `el parpadeo solo cambia las filas de los ojos y KO no parpadea`() {
        for (mood in FlapMood.entries) {
            val open = grid(mood, FlapFrame.OPEN)
            val blink = grid(mood, FlapFrame.BLINK)
            if (mood == FlapMood.KO) {
                // Ojos en X: no hay nada que cerrar.
                assertEquals("KO: los ojos en X no parpadean", open, blink)
            } else {
                assertNotEquals("$mood: el parpadeo tiene que verse", open, blink)
            }
            open.indices.filter { open[it] != blink[it] }.forEach { row ->
                assertTrue("$mood: el parpadeo toca la fila $row", row in 2..5)
            }
        }
    }

    @Test
    fun `los cinco estados se distinguen entre sí, también solo con la tinta`() {
        assertEquals(5, FlapMood.entries.size)
        for (frame in FlapFrame.entries) {
            val frames = FlapMood.entries.map { grid(it, frame) }
            assertEquals("$frame: estados repetidos", frames.size, frames.toSet().size)
            val masks = frames.map(::inkMask)
            assertEquals("$frame: sin color hay estados que no se distinguen", frames.size, masks.toSet().size)
        }
    }

    @Test
    fun `el acento es solo un detalle y la bisagra cruza la ficha`() {
        for (mood in FlapMood.entries) for (frame in FlapFrame.entries) {
            val rows = grid(mood, frame)
            assertTrue("$mood/$frame: ${accent(rows)} píxeles de acento", accent(rows) <= 6)
            assertTrue("$mood/$frame: sin bisagra", rows.any { row -> row.count { it == 'h' } >= 10 })
        }
        // Normal no lleva acento; los demás, un detalle (mofletes, gota, punta del termómetro, lengua).
        assertEquals(0, accent(grid(FlapMood.NORMAL)))
        listOf(FlapMood.EN_FORMA, FlapMood.CANSADO, FlapMood.ENFERMO, FlapMood.KO).forEach { mood ->
            assertTrue("$mood sin acento", accent(grid(mood)) > 0)
        }
    }

    @Test
    fun `enfermo lleva el termómetro con la punta fuera de la ficha`() {
        val rows = grid(FlapMood.ENFERMO)
        val tip = rows.indexOfFirst { it.endsWith('o') }
        assertTrue("sin punta del termómetro en el borde derecho", tip >= 0)
        // La varilla de tinta llega hasta la punta.
        assertEquals('X', rows[tip][FlapSprite.SIZE - 2])
    }

    @Test
    fun `KO está tumbado, sin patas a la vista y con los ojos en X`() {
        val ko = grid(FlapMood.KO)
        val normal = grid(FlapMood.NORMAL)
        val inkRows = { rows: List<String> -> rows.indices.filter { r -> 'X' in rows[r] } }
        // Más bajo que de pie…
        assertTrue(inkRows(ko).size < inkRows(normal).size)
        // …y apoyado en el suelo: lo último con tinta es el borde de la ficha (sin patas sueltas).
        val bottom = ko[inkRows(ko).last()]
        assertTrue("el borde de abajo de KO tiene que ser la ficha entera: «$bottom»", bottom.count { it == 'X' } >= 10)
        // Ojos en X: diagonales de tinta en las filas de los ojos.
        val eyes = ko.filter { row -> Regex("X.X.X").containsMatchIn(row) }
        assertTrue("KO sin ojos en X", eyes.size >= 2)
    }

    @Test
    fun `el contorno de la ficha es simétrico salvo los detalles`() {
        // La primera y la última fila con tinta (techo y suelo de la ficha o los pies) son simétricas en todos los estados.
        for (mood in FlapMood.entries) for (frame in FlapFrame.entries) {
            val rows = grid(mood, frame)
            val ink = rows.indices.filter { 'X' in rows[it] }
            listOf(ink.first(), ink.last()).forEach { r -> assertEquals("$mood/$frame fila $r", rows[r], rows[r].reversed()) }
        }
    }
}
