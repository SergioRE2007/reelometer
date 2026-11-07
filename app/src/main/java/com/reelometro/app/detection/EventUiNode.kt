package com.reelometro.app.detection

import android.view.accessibility.AccessibilityEvent

/**
 * Un clic cuyo nodo ya no existe cuando llega el evento (1.10.0, TikTok: al pulsar «Enviar» la hoja de compartir se
 * cierra al momento y `event.source` llega a `null`). Solo trae lo que lleva el propio evento: su texto, su descripción y
 * su clase; sin padre, hijos ni posición, así que las reglas que miran la fila o la celda no lo reconocen (solo vale para
 * «Enviar», que se reconoce por su texto exacto). Solo se usa en las apps cuya hoja va en su propia ventana
 * ([RecipientDetector.acceptsNodelessClicks]); en Instagram un clic sin nodo se descarta, como hasta la 1.9.0.
 */
internal class EventUiNode(
    override val text: CharSequence?,
    override val contentDescription: CharSequence?,
    override val className: CharSequence?,
) : UiNode {
    override val viewId: String? = null
    override val bounds: NodeBounds = NodeBounds.EMPTY
    override val isVisible: Boolean = true
    override val children: List<UiNode> = emptyList()
    override val parent: UiNode? = null

    companion object {
        /** El clic de [event] como nodo, o `null` si no trae ni texto ni descripción. */
        fun of(event: AccessibilityEvent): EventUiNode? {
            val text = event.text?.filterNotNull()?.joinToString(" ")?.trim()?.ifEmpty { null }
            val desc = event.contentDescription?.toString()?.trim()?.ifEmpty { null }
            if (text == null && desc == null) return null
            return EventUiNode(text, desc, event.className)
        }
    }
}
