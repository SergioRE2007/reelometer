package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Format
import kotlin.math.roundToLong

/**
 * Formateadores «de prosa» que no están en [Format] (que es para cifras y etiquetas):
 * plurales bien hechos, duraciones con palabras, listas con «y/e», «a las 23:00».
 *
 * Las palabras salen de `res/values/copy_words.xml` (`<plurals>` y frases con huecos); aquí solo
 * queda la lógica (qué forma toca). Todas las cifras pasan por [Format] (1.234 · 3,2 · 34 %) y van
 * unidas a su palabra con un espacio duro (`\u00A0` en los recursos) para que «212 vídeos» nunca se
 * parta al final de una línea.
 */
object Words {

    /** Espacio duro entre cifra y palabra. */
    const val NBSP: Char = Format.NBSP

    /** «1 vídeo», «212 vídeos». */
    fun videos(n: Int): String = videos(n.toLong())

    fun videos(n: Long): String = plural(R.plurals.words_videos, n, Format.integer(n))

    /** «1 vez», «3 veces». */
    fun times(n: Int): String = times(n.toLong())

    fun times(n: Long): String = plural(R.plurals.words_times, n, Format.integer(n))

    /** «1 día», «12 días». */
    fun days(n: Int): String = plural(R.plurals.words_days, n, Format.integer(n))

    fun hours(n: Long): String = plural(R.plurals.words_hours, n, Format.integer(n))

    fun minutes(n: Long): String = plural(R.plurals.words_minutes, n, Format.integer(n))

    fun seconds(n: Long): String = plural(R.plurals.words_seconds, n, Format.integer(n))

    /**
     * Duración con palabras, para frases: «45 segundos», «un minuto», «47 minutos», «una hora y
     * 12 minutos», «3 horas», «14 horas», «1.234 horas». Redondea los minutos igual que
     * [Format.durationCompact], así la frase y la etiqueta («47 MIN») siempre coinciden.
     * A partir de 10 horas no da minutos. Puede empezar en minúscula («una hora…»): si va al
     * principio de la frase, pásala por [Format.capitalize].
     */
    fun duration(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        val totalSec = safe / 1000
        if (totalSec < 60) return plural(R.plurals.words_duration_seconds, totalSec, Format.integer(totalSec))
        val totalMin = (safe + 30_000) / 60_000
        if (totalMin < 60) return minutesWord(totalMin)
        val h = totalMin / 60
        val m = totalMin % 60
        if (h >= 10) return hours((safe / 3_600_000.0).roundToLong())
        val hoursText = plural(R.plurals.words_duration_hours, h, Format.integer(h))
        return if (m == 0L) hoursText else str(R.string.words_duration_hours_minutes, hoursText, minutesWord(m))
    }

    /**
     * `true` si [duration] tiene por núcleo un 1 («un minuto», «una hora», «una hora y 40 minutos»),
     * para concordar: «Eso es una hora y 40 minutos…» / «Eso son 47 minutos…». No mira las palabras
     * (sirve en cualquier idioma): el núcleo es la unidad mayor de la duración.
     */
    fun isSingularDuration(ms: Long): Boolean {
        val safe = ms.coerceAtLeast(0)
        val totalSec = safe / 1000
        if (totalSec < 60) return totalSec == 1L
        val totalMin = (safe + 30_000) / 60_000
        if (totalMin < 60) return totalMin == 1L
        return totalMin / 60 == 1L
    }

    private fun minutesWord(n: Long): String = plural(R.plurals.words_duration_minutes, n, Format.integer(n))

    /** Etiqueta corta en mayúsculas: «47 MIN», «3,2 H», «61 H». */
    fun durationLabel(ms: Long): String = Format.upper(Format.durationCompact(ms))

    /** «@pepito». Respeta la arroba si ya viene y no toca mayúsculas. */
    fun handle(name: String): String {
        val clean = name.trim()
        return if (clean.startsWith("@")) clean else "@$clean"
    }

    /**
     * Une con comas e «y» final: «TikTok», «TikTok y Reels», «TikTok, Reels y Shorts».
     * Usa «e» delante de palabras que empiezan por el sonido /i/ («Reels e Instagram»); en otros
     * idiomas, las dos frases (`words_list_last` y `words_list_last_i`) suelen ser iguales.
     */
    fun join(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> str(
            if (soundsLikeI(items.last())) R.string.words_list_last_i else R.string.words_list_last,
            items.dropLast(1).joinToString(str(R.string.words_list_separator)),
            items.last(),
        )
    }

    /** «a las 23:00», «a la 01:00». */
    fun atHour(hour: Int): String = atTime(hour, 0)

    /** «a las 21:30», «a la 01:15». */
    fun atTime(hour: Int, minute: Int): String {
        val h = Math.floorMod(hour, 24)
        val m = Math.floorMod(minute, 60)
        return str(if (h == 1) R.string.words_at_time_one else R.string.words_at_time, Format.time(h, m))
    }

    /** Primera letra en mayúscula y punto final si no lo tiene: «leer 14 libros» → «Leer 14 libros.». */
    fun sentence(text: String): String {
        val t = Format.capitalize(text.trim())
        return if (t.endsWith(".") || t.endsWith("?")) t else "$t."
    }

    /** Mayúsculas con las reglas del idioma de los textos (para etiquetas). */
    fun upper(text: String): String = Format.upper(text)

    /** «Instagram», «hielo» no («hie-»): la «y» pasa a «e» (solo cuenta en español). */
    private fun soundsLikeI(next: String): Boolean {
        val w = next.trim().lowercase(Format.locale)
        return w.startsWith("i") || w.startsWith("í") ||
            (w.startsWith("hi") && !w.startsWith("hie") && !w.startsWith("hia") && !w.startsWith("hio") && !w.startsWith("hiu"))
    }
}

/**
 * Tipos de periodo, con lo necesario para los textos. Las palabras salen de `copy_words.xml`.
 *
 * Para frases con concordancia («tu semana, contada» / «tu mes, contado») no se pegan trozos con
 * [feminine]: se escribe una frase por periodo (cada idioma concuerda a su manera).
 */
enum class CopyPeriod(
    private val nounId: Int,
    /** Femenino en español. Solo para código antiguo: lo nuevo usa una frase por periodo. */
    val feminine: Boolean,
    private val thisOneId: Int,
    private val thatOneId: Int,
    private val previousId: Int?,
) {
    WEEK(R.string.period_week_noun, true, R.string.period_week_this, R.string.period_week_that, R.string.period_week_previous),
    MONTH(R.string.period_month_noun, false, R.string.period_month_this, R.string.period_month_that, R.string.period_month_previous),
    YEAR(R.string.period_year_noun, false, R.string.period_year_this, R.string.period_year_that, R.string.period_year_previous),
    ALL(R.string.period_all_noun, false, R.string.period_all_this, R.string.period_all_that, null),
    ;

    /** «semana», «mes», «año», «historial». */
    val noun: String get() = str(nounId)

    /** «esta semana», «este mes», «este año», «tu historial». */
    val thisOne: String get() = str(thisOneId)

    /** «esa semana», «ese mes», «ese año», «tu historial». */
    val thatOne: String get() = str(thatOneId)

    /** «la semana anterior», «el mes anterior», «el año anterior»; `null` en ALL. */
    val previous: String? get() = previousId?.let { str(it) }
}

/** Hacia dónde va una comparación. La interfaz decide flecha y color (más vídeos = `signalText`). */
enum class Direction { UP, DOWN, SAME, NONE }

/**
 * Línea de comparación lista para pintar.
 *
 * @property text etiqueta en mayúsculas: «+34 % QUE TU MARTES MEDIO A ESTA HORA».
 * @property description lo mismo en frase, para TalkBack o notificaciones: «Un 34 % más que tu martes medio a esta hora.».
 */
data class DeltaLine(val text: String, val description: String, val direction: Direction)

/** Título + texto (notificaciones, avisos). */
data class TitledText(val title: String, val text: String)

/** Línea de ticket: etiqueta a la izquierda, valor a la derecha (`ReceiptLine`). */
data class TicketLine(val label: String, val value: String)

/** Paso numerado de una guía. */
data class Step(val number: Int, val text: String)

/** Bloque con etiqueta corta (mayúsculas) y texto. */
data class LabeledText(val label: String, val text: String)
