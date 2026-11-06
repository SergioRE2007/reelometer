package com.reelometro.app.detection

import com.reelometro.app.detection.BubbleBoard.Mark
import com.reelometro.app.detection.BubbleGesture.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué burbujas se ven y en qué estado, evento a evento (1.4.1), y el gesto de soltar una encima de otra. */
class BubbleBoardTest {

    private val board = BubbleBoard().apply { onReel(1L) }

    private fun visible(now: Long, onReel: Boolean = true, reel: Long? = 1L) = board.visible(onReel, reel, now)

    @Test
    fun `tras enviar no se esconden ni vuelven y la usada dice enviado hasta pasar de reel`() {
        assertTrue(visible(0))
        board.onSending(listOf("Ana"), 1L)
        assertEquals(Mark.SENDING, board.markOf("Ana"))
        // Enviando, con la hoja del propio envío abierta encima: se ven.
        assertTrue(visible(100))
        board.onProvisionallySent(listOf("Ana"))
        assertEquals(Mark.SENT, board.markOf("Ana"))
        board.onFinished(mapOf("Ana" to true), sheetLeftOpen = false, now = 600)
        assertTrue(visible(600))
        // Al cerrarse la hoja, Instagram mueve el visor: no cambia nada (en la 1.4.0 se iban y volvían).
        board.onFeedMoved(700)
        assertTrue(visible(700))
        assertEquals(Mark.SENT, board.markOf("Ana"))
        assertEquals(Mark.IDLE, board.markOf("Marta")) // las demás, normales
        assertNull(board.nextChangeAt(700))
        // Otro reel: la marca se va.
        assertTrue(board.onReel(2L))
        assertEquals(Mark.IDLE, board.markOf("Ana"))
        assertFalse(board.onReel(2L))
    }

    @Test
    fun `una hoja abierta a mano las esconde hasta que se cierra`() {
        assertFalse(board.sheetOpen)
        board.onUserSheetOpened(1_000)
        assertTrue(board.sheetOpen)
        assertFalse(visible(1_000))
        // El movimiento del visor al abrirse no la cierra.
        board.onFeedMoved(1_200)
        assertFalse(visible(1_200))
        // El de al cerrarse, sí.
        board.onFeedMoved(4_000)
        assertTrue(visible(4_000))
        // Enviar desde la hoja también la cierra.
        board.onUserSheetOpened(5_000)
        board.onSheetClosed()
        assertTrue(visible(5_000))
        // Y cambiar de reel.
        board.onUserSheetOpened(6_000)
        board.onReel(2L)
        assertTrue(visible(6_000, reel = 2L))
    }

    @Test
    fun `la X las esconde solo en ese reel y fuera de reels no se ven`() {
        board.onDismissed(1L)
        assertTrue(board.isDismissedOn(1L))
        assertFalse(visible(0))
        board.onReel(2L)
        assertTrue(visible(0, reel = 2L))
        assertFalse(visible(0, onReel = false, reel = 2L))
        // Enviando se ven aunque el tracker diga que no hay reel (la hoja lo tapa).
        board.onSending(listOf("Ana"), 2L)
        assertTrue(visible(0, onReel = false, reel = 2L))
    }

    @Test
    fun `un fallo con la hoja abierta enseña el fallo y luego las esconde`() {
        board.onSending(listOf("Ana", "Primos"), 1L)
        board.onFinished(mapOf("Ana" to false, "Primos" to false), sheetLeftOpen = true, now = 3_000)
        assertEquals(Mark.FAILED, board.markOf("Ana"))
        assertTrue(visible(3_000))
        assertEquals(3_000 + BubbleBoard.FAILED_SHOWN_MS, board.nextChangeAt(3_000))
        assertFalse(visible(3_000 + BubbleBoard.FAILED_SHOWN_MS))
        board.onFeedMoved(9_000) // el usuario cierra la hoja
        assertTrue(visible(9_000))
        assertEquals(Mark.FAILED, board.markOf("Primos"))
    }

    @Test
    fun `cada destino acaba en su estado y un envio de otro reel no deja marcas`() {
        board.onSending(listOf("Ana", "Primos"), 1L)
        board.onFinished(mapOf("Ana" to true, "Primos" to false), sheetLeftOpen = false, now = 0)
        assertEquals(mapOf("Ana" to Mark.SENT, "Primos" to Mark.FAILED), board.shownMarks)
        // Se pasa de reel mientras envía: al acabar, el reel nuevo no lleva marcas.
        board.onSending(listOf("Marta"), 1L)
        board.onReel(2L)
        assertEquals(Mark.SENDING, board.markOf("Marta")) // sigue girando mientras envía
        board.onFinished(mapOf("Marta" to true), sheetLeftOpen = true, now = 10)
        assertEquals(Mark.IDLE, board.markOf("Marta"))
        assertTrue(visible(10, reel = 2L))
    }

    // ── El gesto ────────────────────────────────────────────────────────────────────────────────

    private val slop = 16
    private val gesture = BubbleGesture(touchSlop = slop, longPressMs = 400)

    /** Tres burbujas en columna, a la derecha. */
    private val centers = listOf(1000f to 800f, 1000f to 960f, 1000f to 1120f)
    private val radius = 60f

    @Test
    fun `toque, arrastre de la pila y pulsacion larga se distinguen`() {
        // Toque: sin moverse y antes de la pulsación larga.
        gesture.down(0, 1000f, 800f, now = 0)
        assertFalse(gesture.longPress(now = 300))
        assertEquals(Outcome.Tap(0), gesture.up(1003f, 802f, centers, radius))

        // Arrastre: se mueve más que touchSlop antes de los 400 ms; luego ya no se despega.
        gesture.down(1, 1000f, 960f, now = 1_000)
        assertFalse(gesture.move(1005f, 965f, centers, radius)) // un temblor no es arrastre
        assertTrue(gesture.move(1000f, 1100f, centers, radius))
        assertEquals(BubbleGesture.Mode.DRAG_STACK, gesture.mode)
        assertFalse(gesture.longPress(now = 1_500))
        assertEquals(Outcome.StackDropped, gesture.up(1000f, 1500f, centers, radius))

        // Pulsación larga: quieta (o temblando dentro de touchSlop) 400 ms.
        gesture.down(0, 1000f, 800f, now = 2_000)
        gesture.move(1006f, 804f, centers, radius)
        assertTrue(gesture.longPress(now = 2_400))
        assertEquals(BubbleGesture.Mode.LIFTED, gesture.mode)
        // Ya despegada, moverse mucho no la convierte en arrastre de la pila.
        gesture.move(600f, 400f, centers, radius)
        assertEquals(BubbleGesture.Mode.LIFTED, gesture.mode)
        assertEquals(Outcome.Returned(0), gesture.up(600f, 400f, centers, radius))
    }

    @Test
    fun `soltada encima de otra envia a las dos y la de debajo se ilumina`() {
        gesture.down(0, 1000f, 800f, now = 0)
        assertTrue(gesture.longPress(now = 450))
        // Pasa por encima de la 2 y se queda en la 3.
        assertTrue(gesture.move(1010f, 950f, centers, radius))
        assertEquals(1, gesture.hover)
        assertFalse(gesture.move(1012f, 955f, centers, radius)) // sigue en la misma: sin otro tic
        assertTrue(gesture.move(990f, 1110f, centers, radius))
        assertEquals(2, gesture.hover)
        assertEquals(Outcome.Together(0, 2), gesture.up(990f, 1110f, centers, radius))
        assertNull(gesture.hover)
        // Encima de sí misma no cuenta.
        gesture.down(1, 1000f, 960f, now = 0)
        gesture.longPress(now = 500)
        assertEquals(Outcome.Returned(1), gesture.up(1000f, 960f, centers, radius))
    }

    @Test
    fun `soltar fuera o cancelar nunca envia`() {
        gesture.down(2, 1000f, 1120f, now = 0)
        gesture.longPress(now = 400)
        assertEquals(Outcome.Returned(2), gesture.up(500f, 1120f, centers, radius))
        gesture.down(2, 1000f, 1120f, now = 0)
        gesture.longPress(now = 400)
        gesture.move(1000f, 960f, centers, radius)
        assertEquals(Outcome.Returned(2), gesture.cancel())
        gesture.down(0, 1000f, 800f, now = 0)
        assertEquals(Outcome.None, gesture.cancel())
    }

    @Test
    fun `sobre que burbuja cae`() {
        assertEquals(1, BubbleGesture.bubbleUnder(1000f, 940f, centers, exclude = 0, radius = radius))
        // A medio camino entre dos, la más cercana.
        assertEquals(2, BubbleGesture.bubbleUnder(1000f, 1060f, centers, exclude = 0, radius = radius))
        assertNull(BubbleGesture.bubbleUnder(1000f, 960f, centers, exclude = 1, radius = 40f))
        assertNull(BubbleGesture.bubbleUnder(700f, 960f, centers, exclude = 0, radius = radius))
    }
}
