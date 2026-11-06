package com.reelometro.app.copy

import java.util.Locale

/**
 * De dónde salen las palabras de la app.
 *
 * Todo texto que ve el usuario vive en `res/values` (español en `values/`, el idioma por defecto;
 * cada traducción en su `values-xx/`). La **lógica** que decide qué frase toca (tramos, objetivo,
 * rachas…) sigue en Kotlin y es la misma para todos los idiomas; aquí solo se buscan las palabras.
 *
 *  - En el móvil: [ResourceTexts], que lee `Resources` (lo instala `ReelometroApp` al arrancar).
 *  - En los tests JVM: `JvmTexts` (en `src/test`), que lee los mismos XML a mano, sin Android.
 *
 * Los huecos van numerados (`%1$s`, `%2$s`) y los números llegan **ya formateados** con
 * [com.reelometro.app.stats.Format] (`%s`, nunca `%d`): así «1.234» sale igual en la frase que en
 * la etiqueta y sigue el idioma de los textos.
 */
interface Texts {

    /**
     * Idioma de los textos que se están enseñando (el de `values-xx` que ha ganado, según su
     * `locale_tag`), no el del sistema: si el móvil está en ruso y no hay ruso, los textos salen en
     * español y las cifras y fechas, también.
     */
    val locale: Locale

    /** `<string>` [id]; si hay [args], con sus huecos rellenos (sin [args] se devuelve tal cual, como en Android). */
    fun string(id: Int, vararg args: Any): String

    /** `<plurals>` [id]: la forma que pide [count] en este idioma, con sus huecos rellenos. */
    fun plural(id: Int, count: Int, vararg args: Any): String

    /** `<string-array>` [id]: las variantes de una situación (cada idioma puede tener las suyas). */
    fun array(id: Int): List<String>

    /** Rellena los huecos de [pattern] (para las variantes de un array). */
    fun format(pattern: String, vararg args: Any): String = String.format(locale, pattern, *args)
}

/**
 * Los textos en uso. La app instala los suyos en `ReelometroApp.onCreate`; un test puede instalar
 * unos falsos con [install] (y volver a los de siempre con [reset]).
 *
 * Si nadie ha instalado nada (tests JVM), se cargan los de `res/values` con
 * `com.reelometro.app.copy.JvmTexts.spanish()`, que solo existe en `src/test`.
 */
object AppTexts {

    @Volatile
    private var installed: Texts? = null

    val current: Texts
        get() = installed ?: fallback().also { installed = it }

    fun install(texts: Texts) {
        installed = texts
    }

    /** Vuelve a los textos por defecto (en los tests, los de `res/values`). */
    fun reset() {
        installed = null
    }

    private fun fallback(): Texts = try {
        Class.forName("com.reelometro.app.copy.JvmTexts").getMethod("spanish").invoke(null) as Texts
    } catch (e: ReflectiveOperationException) {
        throw IllegalStateException("No hay textos instalados: ReelometroApp llama a AppTexts.install al arrancar", e)
    }
}

/** `<string>` [id] con sus huecos rellenos: `str(R.string.day_title_today, fecha)`. */
fun str(id: Int, vararg args: Any): String = AppTexts.current.string(id, *args)

/** `<plurals>` [id] para [count]: `plural(R.plurals.words_videos, n, Format.integer(n))`. */
fun plural(id: Int, count: Int, vararg args: Any): String = AppTexts.current.plural(id, count, *args)

/** Igual, para recuentos `Long` (por encima de `Int.MAX_VALUE` se usa la forma de un número enorme). */
fun plural(id: Int, count: Long, vararg args: Any): String =
    AppTexts.current.plural(id, count.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(), *args)

/** `<string-array>` [id] completo (para listas, no para variantes: esas van con [pick]). */
fun strArray(id: Int): List<String> = AppTexts.current.array(id)
