package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareDetectorTest {

    private val detector = ShareDetector()
    private val rightColumn = box(950, 1700, 1050, 1800)

    private fun isShare(node: FakeNode) = detector.evaluate(node, SCREEN_W, SCREEN_H).isShare

    @Test
    fun `el boton compartir cuenta en espanol e ingles`() {
        assertTrue(isShare(desc("Compartir", rightColumn)))
        assertTrue(isShare(desc("Compartir vídeo, 1.234", rightColumn)))
        assertTrue(isShare(desc("Share", rightColumn)))
        assertTrue(isShare(desc("Enviar", rightColumn)))   // avión de papel de Instagram
        assertTrue(isShare(desc("Send", rightColumn)))
    }

    @Test
    fun `cuenta por id o por la etiqueta de un hijo o un antepasado cercano`() {
        assertTrue(isShare(FakeNode(viewId = "com.instagram.android:id/direct_share_button", bounds = rightColumn)))
        assertTrue(isShare(FakeNode(bounds = rightColumn, children = listOf(desc("Compartir", rightColumn)))))
        val icon = FakeNode(className = "android.widget.ImageView", bounds = rightColumn)
        FakeNode(contentDescription = "Compartir", bounds = box(940, 1690, 1060, 1810), children = listOf(icon))
        assertTrue(isShare(icon))
    }

    @Test
    fun `otros botones y la descripcion del video no cuentan`() {
        assertFalse(isShare(desc("Me gusta, 12 K", rightColumn)))
        assertFalse(isShare(desc("Comentarios, 345", rightColumn)))
        assertFalse(isShare(desc("Enviar mensaje", rightColumn)))
        assertFalse(isShare(text("Compártelo con tus amigos", box(40, 1900, 800, 1960))))     // a la izquierda y ancho
        assertFalse(isShare(desc("Compartir", box(40, 1700, 140, 1800))))                    // fuera de la columna de acciones
    }

    @Test
    fun `el enviar del cuadro de comentarios no cuenta`() {
        val send = desc("Enviar", box(980, 2210, 1060, 2340), cls = "android.widget.Button")
        FakeNode(
            bounds = box(0, 2200, 1080, 2350),
            children = listOf(FakeNode(className = "android.widget.EditText", bounds = box(20, 2210, 960, 2340)), send),
        )
        assertFalse(isShare(send))
    }

    @Test
    fun `tras compartir no se mira nada hasta que el feed se mueve`() {
        val d = ShareDetector()
        assertTrue(d.isArmed(Platform.TIKTOK, 0))
        d.onShareRecorded(Platform.TIKTOK, 1_000)
        assertFalse(d.isArmed(Platform.TIKTOK, 5_000))
        assertTrue(d.isArmed(Platform.INSTAGRAM, 5_000))
        d.onFeedActivity(Platform.TIKTOK)
        assertTrue(d.isArmed(Platform.TIKTOK, 6_000))
        d.onShareRecorded(Platform.TIKTOK, 10_000)
        assertTrue(d.isArmed(Platform.TIKTOK, 10_000 + 11 * 60_000L))   // por si el feed no vuelve a moverse
    }

    @Test
    fun `abrir una hoja encima del visor compartir comentarios o mas opciones`() {
        fun opens(node: FakeNode) = detector.opensSheet(node, SCREEN_W, SCREEN_H)
        assertTrue(opens(desc("Compartir", rightColumn)))
        assertTrue(opens(desc("Comentar", rightColumn)))
        assertTrue(opens(desc("Comentarios, 1.234", rightColumn)))
        assertTrue(opens(desc("Comment", rightColumn)))
        assertTrue(opens(desc("Más opciones", rightColumn)))
        assertTrue(opens(desc("More", rightColumn)))
        // Aunque el detector esté en silencio tras un compartido (eso es para contar, no para las burbujas).
        detector.onShareRecorded(Platform.INSTAGRAM, now = 0)
        assertTrue(opens(desc("Compartir", rightColumn)))
        // No: me gusta, la descripción, algo fuera de la columna o el «Enviar» de un comentario.
        assertFalse(opens(desc("Me gusta", rightColumn)))
        assertFalse(opens(text("Comentarios de la gente sobre el reel de hoy que son muchos", box(40, 1900, 800, 1960))))
        assertFalse(opens(desc("Comentar", box(40, 1700, 140, 1800))))
        val field = FakeNode(className = "android.widget.EditText", bounds = box(40, 2200, 900, 2300))
        val sendComment = desc("Comentar", box(950, 2200, 1050, 2300))
        FakeNode(bounds = box(0, 2200, 1080, 2300), children = listOf(field, sendComment))
        assertFalse(opens(sendComment))
    }

    /** 1.10.0: los botones del visor de TikTok 47.7 (volcado real anonimizado). */
    @Test
    fun `en TikTok compartir y comentarios abren hoja y me gusta no`() {
        val feed = UiDump.all(UiDump.load("tiktok-47.7-feed.xml"))
        fun byDesc(prefix: String) = feed.single { it.contentDescription?.startsWith(prefix) == true }
        fun opensTikTok(node: UiNode) = detector.opensSheet(node, UiDump.W, UiDump.H, Platform.TIKTOK)
        val share = byDesc("Compartir el vídeo")
        val comments = byDesc("Leer o añadir comentarios")
        val like = byDesc("Dar me gusta")
        assertTrue(opensTikTok(share))
        assertTrue(opensTikTok(comments))
        // El clic suele llegar del icono de dentro (pulsable, sin etiqueta).
        assertTrue(opensTikTok(feed.single { shortViewId(it.viewId) == "ekj" }))
        assertTrue(detector.evaluate(feed.single { shortViewId(it.viewId) == "wbn" }, UiDump.W, UiDump.H).isShare)
        assertFalse(opensTikTok(like))
        assertFalse(opensTikTok(byDesc("Añade o elimina este vídeo de Favoritos")))
        assertFalse(detector.evaluate(like, UiDump.W, UiDump.H).isShare)
        // Con las reglas de Instagram (sin plataforma), la etiqueta larga de comentarios no cuenta: igual que antes.
        assertFalse(detector.opensSheet(comments, UiDump.W, UiDump.H))
    }
}
