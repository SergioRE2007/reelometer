package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.game.FlapDay
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import com.reelometro.app.game.FlapState
import com.reelometro.app.stats.Format
import java.time.LocalDate

/**
 * Textos de la pestaña **Flap** (antes «Pasaporte»): la mascota que empieza cada día a tope y
 * enferma con cada vídeo visto. Tono: humor seco y cariñoso; Flap se pone malo, pero nadie tiene
 * la culpa y no hay sermones.
 *
 * Las palabras están en `res/values/copy_flap.xml` (y en cada `values-xx/copy_flap.xml`); aquí se
 * decide qué frase toca y con qué huecos. Variantes deterministas por día con
 * `pick(seed, "clave", R.array.x)`; cifras con [Format]. Tests propios en `FlapCopyTest`.
 */
object FlapCopy {

    /** Titular de la pestaña: «Flap». */
    val TITLE: String get() = str(R.string.flap_title)

    /** Kicker de la cabecera: «HOY · MARTES 3 NOV». */
    fun kicker(today: LocalDate): String =
        Format.upper(str(R.string.flap_kicker, Format.weekday(today.dayOfWeek), Format.dayShort(today)))

    // ------------------------------------------------------------------ estado

    /** Etiqueta encima del nombre del estado: «HOY FLAP ESTÁ». */
    val STATE_LABEL: String get() = str(R.string.flap_state_label)

    /**
     * Nombre corto del estado, en mayúsculas («EN FORMA», «KO»): el nombre grande de la pestaña (y la
     * galería). El widget y TalkBack usan [moodSpoken].
     */
    fun moodLabel(mood: FlapMood): String = when (mood) {
        FlapMood.EN_FORMA -> str(R.string.flap_mood_fit)
        FlapMood.NORMAL -> str(R.string.flap_mood_normal)
        FlapMood.CANSADO -> str(R.string.flap_mood_tired)
        FlapMood.ENFERMO -> str(R.string.flap_mood_sick)
        FlapMood.KO -> str(R.string.flap_mood_ko)
    }

    /**
     * El estado dentro de una frase hablada, en minúsculas y sin siglas («enfermo», «fuera de combate»):
     * TalkBack y la descripción del widget (`WidgetCopy.withFlap`).
     */
    fun moodSpoken(mood: FlapMood): String = when (mood) {
        FlapMood.EN_FORMA -> str(R.string.flap_mood_spoken_fit)
        FlapMood.NORMAL -> str(R.string.flap_mood_spoken_normal)
        FlapMood.CANSADO -> str(R.string.flap_mood_spoken_tired)
        FlapMood.ENFERMO -> str(R.string.flap_mood_spoken_sick)
        FlapMood.KO -> str(R.string.flap_mood_spoken_ko)
    }

    /** Frase corta del estado (variantes deterministas por día). */
    fun moodPhrase(mood: FlapMood, seed: Long): String = when (mood) {
        FlapMood.EN_FORMA -> pick(seed, "flap.mood.fit", R.array.flap_phrase_fit)
        FlapMood.NORMAL -> pick(seed, "flap.mood.normal", R.array.flap_phrase_normal)
        FlapMood.CANSADO -> pick(seed, "flap.mood.tired", R.array.flap_phrase_tired)
        FlapMood.ENFERMO -> pick(seed, "flap.mood.sick", R.array.flap_phrase_sick)
        FlapMood.KO -> pick(seed, "flap.mood.ko", R.array.flap_phrase_ko)
    }

    /** Frase de Flap recién llegado (la app aún no ha contado ningún vídeo). */
    fun freshPhrase(seed: Long): String = pick(seed, "flap.fresh", R.array.flap_phrase_fresh)

    /** Frase de Flap revivido o mejorado desde la última vez que lo viste. */
    fun recoveredPhrase(seed: Long): String = pick(seed, "flap.recovered", R.array.flap_phrase_recovered)

    /** La frase de hoy: la de recién llegado si aún no hay registro; si no, la de su estado. */
    fun phrase(state: FlapState, seed: Long): String =
        if (state.trackedDays <= 0) freshPhrase(seed) else moodPhrase(state.mood, seed)

    /** TalkBack de Flap: «Flap: enfermo, vida 32 de 100.» (la frase se lee aparte, en su texto). */
    fun description(mood: FlapMood, life: Int): String =
        str(R.string.flap_description, moodSpoken(mood), lifeValue(life), Format.integer(FlapRules.MAX_LIFE))

    /** Acción de tocar a Flap (TalkBack: «toca dos veces para saludar a Flap»). */
    val FLAP_TAP: String get() = str(R.string.flap_tap)

    // ------------------------------------------------------------------ vida

    /** Etiqueta de la barra: «VIDA». */
    val LIFE: String get() = str(R.string.flap_life)

    /** La cifra de la vida: «62». */
    fun lifeValue(life: Int): String = Format.integer(life.coerceIn(0, FlapRules.MAX_LIFE))

    /** TalkBack de la barra: «Vida: 62 de 100». */
    fun lifeDescription(life: Int): String =
        str(R.string.flap_life_description, lifeValue(life), Format.integer(FlapRules.MAX_LIFE))

    /** «HOY 31 VÍDEOS · AGUANTA 40». */
    fun todayLine(count: Int, limit: Int): String {
        val c = count.coerceAtLeast(0)
        return Format.upper(plural(R.plurals.flap_today_line, c, Format.integer(c), Format.integer(limit.coerceAtLeast(1))))
    }

    /** Lo que falta para el estado siguiente, peor: «Con 9 vídeos más se pondrá enfermo.». */
    fun toWorse(next: FlapMood, videos: Int): String {
        val n = videos.coerceAtLeast(1)
        val id = when (next) {
            // EN_FORMA nunca es «peor»; por si acaso, como NORMAL.
            FlapMood.EN_FORMA, FlapMood.NORMAL -> R.plurals.flap_next_normal
            FlapMood.CANSADO -> R.plurals.flap_next_tired
            FlapMood.ENFERMO -> R.plurals.flap_next_sick
            FlapMood.KO -> R.plurals.flap_next_ko
        }
        return plural(id, n, Format.integer(n))
    }

    /** Ya está KO: «KO hasta mañana. A medianoche revive.». */
    val KO_UNTIL_TOMORROW: String get() = str(R.string.flap_ko_until_tomorrow)

    /** La línea de debajo de la barra: lo que falta para empeorar o, en KO, hasta cuándo. */
    fun nextLine(state: FlapState): String {
        val next = FlapRules.nextWorse(state.count, state.limit)
        val videos = state.videosToWorse ?: FlapRules.videosToWorse(state.count, state.limit)
        return if (state.mood == FlapMood.KO || next == null || videos == null) KO_UNTIL_TOMORROW else toWorse(next, videos)
    }

    // ------------------------------------------------------------------ cómo funciona

    val SECTION_HOW: String get() = str(R.string.flap_section_how)

    /** «Cada día Flap empieza a tope, con 100 de vida. A medianoche revive.». */
    val HOW_START: String get() = str(R.string.flap_how_start, Format.integer(FlapRules.MAX_LIFE))

    /** «Cada vídeo que ves le quita un poco de vida.». */
    val HOW_VIDEO: String get() = str(R.string.flap_how_video)

    /** «Con tu objetivo (40 vídeos) llega a la noche a media vida; con el doble (80), cae KO.». */
    fun howLimit(limit: Int, hasGoal: Boolean): String {
        val l = limit.coerceAtLeast(1)
        val ko = l.toLong() * FlapRules.KO_FACTOR
        val id = if (hasGoal) R.plurals.flap_how_goal else R.plurals.flap_how_default
        return plural(id, l, Format.integer(l), Format.integer(ko))
    }

    /** Las tres líneas de «Cómo funciona». */
    fun howLines(limit: Int, hasGoal: Boolean): List<String> = listOf(HOW_START, HOW_VIDEO, howLimit(limit, hasGoal))

    /** Sin objetivo: «Sin objetivo, Flap aguanta 150 vídeos al día. Con el tuyo, aguanta lo que tú decidas.». */
    fun noGoal(limit: Int): String {
        val l = limit.coerceAtLeast(1)
        return plural(R.plurals.flap_no_goal, l, Format.integer(l))
    }

    /** Botón que lleva a Ajustes a poner el objetivo («PONER OBJETIVO», el mismo que en el resto de la app). */
    val SET_GOAL: String get() = Buttons.SET_GOAL

    /** TalkBack del botón PONER OBJETIVO. */
    val SET_GOAL_DESCRIPTION: String get() = str(R.string.flap_set_goal_description)

    // ------------------------------------------------------------------ la semana

    val SECTION_WEEK: String get() = str(R.string.flap_section_week)

    /** Inicial del día bajo cada Flap pequeño («L», «M», «X»…). */
    fun dayInitial(date: LocalDate): String = Format.weekdayInitial(date.dayOfWeek)

    /** Vídeos del día bajo su Flap («34»); `null` si es anterior al primer registro. */
    fun dayCount(count: Int?): String? = count?.let { Format.integer(it.coerceAtLeast(0)) }

    /** TalkBack de un día: «Martes 3 de noviembre: 34 vídeos, cansado.» o «…: sin registro.». */
    fun dayDescription(day: FlapDay): String {
        val date = Format.capitalize(Format.dayLong(day.date))
        val count = day.count
        val mood = day.mood
        return if (count == null || mood == null) {
            str(R.string.flap_day_description_none, date)
        } else {
            str(R.string.flap_day_description, date, Words.videos(count.coerceAtLeast(0)), moodSpoken(mood))
        }
    }

    /** Etiqueta de la racha: «RACHA». */
    val STREAK_LABEL: String get() = str(R.string.flap_streak_label)

    /** «5 días seguidos sin que enferme»; `null` si la racha es 0. */
    fun streak(days: Int): String? {
        if (days <= 0) return null
        return plural(R.plurals.flap_streak, days, Format.integer(days))
    }
}
