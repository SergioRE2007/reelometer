package com.reelometro.app.copy

import com.reelometro.app.stats.Format
import kotlin.math.roundToLong

/**
 * Formateadores «de prosa» que no están en [Format] (que es para cifras y etiquetas):
 * plurales bien hechos, duraciones con palabras, listas con «y/e», «a las 23:00».
 *
 * Todas las cifras pasan por [Format] (1.234 · 3,2 · 34 %) y van unidas a su palabra con un
 * espacio duro ([Format.NBSP]) para que «212 vídeos» nunca se parta al final de una línea.
 */
object Words {

    /** Espacio duro entre cifra y palabra. */
    const val NBSP: Char = Format.NBSP

    /** «1 vídeo», «2 vídeos», «1.234 vídeos», «0 vídeos». */
    fun count(n: Long, singular: String, plural: String): String =
        "${Format.integer(n)}$NBSP${if (n == 1L) singular else plural}"

    fun count(n: Int, singular: String, plural: String): String = count(n.toLong(), singular, plural)

    /** «1 vídeo», «212 vídeos». */
    fun videos(n: Int): String = count(n, "vídeo", "vídeos")

    fun videos(n: Long): String = count(n, "vídeo", "vídeos")

    /** «1 vez», «3 veces». */
    fun times(n: Int): String = count(n, "vez", "veces")

    fun times(n: Long): String = count(n, "vez", "veces")

    /** «1 día», «12 días». */
    fun days(n: Int): String = count(n, "día", "días")

    fun hours(n: Long): String = count(n, "hora", "horas")

    fun minutes(n: Long): String = count(n, "minuto", "minutos")

    fun seconds(n: Long): String = count(n, "segundo", "segundos")

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
        if (totalSec < 60) return if (totalSec == 1L) "un segundo" else seconds(totalSec)
        val totalMin = (safe + 30_000) / 60_000
        if (totalMin < 60) return minutesWord(totalMin)
        val h = totalMin / 60
        val m = totalMin % 60
        if (h >= 10) return hours((safe / 3_600_000.0).roundToLong())
        val hoursText = if (h == 1L) "una hora" else hours(h)
        return if (m == 0L) hoursText else "$hoursText y ${minutesWord(m)}"
    }

    /**
     * `true` si [duration] empieza en singular («un minuto», «una hora», «una hora y 40 minutos»),
     * para concordar con su núcleo: «Eso es una hora y 40 minutos…» / «Eso son 47 minutos…».
     */
    fun isSingularDuration(ms: Long): Boolean = duration(ms).let { it.startsWith("un ") || it.startsWith("una ") }

    private fun minutesWord(n: Long): String = if (n == 1L) "un minuto" else minutes(n)

    /** Etiqueta corta en mayúsculas: «47 MIN», «3,2 H», «61 H». */
    fun durationLabel(ms: Long): String = Format.upper(Format.durationCompact(ms))

    /** «@pepito». Respeta la arroba si ya viene y no toca mayúsculas. */
    fun handle(name: String): String {
        val clean = name.trim()
        return if (clean.startsWith("@")) clean else "@$clean"
    }

    /**
     * Une con comas e «y» final: «TikTok», «TikTok y Reels», «TikTok, Reels y Shorts».
     * Usa «e» delante de palabras que empiezan por el sonido /i/ («Reels e Instagram»).
     */
    fun join(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " ${conjunction(items.last())} " + items.last()
    }

    /** «a las 23:00», «a la 01:00». */
    fun atHour(hour: Int): String = atTime(hour, 0)

    /** «a las 21:30», «a la 01:15». */
    fun atTime(hour: Int, minute: Int): String {
        val h = Math.floorMod(hour, 24)
        val m = Math.floorMod(minute, 60)
        return if (h == 1) "a la ${Format.time(h, m)}" else "a las ${Format.time(h, m)}"
    }

    /** Primera letra en mayúscula y punto final si no lo tiene: «leer 14 libros» → «Leer 14 libros.». */
    fun sentence(text: String): String {
        val t = Format.capitalize(text.trim())
        return if (t.endsWith(".") || t.endsWith("?")) t else "$t."
    }

    /** Mayúsculas con las reglas del español (para etiquetas). */
    fun upper(text: String): String = Format.upper(text)

    private fun conjunction(next: String): String {
        val w = next.trim().lowercase(Format.locale)
        val soundsLikeI = w.startsWith("i") || w.startsWith("í") ||
            (w.startsWith("hi") && !w.startsWith("hie") && !w.startsWith("hia") && !w.startsWith("hio") && !w.startsWith("hiu"))
        return if (soundsLikeI) "e" else "y"
    }
}

/** Tipos de periodo, con lo necesario para concordar en los textos. */
enum class CopyPeriod(
    /** «semana», «mes», «año», «historial». */
    val noun: String,
    /** Femenino: «tu semana, contada» frente a «tu mes, contado». */
    val feminine: Boolean,
    /** «esta semana», «este mes», «este año», «tu historial». */
    val thisOne: String,
    /** «esa semana», «ese mes», «ese año», «tu historial». */
    val thatOne: String,
    /** «la semana anterior», «el mes anterior», «el año anterior»; `null` en ALL. */
    val previous: String?,
) {
    WEEK("semana", true, "esta semana", "esa semana", "la semana anterior"),
    MONTH("mes", false, "este mes", "ese mes", "el mes anterior"),
    YEAR("año", false, "este año", "ese año", "el año anterior"),
    ALL("historial", false, "tu historial", "tu historial", null),
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
