package com.reelometro.app.detection

import com.reelometro.app.detection.QuickSender.Step
import com.reelometro.app.detection.QuickSender.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un solo [QuickSender] para Instagram y TikTok (como en el servicio): lo de un envío de una app no puede cambiar cómo va
 * el siguiente de la otra. Aquí, las raíces de la hoja que cortan la subida por los antepasados (revisión de la 1.10.0).
 */
class QuickSenderEntreAppsTest {

    /** Cuenta cada `parent` pedido (en el móvil, una llamada a la app) en lo que devuelve el atajo. */
    private class Counted(val inner: UiNode, val n: IntArray) : UiNode by inner {
        override val parent: UiNode?
            get() {
                n[0]++
                return inner.parent?.let { Counted(it, n) }
            }
        override val children: List<UiNode> get() = inner.children.map { Counted(it, n) }
        override fun equals(other: Any?): Boolean = (other is Counted && other.inner === inner) || other === inner
        override fun hashCode(): Int = System.identityHashCode(inner)
    }

    private class CountingFinder(val inner: FakeFinder = FakeFinder()) : UiFinder {
        val parents = IntArray(1)
        var wrap = false
        override fun byText(root: UiNode, text: String): List<UiNode> = inner.byText(root, text).map { if (wrap) Counted(it, parents) else it }
        override fun byViewId(root: UiNode, viewId: String): List<UiNode> = inner.byViewId(root, viewId).map { if (wrap) Counted(it, parents) else it }
    }

    // Instagram, como en QuickSenderTest, pero con la hoja colgada de varios niveles y su raíz con id.
    private fun reel() = FakeNode(
        viewId = "com.instagram.android:id/clips_viewer_view_pager",
        bounds = box(0, 0, 1080, 2400),
        children = listOf(
            FakeNode(
                viewId = "com.instagram.android:id/clips_video_container",
                bounds = box(0, 0, 1080, 2400),
                children = listOf(
                    text("Un reel cualquiera", box(40, 1900, 800, 1960)),
                    FakeNode(
                        viewId = "com.instagram.android:id/clips_ufi_component",
                        bounds = box(960, 1200, 1060, 1900),
                        children = listOf(
                            desc("Me gusta", box(960, 1200, 1060, 1300)),
                            desc("Comentar", box(960, 1350, 1060, 1450)),
                            desc("Compartir", box(960, 1500, 1060, 1600), id = "com.instagram.android:id/direct_share_button"),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun tile(name: String, left: Int, top: Int) = FakeNode(
        viewId = QuickSender.KNOWN_GRID_CELL_ID,
        bounds = box(left, top, left + 240, top + 300),
        children = listOf(desc("Foto del perfil de $name", box(left + 40, top, left + 200, top + 160)), text(name, box(left, top + 180, left + 240, top + 230))),
    )

    private fun igSheet(vararg names: String, selected: Boolean = false): FakeNode {
        val content = FakeNode(
            viewId = "com.instagram.android:id/layout_container_bottom_sheet",
            bounds = box(0, 800, 1080, 2400),
            children = buildList {
                add(FakeNode(className = "android.widget.EditText", text = "Buscar", viewId = QuickSender.KNOWN_SEARCH_ID, bounds = box(40, 860, 1040, 960)))
                add(FakeNode(bounds = box(0, 1000, 1080, 1900), children = names.mapIndexed { i, n -> tile(n, 40 + (i % 4) * 260, 1000 + (i / 4) * 320) }))
                add(
                    FakeNode(
                        bounds = box(0, 1950, 1080, 2150),
                        children = listOf("Copiar enlace", "WhatsApp", "Añadir a la historia").mapIndexed { i, l ->
                            FakeNode(bounds = box(40 + i * 260, 1950, 260 + i * 260, 2150), children = listOf(text(l, box(40 + i * 260, 2080, 260 + i * 260, 2130))))
                        },
                    ),
                )
                if (selected) {
                    add(FakeNode(className = "android.widget.EditText", text = "Escribe un mensaje", bounds = box(40, 2160, 1040, 2200)))
                    add(FakeNode(className = "android.widget.Button", bounds = box(40, 2200, 1040, 2340), children = listOf(text("Enviar", box(400, 2240, 680, 2300)))))
                }
            },
        )
        // Como en Instagram: la hoja va bastantes niveles por debajo de la raíz de la ventana.
        var node = content
        repeat(12) { node = FakeNode(bounds = box(0, 0, 1080, 2400), children = listOf(node)) }
        return node
    }

    private fun clickOf(step: Step): Step.Click {
        assertTrue("se esperaba un clic y llegó $step", step is Step.Click)
        return step as Step.Click
    }

    private class Run(val steps: List<String>, val parents: List<Int>, val describe: String)

    /** Un envío de Instagram sin buscador aprendido (el primero tras instalar o tras borrarlo todo). */
    private fun instagramSend(s: QuickSender, f: CountingFinder): Run {
        f.wrap = true
        val steps = mutableListOf<String>()
        val parents = mutableListOf<Int>()
        fun t(now: Long, vararg roots: UiNode): Step {
            val before = f.parents[0]
            val step = s.tick(roots.toList(), SCREEN_W, SCREEN_H, now)
            parents += f.parents[0] - before
            steps += when (step) {
                is Step.Click -> "${step.what}:${step.node.text ?: step.node.contentDescription}"
                else -> step.toString()
            }
            return step
        }
        s.start(listOf(Target("Los del piso")), now = 0)
        clickOf(t(0, reel())); s.onPerformed(true)
        t(150, reel())
        clickOf(t(300, igSheet("Ana García", "Los del piso"), reel())); s.onPerformed(true)
        clickOf(t(400, igSheet("Ana García", "Los del piso", selected = true), reel())); s.onPerformed(true)
        assertTrue((t(550, reel()) as Step.Done).ok)
        f.wrap = false
        return Run(steps, parents, s.lastReport!!.describe())
    }

    /** Un envío de TikTok sin buscador aprendido, por la fila de contactos (los volcados reales). */
    private fun tiktokSend(s: QuickSender) {
        val feed = UiDump.load("tiktok-47.7-feed.xml", windowId = 1)
        val sheet = UiDump.load("tiktok-47.7-hoja.xml", windowId = 2)
        val marked = UiDump.load("tiktok-47.7-hoja-marcado.xml", windowId = 2)
        s.start(listOf(Target("💿")), 0L, QuickSender.Hints(), false, PlatformProfile.TIKTOK)
        clickOf(s.tick(listOf(feed), UiDump.W, UiDump.H, 0)); s.onPerformed(true)
        clickOf(s.tick(listOf(sheet, feed), UiDump.W, UiDump.H, 100)); s.onPerformed(true)
        clickOf(s.tick(listOf(marked, feed), UiDump.W, UiDump.H, 200)); s.onPerformed(true)
        assertTrue((s.tick(listOf(feed), UiDump.W, UiDump.H, 300) as Step.Done).ok)
    }

    @Test
    fun `un envio de Instagram tras uno de TikTok va como en un QuickSender nuevo`() {
        val fresh = CountingFinder()
        val alone = instagramSend(QuickSender(finder = fresh), fresh)

        val shared = CountingFinder()
        val sender = QuickSender(finder = shared)
        tiktokSend(sender)
        val after = instagramSend(sender, shared)

        assertEquals("mismos pasos", alone.steps, after.steps)
        assertEquals("mismo informe", alone.describe, after.describe)
        assertEquals("la subida se para en la raíz de la hoja de Instagram, no en la de TikTok", alone.parents, after.parents)
    }
}
