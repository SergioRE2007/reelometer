package com.reelometro.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * Enlaces externos de la app. La app no tiene permiso INTERNET: estos enlaces los abre el navegador del móvil.
 */
object AppLinks {
    /**
     * Página pública del proyecto (GitHub), donde está el botón de donaciones (Buy Me a Coffee). La app enlaza al
     * proyecto, no a pagar: así no choca con la política de pagos de Google Play. El repo es público.
     * Vacía = ni el enlace escondido de Ajustes ni el aviso de apoyo se enseñan.
     */
    const val PROJECT_URL = "https://github.com/SergioRE2007/reelometer"

    /**
     * Política de privacidad publicada en la web del proyecto (sergiore2007.github.io/reelometer). Es la URL que se da a
     * Google Play. La web solo está en español e inglés.
     */
    const val PRIVACY_URL_ES = "https://sergiore2007.github.io/reelometer/privacidad/"
    const val PRIVACY_URL_EN = "https://sergiore2007.github.io/reelometer/en/privacy/"

    /** La política en el idioma de los textos de la app: la española en español; la inglesa en cualquier otro. */
    fun privacyUrl(locale: Locale): String = if (locale.language == "es") PRIVACY_URL_ES else PRIVACY_URL_EN

    /** Abre [url] en el navegador. `false` si no hay ninguna app que lo abra (o la URL está vacía). */
    fun open(context: Context, url: String = PROJECT_URL): Boolean {
        if (url.isBlank()) return false
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
