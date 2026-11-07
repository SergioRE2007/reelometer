package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Found
import com.reelometro.app.detection.QuickSender.Step
import com.reelometro.app.detection.QuickSender.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.10.1, **el buscador de TikTok va en otra ventana** que la hoja de contactos. En los volcados de la 47.7 la ventana de la
 * hoja tiene un `FrameLayout` más entre `content` y el contenedor (`f3f`) que la del buscador: son dos diálogos. En el móvil,
 * tras la lupa el atajo (que solo miraba la ventana de la hoja) no veía el campo durante ~1 s y lo encontraba el recorrido.
 * Aquí el buscador va con su propio `windowId` (3), con la hoja ya cerrada o aún debajo: el campo y la fila se encuentran en
 * el mismo tick, y una ventana con un campo que no es una hoja de abajo nunca vale.
 */
class QuickSenderTikTokVentanaTest {

    private val finder = FakeFinder()
    private val sender = QuickSender(finder = finder)

    private val feed by lazy { UiDump.load("tiktok-47.7-feed.xml", windowId = FEED) }
    private val sheet by lazy { UiDump.load("tiktok-47.7-hoja.xml", windowId = SHEET) }
    private val search by lazy { UiDump.load("tiktok-47.7-buscador.xml", windowId = SEARCH) }
    private val searchTyped by lazy { UiDump.load("tiktok-47.7-buscador-escrito.xml", windowId = SEARCH) }
    private val searchMarked by lazy { UiDump.load("tiktok-47.7-buscador-marcado.xml", windowId = SEARCH) }

    @Test
    fun `los volcados de la hoja y del buscador son de dos ventanas distintas`() {
        // Bajo la decoración de la ventana (… a43 › content), la hoja lleva un FrameLayout sin id antes del contenedor f3f.
        fun path(root: UiNode): List<String> {
            val out = ArrayList<String>()
            var n: UiNode? = root
            while (n != null && shortViewId(n.viewId) != "f3f") {
                out += shortViewId(n.viewId).ifEmpty { simpleClassName(n.className) }
                n = n.children.firstOrNull()
            }
            return out
        }
        assertEquals(listOf("FrameLayout", "LinearLayout", "FrameLayout", "a43", "content", "FrameLayout"), path(sheet))
        assertEquals(listOf("FrameLayout", "LinearLayout", "FrameLayout", "a43", "content"), path(search))
    }

    private fun start(target: Target, s: QuickSender = sender) = s.start(listOf(target), 0L, QuickSender.Hints(), false, PlatformProfile.TIKTOK)

    private fun tick(now: Long, vararg roots: UiNode, s: QuickSender = sender): Step = s.tick(roots.toList(), UiDump.W, UiDump.H, now)

    private fun tickCounted(now: Long, vararg roots: UiNode): Pair<Step, Int> {
        val before = finder.calls.size
        return tick(now, *roots) to finder.calls.size - before
    }

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    /** Compartir y la lupa (directo al buscador). */
    private fun toLupa(target: Target, s: QuickSender = sender) {
        start(target, s)
        assertEquals("compartir", clickOf(tick(0, feed, s = s)).what); s.onPerformed(true)
        assertEquals("lupa del buscador", clickOf(tick(100, sheet, feed, s = s)).what); s.onPerformed(true)
    }

    @Test
    fun `con el buscador en otra ventana y la hoja ya cerrada el atajo lo ve al momento`() {
        toLupa(Target("rubio_m", skipGrid = true))
        val (row, q) = tickCounted(200, search, feed)
        val click = clickOf(row)
        assertEquals("nombre del buscador", click.what)
        assertEquals(NodeBounds(0, 587, 1080, 765), click.node.bounds)
        assertEquals(SEARCH, click.node.windowId)
        assertEquals("la lupa del buscador (todas las ventanas menos el visor), touch_outside en esa ventana y el nombre", 3, q)
        sender.onPerformed(true)
        val send = clickOf(tick(300, searchMarked, feed))
        assertEquals("tv_send", shortViewId(send.node.viewId))
        assertEquals(SEARCH, send.node.windowId)
        sender.onPerformed(true)
        assertTrue((tick(400, feed) as Step.Done).ok)
        val report = sender.lastReport!!
        assertEquals(0, report.walks)
        assertEquals(Found.SEARCH, report.foundIn)
        assertEquals("com.zhiliaoapp.musically:id/vlq", report.searchId)
    }

    @Test
    fun `con el buscador en otra ventana y la hoja aun debajo tambien`() {
        toLupa(Target("rubio_m", skipGrid = true))
        val (row, q) = tickCounted(200, search, sheet, feed)
        assertEquals(SEARCH, clickOf(row).node.windowId)
        assertEquals("la lupa en la hoja (sin campo al lado), en las demás, touch_outside y el nombre", 4, q)
    }

    @Test
    fun `con el buscador en otra ventana se escribe el nombre en su campo`() {
        toLupa(Target("ElRubencio Cuenta secundaria", skipGrid = true))
        val typed = tick(200, search, feed)
        assertTrue("se esperaba escribir y llegó $typed", typed is Step.SetText)
        typed as Step.SetText
        assertEquals("vlq", shortViewId(typed.node.viewId))
        assertEquals(SEARCH, typed.node.windowId)
        sender.onPerformed(true)
        assertEquals(NodeBounds(0, 853, 1080, 1031), clickOf(tick(300, searchTyped, feed)).node.bounds)
    }

    @Test
    fun `sin atajo el recorrido elige la ventana del buscador aunque la hoja siga debajo`() {
        val walker = QuickSender()
        toLupa(Target("rubio_m", skipGrid = true), walker)
        val row = clickOf(tick(200, search, sheet, feed, s = walker))
        assertEquals("nombre del buscador", row.what)
        assertEquals(SEARCH, row.node.windowId)
        walker.onPerformed(true)
        assertEquals("tv_send", shortViewId(clickOf(tick(300, searchMarked, sheet, feed, s = walker)).node.viewId))
    }

    /** Una ventana con un campo junto a «Buscar» que no es una hoja de abajo (sin touch_outside ni el título). */
    private val notASheet by lazy {
        var xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-buscador.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        listOf("com.zhiliaoapp.musically:id/touch_outside" to "", "text=\"Enviar a\"" to "text=\"Otra pantalla\"").forEach { (from, to) ->
            assertTrue(xml.contains(from))
            xml = xml.replace(from, to)
        }
        UiDump.parse(xml, windowId = SEARCH)
    }

    @Test
    fun `una ventana con un campo que no es una hoja nunca vale`() {
        for ((s, roots) in listOf(sender to listOf(notASheet, feed), QuickSender() to listOf(notASheet, feed), sender to listOf(notASheet, sheet, feed))) {
            toLupa(Target("rubio_m", skipGrid = true), s)
            var now = 200L
            var step = s.tick(roots, UiDump.W, UiDump.H, now)
            while (step !is Step.Done) {
                assertFalse("pulsó o escribió fuera de la hoja: $step", step is Step.Click || step is Step.SetText)
                now += 100
                step = s.tick(roots, UiDump.W, UiDump.H, now)
            }
            assertFalse(step.ok)
        }
    }

    @Test
    fun `el id del campo de una ventana que no es una hoja no se aprende`() {
        // Revisión de la 1.10.1: el id se aprendía antes de mirar si la ventana tenía touch_outside.
        val other = UiDump.parse(
            listOf("com.zhiliaoapp.musically:id/touch_outside" to "", "text=\"Enviar a\"" to "text=\"Otra pantalla\"", "id/vlq\"" to "id/zzq\"")
                .fold(UiDump::class.java.classLoader!!.getResourceAsStream("calibration/tiktok-47.7-buscador.xml")!!.use { it.readBytes().toString(Charsets.UTF_8) }) { xml, (from, to) ->
                    assertTrue(xml.contains(from))
                    xml.replace(from, to)
                },
            windowId = SEARCH,
        )
        toLupa(Target("rubio_m", skipGrid = true))
        var now = 200L
        while (tick(now, other, feed) !is Step.Done) now += 100
        assertFalse("aprendió el id de otra ventana: ${sender.lastReport!!.searchId}", sender.lastReport!!.searchId.orEmpty().endsWith("zzq"))
    }

    private companion object {
        const val FEED = 1
        const val SHEET = 2
        const val SEARCH = 3
    }
}
