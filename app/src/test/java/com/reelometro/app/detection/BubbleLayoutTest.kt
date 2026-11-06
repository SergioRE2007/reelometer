package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLayoutTest {

    // Tres burbujas de 48 dp con 4 dp entre ellas a 2,625×: 126 px cada una y 11 px de hueco.
    private val bubble = 126
    private val gap = 11
    private fun size(o: BubbleOrientation) = BubbleLayout.stackSize(3, bubble, gap, o)

    /** Barra de estado de 110 px y de navegación de 63 px, más 21 px (8 dp) de aire. */
    private val safe = BubbleLayout.safeArea(SCREEN_W, SCREEN_H, SafeArea(0, 110, 0, 63), edge = 21)

    @Test
    fun `por defecto junto al borde derecho a media altura sin tapar la columna de botones`() {
        val spot = BubbleLayout.DEFAULT
        assertEquals(BubbleOrientation.COLUMN, BubbleLayout.orientationFor(spot.y))
        val (w, h) = size(BubbleOrientation.COLUMN)
        assertEquals(126 to 400, w to h)
        val (x, y) = BubbleLayout.windowXY(spot, w, h, SCREEN_W, SCREEN_H, safe)
        assertEquals((0.40f * SCREEN_H - h / 2f).toInt(), y)
        assertTrue("la pila acaba antes de la columna de Instagram (55 %)", y + h < SCREEN_H * 0.55f)
        // Pegada a la derecha como en la 1.4.1: el hueco hasta el borde es pequeño y la pila no se sale.
        assertTrue(x + w <= SCREEN_W - 21)
        assertTrue(SCREEN_W - (x + w) < 60)
    }

    @Test
    fun `al soltar se queda donde se suelta sin iman al borde`() {
        // En medio de la pantalla: ni se va al borde ni cambia de altura.
        val middle = BubbleLayout.drop(540f, 1000f, SCREEN_W, SCREEN_H, safe, size = ::size)
        assertEquals(0.5f, middle.x, 0.0001f)
        assertEquals(1000f / SCREEN_H, middle.y, 0.0001f)
        val (x, y) = BubbleLayout.windowXY(middle, 126, 400, SCREEN_W, SCREEN_H, safe)
        assertEquals(540 - 63, x)
        assertEquals(1000 - 200, y)
        // Un poco a la izquierda del centro: no salta al borde izquierdo.
        assertEquals(400f / SCREEN_W, BubbleLayout.drop(400f, 1000f, SCREEN_W, SCREEN_H, safe, size = ::size).x, 0.0001f)
    }

    @Test
    fun `solo se corrige lo justo para no salirse ni quedar bajo las barras`() {
        val (w, h) = size(BubbleOrientation.COLUMN)
        // Arriba del todo: justo debajo de la barra de estado (110 px) y del aire (21 px).
        val top = BubbleLayout.drop(0f, 0f, SCREEN_W, SCREEN_H, safe, size = ::size)
        val (tx, ty) = BubbleLayout.windowXY(top, w, h, SCREEN_W, SCREEN_H, safe)
        assertEquals(21, tx)
        assertEquals(131, ty)
        // Abajo a la derecha del todo: en fila, justo encima de la barra de navegación.
        val bottom = BubbleLayout.drop(99_999f, 99_999f, SCREEN_W, SCREEN_H, safe, size = ::size)
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(bottom.y))
        val (rw, rh) = size(BubbleOrientation.ROW)
        val (bx, by) = BubbleLayout.windowXY(bottom, rw, rh, SCREEN_W, SCREEN_H, safe)
        assertEquals(SCREEN_W - 21 - rw, bx)
        assertEquals(SCREEN_H - 63 - 21 - rh, by)
        // Sin los márgenes del sistema: la franja de siempre (8 %–92 %).
        val fallback = BubbleLayout.safeArea(SCREEN_W, SCREEN_H, null, edge = 21)
        assertEquals(SafeArea(21, 192, 21, 192), fallback)
        val high = BubbleLayout.clamp(BubbleSpot(0.5f, 0f), w, h, SCREEN_W, SCREEN_H, fallback)
        assertEquals(BubbleLayout.MIN_Y + h / 2f / SCREEN_H, high.y, 0.0001f)
        // En fracción: vale igual al girar la pantalla (en horizontal, la columna sigue cabiendo).
        val (_, ly) = BubbleLayout.windowXY(BubbleSpot(0.92f, 0.5f), w, h, SCREEN_H, SCREEN_W, safe)
        assertEquals((0.5f * SCREEN_W - h / 2f).toInt(), ly)
        // Una pila más grande que el hueco se centra en lo que queda.
        val tiny = BubbleLayout.clamp(BubbleSpot(0.1f, 0.1f), 2000, 3000, SCREEN_W, SCREEN_H, safe)
        assertEquals(0.5f, tiny.x, 0.0001f)
        // Valores guardados rotos vuelven a los de por defecto.
        assertEquals(BubbleLayout.DEFAULT, BubbleLayout.sanitize(BubbleSpot(Float.NaN, Float.POSITIVE_INFINITY)))
        assertEquals(BubbleSpot(1f, 0f), BubbleLayout.sanitize(BubbleSpot(7f, -3f)))
    }

    @Test
    fun `en fila si se suelta en la franja de abajo y en columna en el resto`() {
        assertEquals(BubbleOrientation.COLUMN, BubbleLayout.orientationFor(0.1f))
        assertEquals(BubbleOrientation.COLUMN, BubbleLayout.orientationFor(0.71f))
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(BubbleLayout.ROW_FROM_Y))
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(0.95f))
        assertEquals(BubbleOrientation.COLUMN, BubbleLayout.orientationFor(Float.NaN))
        assertEquals(400 to 126, size(BubbleOrientation.ROW))
        assertEquals(0 to 0, BubbleLayout.stackSize(0, bubble, gap, BubbleOrientation.ROW))

        // Encima del nombre del reel (80 %, a la izquierda): fila, y se queda ahí.
        val name = BubbleLayout.drop(300f, 0.80f * SCREEN_H, SCREEN_W, SCREEN_H, safe, size = ::size)
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(name.y))
        assertEquals(0.80f, name.y, 0.0001f)
        assertEquals(300f / SCREEN_W, name.x, 0.0001f)
        // Soltada justo antes de la franja: columna; y lo que se guarda da la misma orientación al leerlo.
        val above = BubbleLayout.drop(540f, 0.70f * SCREEN_H, SCREEN_W, SCREEN_H, safe, size = ::size)
        assertEquals(BubbleOrientation.COLUMN, BubbleLayout.orientationFor(above.y))
        // Muy abajo pegada a la izquierda: fila corregida hacia dentro, sin dejar de ser fila.
        val corner = BubbleLayout.drop(0f, SCREEN_H.toFloat(), SCREEN_W, SCREEN_H, safe, size = ::size)
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(corner.y))
        assertEquals((21 + 200f) / SCREEN_W, corner.x, 0.0001f)
    }

    @Test
    fun `la franja de fila no quita la zona x de esconder`() {
        val (cx, cy) = BubbleLayout.dismissCenter(SCREEN_W, SCREEN_H)
        assertTrue(BubbleLayout.DISMISS_CENTER_Y >= BubbleLayout.ROW_FROM_Y)
        // Encima de la «X»: esconde (lo mira la ventana antes de colocar).
        assertTrue(BubbleLayout.inDismissZone(cx.toFloat(), cy.toFloat(), SCREEN_W, SCREEN_H, 189))
        // A un lado de la «X», a la misma altura: no esconde y se queda en fila.
        assertFalse(BubbleLayout.inDismissZone(150f, cy.toFloat(), SCREEN_W, SCREEN_H, 189))
        val side = BubbleLayout.drop(250f, cy.toFloat(), SCREEN_W, SCREEN_H, safe, size = ::size)
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(side.y))
    }

    @Test
    fun `la posicion de antes se convierte a la equivalente`() {
        // 1.4.1: borde derecho al 40 % = la de por defecto.
        assertEquals(BubbleLayout.DEFAULT.x, BubbleLayout.fromLegacy(left = false, centerY = 0.40f).x, 0.0001f)
        assertEquals(BubbleLayout.DEFAULT, BubbleLayout.fromLegacy(left = false, centerY = 0.40f))
        // Borde izquierdo al 70 %: pegada a la izquierda a esa altura, en columna (como estaba).
        val left = BubbleLayout.fromLegacy(left = true, centerY = 0.70f)
        assertEquals(BubbleSpot(BubbleLayout.LEGACY_EDGE_X, 0.70f), left)
        assertEquals(BubbleOrientation.COLUMN, BubbleLayout.orientationFor(left.y))
        val (w, h) = size(BubbleOrientation.COLUMN)
        val (x, _) = BubbleLayout.windowXY(left, w, h, SCREEN_W, SCREEN_H, safe)
        assertTrue("pegada al borde izquierdo con un poco de aire (8 dp = 21 px en la 1.4.1)", x in 21..30)
        // Lo de antes era siempre una columna: aunque estuviera en la franja de abajo, sigue siéndolo (sube lo justo).
        assertEquals(0.5f, BubbleLayout.fromLegacy(left = true, centerY = 0.5f).y, 0.0001f)
        val low = BubbleLayout.fromLegacy(left = false, centerY = 0.85f)
        assertEquals(BubbleOrientation.COLUMN, BubbleLayout.orientationFor(low.y))
        assertEquals(BubbleLayout.LEGACY_MAX_Y, low.y, 0.0001f)
        assertEquals(BubbleLayout.DEFAULT.y, BubbleLayout.fromLegacy(left = true, centerY = Float.NaN).y, 0.0001f)
    }

    @Test
    fun `soltada justo debajo de la x nunca se guarda encima de ella`() {
        // La «X» a 189 px de radio. La fila se suelta abajo del todo en el centro (fuera del radio), y al subirla
        // por encima de la barra de navegación caería dentro: se aparta a un lado, al que estaba.
        val radius = 189
        val (cx, cy) = BubbleLayout.dismissCenter(SCREEN_W, SCREEN_H)
        assertFalse(BubbleLayout.inDismissZone(cx - 10f, SCREEN_H - 5f, SCREEN_W, SCREEN_H, radius))
        val spot = BubbleLayout.drop(cx - 10f, SCREEN_H - 5f, SCREEN_W, SCREEN_H, safe, dismissRadius = radius, size = ::size)
        assertEquals(BubbleOrientation.ROW, BubbleLayout.orientationFor(spot.y))
        assertFalse(BubbleLayout.inDismissZone(spot.x * SCREEN_W, spot.y * SCREEN_H, SCREEN_W, SCREEN_H, radius))
        assertTrue("a la izquierda de la X, donde se soltó", spot.x * SCREEN_W < cx)
        // Y sin márgenes del sistema (Android 10 o menos), igual.
        val bare = BubbleLayout.safeArea(SCREEN_W, SCREEN_H, null, edge = 21)
        val other = BubbleLayout.drop(cx + 10f, SCREEN_H - 5f, SCREEN_W, SCREEN_H, bare, dismissRadius = radius, size = ::size)
        assertFalse(BubbleLayout.inDismissZone(other.x * SCREEN_W, other.y * SCREEN_H, SCREEN_W, SCREEN_H, radius))
        assertTrue(other.x * SCREEN_W > cx)
        // Lejos de la X no cambia nada.
        assertEquals(
            BubbleLayout.drop(200f, 1000f, SCREEN_W, SCREEN_H, safe, size = ::size),
            BubbleLayout.drop(200f, 1000f, SCREEN_W, SCREEN_H, safe, dismissRadius = radius, size = ::size),
        )
        assertTrue(cy > 0)
    }

    @Test
    fun `arrastrar nunca es tocar`() {
        assertFalse(BubbleLayout.isDrag(3f, 4f, 8))
        assertFalse(BubbleLayout.isDrag(8f, 0f, 8))
        assertTrue(BubbleLayout.isDrag(6f, 6f, 8))
    }

    @Test
    fun `zona x abajo en el centro`() {
        val (cx, cy) = BubbleLayout.dismissCenter(SCREEN_W, SCREEN_H)
        assertEquals(540, cx)
        assertEquals((SCREEN_H * BubbleLayout.DISMISS_CENTER_Y).toInt(), cy)
        assertTrue(BubbleLayout.inDismissZone(cx + 50f, cy - 50f, SCREEN_W, SCREEN_H, 100))
        assertFalse(BubbleLayout.inDismissZone(cx + 90f, cy - 90f, SCREEN_W, SCREEN_H, 100))
    }

    @Test
    fun `tres veces seguidas a la x pregunta una sola vez`() {
        var streak = DismissStreak()
        var asked: Boolean
        repeat(2) {
            streak.dismissed().let { (s, ask) -> streak = s; asked = ask; assertFalse(asked) }
        }
        streak.dismissed().let { (s, ask) -> streak = s; assertTrue(ask) }
        assertTrue(streak.asked)
        repeat(5) { streak.dismissed().let { (s, ask) -> streak = s; assertFalse(ask) } }

        // Usar una burbuja entre medias rompe la racha.
        var other = DismissStreak()
        other = other.dismissed().first.dismissed().first.used()
        assertEquals(0, other.count)
        other.dismissed().let { (s, ask) -> assertFalse(ask); assertEquals(1, s.count) }
    }
}
