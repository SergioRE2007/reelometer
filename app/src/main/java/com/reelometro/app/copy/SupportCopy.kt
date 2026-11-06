package com.reelometro.app.copy

/**
 * Textos del **aviso de apoyo**: un único aviso, grande y centrado, que sale al poco de usar la app (ver
 * `ui/support/SupportPrompt`) y lleva al GitHub del proyecto. Sin variantes: sale como mucho dos veces en
 * la vida de la app y debe decir siempre lo mismo. Nada de prometer lo que la app no hace: no tiene
 * internet; el enlace lo abre el navegador.
 */
object SupportCopy {
    const val KICKER = "UNA COSA"
    const val TITLE = "Esta app la hace un estudiante."
    const val TEXT =
        "Reelómetro es gratis, no tiene anuncios y tus datos no salen del móvil. La hace un estudiante en sus " +
            "ratos libres. En su GitHub puedes ver cómo está hecha y, si te apetece, apoyarlo."

    /** Pie pequeño bajo el texto: deja claro qué pasa al pulsar. */
    const val NOTE = "Se abre en el navegador. La app sigue sin internet."

    const val OPEN = "VER SU GITHUB"
    const val LATER = Buttons.NOT_NOW
    const val NEVER = "No volver a mostrar"

    /** Si no hay navegador (o no se pudo abrir). El aviso no se cierra. */
    fun openFailed(url: String): String =
        "No se ha podido abrir el navegador. Búscalo en GitHub: ${url.removePrefix("https://").removePrefix("github.com/").trimEnd('/')}."

    /** TalkBack. */
    const val PANE_TITLE = "Apoyar Reelómetro"
    const val OPEN_DESCRIPTION = "Ver su GitHub. Se abre en el navegador"
    const val NEVER_DESCRIPTION = "No volver a mostrar este aviso"
}
