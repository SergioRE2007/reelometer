package com.reelometro.app.detection

import com.reelometro.app.data.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que cuesta una lectura de [ContentExtractor] en un árbol grande: visita como mucho [ContentExtractor.MAX_NODES]
 * nodos, en el mismo orden que antes, y ya no pide al sistema los hijos de nodos que nunca va a visitar (cada hijo
 * del móvil puede ser una llamada entre procesos).
 */
class ContentExtractorNodesTest {

    /** Registro compartido por un árbol: orden de visita y cuántos hijos se han pedido. */
    private class Probe {
        val visited = mutableListOf<String>()
        var childrenFetched = 0
    }

    /** Nodo que apunta cuándo lo visita el recorrido (lee `isVisible`) y cuántos hijos se le piden. */
    private class CountingNode(
        val name: String,
        private val probe: Probe,
        override val text: CharSequence? = null,
        override val viewId: String? = null,
        private val visible: Boolean = true,
        kids: List<CountingNode> = emptyList(),
    ) : UiNode {
        private val kidList: List<UiNode> = kids
        override val contentDescription: CharSequence? = null
        override val className: CharSequence? = "android.view.ViewGroup"
        override val bounds: NodeBounds = NodeBounds(0, 0, 1080, 2400)
        override val isVisible: Boolean
            get() {
                probe.visited += name
                return visible
            }
        override val children: List<UiNode>
            get() {
                probe.childrenFetched += kidList.size
                return kidList
            }
        override val parent: UiNode? = null
    }

    /** Raíz → [fanOut] hijos → [grandFanOut] nietos cada uno; [texts] pone un texto en los nodos con ese nombre. */
    private fun tree(
        probe: Probe,
        fanOut: Int = 40,
        grandFanOut: Int = 30,
        texts: Map<String, Pair<String, String?>> = emptyMap(),
        hidden: Set<String> = emptySet(),
    ): CountingNode {
        fun node(name: String, kids: List<CountingNode> = emptyList()) =
            CountingNode(name, probe, texts[name]?.first, texts[name]?.second, visible = name !in hidden, kids = kids)
        val children = (0 until fanOut).map { i ->
            node("c$i", (0 until grandFanOut).map { j -> node("c$i.$j") })
        }
        return node("root", children)
    }

    /** Orden de visita esperado: en anchura, sin entrar en los invisibles, hasta [limit] nodos (la regla de siempre). */
    private fun expectedOrder(root: UiNode, hidden: Set<String>, limit: Int = ContentExtractor.MAX_NODES): List<String> {
        val out = mutableListOf<String>()
        val queue = ArrayDeque<UiNode>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < limit) {
            val n = queue.removeFirst() as CountingNode
            out += n.name
            if (n.name in hidden) continue
            queue.addAll(n.children)
        }
        return out
    }

    private fun extract(root: UiNode, stats: ContentExtractor.Stats? = null) =
        ContentExtractor.extract(Platform.TIKTOK, root, SCREEN_W, SCREEN_H, rootIsFeed = false, stats = stats)

    @Test
    fun `visita como mucho 300 nodos y no pide hijos que no va a visitar`() {
        val probe = Probe()
        val root = tree(probe)
        val stats = ContentExtractor.Stats()
        extract(root, stats)
        assertEquals(ContentExtractor.MAX_NODES, stats.nodes)
        assertEquals(ContentExtractor.MAX_NODES, probe.visited.size)
        // Antes: los 40 hijos de la raíz y los 30 de cada uno de los 40 (1.240). Ahora: la raíz y los 9 primeros hijos,
        // hasta tener 300 nodos en la cola (40 + 9 × 30 = 310).
        assertEquals(310, probe.childrenFetched)
        assertTrue(probe.childrenFetched <= ContentExtractor.MAX_NODES + 40)
    }

    @Test
    fun `el orden de visita es el de siempre, tambien con ramas invisibles`() {
        for (hidden in listOf(emptySet(), setOf("c0", "c3", "c4"), setOf("root"), (0 until 40).map { "c$it" }.toSet())) {
            val probe = Probe()
            val root = tree(probe, hidden = hidden)
            val stats = ContentExtractor.Stats()
            extract(root, stats)
            val expected = expectedOrder(tree(Probe(), hidden = hidden), hidden)
            assertEquals("ocultos: $hidden", expected, probe.visited)
            assertEquals(expected.size, stats.nodes)
        }
    }

    @Test
    fun `lee lo que hay dentro de los 300 primeros y nada de lo que queda fuera`() {
        // En anchura: root (1), c0..c39 (2..41), c0.0..c0.29 (42..71)… El nodo 300 es c8.18 y el 301, c8.19.
        val inside = "c8.18"
        val outside = "c8.19"
        val probe = Probe()
        val order = expectedOrder(tree(probe, hidden = emptySet()), emptySet(), limit = 301)
        assertEquals(inside, order[299])
        assertEquals(outside, order[300])

        val found = extract(tree(Probe(), texts = mapOf(inside to ("@creadora_dentro" to null))))
        assertEquals("creadora_dentro", found.creator)

        val missed = extract(tree(Probe(), texts = mapOf(outside to ("@creador_fuera" to null))))
        assertNull(missed.creator)

        val both = extract(
            tree(
                Probe(),
                texts = mapOf(
                    "c2" to ("Receta de lentejas de mi abuela, sin prisa #cocina #receta" to "com.app:id/desc"),
                    outside to ("@creador_fuera" to null),
                    "c39.29" to ("#nolee" to null),
                ),
            ),
        )
        assertEquals(ContentExtractor.VideoInfo(null, "Receta de lentejas de mi abuela, sin prisa #cocina #receta", listOf("cocina", "receta")), both)
    }

    @Test
    fun `sin contadores funciona igual`() {
        val texts = mapOf("c1.1" to ("@alguien" to null))
        assertEquals(extract(tree(Probe(), texts = texts), ContentExtractor.Stats()), extract(tree(Probe(), texts = texts)))
    }

    @Test
    fun `los contadores se suman entre lecturas`() {
        val stats = ContentExtractor.Stats()
        extract(tree(Probe(), fanOut = 2, grandFanOut = 2), stats)
        extract(tree(Probe(), fanOut = 2, grandFanOut = 2), stats)
        assertEquals(14, stats.nodes)
    }
}
