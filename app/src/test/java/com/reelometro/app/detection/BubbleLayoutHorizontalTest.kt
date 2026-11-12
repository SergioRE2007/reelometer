package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 1.10.5: la pila por defecto, en horizontal y en tablet, va pegada al borde derecho seguro (antes, con `x` = 0,92 del
 * ancho, quedaba a ~70 dp), y una pila soltada contra un borde se guarda pegada a él ([BubbleLayout.drop] con `edgeSnap`).
 */
class BubbleLayoutHorizontalTest {

    // Tres burbujas de 48 dp con 4 dp entre ellas a 2,625×: 126 px cada una y 11 px de hueco.
    private val bubble = 126
    private val gap = 11
    private fun size(o: BubbleOrientation) = BubbleLayout.stackSize(3, bubble, gap, o)
    private val edge = 21
    private val snap = 11 // 4 dp a 2,625×

    private fun gapToRight(spot: BubbleSpot, w: Int, h: Int, safe: SafeArea): Int {
        val (cw, ch) = size(BubbleLayout.orientationFor(spot.y))
        val (x, _) = BubbleLayout.windowXY(spot, cw, ch, w, h, safe)
        return w - (x + cw)
    }

    @Test
    fun `por defecto en horizontal va pegada al borde derecho seguro`() {
        // Barra de navegación a la derecha (126 px) y de estado arriba.
        val withBar = BubbleLayout.safeArea(2400, 1080, SafeArea(0, 63, 126, 0), edge)
        assertEquals(withBar.right, gapToRight(BubbleLayout.DEFAULT, 2400, 1080, withBar))
        assertEquals(withBar.right, gapToRight(BubbleLayout.DEFAULT_TIKTOK, 2400, 1080, withBar))
        // Sin barra lateral (navegación por gestos).
        val gestures = BubbleLayout.safeArea(2400, 1080, SafeArea(0, 63, 0, 0), edge)
        assertEquals(edge, gapToRight(BubbleLayout.DEFAULT, 2400, 1080, gestures))
        // Con la x de antes (0,92) quedaba lejos del borde.
        assertTrue(gapToRight(BubbleSpot(0.92f, 0.40f), 2400, 1080, gestures) > 100)
    }

    @Test
    fun `por defecto en tablet va pegada al borde derecho seguro`() {
        val safe = BubbleLayout.safeArea(1600, 2560, SafeArea(0, 60, 0, 80), edge)
        assertEquals(edge, gapToRight(BubbleLayout.DEFAULT, 1600, 2560, safe))
        assertEquals(edge, gapToRight(BubbleLayout.defaultFor(Platform.TIKTOK), 1600, 2560, safe))
        // Y en horizontal (2560 × 1600).
        val land = BubbleLayout.safeArea(2560, 1600, SafeArea(0, 60, 0, 80), edge)
        assertEquals(edge, gapToRight(BubbleLayout.DEFAULT, 2560, 1600, land))
    }

    @Test
    fun `en vertical se ve donde antes`() {
        val safe = BubbleLayout.safeArea(SCREEN_W, SCREEN_H, SafeArea(0, 110, 0, 63), edge)
        val (w, h) = size(BubbleOrientation.COLUMN)
        val now = BubbleLayout.windowXY(BubbleLayout.DEFAULT, w, h, SCREEN_W, SCREEN_H, safe)
        val before = BubbleLayout.windowXY(BubbleSpot(0.92f, 0.40f), w, h, SCREEN_W, SCREEN_H, safe)
        assertTrue("±3 px (${now.first} frente a ${before.first})", abs(now.first - before.first) <= 3)
        assertEquals(before.second, now.second)
    }

    @Test
    fun `soltada contra un borde se guarda pegada y sigue pegada en otra pantalla`() {
        val safe = BubbleLayout.safeArea(SCREEN_W, SCREEN_H, SafeArea(0, 110, 0, 63), edge)
        val (w, _) = size(BubbleOrientation.COLUMN)
        val maxCenter = SCREEN_W - safe.right - w / 2f
        val minCenter = safe.left + w / 2f
        // Más allá del borde, justo en él o a menos de 4 dp: x = 1.
        for (cx in listOf(SCREEN_W.toFloat(), maxCenter, maxCenter - 8f)) {
            val spot = BubbleLayout.drop(cx, 1000f, SCREEN_W, SCREEN_H, safe, edgeSnap = snap, size = ::size)
            assertEquals("soltada en $cx", 1f, spot.x, 0f)
            // El salto que se ve al pegarla no pasa de edgeSnap.
            assertTrue(maxCenter - cx.coerceAtMost(maxCenter) <= snap)
        }
        val left = BubbleLayout.drop(0f, 1000f, SCREEN_W, SCREEN_H, safe, edgeSnap = snap, size = ::size)
        assertEquals(0f, left.x, 0f)
        assertEquals(0f, BubbleLayout.drop(minCenter + 5f, 1000f, SCREEN_W, SCREEN_H, safe, edgeSnap = snap, size = ::size).x, 0f)

        // Leída en horizontal o en tablet, sigue pegada al borde.
        val land = BubbleLayout.safeArea(2400, 1080, SafeArea(0, 63, 126, 0), edge)
        val right = BubbleLayout.drop(SCREEN_W.toFloat(), 1000f, SCREEN_W, SCREEN_H, safe, edgeSnap = snap, size = ::size)
        assertEquals(land.right, gapToRight(right, 2400, 1080, land))
        val (lx, _) = BubbleLayout.windowXY(left, w, size(BubbleOrientation.COLUMN).second, 1600, 2560, BubbleLayout.safeArea(1600, 2560, SafeArea(0, 60, 0, 80), edge))
        assertEquals(edge, lx)
    }

    @Test
    fun `soltada en medio o sin edgeSnap la fraccion no cambia`() {
        val safe = BubbleLayout.safeArea(SCREEN_W, SCREEN_H, SafeArea(0, 110, 0, 63), edge)
        val (w, _) = size(BubbleOrientation.COLUMN)
        val maxCenter = SCREEN_W - safe.right - w / 2f
        val middle = BubbleLayout.drop(500f, 1000f, SCREEN_W, SCREEN_H, safe, edgeSnap = snap, size = ::size)
        assertEquals(500f / SCREEN_W, middle.x, 0.0001f)
        // A más de edgeSnap del borde: tal cual.
        val near = BubbleLayout.drop(maxCenter - 20f, 1000f, SCREEN_W, SCREEN_H, safe, edgeSnap = snap, size = ::size)
        assertEquals((maxCenter - 20f) / SCREEN_W, near.x, 0.0001f)
        // Sin edgeSnap, igual que antes: la fracción corregida, no 1.
        val old = BubbleLayout.drop(SCREEN_W.toFloat(), 1000f, SCREEN_W, SCREEN_H, safe, size = ::size)
        assertEquals(maxCenter / SCREEN_W, old.x, 0.0001f)
        assertNotEquals(1f, old.x)
        // En fila (franja de abajo) también se pega con su ancho.
        val row = BubbleLayout.drop(SCREEN_W.toFloat(), 0.80f * SCREEN_H, SCREEN_W, SCREEN_H, safe, edgeSnap = snap, size = ::size)
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(row.y))
        assertEquals(1f, row.x, 0f)
    }

    @Test
    fun `si la pila no cabe no se pega`() {
        val safe = SafeArea(21, 0, 21, 0)
        val spot = BubbleSpot(0.5f, 0.4f)
        assertEquals(spot, BubbleLayout.snapToEdge(spot, stackWidth = 2000, screenWidth = SCREEN_W, safe = safe, tolerance = 11))
        assertEquals(spot, BubbleLayout.snapToEdge(spot, stackWidth = 126, screenWidth = SCREEN_W, safe = safe, tolerance = null))
    }

    @Test
    fun `sin margenes del sistema en horizontal se aparta de la barra lateral`() {
        val land = BubbleLayout.safeArea(2400, 1080, null, edge, landscapeSide = 126)
        assertEquals(edge + 126, land.left)
        assertEquals(edge + 126, land.right)
        assertEquals(edge, gapToRight(BubbleLayout.DEFAULT, 2400, 1080, land) - 126)
        // En vertical, no (la barra va abajo y ya la cubre la franja de MAX_Y).
        val port = BubbleLayout.safeArea(SCREEN_W, SCREEN_H, null, edge, landscapeSide = 126)
        assertEquals(edge, port.left)
        assertEquals(edge, port.right)
        // Con los márgenes del sistema a mano no se suma nada.
        val withInsets = BubbleLayout.safeArea(2400, 1080, SafeArea(0, 63, 126, 0), edge, landscapeSide = 126)
        assertEquals(126 + edge, withInsets.right)
        assertEquals(edge, withInsets.left)
    }
}
