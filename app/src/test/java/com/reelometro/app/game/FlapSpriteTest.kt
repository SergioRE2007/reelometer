package com.reelometro.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El arte de Flap: rejillas válidas, parpadeo solo en los ojos y estados distinguibles. */
class FlapSpriteTest {

    private fun grid(mood: FlapMood, frame: FlapFrame = FlapFrame.OPEN) = FlapSprite.frame(mood, frame)

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
    fun `el parpadeo solo cambia las filas de los ojos`() {
        for (mood in FlapMood.entries) {
            val open = grid(mood, FlapFrame.OPEN)
            val blink = grid(mood, FlapFrame.BLINK)
            assertNotEquals("$mood: el parpadeo tiene que verse", open, blink)
            open.indices.filter { open[it] != blink[it] }.forEach { row ->
                assertTrue("$mood: el parpadeo toca la fila $row", row in 2..5)
            }
        }
    }

    @Test
    fun `los cuatro estados se distinguen entre sí`() {
        val frames = FlapMood.entries.map { grid(it) }
        assertEquals(frames.size, frames.toSet().size)
        // Y también sin color: solo con la tinta (lo que se ve en un widget monocromo) ya son distintos.
        val masks = frames.map { rows -> rows.joinToString("") { row -> row.map { if (it == 'X') 'X' else '.' }.joinToString("") } }
        assertEquals(frames.size, masks.toSet().size)
    }

    @Test
    fun `el acento es solo un detalle y la bisagra cruza la ficha`() {
        for (mood in FlapMood.entries) for (frame in FlapFrame.entries) {
            val rows = grid(mood, frame)
            val accent = rows.sumOf { row -> row.count { it == 'o' } }
            assertTrue("$mood/$frame: $accent píxeles de acento", accent <= 6)
            assertTrue("$mood/$frame: sin bisagra", rows.any { row -> row.count { it == 'h' } >= 10 })
        }
        // En forma y zombi llevan acento; normal no.
        assertEquals(0, grid(FlapMood.NORMAL).sumOf { row -> row.count { it == 'o' } })
        assertTrue(grid(FlapMood.EN_FORMA).sumOf { row -> row.count { it == 'o' } } > 0)
        assertTrue(grid(FlapMood.ZOMBI).sumOf { row -> row.count { it == 'o' } } > 0)
    }

    @Test
    fun `la ficha es simétrica salvo los detalles`() {
        // El contorno (tinta de las filas 1 y 12) es simétrico en todos los estados.
        for (mood in FlapMood.entries) {
            val rows = grid(mood)
            listOf(1, 12).forEach { r -> assertEquals("$mood fila $r", rows[r], rows[r].reversed()) }
        }
    }
}
