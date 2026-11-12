package com.reelometro.app.detection

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Lo que leen del móvil las reglas de [SystemShade] y [FeedPanel] (1.10.5): la capa fina de Android, para que el servicio
 * casi no crezca. Nunca lanzan.
 */

/** Las ventanas de la pantalla como [ScreenWindow] (sin leer ninguna raíz), o `null` si Android no las da. */
internal fun AccessibilityService.screenWindows(): List<ScreenWindow>? {
    val list = try {
        windows
    } catch (e: RuntimeException) {
        return null
    } ?: return null
    val r = Rect()
    return list.mapNotNull { w ->
        runCatching {
            r.setEmpty()
            w.getBoundsInScreen(r)
            val kind = when (w.type) {
                AccessibilityWindowInfo.TYPE_APPLICATION -> ScreenWindow.Kind.APP
                AccessibilityWindowInfo.TYPE_SYSTEM -> ScreenWindow.Kind.SYSTEM
                else -> ScreenWindow.Kind.OTHER
            }
            ScreenWindow(w.id, kind, w.layer, r.width(), r.height(), w.isFocused, w.isActive)
        }.getOrNull()
    }
}

/**
 * [FeedPanel.isOpen] en la ventana del feed ([feedWindow]) con los ids del visor [feedIds]; `null` si no se sabe (sin esa
 * ventana, sin raíz o si falla). Lee el árbol (podado, ~100 nodos): solo en el hilo de fondo.
 */
internal fun AccessibilityService.feedPanelOpen(
    feedWindow: Int,
    feedIds: List<String>,
    screenWidth: Int,
    screenHeight: Int,
    relaxed: Boolean = false,
): Boolean? = try {
    val root = windows?.firstOrNull { it.id == feedWindow }?.root
    root?.let { FeedPanel.isOpen(AccessibilityUiNode(it), feedIds, screenWidth, screenHeight, relaxed = relaxed) }
} catch (e: RuntimeException) {
    null
}
