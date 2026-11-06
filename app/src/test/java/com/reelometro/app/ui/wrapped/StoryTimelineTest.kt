package com.reelometro.app.ui.wrapped

import com.reelometro.app.ui.wrapped.StoryTimeline.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTimelineTest {

    private val slide = StoryTimeline.SLIDE_MS

    /** Avanza [ms] en fotogramas de 16 ms, como el bucle de la pantalla. */
    private fun run(from: Position, ms: Long, count: Int): Position {
        var p = from
        var left = ms
        while (left > 0) {
            val d = minOf(16L, left)
            p = StoryTimeline.tick(p, d, count)
            left -= d
        }
        return p
    }

    @Test
    fun `el tiempo avanza dentro de la diapositiva`() {
        assertEquals(Position(0, 3_000), run(Position.START, 3_000, 5))
    }

    @Test
    fun `al completar una diapositiva pasa a la siguiente desde cero`() {
        val p = run(Position.START, slide, 5)
        assertEquals(1, p.index)
        assertEquals(0, p.elapsedMs)
    }

    @Test
    fun `la última diapositiva se queda llena y no cierra la historia`() {
        val p = run(Position(4, 0), slide * 3, 5)
        assertEquals(Position(4, slide), p)
        assertTrue(StoryTimeline.isFinished(p, 5))
        assertFalse(StoryTimeline.isFinished(Position(4, slide - 1), 5))
    }

    @Test
    fun `un tirón largo no se salta diapositivas`() {
        val p = StoryTimeline.tick(Position(0, slide - 50), 60_000, 5)
        assertEquals(1, p.index)
        val q = StoryTimeline.tick(Position(2, 0), 60_000, 5)
        assertEquals(Position(2, StoryTimeline.MAX_FRAME_MS), q)
    }

    @Test
    fun `la pausa es no llamar a tick y se retoma donde estaba`() {
        val paused = run(Position.START, 2_500, 5)
        // ... el dedo sigue en la pantalla: no pasa el tiempo ...
        val resumed = run(paused, 1_000, 5)
        assertEquals(Position(0, 3_500), resumed)
    }

    @Test
    fun `siguiente y anterior con sus topes`() {
        assertEquals(Position(1, 0), StoryTimeline.next(Position(0, 4_000), 3))
        assertEquals(Position(2, 1_000), StoryTimeline.next(Position(2, 1_000), 3))
        assertEquals(Position(0, 0), StoryTimeline.previous(Position(1, 2_000), 3))
        assertEquals(Position(0, 0), StoryTimeline.previous(Position(0, 2_000), 3))
    }

    @Test
    fun `relleno de los segmentos de la barra`() {
        val p = Position(2, slide / 4)
        assertEquals(1f, StoryTimeline.segmentProgress(p, 0), 0f)
        assertEquals(1f, StoryTimeline.segmentProgress(p, 1), 0f)
        assertEquals(0.25f, StoryTimeline.segmentProgress(p, 2), 0.0001f)
        assertEquals(0f, StoryTimeline.segmentProgress(p, 3), 0f)
    }

    @Test
    fun `el tercio izquierdo vuelve y el resto avanza`() {
        assertTrue(StoryTimeline.isBackTap(100f, 1080f))
        assertFalse(StoryTimeline.isBackTap(540f, 1080f))
        assertFalse(StoryTimeline.isBackTap(1000f, 1080f))
    }

    @Test
    fun `el reproductor avisa al llegar al final y pinta estático sin reloj`() {
        val player = StoryPlayer(count = 2)
        assertFalse(player.tick(16))
        player.next()
        assertTrue(player.isLast)
        var done = false
        repeat(1_000) { if (!done) done = player.tick(16) }
        assertTrue(done)
        // «Reducir animaciones»: la actual cuenta como completa, las siguientes vacías.
        player.previous()
        assertEquals(1f, player.progress(0, static = true), 0f)
        assertEquals(0f, player.progress(1, static = true), 0f)
    }
}
