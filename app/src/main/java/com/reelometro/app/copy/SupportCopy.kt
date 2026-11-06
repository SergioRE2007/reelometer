package com.reelometro.app.copy

import com.reelometro.app.R

/**
 * Textos del **aviso de apoyo** (`res/values/copy_support.xml`): un único aviso, grande y centrado, que
 * sale al poco de usar la app (ver `ui/support/SupportPrompt`) y lleva al GitHub del proyecto. Sin
 * variantes: sale como mucho dos veces en la vida de la app y debe decir siempre lo mismo. Nada de
 * prometer lo que la app no hace: no tiene internet; el enlace lo abre el navegador.
 */
object SupportCopy {
    val KICKER: String get() = str(R.string.support_kicker)
    val TITLE: String get() = str(R.string.support_title)
    val TEXT: String get() = str(R.string.support_text)

    /** Pie pequeño bajo el texto: deja claro qué pasa al pulsar. */
    val NOTE: String get() = str(R.string.support_note)

    val OPEN: String get() = str(R.string.support_open)
    val LATER: String get() = Buttons.NOT_NOW
    val NEVER: String get() = str(R.string.support_never)

    /** Si no hay navegador (o no se pudo abrir). El aviso no se cierra. */
    fun openFailed(url: String): String =
        str(R.string.support_open_failed, url.removePrefix("https://").removePrefix("github.com/").trimEnd('/'))

    /** TalkBack. */
    val PANE_TITLE: String get() = str(R.string.support_pane_title)
    val OPEN_DESCRIPTION: String get() = str(R.string.support_open_description)
    val NEVER_DESCRIPTION: String get() = str(R.string.support_never_description)
}
