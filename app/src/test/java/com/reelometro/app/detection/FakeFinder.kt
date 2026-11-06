package com.reelometro.app.detection

/**
 * [UiFinder] falso: recorre el árbol entero como haría Instagram al buscar (texto o descripción que
 * **contiene** lo pedido, sin mayúsculas pero con tildes; nunca el texto de un campo, que en los árboles falsos
 * hace de pista). Apunta cada búsqueda en [calls].
 */
class FakeFinder : UiFinder {
    val calls = mutableListOf<String>()

    override fun byText(root: UiNode, text: String): List<UiNode> {
        calls += "texto:$text"
        // Como Android: el texto de un campo vacío es su pista («Buscar», «Escribe un mensaje…») y esa no la
        // encuentra la búsqueda (TextView.findViewsWithText mira el texto escrito, no la pista).
        return all(root).filter { n ->
            val ownText = n.text.takeUnless { n.className?.contains("EditText") == true }
            listOfNotNull(ownText, n.contentDescription).any { it.toString().contains(text, ignoreCase = true) }
        }
    }

    override fun byViewId(root: UiNode, viewId: String): List<UiNode> {
        calls += "id:$viewId"
        return all(root).filter { it.viewId == viewId }
    }

    private fun all(root: UiNode): List<UiNode> = buildList {
        fun visit(n: UiNode) {
            add(n)
            n.children.forEach(::visit)
        }
        visit(root)
    }
}
