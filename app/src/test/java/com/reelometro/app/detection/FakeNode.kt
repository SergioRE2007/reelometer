package com.reelometro.app.detection

/** Nodo falso para construir árboles de accesibilidad en los tests. */
class FakeNode(
    override val text: CharSequence? = null,
    override val contentDescription: CharSequence? = null,
    override val viewId: String? = null,
    override val className: CharSequence? = "android.view.ViewGroup",
    override val bounds: NodeBounds = NodeBounds(0, 0, 100, 100),
    override val isVisible: Boolean = true,
    children: List<FakeNode> = emptyList(),
) : UiNode {
    override val children: List<UiNode> = children
    override var parent: UiNode? = null

    init {
        children.forEach { it.parent = this }
    }
}

/** Pantalla de referencia de los tests (1080 × 2400). */
const val SCREEN_W = 1080
const val SCREEN_H = 2400

fun box(left: Int, top: Int, right: Int, bottom: Int) = NodeBounds(left, top, right, bottom)

fun text(value: String, bounds: NodeBounds = box(40, 1900, 800, 1960), id: String? = null) =
    FakeNode(text = value, viewId = id, className = "android.widget.TextView", bounds = bounds)

fun desc(value: String, bounds: NodeBounds = box(950, 1300, 1050, 1400), id: String? = null, cls: String = "android.widget.ImageView") =
    FakeNode(contentDescription = value, viewId = id, className = cls, bounds = bounds)
