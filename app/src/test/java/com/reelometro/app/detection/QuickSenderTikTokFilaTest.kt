package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Found
import com.reelometro.app.detection.QuickSender.Step
import com.reelometro.app.detection.QuickSender.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.1, la fila de contactos de TikTok **abierta desplazada** (visto en el móvil: «idx 2→8 de 61»; el fijado, de los
 * primeros, no sale y el envío iba por el buscador, ~2,7 s en vez de ~0,5 s). Si el fijado no está y la fila se puede volver
 * hacia atrás, se vuelve al principio (`ACTION_SCROLL_BACKWARD`) una sola vez y se mira otra vez antes de la lupa. Con los
 * volcados reales: la fila desplazada es la hoja sin la celda de 💿, y su `RecyclerView` (`v8u`) dice que puede ir atrás.
 */
class QuickSenderTikTokFilaTest {

    private val finder = FakeFinder()
    private val sender = QuickSender(finder = finder)

    private val feed by lazy { UiDump.load("tiktok-47.7-feed.xml", windowId = FEED) }
    private val sheet by lazy { UiDump.load("tiktok-47.7-hoja.xml", windowId = SHEET) }
    private val sheetMarked by lazy { UiDump.load("tiktok-47.7-hoja-marcado.xml", windowId = SHEET) }

    /** Lo que se ha pedido desplazar: la fila de contactos y cualquier otra cosa (que nunca debe). */
    private class Scrolls {
        var row = 0
        var other = 0
    }

    /**
     * Copia de un árbol en la que la fila de contactos (`v8u`) puede ir hacia atrás si [shifted] y apunta los desplazamientos
     * ([accepts]: lo que responde Android). Lo demás, como el original.
     */
    private class Mirror(
        private val inner: UiNode,
        override val parent: UiNode?,
        private val scrolls: Scrolls,
        private val shifted: Boolean,
        private val accepts: Boolean,
    ) : UiNode by inner {
        private val isRow get() = shortViewId(inner.viewId) == "v8u"
        override val children: List<UiNode> by lazy { inner.children.map { Mirror(it, this, scrolls, shifted, accepts) } }
        override val canScrollBackward: Boolean get() = isRow && shifted
        override fun scrollBackward(): Boolean {
            if (isRow) scrolls.row++ else scrolls.other++
            return isRow && accepts
        }
    }

    /** La hoja con la fila desplazada: sin la celda de 💿 (la de otra cuenta en su sitio). */
    private fun shiftedSheet(scrolls: Scrolls, shifted: Boolean = true, accepts: Boolean = true): UiNode {
        val xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-hoja.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        assertTrue(xml.contains("&#128191;"))
        return Mirror(UiDump.parse(xml.replace("&#128191;", "otra_cuenta"), windowId = SHEET), null, scrolls, shifted, accepts)
    }

    private fun start(target: Target, s: QuickSender = sender) = s.start(listOf(target), 0L, QuickSender.Hints(), false, PlatformProfile.TIKTOK)

    private fun tick(now: Long, vararg roots: UiNode, s: QuickSender = sender): Step = s.tick(roots.toList(), UiDump.W, UiDump.H, now)

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    @Test
    fun `con la fila desplazada se vuelve al principio y se envia por la fila`() {
        val scrolls = Scrolls()
        start(Target("💿", wantAvatar = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("se vuelve la fila al principio y se espera", Step.Wait, tick(100, shiftedSheet(scrolls), feed))
        assertEquals(1, scrolls.row)
        assertEquals("nunca se desplaza otra cosa", 0, scrolls.other)
        assertEquals("con la fila moviéndose, la celda debe salir dos veces en el mismo sitio", Step.Wait, tick(200, sheet, feed))
        val cell = clickOf(tick(300, sheet, feed))
        assertEquals("nombre", cell.what)
        assertEquals("💿", cell.node.contentDescription)
        assertEquals("con la fila aún moviéndose, sin recortar la foto", null, cell.avatar)
        sender.onPerformed(true)
        assertEquals("enviar", clickOf(tick(400, sheetMarked, feed)).what); sender.onPerformed(true)
        assertTrue((tick(500, feed) as Step.Done).ok)
        val report = sender.lastReport!!
        assertEquals(Found.GRID, report.foundIn)
        assertEquals(0, report.walks)
        assertTrue(report.searches.isEmpty())
        assertEquals(true, report.actions.single { it.what == "vuelve la fila al principio" }.accepted)
        // Por la fila sin desplazar son 4 consultas (compartir, título, nombre, «Enviar»); desplazada, dos ticks más (volverla
        // al principio y ver la celda quieta): título y nombre en cada uno.
        assertEquals("consultas con la fila desplazada: ${finder.calls}", 8, finder.calls.size)
    }

    @Test
    fun `con la fila moviendose no pulsa la celda hasta verla quieta`() {
        // Revisión de la 1.10.1: a mitad de movimiento la celda con el nombre cambia de sitio (reciclada podría ser otra).
        val scrolls = Scrolls()
        val xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-hoja.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val moving = UiDump.parse(xml.replace("[894,1566][1057,1786]", "[600,1566][763,1786]"), windowId = SHEET)
        start(Target("💿"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals(Step.Wait, tick(100, shiftedSheet(scrolls), feed))
        assertEquals(Step.Wait, tick(200, moving, feed))
        assertEquals("se ha movido: otra vez a esperar", Step.Wait, tick(300, sheet, feed))
        val cell = clickOf(tick(400, sheet, feed))
        assertEquals(NodeBounds(894, 1566, 1057, 1786), cell.node.bounds)
    }

    @Test
    fun `sin desplazar la celda se pulsa a la primera`() {
        start(Target("💿"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("nombre", clickOf(tick(100, sheet, feed)).what)
    }

    @Test
    fun `si tras volverla al principio sigue sin estar va a la lupa una sola vez desplazada`() {
        val scrolls = Scrolls()
        start(Target("💿"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals(Step.Wait, tick(100, shiftedSheet(scrolls), feed))
        // Mientras se mueve (700 ms) se sigue mirando la fila; sin volver a desplazar.
        for (now in 200L..700L step 100) assertEquals("a los $now ms", Step.Wait, tick(now, shiftedSheet(scrolls), feed))
        val lupa = clickOf(tick(800, shiftedSheet(scrolls), feed))
        assertEquals("lupa del buscador", lupa.what)
        assertEquals("solo una vez por envío", 1, scrolls.row)
        assertEquals(0, scrolls.other)
    }

    @Test
    fun `con la fila al principio va a la lupa sin desplazar`() {
        val scrolls = Scrolls()
        start(Target("💿"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, shiftedSheet(scrolls, shifted = false), feed)).what)
        assertEquals(0, scrolls.row + scrolls.other)
    }

    @Test
    fun `si Android no la desplaza va a la lupa en el mismo tick`() {
        val scrolls = Scrolls()
        start(Target("💿"))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, shiftedSheet(scrolls, accepts = false), feed)).what)
        assertEquals(1, scrolls.row)
    }

    @Test
    fun `directo al buscador aprendido no desplaza la fila`() {
        val scrolls = Scrolls()
        start(Target("💿", skipGrid = true))
        clickOf(tick(0, feed)); sender.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, shiftedSheet(scrolls), feed)).what)
        assertEquals(0, scrolls.row)
    }

    @Test
    fun `sin atajo tambien vuelve la fila al principio`() {
        val walker = QuickSender()
        val scrolls = Scrolls()
        start(Target("💿"), walker)
        clickOf(tick(0, feed, s = walker)); walker.onPerformed(true)
        assertEquals(Step.Wait, tick(100, shiftedSheet(scrolls), feed, s = walker))
        assertEquals(1, scrolls.row)
        assertEquals(Step.Wait, tick(200, sheet, feed, s = walker))
        assertEquals("💿", clickOf(tick(300, sheet, feed, s = walker)).node.contentDescription)
    }

    private companion object {
        const val FEED = 1
        const val SHEET = 2
    }
}
