package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.1: en TikTok la hoja abierta a mano es otra ventana y el feed no se mueve al cerrarla («X», atrás, tocar fuera).
 * Las burbujas vuelven cuando ya no queda ninguna ventana de la app encima del feed ([SheetWindows], [BubbleBoard.onSheetGone]).
 */
class BubbleBoardHojaTikTokTest {

    private val board = BubbleBoard().apply { onReel(1L) }
    private val h = 2400

    private fun visible(now: Long) = board.visible(onReel = true, videoId = 1L, now = now)

    private val feed = SheetWindows.Sample(id = 10, layer = 1, height = h)
    private val sheet = SheetWindows.Sample(id = 20, layer = 2, height = h)
    private val banner = SheetWindows.Sample(id = 21, layer = 3, height = 160)

    @Test
    fun `al cerrar la hoja con la X vuelven sin tocar el visor`() {
        board.onUserSheetOpened(1_000)
        assertFalse(visible(1_000))
        // Cambio de ventana al cerrarla; pasado SETTLE_MS, solo queda el feed.
        assertEquals(false, SheetWindows.sheetAbove(listOf(feed), feedWindowId = 10, screenHeight = h))
        assertTrue(board.onSheetGone(openedAt = 1_000, now = 3_000))
        assertFalse(board.sheetOpen)
        assertTrue(visible(3_000))
    }

    @Test
    fun `con los comentarios u otra hoja encima siguen fuera`() {
        board.onUserSheetOpened(1_000)
        val comments = SheetWindows.Sample(id = 30, layer = 2, height = h * 3 / 4)
        assertEquals(true, SheetWindows.sheetAbove(listOf(comments, feed), feedWindowId = 10, screenHeight = h))
        assertEquals(true, SheetWindows.sheetAbove(listOf(banner, sheet, feed), feedWindowId = 10, screenHeight = h))
        // Sin onSheetGone, nada cambia.
        assertFalse(visible(5_000))
    }

    @Test
    fun `el aviso de enviado no cuenta como hoja y una ventana de otra app encima si`() {
        assertEquals(false, SheetWindows.sheetAbove(listOf(banner, feed), feedWindowId = 10, screenHeight = h))
        // El selector de Android (u otra app) encima del feed: ante la duda, siguen fuera.
        val other = SheetWindows.Sample(id = 40, layer = 5, height = h)
        assertEquals(true, SheetWindows.sheetAbove(listOf(other, feed), feedWindowId = 10, screenHeight = h))
        // Una ventana de la app por debajo del feed tampoco.
        val below = SheetWindows.Sample(id = 5, layer = 0, height = h)
        assertEquals(false, SheetWindows.sheetAbove(listOf(feed, below), feedWindowId = 10, screenHeight = h))
    }

    @Test
    fun `sin la ventana del feed no se sabe y no se cambia nada`() {
        assertNull(SheetWindows.sheetAbove(listOf(sheet), feedWindowId = 10, screenHeight = h))
        assertNull(SheetWindows.sheetAbove(listOf(sheet, feed), feedWindowId = null, screenHeight = h))
    }

    @Test
    fun `justo al abrirse o con una hoja posterior no se da por cerrada`() {
        board.onUserSheetOpened(1_000)
        assertFalse("antes de SETTLE_MS", board.onSheetGone(openedAt = 1_000, now = 1_000 + BubbleBoard.SETTLE_MS - 1))
        assertTrue(board.sheetOpen)
        // Una comprobación de la hoja de antes no cierra la que se ha abierto después (los comentarios, por ejemplo).
        board.onUserSheetOpened(2_000)
        assertFalse(board.onSheetGone(openedAt = 1_000, now = 4_000))
        assertTrue(board.sheetOpen)
        assertEquals(2_000L, board.sheetOpenedAt)
        assertTrue(board.onSheetGone(openedAt = 2_000, now = 4_000))
        assertNull(board.sheetOpenedAt)
        // Sin hoja, nada.
        assertFalse(board.onSheetGone(openedAt = 2_000, now = 5_000))
    }

    @Test
    fun `la hoja que deja un envio rapido fallido tambien vuelve al cerrarla`() {
        board.onSending(listOf("Ana"), 1L)
        board.onFinished(mapOf("Ana" to false), sheetLeftOpen = true, now = 1_000)
        assertFalse(visible(1_000 + BubbleBoard.FAILED_SHOWN_MS))
        assertTrue(board.onSheetGone(openedAt = 1_000, now = 5_000))
        assertTrue(visible(5_000))
    }

    @Test
    fun `Instagram como antes, el visor que se mueve cierra la hoja`() {
        board.onUserSheetOpened(1_000)
        board.onFeedMoved(1_100)
        assertTrue("justo al abrirse no", board.sheetOpen)
        board.onFeedMoved(2_000)
        assertFalse(board.sheetOpen)
    }

    @Test
    fun `la X de la hoja de compartir las devuelve aunque el visor parezca tener un panel`() {
        // 1.10.5 (regresión del 1.10.1): la hoja de compartir nunca mira FeedPanel, así que aunque algo encajara por error en
        // su regla, al cerrarla con la «X» (sin ventanas encima) vuelven.
        board.onUserSheetOpened(1_000, BubbleBoard.SheetKind.SHARE)
        assertFalse("el servicio ni lo lee", SheetWatch.usesPanel(board.sheetKind))
        val look = SheetWatch.Look(windowAbove = SheetWindows.sheetAbove(listOf(feed), feedWindowId = 10, screenHeight = h), panel = true)
        assertEquals(SheetWatch.Step.Gone, SheetWatch.next(board.sheetKind, look, waited = 500, panelSeen = false, sinceOpened = 500))
        assertTrue(board.onSheetGone(openedAt = 1_000, now = 1_650))
        assertTrue(visible(1_650))
    }

    @Test
    fun `con los comentarios en la misma ventana siguen fuera hasta cerrarlos`() {
        board.onUserSheetOpened(1_000) // «Leer o añadir comentarios»: otra hoja
        assertEquals(BubbleBoard.SheetKind.OTHER, board.sheetKind)
        // Sin ventana encima (van en la del feed) pero con el panel: no se da por cerrada.
        val open = SheetWatch.next(board.sheetKind, SheetWatch.Look(windowAbove = false, panel = true), waited = 1_500, panelSeen = false)
        assertEquals(SheetWatch.Step.Wait(SheetWatch.PANEL_POLL_MS), open)
        assertFalse(visible(2_500))
        // Se cierran: vuelven.
        assertEquals(SheetWatch.Step.Gone, SheetWatch.next(board.sheetKind, SheetWatch.Look(windowAbove = false, panel = false), waited = 9_500, panelSeen = true))
        assertTrue(board.onSheetGone(openedAt = 1_000, now = 10_500))
        assertTrue(visible(10_500))
    }
}
