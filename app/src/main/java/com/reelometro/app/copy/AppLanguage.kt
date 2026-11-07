package com.reelometro.app.copy

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.reelometro.app.R
import java.util.Locale

/** Una opción del selector de idioma: [tag] `null` = el idioma del móvil. */
data class LanguageOption(val tag: String?, val label: String)

/**
 * Idioma elegido para la app desde Ajustes (Android 13+, `LocaleManager`).
 *
 * Se elige dentro de la app: la pantalla de idioma de Android mezcla los idiomas de la app con los
 * que tienen puestos otras apps y el teclado («Español (Francia)» porque otra app lo usa) y confunde.
 * La lista sale de `locales_config.xml` (generado de las carpetas `values-xx`), así que un idioma
 * nuevo aparece solo.
 *
 * El idioma elegido se guarda aquí en memoria (leerlo de `LocaleManager` es una llamada al sistema
 * y [ResourceTexts] lo consulta en cada texto). Con el servicio de accesibilidad vivo, la
 * configuración del proceso puede quedarse con el idioma del sistema: [ResourceTexts] usa este.
 */
object AppLanguage {
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @Volatile
    private var chosen: LocaleList? = null

    @Volatile
    private var system: LocaleList? = null

    @Volatile
    private var loaded = false

    /** El idioma elegido para la app, o `null` si sigue al del móvil. */
    fun chosen(context: Context): LocaleList? {
        if (!available) return null
        if (!loaded) refresh(context)
        return chosen
    }

    /**
     * Los idiomas en los que deben salir los textos: los elegidos para la app o, si sigue al móvil,
     * los del sistema. `null` (Android 12 o menos) = los de la configuración del proceso. No se fía
     * de la configuración del proceso porque, con el servicio vivo, se queda con la de antes al
     * quitar el idioma propio de la app.
     */
    fun effective(context: Context): LocaleList? {
        if (!available) return null
        if (!loaded) refresh(context)
        return chosen ?: system
    }

    /** Vuelve a leer los idiomas (al abrir la app o al cambiar la configuración del sistema). */
    fun refresh(context: Context) {
        if (!available) return
        chosen = read(context)
        system = manager(context)?.systemLocales?.takeUnless { it.isEmpty }
        loaded = true
    }

    /** Cambia el idioma de la app ([tag] `null` = el del móvil). Android recrea la pantalla. */
    fun set(context: Context, tag: String?) {
        if (!available) return
        val list = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        manager(context)?.applicationLocales = list
        refresh(context)
        chosen = list.takeUnless { it.isEmpty }
    }

    /** Opciones del selector: el del móvil y cada idioma de la app, en su propio idioma. */
    fun options(context: Context, systemLabel: String): List<LanguageOption> {
        if (!available) return emptyList()
        return languageOptions(supported(context), baseTag(context), systemLabel)
    }

    /** La opción elegida ahora (`null` = la del móvil). */
    fun selectedTag(context: Context): String? =
        selectedLanguageTag(chosen(context)?.get(0), supported(context).map { it.toLanguageTag() })

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun read(context: Context): LocaleList? =
        manager(context)?.applicationLocales?.takeUnless { it.isEmpty }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun manager(context: Context): LocaleManager? = context.getSystemService(LocaleManager::class.java)

    private fun supported(context: Context): List<Locale> {
        if (!available) return emptyList()
        val list = runCatching { LocaleConfig(context).supportedLocales }.getOrNull() ?: return emptyList()
        return (0 until list.size()).map { list[it] }
    }

    /** Idioma de `values/` (el de por defecto): el `locale_tag` de una configuración sin idioma. */
    private fun baseTag(context: Context): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.ROOT) }
        return context.createConfigurationContext(config).resources.getString(R.string.locale_tag)
    }
}

/**
 * Opciones del selector: primero [systemLabel] y luego cada idioma con su nombre en ese idioma. El
 * de por defecto ([baseTag]) va sin región («Español»); los demás con ella si la tienen
 * («Português (Brasil)»). Orden alfabético por el nombre.
 */
internal fun languageOptions(supported: List<Locale>, baseTag: String, systemLabel: String): List<LanguageOption> {
    val languages = supported.distinctBy { it.toLanguageTag() }.map { locale ->
        val tag = locale.toLanguageTag()
        val name = if (tag.equals(baseTag, ignoreCase = true) || locale.country.isEmpty()) {
            locale.getDisplayLanguage(locale)
        } else {
            locale.getDisplayName(locale)
        }
        LanguageOption(tag, name.replaceFirstChar { it.titlecase(locale) })
    }.sortedBy { it.label.lowercase(Locale.ROOT) }
    return listOf(LanguageOption(null, systemLabel)) + languages
}

/**
 * Qué opción está elegida para el idioma [chosen] de la app: la de la misma etiqueta o, si no hay,
 * la del mismo idioma (Android puede guardar «es» o «en-ES»); `null` si sigue al móvil o no hay
 * ninguna de ese idioma.
 */
internal fun selectedLanguageTag(chosen: Locale?, supportedTags: List<String>): String? {
    if (chosen == null) return null
    val tag = chosen.toLanguageTag()
    supportedTags.firstOrNull { it.equals(tag, ignoreCase = true) }?.let { return it }
    return supportedTags.firstOrNull { Locale.forLanguageTag(it).language == chosen.language }
}
