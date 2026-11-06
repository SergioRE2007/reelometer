package com.reelometro.app.copy

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.reelometro.app.R
import java.util.Locale

/**
 * [Texts] sobre los recursos de Android.
 *
 * No lee directamente los `Resources` de la app, sino los de una configuración con el idioma de
 * los textos que ganan (`locale_tag`): si el móvil está en ruso y no hay `values-ru`, los textos
 * salen de `values/` (español) y los plurales tienen que seguir las reglas del español, no las del
 * ruso (que diría «21 vídeo»). Se recalcula cuando cambia el idioma de la app o del sistema.
 */
class ResourceTexts(
    context: Context,
    /** Idioma fijo (p. ej. español, para leer un CSV viejo); `null` = el de la app, que puede cambiar. */
    private val fixed: Locale? = null,
) : Texts {

    private val app: Context = context.applicationContext

    private class Entry(val locales: LocaleList, val locale: Locale, val resources: Resources)

    @Volatile
    private var entry: Entry? = null

    private fun current(): Entry {
        val base = app.resources
        val locales = base.configuration.locales
        entry?.let { if (it.locales === locales || it.locales == locales) return it }
        val locale = fixed ?: Locale.forLanguageTag(base.getString(R.string.locale_tag))
        val config = Configuration(base.configuration).apply { setLocale(locale) }
        return Entry(locales, locale, app.createConfigurationContext(config).resources).also { entry = it }
    }

    override val locale: Locale get() = current().locale

    override fun string(id: Int, vararg args: Any): String {
        val e = current()
        val raw = e.resources.getString(id)
        return if (args.isEmpty()) raw else String.format(e.locale, raw, *args)
    }

    override fun plural(id: Int, count: Int, vararg args: Any): String {
        val e = current()
        val raw = e.resources.getQuantityString(id, count)
        return if (args.isEmpty()) raw else String.format(e.locale, raw, *args)
    }

    override fun array(id: Int): List<String> = current().resources.getStringArray(id).asList()
}
