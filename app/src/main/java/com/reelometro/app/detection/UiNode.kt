package com.reelometro.app.detection

/** Rectángulo en coordenadas de pantalla (px). Sin dependencias de Android para poder probarlo en la JVM. */
data class NodeBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** Área (px²) de la parte que cae dentro de una pantalla de `screenWidth × screenHeight`. */
    fun visibleArea(screenWidth: Int, screenHeight: Int): Long {
        val w = minOf(right, screenWidth) - maxOf(left, 0)
        val h = minOf(bottom, screenHeight) - maxOf(top, 0)
        return if (w > 0 && h > 0) w.toLong() * h else 0L
    }

    fun intersectsScreen(screenWidth: Int, screenHeight: Int): Boolean = visibleArea(screenWidth, screenHeight) > 0

    companion object {
        val EMPTY = NodeBounds(0, 0, 0, 0)
    }
}

/**
 * Vista mínima de un nodo del árbol de accesibilidad. Permite probar la lectura de la pantalla con
 * árboles falsos (tests) y usar [AccessibilityUiNode] en el móvil.
 *
 * `children` y `parent` pueden costar una llamada entre procesos: recórrelos con límite.
 */
interface UiNode {
    val text: CharSequence?
    val contentDescription: CharSequence?

    /** Id completo del recurso (`"com.app:id/nombre"`) o null. */
    val viewId: String?
    val className: CharSequence?
    val bounds: NodeBounds
    val isVisible: Boolean
    val children: List<UiNode>
    val parent: UiNode?

    /** Ventana a la que pertenece (`AccessibilityNodeInfo.windowId`); [NO_WINDOW] si no se sabe (tests). */
    val windowId: Int get() = NO_WINDOW

    companion object {
        const val NO_WINDOW = -1
    }
}

/** `"com.instagram.android:id/clips_viewer"` → `"clips_viewer"` (en minúsculas). `""` si no hay id. */
internal fun shortViewId(viewId: String?): String = viewId?.substringAfter(":id/")?.lowercase().orEmpty()

/** `"androidx.viewpager.widget.ViewPager"` → `"ViewPager"`. */
internal fun simpleClassName(className: CharSequence?): String =
    className?.toString()?.substringAfterLast('.')?.ifEmpty { null } ?: "?"
