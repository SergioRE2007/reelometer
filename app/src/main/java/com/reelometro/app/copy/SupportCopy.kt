package com.reelometro.app.copy

import com.reelometro.app.AppDistribution
import com.reelometro.app.Distribution
import com.reelometro.app.R

/**
 * Textos del **aviso de apoyo** (`res/values/copy_support.xml`): un único aviso, grande y centrado, que
 * sale al poco de usar la app (ver `ui/support/SupportPrompt`) y lleva al GitHub del proyecto. Sin
 * variantes: sale como mucho dos veces en la vida de la app y debe decir siempre lo mismo. Nada de
 * prometer lo que la app no hace: no tiene internet; el enlace lo abre el navegador.
 *
 * Dos versiones según la [Distribution] (por defecto la de esta build, `AppDistribution.current`):
 * - GitHub: invita a ver el GitHub y, si apetece, apoyar el proyecto.
 * - Google Play: **neutra**, solo ofrece ver el código (la política de pagos de Play no admite pedir apoyo
 *   ni donaciones). Cambian el texto, el botón y sus textos de TalkBack; el resto ya es neutro y se comparte.
 */
object SupportCopy {
    val KICKER: String get() = str(R.string.support_kicker)
    val TITLE: String get() = str(R.string.support_title)
    val TEXT: String get() = text()

    /** Pie pequeño bajo el texto: deja claro qué pasa al pulsar. */
    val NOTE: String get() = str(R.string.support_note)

    val OPEN: String get() = open()
    val LATER: String get() = Buttons.NOT_NOW
    val NEVER: String get() = str(R.string.support_never)

    /** Si no hay navegador (o no se pudo abrir). El aviso no se cierra. */
    fun openFailed(url: String): String =
        str(R.string.support_open_failed, url.removePrefix("https://").removePrefix("github.com/").trimEnd('/'))

    /** TalkBack. */
    val PANE_TITLE: String get() = paneTitle()
    val OPEN_DESCRIPTION: String get() = openDescription()
    val NEVER_DESCRIPTION: String get() = str(R.string.support_never_description)

    fun text(distribution: Distribution = AppDistribution.current): String =
        if (distribution == Distribution.PLAY) str(R.string.support_text_play) else str(R.string.support_text)

    fun open(distribution: Distribution = AppDistribution.current): String =
        if (distribution == Distribution.PLAY) str(R.string.support_open_play) else str(R.string.support_open)

    fun paneTitle(distribution: Distribution = AppDistribution.current): String =
        if (distribution == Distribution.PLAY) str(R.string.support_pane_title_play) else str(R.string.support_pane_title)

    fun openDescription(distribution: Distribution = AppDistribution.current): String =
        if (distribution == Distribution.PLAY) {
            str(R.string.support_open_description_play)
        } else {
            str(R.string.support_open_description)
        }

    /** Todos los textos que enseña el aviso en [distribution] (para los tests de la versión de Play). */
    fun all(distribution: Distribution, url: String): List<String> = listOf(
        KICKER, TITLE, text(distribution), NOTE, open(distribution), LATER, NEVER, openFailed(url),
        paneTitle(distribution), openDescription(distribution), NEVER_DESCRIPTION,
    )
}
