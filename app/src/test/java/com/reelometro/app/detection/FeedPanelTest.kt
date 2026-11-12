package com.reelometro.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.10.5: los comentarios de TikTok van en la misma ventana del feed. [FeedPanel] los ve por ids y geometría, sin leer
 * textos. Con el volcado real del feed de TikTok 47.7 (sin comentarios abiertos) y árboles hechos a mano con el panel:
 * aún no hay volcado con los comentarios abiertos (falta sacarlo en el móvil).
 */
class FeedPanelTest {

    private val ids = listOf("viewpager")

    private fun isOpen(root: UiNode, w: Int = SCREEN_W, h: Int = SCREEN_H, maxNodes: Int = FeedPanel.MAX_NODES) =
        FeedPanel.isOpen(root, ids, w, h, maxNodes)

    /** Una hoja de abajo a lo ancho, con contenido. */
    private fun panel(top: Int, w: Int = SCREEN_W, h: Int = SCREEN_H, visible: Boolean = true, left: Int = 0) =
        FakeNode(
            viewId = "com.ss.android.ugc.trill:id/c2x",
            bounds = box(left, top, left + w, h),
            isVisible = visible,
            children = listOf(FakeNode(className = "androidx.recyclerview.widget.RecyclerView", bounds = box(left, top + 100, left + w, h - 150))),
        )

    /**
     * Como TikTok 47.7: el `viewpager` de las pestañas (fuera) envuelve un contenedor con el `viewpager` del visor; en el
     * contenedor van además [extra] (hermanos del visor) y dentro del visor [inViewer].
     */
    private fun tree(w: Int = SCREEN_W, h: Int = SCREEN_H, viewer: NodeBounds = box(0, 0, w, h - 130), inViewer: List<FakeNode> = emptyList(), extra: List<FakeNode> = emptyList()): FakeNode {
        val visor = FakeNode(
            viewId = "com.ss.android.ugc.trill:id/viewpager",
            className = "androidx.viewpager.widget.ViewPager",
            bounds = viewer,
            children = listOf(FakeNode(viewId = "com.ss.android.ugc.trill:id/view_rootview", bounds = viewer, children = inViewer + desc("Me gusta", box(w - 130, h / 2, w - 10, h / 2 + 120)))),
        )
        val container = FakeNode(viewId = "com.ss.android.ugc.trill:id/viewpager_container", bounds = box(0, 0, w, h - 60), children = listOf(visor) + extra)
        val tabs = FakeNode(viewId = "com.ss.android.ugc.trill:id/viewpager", bounds = box(0, 0, w, h), children = listOf(container))
        return FakeNode(bounds = box(0, 0, w, h), children = listOf(tabs))
    }

    @Test
    fun `el feed real de TikTok sin comentarios no tiene panel`() {
        // El nodo lkj ([0,1124][1080,2232]) tiene forma de panel, pero va dentro del visor: no cuenta.
        assertEquals(false, isOpen(UiDump.load("tiktok-47.7-feed.xml"), UiDump.W, UiDump.H))
    }

    @Test
    fun `una ventana sin visor no se sabe`() {
        assertNull(isOpen(UiDump.load("tiktok-47.7-hoja.xml"), UiDump.W, UiDump.H))
        assertNull(isOpen(tree(), w = 0, h = 0))
    }

    @Test
    fun `un panel hermano del feed real lo tapa`() {
        val feed = UiDump.load("tiktok-47.7-feed.xml")
        val root = FakeNode(bounds = box(0, 0, UiDump.W, UiDump.H), children = listOf(feed, panel(top = 700, w = UiDump.W, h = UiDump.H)))
        assertEquals(true, isOpen(root, UiDump.W, UiDump.H))
    }

    @Test
    fun `un panel dentro del pager de fuera pero fuera del visor lo tapa`() {
        assertEquals(false, isOpen(tree()))
        assertEquals(true, isOpen(tree(extra = listOf(panel(top = 720)))))
    }

    @Test
    fun `aun subiendo cuenta`() {
        assertEquals(true, isOpen(tree(extra = listOf(panel(top = (SCREEN_H * 0.65f).toInt())))))
    }

    @Test
    fun `lo que tiene forma de panel dentro del visor no cuenta`() {
        assertEquals(false, isOpen(tree(inViewer = listOf(panel(top = 1100, h = SCREEN_H - 130)))))
    }

    @Test
    fun `ni lo de pantalla completa ni la barra de abajo ni lo invisible ni lo vacio`() {
        val full = panel(top = 0)
        val tabBar = panel(top = SCREEN_H - 130)
        val hidden = panel(top = 700, visible = false)
        val empty = FakeNode(bounds = box(0, 700, SCREEN_W, SCREEN_H))
        val narrow = FakeNode(bounds = box(0, 700, SCREEN_W / 2, SCREEN_H), children = listOf(FakeNode()))
        val otherTab = panel(top = 700, left = SCREEN_W) // la página de otra pestaña, fuera de la pantalla
        assertEquals(false, isOpen(tree(extra = listOf(full, tabBar, hidden, empty, narrow, otherTab))))
    }

    @Test
    fun `el visor encogido arriba cuenta como panel`() {
        assertEquals(true, isOpen(tree(viewer = box(0, 100, SCREEN_W, 1300))))
    }

    @Test
    fun `demasiados nodos no se sabe`() {
        assertNull(isOpen(UiDump.load("tiktok-47.7-feed.xml"), UiDump.W, UiDump.H, maxNodes = 10))
    }

    @Test
    fun `en horizontal las mismas reglas`() {
        val w = 2400
        val h = 1080
        assertEquals(false, isOpen(tree(w = w, h = h), w, h))
        assertEquals(true, isOpen(tree(w = w, h = h, extra = listOf(panel(top = 300, w = w, h = h))), w, h))
    }

    /** Un nodo que cuenta cuántas veces se le piden los hijos (en el móvil, una llamada entre procesos). */
    private class Counting(override val bounds: NodeBounds, private val kids: List<UiNode> = emptyList(), override val viewId: String? = null) : UiNode {
        var reads = 0
        override val text: CharSequence? = null
        override val contentDescription: CharSequence? = null
        override val className: CharSequence? = null
        override val isVisible = true
        override val parent: UiNode? = null
        override val children: List<UiNode>
            get() {
                reads++
                return kids
            }
    }

    @Test
    fun `no baja a los nodos estrechos`() {
        val button = Counting(box(950, 1200, 1060, 1320), kids = listOf(Counting(box(960, 1210, 1050, 1310))))
        val column = Counting(box(900, 800, SCREEN_W, 2000), kids = listOf(button))
        val viewer = Counting(box(0, 0, SCREEN_W, 2270), kids = listOf(column), viewId = "com.ss.android.ugc.trill:id/viewpager")
        val root = Counting(box(0, 0, SCREEN_W, SCREEN_H), kids = listOf(viewer))
        assertEquals(false, isOpen(root))
        assertEquals(1, root.reads)
        assertEquals(1, viewer.reads)
        assertEquals(0, column.reads)
        assertEquals(0, button.reads)
    }

    @Test
    fun `el feed real se mira con pocos nodos`() {
        // Con el tope en 120 aún se decide: el recorrido podado no pasa de ~100 nodos.
        assertEquals(false, isOpen(UiDump.load("tiktok-47.7-feed.xml"), UiDump.W, UiDump.H, maxNodes = 120))
    }

    @Test
    fun `ya vistos, los comentarios encogidos por el teclado siguen contando`() {
        // Escribiendo un comentario: la hoja va del 30 % a encima del teclado (~55 %), sin llegar abajo.
        val shrunk = FakeNode(
            viewId = "com.ss.android.ugc.trill:id/c2x",
            bounds = box(0, 700, SCREEN_W, 1250),
            children = listOf(FakeNode(className = "androidx.recyclerview.widget.RecyclerView", bounds = box(0, 800, SCREEN_W, 1150))),
        )
        val root = tree(extra = listOf(shrunk))
        assertEquals("sin haberlos visto, la regla estricta", false, isOpen(root))
        assertEquals(true, FeedPanel.isOpen(root, ids, SCREEN_W, SCREEN_H, relaxed = true))
    }

    @Test
    fun `la regla relajada no ve paneles en el feed real ni en lo de siempre`() {
        assertEquals(false, FeedPanel.isOpen(UiDump.load("tiktok-47.7-feed.xml"), ids, UiDump.W, UiDump.H, relaxed = true))
        // Una barra fina a lo ancho (menos del 15 % del alto) tampoco.
        val bar = FakeNode(bounds = box(0, 900, SCREEN_W, 1100), children = listOf(FakeNode(bounds = box(0, 900, 200, 1100))))
        assertEquals(false, FeedPanel.isOpen(tree(extra = listOf(bar)), ids, SCREEN_W, SCREEN_H, relaxed = true))
    }
}
