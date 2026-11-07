package com.reelometro.app.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Adaptador de [AccessibilityNodeInfo] a [UiNode]. Nunca lanza: si el nodo ya no existe devuelve vacíos.
 * Hijos y padre se piden al sistema solo cuando se necesitan (cada uno es una llamada entre procesos).
 */
class AccessibilityUiNode(
    val node: AccessibilityNodeInfo,
    private val knownParent: UiNode? = null,
) : UiNode {

    override val text: CharSequence? get() = safe { node.text }
    override val contentDescription: CharSequence? get() = safe { node.contentDescription }
    override val viewId: String? get() = safe { node.viewIdResourceName }
    override val className: CharSequence? get() = safe { node.className }
    override val windowId: Int get() = safe { node.windowId } ?: UiNode.NO_WINDOW
    override val isClickable: Boolean get() = safe { node.isClickable } ?: false
    override val isChecked: Boolean get() = safe { node.isChecked } ?: false
    override val canScrollBackward: Boolean
        get() = safe { node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD } } ?: false

    override fun scrollBackward(): Boolean = safe { node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) } ?: false

    override val bounds: NodeBounds by lazy {
        safe {
            val r = Rect()
            node.getBoundsInScreen(r)
            NodeBounds(r.left, r.top, r.right, r.bottom)
        } ?: NodeBounds.EMPTY
    }

    override val isVisible: Boolean by lazy { safe { node.isVisibleToUser } ?: false }

    override val children: List<UiNode> by lazy {
        val count = safe { node.childCount } ?: 0
        (0 until count).mapNotNull { i -> safe { node.getChild(i) }?.let { AccessibilityUiNode(it, this) } }
    }

    override val parent: UiNode? by lazy { knownParent ?: safe { node.parent }?.let { AccessibilityUiNode(it) } }

    private inline fun <T> safe(block: () -> T): T? = try {
        block()
    } catch (e: RuntimeException) {
        null
    }
}

/**
 * [UiFinder] del móvil: la búsqueda la hace la app de destino (`findAccessibilityNodeInfosByText` /
 * `findAccessibilityNodeInfosByViewId`) en una sola llamada. Nunca lanza: si falla, lista vacía (y quien
 * lo usa recorre el árbol como siempre). Solo sirve con raíces del móvil ([AccessibilityUiNode]).
 */
object AccessibilityUiFinder : UiFinder {

    override fun byText(root: UiNode, text: String): List<UiNode> = find(root) { it.findAccessibilityNodeInfosByText(text) }

    override fun byViewId(root: UiNode, viewId: String): List<UiNode> = find(root) { it.findAccessibilityNodeInfosByViewId(viewId) }

    private inline fun find(root: UiNode, query: (AccessibilityNodeInfo) -> List<AccessibilityNodeInfo>?): List<UiNode> {
        val node = (root as? AccessibilityUiNode)?.node ?: return emptyList()
        return try {
            query(node).orEmpty().map { AccessibilityUiNode(it) }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }
}
