package com.reelometro.app.detection

/**
 * Carga un volcado de `uiautomator dump` (los de `src/test/resources/calibration/`, anonimizados) como un árbol de
 * [FakeNode], para probar el envío rápido y «A quién envías» con el árbol **real** de una app (1.10.0: TikTok 47.7).
 *
 * Cada volcado es **una sola ventana** (la que tenía el foco). En TikTok la hoja de compartir va en su propia ventana
 * encima del feed: para un tick con las dos, pasa las dos raíces (la de arriba primero) con `windowId` distintos, p. ej.
 * `listOf(UiDump.load("tiktok-47.7-hoja.xml", windowId = 2), UiDump.load("tiktok-47.7-feed.xml", windowId = 1))`.
 *
 * Se leen `text`, `content-desc`, `resource-id`, `class`, `bounds`, `clickable` y `checked`. Los volcados son de un
 * Pixel de 1080 × 2424 (no los [SCREEN_W] × [SCREEN_H] de los árboles hechos a mano): usa [W] y [H].
 */
object UiDump {

    /** Tamaño de la pantalla de los volcados (Pixel 10a). */
    const val W = 1080
    const val H = 2424

    private class Raw(val attrs: Map<String, String>) {
        val children = ArrayList<Raw>()
    }

    private val TAG = Regex("""<(/?)node\b([^>]*?)(/?)>""")
    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private val BOUNDS = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")
    private val ENTITY = Regex("""&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos);""")

    /** El árbol de [resource] (ruta dentro de `src/test/resources/calibration/`), con todos sus nodos en [windowId]. */
    fun load(resource: String, windowId: Int = UiNode.NO_WINDOW): FakeNode {
        val xml = UiDump::class.java.classLoader!!.getResourceAsStream("calibration/$resource")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("No está el volcado calibration/$resource")
        return parse(xml, windowId)
    }

    fun parse(xml: String, windowId: Int = UiNode.NO_WINDOW): FakeNode {
        val roots = ArrayList<Raw>()
        val stack = ArrayDeque<Raw>()
        for (m in TAG.findAll(xml)) {
            if (m.groupValues[1] == "/") {
                stack.removeLastOrNull()
                continue
            }
            val raw = Raw(ATTR.findAll(m.groupValues[2]).associate { it.groupValues[1] to decode(it.groupValues[2]) })
            (stack.lastOrNull()?.children ?: roots).add(raw)
            if (m.groupValues[3] != "/") stack.addLast(raw)
        }
        val root = roots.singleOrNull() ?: error("El volcado debería tener una sola raíz (tiene ${roots.size})")
        return build(root, windowId)
    }

    private fun build(raw: Raw, windowId: Int): FakeNode {
        val a = raw.attrs
        val b = BOUNDS.find(a["bounds"].orEmpty())?.groupValues?.drop(1)?.map { it.toInt() }
        return FakeNode(
            text = a["text"]?.ifEmpty { null },
            contentDescription = a["content-desc"]?.ifEmpty { null },
            viewId = a["resource-id"]?.ifEmpty { null },
            className = a["class"]?.ifEmpty { null },
            bounds = b?.let { NodeBounds(it[0], it[1], it[2], it[3]) } ?: NodeBounds.EMPTY,
            children = raw.children.map { build(it, windowId) },
            windowId = windowId,
            isClickable = a["clickable"] == "true",
            isChecked = a["checked"] == "true",
        )
    }

    private fun decode(value: String): String = ENTITY.replace(value) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val code = if (e.startsWith("#x")) e.substring(2).toInt(16) else e.substring(1).toInt()
                String(Character.toChars(code))
            }
        }
    }

    /** Todos los nodos del árbol (en orden), para buscar en los tests. */
    fun all(root: UiNode): List<UiNode> = buildList {
        fun visit(n: UiNode) {
            add(n)
            n.children.forEach(::visit)
        }
        visit(root)
    }
}
