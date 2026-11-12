package com.reelometro.app.detection

import com.reelometro.app.detection.BubbleBoard.Mark
import com.reelometro.app.detection.BubbleBoard.SheetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.10.5: la barra de notificaciones bajada las esconde, y qué hoja está abierta. */
class BubbleBoardTapadasTest {

    private val board = BubbleBoard().apply { onReel(1L) }

    private fun visible(now: Long = 1_000, reel: Long = 1L) = board.visible(onReel = true, videoId = reel, now = now)

    @Test
    fun `con la barra de notificaciones bajada no se ven y al subirla vuelven`() {
        assertTrue(visible())
        assertTrue(board.onSystemCover(true))
        assertFalse(board.onSystemCover(true))
        assertTrue(board.systemCovered)
        assertFalse(visible())
        assertTrue(board.onSystemCover(false))
        assertFalse(board.onSystemCover(false))
        assertTrue(visible())
    }

    @Test
    fun `tambien enviando y las marcas se conservan`() {
        board.onSending(listOf("Ana"), 1L)
        board.onSystemCover(true)
        assertFalse(visible())
        board.onProvisionallySent(listOf("Ana"))
        assertEquals(Mark.SENT, board.markOf("Ana"))
        board.onSystemCover(false)
        assertTrue(visible())
        assertEquals(Mark.SENT, board.markOf("Ana"))
    }

    @Test
    fun `cambiar de video detras no la quita`() {
        board.onSystemCover(true)
        board.onReel(2L)
        assertTrue(board.systemCovered)
        assertFalse(visible(reel = 2L))
    }

    @Test
    fun `que hoja esta abierta`() {
        assertNull(board.sheetKind)
        board.onUserSheetOpened(1_000)
        assertEquals(SheetKind.OTHER, board.sheetKind)
        board.onUserSheetOpened(2_000, SheetKind.SHARE)
        assertEquals(SheetKind.SHARE, board.sheetKind)
        assertTrue(board.onSheetGone(openedAt = 2_000, now = 3_000))
        assertNull(board.sheetKind)

        board.onUserSheetOpened(4_000, SheetKind.OTHER)
        board.onFeedMoved(4_100) // justo al abrirse: sigue
        assertEquals(SheetKind.OTHER, board.sheetKind)
        board.onFeedMoved(5_000)
        assertNull(board.sheetKind)

        board.onUserSheetOpened(6_000)
        board.onSheetClosed()
        assertNull(board.sheetKind)

        board.onUserSheetOpened(7_000)
        board.onReel(2L)
        assertNull(board.sheetKind)
    }

    @Test
    fun `la hoja que deja un envio fallido es la de compartir`() {
        board.onSending(listOf("Ana"), 1L)
        board.onFinished(mapOf("Ana" to false), sheetLeftOpen = true, now = 1_000)
        assertTrue(board.sheetOpen)
        assertEquals(SheetKind.SHARE, board.sheetKind)
    }
}
