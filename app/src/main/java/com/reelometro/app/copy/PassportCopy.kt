package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.game.ChallengeResult
import com.reelometro.app.game.ChallengeStatus
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapState
import com.reelometro.app.game.StampId
import com.reelometro.app.game.StampKind
import com.reelometro.app.game.StampState
import com.reelometro.app.game.TravelerLevel
import com.reelometro.app.game.WeeklyChallenge
import com.reelometro.app.stats.Format
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Textos del modo **Pasaporte** (pestaña, Flap, niveles, sellos, reto semanal y celebraciones).
 * Tono: aeropuerto, tarjeta de embarque, ironía amable. Premia ver menos; nunca culpa.
 *
 * La experiencia se enseña como **millas** (programa de viajero frecuente): salen de días bajo la
 * referencia, rachas, retos y sellos; nunca de vídeos vistos (y la pantalla lo dice).
 *
 * Las palabras están en `res/values/copy_passport.xml`; aquí se decide qué frase toca y con qué
 * huecos. Variantes deterministas con `pick(seed, "clave", R.array.x, …)`; reglas en
 * `src/test/.../copy/CopyRules.kt` (tests propios en `PassportCopyTest`).
 */
object PassportCopy {

    /** Titular de la pestaña. */
    val TITLE: String get() = str(R.string.passport_title)

    val SECTION_FLAP: String get() = str(R.string.passport_section_flap)
    val SECTION_LEVEL: String get() = str(R.string.passport_section_level)
    val SECTION_CHALLENGE: String get() = str(R.string.passport_section_challenge)
    val SECTION_STAMPS: String get() = str(R.string.passport_section_stamps)
    val SECTION_PAST: String get() = str(R.string.passport_section_past)

    /** Kicker de la cabecera: «63 DÍAS A BORDO». */
    fun onBoard(trackedDays: Int): String =
        Format.upper(str(R.string.passport_on_board, Words.days(trackedDays.coerceAtLeast(0))))

    // ------------------------------------------------------------------ Flap

    /** Etiqueta del estado de Flap («EN FORMA», «ZOMBI DEL SCROLL»). */
    fun moodLabel(mood: FlapMood): String = when (mood) {
        FlapMood.EN_FORMA -> str(R.string.passport_mood_fit)
        FlapMood.NORMAL -> str(R.string.passport_mood_normal)
        FlapMood.CANSADO -> str(R.string.passport_mood_tired)
        FlapMood.ZOMBI -> str(R.string.passport_mood_zombie)
    }

    /** Etiqueta mientras Flap no tiene con qué comparar (ni objetivo ni historial). */
    val WARMING_UP: String get() = str(R.string.passport_warming_up)

    /** Frase de estado de Flap (variantes deterministas por día). */
    fun moodPhrase(mood: FlapMood, seed: Long): String = when (mood) {
        FlapMood.EN_FORMA -> pick(seed, "passport.mood.fit", R.array.passport_phrase_fit)
        FlapMood.NORMAL -> pick(seed, "passport.mood.normal", R.array.passport_phrase_normal)
        FlapMood.CANSADO -> pick(seed, "passport.mood.tired", R.array.passport_phrase_tired)
        FlapMood.ZOMBI -> pick(seed, "passport.mood.zombie", R.array.passport_phrase_zombie)
    }

    /** Frase mientras Flap calienta (sin objetivo ni historial suficiente). */
    fun warmingPhrase(seed: Long): String = pick(seed, "passport.mood.warming", R.array.passport_phrase_warming)

    /** Frase cuando Flap vuelve de zombi a normal o mejor. */
    fun recoveredPhrase(seed: Long): String = pick(seed, "passport.mood.recovered", R.array.passport_phrase_recovered)

    /** La frase que toca hoy según la situación de Flap. */
    fun flapPhrase(flap: FlapState, seed: Long): String = when {
        flap.warmingUp -> warmingPhrase(seed)
        flap.previousMood == FlapMood.ZOMBI && flap.mood != FlapMood.ZOMBI -> recoveredPhrase(seed)
        else -> moodPhrase(flap.mood, seed)
    }

    /** «5 DE 7 DÍAS BAJO TU OBJETIVO» / «… BAJO TU MEDIA» / «CALENTANDO». */
    fun record(flap: FlapState): String {
        if (flap.warmingUp) return WARMING_UP
        val tracked = flap.daysTracked.coerceIn(0, 7)
        if (tracked == 0) return str(R.string.passport_record_none)
        val under = flap.daysUnder.coerceIn(0, tracked)
        val id = if (flap.referenceIsGoal) R.plurals.passport_record_goal else R.plurals.passport_record_average
        return Format.upper(plural(id, tracked, Format.integer(under), Format.integer(tracked)))
    }

    /** «TU OBJETIVO: 150 AL DÍA» / «TU MEDIA: 140 AL DÍA»; `null` mientras calienta. */
    fun referenceLine(flap: FlapState): String? {
        val reference = flap.reference ?: return null
        val id = if (flap.referenceIsGoal) R.string.passport_reference_goal else R.string.passport_reference_average
        return Format.upper(str(id, Format.integer(reference.coerceAtLeast(0))))
    }

    /** Para TalkBack: estado, registro y frase en una sola lectura. */
    fun flapDescription(flap: FlapState, phrase: String): String {
        if (flap.warmingUp) return str(R.string.passport_flap_description_warming, phrase).trim()
        val mood = moodLabel(flap.mood).lowercase(Format.locale)
        val record = Words.sentence(record(flap).lowercase(Format.locale))
        return str(R.string.passport_flap_description, mood, record, phrase).trim()
    }

    /** Acción de tocar a Flap (TalkBack: «toca dos veces para saludar a Flap»). */
    val FLAP_TAP: String get() = str(R.string.passport_flap_tap)

    // ------------------------------------------------------------------ niveles y millas

    /** Nombre del nivel de viajero frecuente. */
    fun levelName(level: TravelerLevel): String = when (level) {
        TravelerLevel.PASAJERO -> str(R.string.passport_level_pasajero)
        TravelerLevel.FRECUENTE -> str(R.string.passport_level_frecuente)
        TravelerLevel.PLATA -> str(R.string.passport_level_plata)
        TravelerLevel.ORO -> str(R.string.passport_level_oro)
        TravelerLevel.PLATINO -> str(R.string.passport_level_platino)
    }

    /** Código de tres letras, como el de un aeropuerto («PAS → FRE»). */
    fun levelCode(level: TravelerLevel): String = when (level) {
        TravelerLevel.PASAJERO -> str(R.string.passport_level_code_pasajero)
        TravelerLevel.FRECUENTE -> str(R.string.passport_level_code_frecuente)
        TravelerLevel.PLATA -> str(R.string.passport_level_code_plata)
        TravelerLevel.ORO -> str(R.string.passport_level_code_oro)
        TravelerLevel.PLATINO -> str(R.string.passport_level_code_platino)
    }

    /** Lema irónico de cada nivel. */
    fun levelTagline(level: TravelerLevel): String = when (level) {
        TravelerLevel.PASAJERO -> str(R.string.passport_level_tagline_pasajero)
        TravelerLevel.FRECUENTE -> str(R.string.passport_level_tagline_frecuente)
        TravelerLevel.PLATA -> str(R.string.passport_level_tagline_plata)
        TravelerLevel.ORO -> str(R.string.passport_level_tagline_oro)
        TravelerLevel.PLATINO -> str(R.string.passport_level_tagline_platino)
    }

    /** «NIVEL 2 DE 5». */
    fun levelIndex(level: TravelerLevel): String =
        str(R.string.passport_level_index, Format.integer(level.ordinal + 1), Format.integer(TravelerLevel.entries.size))

    val LABEL_ORIGIN: String get() = str(R.string.passport_label_origin)
    val LABEL_DESTINATION: String get() = str(R.string.passport_label_destination)
    val LABEL_FINAL: String get() = str(R.string.passport_label_final)

    /** «520 millas», «1 milla» (en minúsculas, para frases). */
    private fun milesWords(n: Int): String = plural(R.plurals.passport_miles, n, Format.integer(n))

    /** «520 MILLAS», «1 MILLA». */
    fun miles(xp: Int): String = Format.upper(milesWords(xp.coerceAtLeast(0)))

    /** «FALTAN 30 MILLAS PARA PLATA», «FALTA 1 MILLA PARA PLATA». */
    fun milesToNext(remaining: Int, next: TravelerLevel): String {
        val r = remaining.coerceAtLeast(0)
        return Format.upper(plural(R.plurals.passport_miles_to_next, r, Format.integer(r), levelName(next)))
    }

    /** En el último nivel no hay siguiente. */
    val TOP_LEVEL: String get() = str(R.string.passport_top_level)

    /** Etiquetas del desglose (como las de las líneas de ticket de Estadísticas). */
    fun lineDays(referenceIsGoal: Boolean): String =
        str(if (referenceIsGoal) R.string.passport_line_days_goal else R.string.passport_line_days_average)
    val LINE_STREAKS: String get() = str(R.string.passport_line_streaks)
    val LINE_CHALLENGES: String get() = str(R.string.passport_line_challenges)
    val LINE_STAMPS: String get() = str(R.string.passport_line_stamps)
    val LINE_TOTAL: String get() = str(R.string.passport_line_total)
    val LINE_STREAK: String get() = str(R.string.passport_line_streak)

    /** Valor de una línea del desglose: «+260». */
    fun milesValue(xp: Int): String = str(R.string.passport_miles_value, Format.integer(xp.coerceAtLeast(0)))

    /** «4 DÍAS». */
    fun streakValue(days: Int): String = Format.upper(Words.days(days.coerceAtLeast(0)))

    /** La regla del juego, a la vista. */
    val NO_MILES_FOR_VIDEOS: String get() = str(R.string.passport_no_miles_for_videos)

    /** Para TalkBack: la tarjeta de embarque entera. */
    fun levelDescription(level: TravelerLevel, xp: Int, remaining: Int?, streak: Int): String {
        val name = levelSpoken(level)
        val index = Format.integer(level.ordinal + 1)
        val count = Format.integer(TravelerLevel.entries.size)
        val miles = milesWords(xp.coerceAtLeast(0))
        val days = Words.days(streak.coerceAtLeast(0))
        val next = level.next
        return if (next != null && remaining != null) {
            val r = remaining.coerceAtLeast(0)
            plural(R.plurals.passport_level_description, r, name, index, count, miles, Format.integer(r), levelSpoken(next), days)
        } else {
            str(R.string.passport_level_description_top, name, index, count, miles, days)
        }
    }

    /** «Plata» (para frases y TalkBack). */
    fun levelSpoken(level: TravelerLevel): String = Format.capitalize(levelName(level).lowercase(Format.locale))

    // ------------------------------------------------------------------ sellos

    /** Nombre corto del sello (MAYÚSCULAS, cabe en el sello en una o dos líneas). */
    fun stampName(id: StampId): String = when (id) {
        StampId.PRIMER_DIA -> str(R.string.passport_stamp_primer_dia)
        StampId.MEDIO_DIA -> str(R.string.passport_stamp_medio_dia)
        StampId.DIA_EN_BLANCO -> str(R.string.passport_stamp_dia_en_blanco)
        StampId.FIN_DE_SEMANA -> str(R.string.passport_stamp_fin_de_semana)
        StampId.RACHA_3 -> str(R.string.passport_stamp_racha_3)
        StampId.RACHA_7 -> str(R.string.passport_stamp_racha_7)
        StampId.RACHA_14 -> str(R.string.passport_stamp_racha_14)
        StampId.RACHA_30 -> str(R.string.passport_stamp_racha_30)
        StampId.SEMANA_MENOS_20 -> str(R.string.passport_stamp_semana_menos_20, Format.signedPercent(-0.20))
        StampId.MES_A_LA_BAJA -> str(R.string.passport_stamp_mes_a_la_baja)
        StampId.PRIMER_RETO -> str(R.string.passport_stamp_primer_reto)
        StampId.CUATRO_RETOS -> str(R.string.passport_stamp_cuatro_retos)
        StampId.REGRESO -> str(R.string.passport_stamp_regreso)
        StampId.PRIMERA_SEMANA -> str(R.string.passport_stamp_primera_semana)
        StampId.WRAPPED_COMPARTIDO -> str(R.string.passport_stamp_wrapped_compartido)
        StampId.TICKET_GUARDADO -> str(R.string.passport_stamp_ticket_guardado)
        StampId.PRIMER_ASCENSO -> str(R.string.passport_stamp_primer_ascenso)
        StampId.MADRUGADOR -> str(R.string.passport_stamp_madrugador)
        StampId.MIL_VIDEOS -> str(R.string.passport_stamp_mil_videos, Format.integer(1_000))
        StampId.DIEZ_MIL_VIDEOS -> str(R.string.passport_stamp_diez_mil_videos, Format.integer(10_000))
        StampId.TRES_PLATAFORMAS -> str(R.string.passport_stamp_tres_plataformas)
        StampId.CINCO_CATEGORIAS -> str(R.string.passport_stamp_cinco_categorias)
        StampId.MARATON -> str(R.string.passport_stamp_maraton)
    }

    /** Rótulo pequeño del sello según su familia; los irónicos dicen que no suman. */
    fun stampKindLabel(kind: StampKind): String = when (kind) {
        StampKind.OBJETIVO -> str(R.string.passport_stamp_kind_objetivo)
        StampKind.RACHA -> str(R.string.passport_stamp_kind_racha)
        StampKind.RETO -> str(R.string.passport_stamp_kind_reto)
        StampKind.HITO -> str(R.string.passport_stamp_kind_hito)
        StampKind.IRONICO -> str(R.string.passport_stamp_kind_ironico)
    }

    /** Pista del sello que falta (también explica el que ya tienes). */
    fun stampHint(id: StampId): String = when (id) {
        StampId.PRIMER_DIA -> str(R.string.passport_stamp_hint_primer_dia)
        StampId.MEDIO_DIA -> str(R.string.passport_stamp_hint_medio_dia)
        StampId.DIA_EN_BLANCO -> str(R.string.passport_stamp_hint_dia_en_blanco)
        StampId.FIN_DE_SEMANA -> str(R.string.passport_stamp_hint_fin_de_semana)
        StampId.RACHA_3 -> str(R.string.passport_stamp_hint_racha_3)
        StampId.RACHA_7 -> str(R.string.passport_stamp_hint_racha_7)
        StampId.RACHA_14 -> str(R.string.passport_stamp_hint_racha_14)
        StampId.RACHA_30 -> str(R.string.passport_stamp_hint_racha_30)
        StampId.SEMANA_MENOS_20 -> str(R.string.passport_stamp_hint_semana_menos_20, Format.percent(0.20))
        StampId.MES_A_LA_BAJA -> str(R.string.passport_stamp_hint_mes_a_la_baja)
        StampId.PRIMER_RETO -> str(R.string.passport_stamp_hint_primer_reto)
        StampId.CUATRO_RETOS -> str(R.string.passport_stamp_hint_cuatro_retos)
        StampId.REGRESO -> str(R.string.passport_stamp_hint_regreso)
        StampId.PRIMERA_SEMANA -> str(R.string.passport_stamp_hint_primera_semana)
        StampId.WRAPPED_COMPARTIDO -> str(R.string.passport_stamp_hint_wrapped_compartido)
        StampId.TICKET_GUARDADO -> str(R.string.passport_stamp_hint_ticket_guardado)
        StampId.PRIMER_ASCENSO -> str(R.string.passport_stamp_hint_primer_ascenso)
        StampId.MADRUGADOR -> str(R.string.passport_stamp_hint_madrugador)
        StampId.MIL_VIDEOS -> str(R.string.passport_stamp_hint_mil_videos, Words.videos(1_000))
        StampId.DIEZ_MIL_VIDEOS -> str(R.string.passport_stamp_hint_diez_mil_videos, Words.videos(10_000))
        StampId.TRES_PLATAFORMAS -> str(R.string.passport_stamp_hint_tres_plataformas)
        StampId.CINCO_CATEGORIAS -> str(R.string.passport_stamp_hint_cinco_categorias)
        StampId.MARATON -> str(R.string.passport_stamp_hint_maraton, Words.videos(100))
    }

    /** Nota bajo la pista cuando no hay objetivo: la referencia es la media. */
    val REFERENCE_NOTE: String get() = str(R.string.passport_reference_note)

    val STAMP_PENDING: String get() = str(R.string.passport_stamp_pending)
    val KICKER_HINT: String get() = str(R.string.passport_kicker_hint)
    val KICKER_EARNED: String get() = str(R.string.passport_kicker_earned)

    /** «12 OCT 2026» (fecha de obtención, en mono). */
    fun stampDate(date: LocalDate): String = Format.upper(Format.dayShortWithYear(date))

    /** «CONSEGUIDO EL 12 OCT 2026». */
    fun stampEarnedOn(date: LocalDate): String = str(R.string.passport_stamp_earned_on, stampDate(date))

    /** «50 % HECHO». */
    fun stampProgress(progress: Float): String =
        str(R.string.passport_stamp_progress, Format.percent(progress.coerceIn(0f, 1f).toDouble()))

    /** «+60 MILLAS» o «SIN MILLAS» (irónicos y los que no dan nada). */
    fun stampXp(id: StampId): String =
        if (id.xp <= 0) stampKindLabel(StampKind.IRONICO) else str(R.string.passport_stamp_xp, miles(id.xp))

    /** «9 DE 23». */
    fun stampsCount(earned: Int, total: Int): String =
        str(R.string.passport_stamps_count, Format.integer(earned), Format.integer(total))

    /** «Racha de 7» (para frases y TalkBack). */
    fun stampSpoken(id: StampId): String = Format.capitalize(stampName(id).lowercase(Format.locale))

    /** Para TalkBack: «Sello Racha de 7, conseguido el 12 de octubre de 2026. Da 60 millas.». */
    fun stampDescription(stamp: StampState): String {
        val id = stamp.id
        val earnedOn = stamp.earnedOn
        return if (earnedOn != null) {
            val miles = if (id.xp <= 0) {
                str(R.string.passport_stamp_no_miles)
            } else {
                plural(R.plurals.passport_stamp_gives_miles, id.xp, Format.integer(id.xp))
            }
            str(R.string.passport_stamp_description_earned, stampSpoken(id), Format.dayFull(earnedOn), miles)
        } else {
            val progress = stamp.progress
            if (progress != null) {
                val pct = Format.percent(progress.coerceIn(0f, 1f).toDouble())
                str(R.string.passport_stamp_description_progress, stampSpoken(id), pct, stampHint(id))
            } else {
                str(R.string.passport_stamp_description_pending, stampSpoken(id), stampHint(id))
            }
        }
    }

    /** Acción de tocar un sello. */
    val STAMP_OPEN: String get() = str(R.string.passport_stamp_open)

    // ------------------------------------------------------------------ reto semanal

    /** «ESTA SEMANA, MENOS QUE LA ANTERIOR» / «ESTA SEMANA, UN 10 % MENOS». */
    fun challengeTitle(reductionPercent: Int): String =
        if (reductionPercent <= 0) str(R.string.passport_challenge_title_less)
        else str(R.string.passport_challenge_title_percent, Format.percent(reductionPercent / 100.0))

    fun challengeStatus(status: ChallengeStatus): String = when (status) {
        ChallengeStatus.EN_CURSO -> str(R.string.passport_challenge_status_running)
        ChallengeStatus.CUMPLIDO -> str(R.string.passport_challenge_status_won)
        ChallengeStatus.FALLADO -> str(R.string.passport_challenge_status_lost)
    }

    val LINE_SO_FAR: String get() = str(R.string.passport_line_so_far)
    val LINE_TARGET: String get() = str(R.string.passport_line_target)
    val LINE_GHOST_SO_FAR: String get() = str(R.string.passport_line_ghost_so_far)
    val LINE_REMAINING: String get() = str(R.string.passport_line_remaining)
    val LINE_GHOST_TOTAL: String get() = str(R.string.passport_line_ghost_total)

    /** «DE UN TOPE DE 1.098» bajo la cifra grande del reto. */
    fun challengeCaption(target: Int): String =
        str(R.string.passport_challenge_caption, Format.integer(target.coerceAtLeast(0)))

    /** Frase del reto: en curso (por delante o por detrás del fantasma), cumplido o no. Sin culpa. */
    fun challengePhrase(challenge: WeeklyChallenge, seed: Long): String {
        val total = challenge.total.coerceAtLeast(0)
        val target = challenge.target.coerceAtLeast(0)
        val t = Format.integer(total)
        val cap = Format.integer(target)
        return when (challenge.status) {
            ChallengeStatus.CUMPLIDO -> pick(seed, "passport.challenge.won", R.array.passport_challenge_won, t, cap)
            ChallengeStatus.FALLADO -> pick(seed, "passport.challenge.lost", R.array.passport_challenge_lost, t, cap)
            ChallengeStatus.EN_CURSO -> {
                val ghost = challenge.ghostSoFar.coerceAtLeast(0)
                when {
                    total > target -> pick(seed, "passport.challenge.over", R.array.passport_challenge_over)
                    total == ghost -> pick(seed, "passport.challenge.tie", R.array.passport_challenge_tie)
                    total < ghost ->
                        pick(seed, "passport.challenge.ahead", R.array.passport_challenge_ahead, Words.videos(ghost - total))
                    else ->
                        pick(seed, "passport.challenge.behind", R.array.passport_challenge_behind, Words.videos(total - ghost))
                }
            }
        }
    }

    /** Sin reto (aún no hay semana anterior con la que comparar). */
    fun warmupText(seed: Long): String = pick(seed, "passport.challenge.warmup", R.array.passport_challenge_warmup)

    /** Leyenda de la gráfica del reto. */
    val CHART_LEGEND: String get() = str(R.string.passport_chart_legend)

    /** Un día de la gráfica: «Lunes: 130 vídeos; la semana pasada, 160.». */
    fun ghostDayDescription(date: LocalDate, count: Int?, ghost: Int?): String {
        val day = Format.capitalize(Format.weekday(date.dayOfWeek))
        val mine = count?.let { Words.videos(it.coerceAtLeast(0)) }
        val past = ghost?.let { Format.integer(it.coerceAtLeast(0)) }
        return when {
            mine != null && past != null -> str(R.string.passport_ghost_day, day, mine, past)
            mine != null -> str(R.string.passport_ghost_day_no_past, day, mine)
            past != null -> str(R.string.passport_ghost_day_pending, day, past)
            else -> str(R.string.passport_ghost_day_pending_no_past, day)
        }
    }

    /** Resumen hablado de la gráfica entera. */
    fun chartDescription(total: Int, ghostSoFar: Int): String =
        str(
            R.string.passport_chart_description,
            Words.videos(total.coerceAtLeast(0)),
            Format.integer(ghostSoFar.coerceAtLeast(0)),
        )

    /** Etiqueta de un reto pasado: «Semana del 26 oct». */
    fun pastLabel(weekStart: LocalDate): String = str(R.string.passport_past_label, Format.dayShort(weekStart))

    /** Valor de un reto pasado: «GANADO» o «NO ESTA VEZ». */
    fun pastValue(won: Boolean): String =
        if (won) str(R.string.passport_past_won) else challengeStatus(ChallengeStatus.FALLADO)

    /** Para TalkBack: «Semana del 26 de octubre: 980 de un tope de 1.100, ganado.». */
    fun pastDescription(result: ChallengeResult): String = str(
        if (result.won) R.string.passport_past_description_won else R.string.passport_past_description_lost,
        Format.dayMonth(result.weekStart),
        Format.integer(result.total.coerceAtLeast(0)),
        Format.integer(result.target.coerceAtLeast(0)),
    )

    /** Para TalkBack: el reto en curso entero. */
    fun challengeDescription(challenge: WeeklyChallenge, phrase: String): String = str(
        R.string.passport_challenge_description,
        challengeTitle(challenge.reductionPercent).lowercase(Format.locale),
        Format.capitalize(challengeStatus(challenge.status).lowercase(Format.locale)),
        Words.videos(challenge.total.coerceAtLeast(0)),
        Format.integer(challenge.target.coerceAtLeast(0)),
        phrase,
    )

    // ------------------------------------------------------------------ celebraciones

    val CELEBRATE_STAMP: String get() = str(R.string.passport_celebrate_stamp)
    val CELEBRATE_LEVEL: String get() = str(R.string.passport_celebrate_level)
    val CELEBRATE_CHALLENGE: String get() = str(R.string.passport_celebrate_challenge)

    val SHARE_TICKET: String get() = str(R.string.passport_share_ticket)
    val SHARE_TICKET_DESCRIPTION: String get() = str(R.string.passport_share_ticket_description)
    val SEE_PASSPORT: String get() = str(R.string.passport_see_passport)
    val CONTINUE: String get() = str(R.string.passport_continue)

    /** Frase bajo un sello nuevo. Los irónicos lo dicen: no suman. */
    fun stampCaption(id: StampId, seed: Long): String = when {
        id.ironic -> pick(seed, "passport.celebrate.ironic", R.array.passport_caption_ironic)
        id.xp <= 0 -> pick(seed, "passport.celebrate.free", R.array.passport_caption_free)
        else -> pick(seed, "passport.celebrate.stamp", R.array.passport_caption_stamp, milesWords(id.xp))
    }

    /** Frase bajo un nivel nuevo. */
    fun levelCaption(level: TravelerLevel, seed: Long): String =
        pick(seed, "passport.celebrate.level", R.array.passport_caption_level, levelSpoken(level))

    /** Titular de un reto cumplido: «Semana del 26 de octubre». */
    fun challengeWonTitle(result: ChallengeResult): String =
        str(R.string.passport_challenge_won_title, Format.dayMonth(result.weekStart))

    /** Frase bajo un reto cumplido. */
    fun challengeWonCaption(result: ChallengeResult, seed: Long): String = pick(
        seed, "passport.celebrate.challenge", R.array.passport_caption_challenge,
        Format.integer(result.total.coerceAtLeast(0)),
        Format.integer(result.target.coerceAtLeast(0)),
    )

    // ------------------------------------------------------------------ ticket de nivel (imagen 1080 × 1920)

    val TICKET_TITLE: String get() = str(R.string.passport_ticket_title)
    val TICKET_STAMP: String get() = str(R.string.passport_ticket_stamp)
    val TICKET_LEVEL: String get() = str(R.string.passport_ticket_level)
    val TICKET_MILES: String get() = str(R.string.passport_ticket_miles)
    val TICKET_STAMPS: String get() = str(R.string.passport_ticket_stamps)
    val TICKET_STREAK: String get() = str(R.string.passport_ticket_streak)
    val TICKET_CHALLENGES: String get() = str(R.string.passport_ticket_challenges)
    val TICKET_DAYS: String get() = str(R.string.passport_ticket_days)

    /** Frase del pie del ticket de nivel. */
    fun ticketFooter(seed: Long): String = pick(seed, "passport.ticket.footer", R.array.passport_ticket_footer)

    /** Texto que acompaña a la imagen al compartirla. */
    fun ticketShareText(level: TravelerLevel): String = str(R.string.passport_ticket_share_text, levelName(level))

    /**
     * Nombre del PNG: «reelometro-nivel-plata.png». El nivel va en ASCII sin acentos; si su nombre
     * no deja nada (otro alfabeto), se usa el id interno del nivel.
     */
    fun ticketFileName(level: TravelerLevel): String {
        val plain = Normalizer.normalize(levelName(level).lowercase(Format.locale), Normalizer.Form.NFD)
            .filter { it in 'a'..'z' || it in '0'..'9' }
        return str(R.string.passport_ticket_file_name, plain.ifEmpty { level.name.lowercase(Locale.ROOT) })
    }

    // ------------------------------------------------------------------ aviso (solo el nivel nuevo avisa)

    fun levelUpNotificationTitle(level: TravelerLevel): String = str(R.string.passport_notify_level_title, levelSpoken(level))

    fun levelUpNotificationText(seed: Long): String = pick(seed, "passport.notify.level", R.array.passport_notify_level)
}
