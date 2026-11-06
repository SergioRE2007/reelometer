package com.reelometro.app.detection

/**
 * Búsquedas que hace **la app de destino** y no este proceso: una sola llamada entre procesos devuelve todos
 * los nodos que coinciden, en lugar de recorrer cientos de nodos uno a uno. En el móvil es
 * [AccessibilityUiFinder] (`findAccessibilityNodeInfosByText` / `…ByViewId`); en los tests, uno falso que
 * recorre el árbol.
 *
 * Lo que devuelven es solo un atajo: quien lo usa vuelve a comprobar cada nodo con sus propias reglas, y si
 * no sale nada, recorre el árbol como siempre.
 */
interface UiFinder {
    /**
     * Nodos de la ventana de [root] cuyo texto o descripción **contiene** [text], sin distinguir mayúsculas
     * (pero sí tildes), como `AccessibilityNodeInfo.findAccessibilityNodeInfosByText`.
     */
    fun byText(root: UiNode, text: String): List<UiNode>

    /** Nodos de la ventana de [root] con ese id completo (`"com.instagram.android:id/…"`). */
    fun byViewId(root: UiNode, viewId: String): List<UiNode>
}
