package com.reelometro.app.detection

import com.reelometro.app.detection.BubbleBoard.SheetKind.OTHER
import com.reelometro.app.detection.BubbleBoard.SheetKind.SHARE
import com.reelometro.app.detection.SheetWatch.ClickAction
import com.reelometro.app.detection.SheetWatch.Look
import com.reelometro.app.detection.SheetWatch.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.10.5: cómo se vigila una hoja abierta a mano en TikTok (otra ventana o un panel en la del feed) y qué hace un clic. */
class SheetWatchTest {

    @Test
    fun `con una ventana encima se mira cada medio segundo hasta 8 s`() {
        val above = Look(windowAbove = true, panel = false)
        assertEquals(Step.Wait(SheetWatch.SHEET_POLL_MS), SheetWatch.next(OTHER, above, waited = 500, panelSeen = false))
        assertEquals(Step.Wait(500), SheetWatch.next(OTHER, above, waited = 7_500, panelSeen = false))
        assertEquals(Step.GiveUp, SheetWatch.next(OTHER, above, waited = 8_000, panelSeen = false))
    }

    @Test
    fun `sin nada encima la hoja se ha cerrado`() {
        assertEquals(Step.Gone, SheetWatch.next(OTHER, Look(windowAbove = false, panel = false), waited = 500, panelSeen = false))
        // Sin saber del panel (la hoja de compartir no lo mira, o no se pudo leer): como en la 1.10.2.
        assertEquals(Step.Gone, SheetWatch.next(OTHER, Look(windowAbove = false, panel = null), waited = 500, panelSeen = false))
    }

    @Test
    fun `con los comentarios abiertos se mira cada segundo hasta 10 minutos`() {
        val panel = Look(windowAbove = false, panel = true)
        assertEquals(Step.Wait(SheetWatch.PANEL_POLL_MS), SheetWatch.next(OTHER, panel, waited = 1_500, panelSeen = false))
        assertEquals(Step.Wait(1_000), SheetWatch.next(OTHER, panel, waited = 599_000, panelSeen = true))
        assertEquals(Step.GiveUp, SheetWatch.next(OTHER, panel, waited = SheetWatch.PANEL_WATCH_MS, panelSeen = true))
        // Se cierran: vuelven.
        assertEquals(Step.Gone, SheetWatch.next(OTHER, Look(windowAbove = false, panel = false), waited = 30_000, panelSeen = true))
    }

    @Test
    fun `tras ver el panel una ventana encima usa el tope largo`() {
        val above = Look(windowAbove = true, panel = false)
        assertEquals(Step.Wait(500), SheetWatch.next(OTHER, above, waited = 20_000, panelSeen = true))
        assertEquals(Step.GiveUp, SheetWatch.next(OTHER, above, waited = 20_000, panelSeen = false))
    }

    @Test
    fun `sin saber de las ventanas solo se cierra tras tocar el visor`() {
        val unknown = Look(windowAbove = null, panel = null)
        assertEquals(Step.Wait(500), SheetWatch.next(OTHER, unknown, waited = 500, panelSeen = false))
        assertEquals(Step.GiveUp, SheetWatch.next(OTHER, unknown, waited = 8_000, panelSeen = false))
        assertEquals(Step.Gone, SheetWatch.next(OTHER, unknown, waited = 400, panelSeen = false, unknownIsGone = true))
        // Pero si el panel sigue ahí, no.
        assertEquals(Step.Wait(1_000), SheetWatch.next(OTHER, Look(windowAbove = null, panel = true), waited = 400, panelSeen = false, unknownIsGone = true))
    }

    @Test
    fun `un clic en el visor`() {
        assertEquals(ClickAction.OPENED, SheetWatch.onViewerClick(opensSheet = true, sheetOpen = false, sheetOwnWindow = true))
        assertEquals(ClickAction.OPENED, SheetWatch.onViewerClick(opensSheet = true, sheetOpen = true, sheetOwnWindow = false))
        // Instagram (la hoja en la misma ventana): tocar el visor la cierra, como siempre.
        assertEquals(ClickAction.CLOSED, SheetWatch.onViewerClick(opensSheet = false, sheetOpen = true, sheetOwnWindow = false))
        assertEquals(ClickAction.NONE, SheetWatch.onViewerClick(opensSheet = false, sheetOpen = false, sheetOwnWindow = false))
        // TikTok: un toque dentro de los comentarios también llega como clic en el visor; se comprueba.
        assertEquals(ClickAction.VERIFY, SheetWatch.onViewerClick(opensSheet = false, sheetOpen = true, sheetOwnWindow = true))
        assertEquals(ClickAction.PROBE, SheetWatch.onViewerClick(opensSheet = false, sheetOpen = false, sheetOwnWindow = true))
    }

    @Test
    fun `tras un clic sin etiqueta solo cuenta lo que antes no estaba`() {
        val nothing = Look(windowAbove = false, panel = false)
        assertTrue(SheetWatch.probeOpened(nothing, Look(windowAbove = true, panel = false)))
        assertTrue(SheetWatch.probeOpened(nothing, Look(windowAbove = false, panel = true)))
        assertFalse(SheetWatch.probeOpened(nothing, nothing))
        // Un «panel» que ya estaba (algo que encaja por error en la regla) nunca esconde nada.
        val always = Look(windowAbove = false, panel = true)
        assertFalse(SheetWatch.probeOpened(always, always))
        // Sin saber, nada.
        assertFalse(SheetWatch.probeOpened(Look(null, null), Look(true, true)))
        assertFalse(SheetWatch.probeOpened(nothing, Look(null, null)))
    }

    @Test
    fun `solo se mira tras un clic en la columna de botones`() {
        val detector = ShareDetector()
        // Un botón de la derecha sin etiqueta conocida (comentarios en otro idioma): sí.
        assertTrue(detector.inActionColumn(desc("Commenti", box(950, 1300, 1050, 1400)), SCREEN_W, SCREEN_H))
        // Un toque en el vídeo (a lo ancho) o un botón de la izquierda: no.
        assertFalse(detector.inActionColumn(FakeNode(bounds = box(0, 0, SCREEN_W, 2270)), SCREEN_W, SCREEN_H))
        assertFalse(detector.inActionColumn(desc("Perfil", box(40, 1900, 140, 2000)), SCREEN_W, SCREEN_H))
        assertFalse(detector.inActionColumn(FakeNode(bounds = NodeBounds.EMPTY), SCREEN_W, SCREEN_H))
        assertFalse(detector.inActionColumn(desc("x"), 0, 0))
    }

    @Test
    fun `los comentarios que aun no han salido no se dan por cerrados`() {
        // Un cambio de ventana de TikTok justo al abrirlos relanza la vigilancia a los 500 ms: el panel aún no está.
        val nothing = Look(windowAbove = false, panel = false)
        assertEquals(Step.Wait(SheetWatch.SHEET_POLL_MS), SheetWatch.next(OTHER, nothing, waited = 500, panelSeen = false, sinceOpened = 500))
        assertEquals(Step.Wait(500), SheetWatch.next(OTHER, Look(false, null), waited = 500, panelSeen = false, sinceOpened = 1_000))
        assertEquals(Step.Wait(500), SheetWatch.next(OTHER, Look(null, null), waited = 500, panelSeen = false, unknownIsGone = true, sinceOpened = 900))
        // Pasado el margen, sin haberlo visto: cerrada (como en la 1.10.4).
        assertEquals(Step.Gone, SheetWatch.next(OTHER, nothing, waited = 1_500, panelSeen = false, sinceOpened = SheetWatch.PANEL_GRACE_MS))
        // Visto ya, un «no hay panel» sí vale enseguida.
        assertEquals(Step.Gone, SheetWatch.next(OTHER, nothing, waited = 500, panelSeen = true, sinceOpened = 600))
        // La hoja de compartir no espera: la «X» las devuelve como en la 1.10.1.
        assertEquals(Step.Gone, SheetWatch.next(SHARE, nothing, waited = 500, panelSeen = false, sinceOpened = 500))
    }

    @Test
    fun `una lectura fallida del panel con los comentarios ya vistos mantiene lo de antes`() {
        assertEquals(Step.Wait(SheetWatch.PANEL_POLL_MS), SheetWatch.next(OTHER, Look(windowAbove = false, panel = null), waited = 30_000, panelSeen = true))
        assertEquals(Step.GiveUp, SheetWatch.next(OTHER, Look(windowAbove = false, panel = null), waited = SheetWatch.PANEL_WATCH_MS, panelSeen = true))
    }

    @Test
    fun `sin saber nada tras ver el panel no se mira 10 minutos`() {
        // Ha salido de TikTok o se ha apagado la pantalla: la ventana del feed ya no está.
        assertEquals(Step.GiveUp, SheetWatch.next(OTHER, Look(null, null), waited = 20_000, panelSeen = true))
        assertEquals(Step.Wait(500), SheetWatch.next(OTHER, Look(null, null), waited = 2_000, panelSeen = true))
    }

    @Test
    fun `la hoja de compartir no mira el panel`() {
        assertFalse(SheetWatch.usesPanel(SHARE))
        assertFalse(SheetWatch.usesPanel(null))
        assertTrue(SheetWatch.usesPanel(OTHER))
        // Aunque algo encajara por error en FeedPanel, con la de compartir no cuenta.
        assertEquals(Step.Gone, SheetWatch.next(SHARE, Look(windowAbove = false, panel = true), waited = 500, panelSeen = false))
    }

    @Test
    fun `tambien se mira tras tocar abajo, no tras tocar el video`() {
        // El adelanto de un comentario sobre la descripción (a la izquierda, abajo) y la barra «Añadir comentario…».
        assertTrue(SheetWatch.worthProbing(false, box(40, 1700, 700, 1800), SCREEN_W, SCREEN_H))
        assertTrue(SheetWatch.worthProbing(false, box(0, 2100, SCREEN_W, 2230), SCREEN_W, SCREEN_H))
        assertTrue(SheetWatch.worthProbing(true, box(950, 300, 1050, 400), SCREEN_W, SCREEN_H))
        // El vídeo (alto) o algo de arriba: no.
        assertFalse(SheetWatch.worthProbing(false, box(0, 0, SCREEN_W, 2270), SCREEN_W, SCREEN_H))
        assertFalse(SheetWatch.worthProbing(false, box(40, 200, 400, 300), SCREEN_W, SCREEN_H))
        assertFalse(SheetWatch.worthProbing(false, NodeBounds.EMPTY, SCREEN_W, SCREEN_H))
        assertFalse(SheetWatch.worthProbing(false, box(40, 1700, 700, 1800), 0, 0))
    }

    @Test
    fun `el antes leido tarde se cambia por lo ultimo visto sin hoja`() {
        val clear = Look(windowAbove = false, panel = false)
        val opened = Look(windowAbove = false, panel = true)
        // A tiempo: lo leído.
        assertEquals(opened, SheetWatch.probeBefore(opened, readLate = 40, baseline = clear))
        // Tarde (el hilo de lectura estaba ocupado y el panel ya estaba subiendo): lo de antes del clic.
        val before = SheetWatch.probeBefore(opened, readLate = 300, baseline = clear)
        assertEquals(clear, before)
        assertTrue(SheetWatch.probeOpened(before, opened))
        // Tarde y sin nada con qué comparar: lo leído (como antes).
        assertEquals(opened, SheetWatch.probeBefore(opened, readLate = 300, baseline = null))
        // Un «panel» que siempre encaja por error sigue sin esconder nada.
        assertFalse(SheetWatch.probeOpened(SheetWatch.probeBefore(opened, 300, opened), opened))
    }
}
