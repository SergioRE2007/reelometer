package com.reelometro.app.copy

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
import java.time.LocalDate

/**
 * Textos del modo **Pasaporte** (pestaña, Flap, niveles, sellos, reto semanal y celebraciones).
 * Tono: aeropuerto, tarjeta de embarque, ironía amable. Premia ver menos; nunca culpa.
 *
 * La experiencia se enseña como **millas** (programa de viajero frecuente): salen de días bajo la
 * referencia, rachas, retos y sellos; nunca de vídeos vistos (y la pantalla lo dice).
 *
 * Variantes deterministas con `pick(seed, "clave", …)`; reglas en `src/test/.../copy/CopyRules.kt`
 * (tests propios en `PassportCopyTest`).
 */
object PassportCopy {

    /** Titular de la pestaña. */
    const val TITLE = "Pasaporte"

    const val SECTION_FLAP = "FLAP"
    const val SECTION_LEVEL = "TARJETA DE EMBARQUE"
    const val SECTION_CHALLENGE = "RETO DE LA SEMANA"
    const val SECTION_STAMPS = "SELLOS"
    const val SECTION_PAST = "RETOS ANTERIORES"

    /** Kicker de la cabecera: «63 DÍAS A BORDO». */
    fun onBoard(trackedDays: Int): String = Format.upper("${Words.days(trackedDays.coerceAtLeast(0))} a bordo")

    // ------------------------------------------------------------------ Flap

    /** Etiqueta del estado de Flap («EN FORMA», «ZOMBI DEL SCROLL»). */
    fun moodLabel(mood: FlapMood): String = when (mood) {
        FlapMood.EN_FORMA -> "EN FORMA"
        FlapMood.NORMAL -> "NORMAL"
        FlapMood.CANSADO -> "CANSADO"
        FlapMood.ZOMBI -> "ZOMBI DEL SCROLL"
    }

    /** Etiqueta mientras Flap no tiene con qué comparar (ni objetivo ni historial). */
    const val WARMING_UP = "CALENTANDO"

    /** Frase de estado de Flap (variantes deterministas por día). */
    fun moodPhrase(mood: FlapMood, seed: Long): String = when (mood) {
        FlapMood.EN_FORMA -> pick(
            seed, "passport.mood.fit",
            "Flap va sobrado esta semana.",
            "Flap está en plena forma. Tú también, aunque no lo parezca.",
            "Flap ya ni se acuerda de lo que era el scroll.",
            "Flap ha vuelto a mirar por la ventanilla.",
            "Flap se ha apuntado a correr. De momento, va.",
        )
        FlapMood.NORMAL -> pick(
            seed, "passport.mood.normal",
            "Flap va tirando.",
            "Flap, ni fu ni fa.",
            "Flap aguanta el tipo.",
            "Flap está como tu semana: normal.",
            "Flap ni bien ni mal. Flap, a secas.",
        )
        FlapMood.CANSADO -> pick(
            seed, "passport.mood.tired",
            "Flap tiene ojeras.",
            "Flap ha dormido poco y se le nota.",
            "Flap pide un respiro. Con un día tranquilo le vale.",
            "Flap bosteza entre vídeo y vídeo.",
            "Flap está cansado, pero no se rinde.",
        )
        FlapMood.ZOMBI -> pick(
            seed, "passport.mood.zombie",
            "Flap camina con los brazos por delante.",
            "Flap ya solo dice «uno más».",
            "Flap es un zombi del scroll. Se cura con un día tranquilo.",
            "Flap no muere. Solo pasa una mala racha.",
            "Flap se recupera en cuanto cumplas un día.",
        )
    }

    /** Frase mientras Flap calienta (sin objetivo ni historial suficiente). */
    fun warmingPhrase(seed: Long): String = pick(
        seed, "passport.mood.warming",
        "Flap aún te está conociendo. Dale unos días.",
        "Sin objetivo ni historial, Flap no juzga. Todavía.",
        "Flap está estirando. En unos días te dice algo.",
        "Primeros días: Flap observa y toma notas.",
    )

    /** Frase cuando Flap vuelve de zombi a normal o mejor. */
    fun recoveredPhrase(seed: Long): String = pick(
        seed, "passport.mood.recovered",
        "Flap ha vuelto de entre los scrolls.",
        "Ni zombi ni nada: Flap se ha recuperado.",
        "Flap vuelve a parpadear con normalidad.",
        "Flap ha vuelto. Dice que no se acuerda de nada.",
    )

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
        if (tracked == 0) return "SIN REGISTRO ESTA SEMANA"
        val under = flap.daysUnder.coerceIn(0, tracked)
        val reference = if (flap.referenceIsGoal) "tu objetivo" else "tu media"
        return Format.upper("${Format.integer(under)} de ${Words.days(tracked)} bajo $reference")
    }

    /** «TU OBJETIVO: 150 AL DÍA» / «TU MEDIA: 140 AL DÍA»; `null` mientras calienta. */
    fun referenceLine(flap: FlapState): String? {
        val reference = flap.reference ?: return null
        val what = if (flap.referenceIsGoal) "tu objetivo" else "tu media"
        return Format.upper("$what: ${Format.integer(reference.coerceAtLeast(0))} al día")
    }

    /** Para TalkBack: estado, registro y frase en una sola lectura. */
    fun flapDescription(flap: FlapState, phrase: String): String {
        val mood = if (flap.warmingUp) "calentando" else moodLabel(flap.mood).lowercase(Format.locale)
        val record = if (flap.warmingUp) null else record(flap).lowercase(Format.locale)
        return buildString {
            append("Flap, tu mascota del pasaporte: ")
            append(mood)
            append(". ")
            if (record != null) append(Words.sentence(record)).append(' ')
            append(phrase)
        }.trim()
    }

    /** Acción de tocar a Flap (TalkBack: «toca dos veces para saludar a Flap»). */
    const val FLAP_TAP = "saludar a Flap"

    // ------------------------------------------------------------------ niveles y millas

    /** Nombre del nivel de viajero frecuente. */
    fun levelName(level: TravelerLevel): String = when (level) {
        TravelerLevel.PASAJERO -> "PASAJERO"
        TravelerLevel.FRECUENTE -> "FRECUENTE"
        TravelerLevel.PLATA -> "PLATA"
        TravelerLevel.ORO -> "ORO"
        TravelerLevel.PLATINO -> "PLATINO"
    }

    /** Código de tres letras, como el de un aeropuerto («PAS → FRE»). */
    fun levelCode(level: TravelerLevel): String = when (level) {
        TravelerLevel.PASAJERO -> "PAS"
        TravelerLevel.FRECUENTE -> "FRE"
        TravelerLevel.PLATA -> "PLA"
        TravelerLevel.ORO -> "ORO"
        TravelerLevel.PLATINO -> "PLT"
    }

    /** Lema irónico de cada nivel. */
    fun levelTagline(level: TravelerLevel): String = when (level) {
        TravelerLevel.PASAJERO -> "Recién facturado. Todo el viaje por delante."
        TravelerLevel.FRECUENTE -> "Ya te saludan en la puerta de embarque."
        TravelerLevel.PLATA -> "Sala VIP con vistas a la pista y sin pantallas."
        TravelerLevel.ORO -> "Embarque prioritario, lejos del scroll."
        TravelerLevel.PLATINO -> "Lo más alto del programa. El scroll te ve pasar desde la pista."
    }

    /** «NIVEL 2 DE 5». */
    fun levelIndex(level: TravelerLevel): String =
        "NIVEL ${level.ordinal + 1} DE ${TravelerLevel.entries.size}"

    const val LABEL_ORIGIN = "ORIGEN"
    const val LABEL_DESTINATION = "DESTINO"
    const val LABEL_FINAL = "FIN DEL TRAYECTO"

    /** «520 MILLAS», «1 MILLA». */
    fun miles(xp: Int): String = Format.upper(Words.count(xp.coerceAtLeast(0), "milla", "millas"))

    /** «FALTAN 30 MILLAS PARA PLATA», «FALTA 1 MILLA PARA PLATA». */
    fun milesToNext(remaining: Int, next: TravelerLevel): String {
        val r = remaining.coerceAtLeast(0)
        val verb = if (r == 1) "falta" else "faltan"
        return Format.upper("$verb ${Words.count(r, "milla", "millas")} para ${levelName(next)}")
    }

    /** En el último nivel no hay siguiente. */
    const val TOP_LEVEL = "NIVEL MÁXIMO: AQUÍ SE ACABA EL PROGRAMA"

    /** Etiquetas del desglose (como las de las líneas de ticket de Estadísticas). */
    fun lineDays(referenceIsGoal: Boolean): String = if (referenceIsGoal) "Días bajo el objetivo" else "Días bajo tu media"
    const val LINE_STREAKS = "Rachas"
    const val LINE_CHALLENGES = "Retos ganados"
    const val LINE_STAMPS = "Sellos"
    const val LINE_TOTAL = "TOTAL"
    const val LINE_STREAK = "Racha actual"

    /** Valor de una línea del desglose: «+260». */
    fun milesValue(xp: Int): String = "+${Format.integer(xp.coerceAtLeast(0))}"

    /** «4 DÍAS». */
    fun streakValue(days: Int): String = Format.upper(Words.days(days.coerceAtLeast(0)))

    /** La regla del juego, a la vista. */
    const val NO_MILES_FOR_VIDEOS = "Ver vídeos no da millas. Dejar de verlos, sí."

    /** Para TalkBack: la tarjeta de embarque entera. */
    fun levelDescription(level: TravelerLevel, xp: Int, remaining: Int?, streak: Int): String = buildString {
        append("Tarjeta de embarque: nivel ")
        append(levelSpoken(level))
        append(", ${level.ordinal + 1} de ${TravelerLevel.entries.size}. ")
        append(Words.count(xp.coerceAtLeast(0), "milla", "millas"))
        append(". ")
        val next = level.next
        if (next != null && remaining != null) {
            val r = remaining.coerceAtLeast(0)
            append(if (r == 1) "Falta " else "Faltan ")
            append(Words.count(r, "milla", "millas"))
            append(" para ")
            append(levelSpoken(next))
            append(". ")
        } else {
            append("Nivel máximo. ")
        }
        append("Racha actual: ")
        append(Words.days(streak.coerceAtLeast(0)))
        append('.')
    }

    /** «Plata» (para frases y TalkBack). */
    fun levelSpoken(level: TravelerLevel): String = Format.capitalize(levelName(level).lowercase(Format.locale))

    // ------------------------------------------------------------------ sellos

    /** Nombre corto del sello (MAYÚSCULAS, cabe en el sello en una o dos líneas). */
    fun stampName(id: StampId): String = when (id) {
        StampId.PRIMER_DIA -> "PRIMER DÍA"
        StampId.MEDIO_DIA -> "A MEDIAS"
        StampId.DIA_EN_BLANCO -> "DÍA EN BLANCO"
        StampId.FIN_DE_SEMANA -> "FINDE LIMPIO"
        StampId.RACHA_3 -> "RACHA DE 3"
        StampId.RACHA_7 -> "RACHA DE 7"
        StampId.RACHA_14 -> "RACHA DE 14"
        StampId.RACHA_30 -> "RACHA DE 30"
        StampId.SEMANA_MENOS_20 -> "SEMANA ${Format.signedPercent(-0.20)}"
        StampId.MES_A_LA_BAJA -> "MES A LA BAJA"
        StampId.PRIMER_RETO -> "PRIMER RETO"
        StampId.CUATRO_RETOS -> "CUATRO RETOS"
        StampId.REGRESO -> "DE VUELTA"
        StampId.PRIMERA_SEMANA -> "SEMANA COMPLETA"
        StampId.WRAPPED_COMPARTIDO -> "RESUMEN COMPARTIDO"
        StampId.TICKET_GUARDADO -> "TICKET GUARDADO"
        StampId.PRIMER_ASCENSO -> "DESPEGUE"
        StampId.MADRUGADOR -> "MADRUGADOR"
        StampId.MIL_VIDEOS -> "${Format.integer(1_000)} VÍDEOS"
        StampId.DIEZ_MIL_VIDEOS -> "${Format.integer(10_000)} VÍDEOS"
        StampId.TRES_PLATAFORMAS -> "TRIPLETE"
        StampId.CINCO_CATEGORIAS -> "MENÚ VARIADO"
        StampId.MARATON -> "MARATÓN"
    }

    /** Rótulo pequeño del sello según su familia; los irónicos dicen que no suman. */
    fun stampKindLabel(kind: StampKind): String = when (kind) {
        StampKind.OBJETIVO -> "LLEGADAS"
        StampKind.RACHA -> "CONEXIÓN"
        StampKind.RETO -> "ADUANA"
        StampKind.HITO -> "SALIDAS"
        StampKind.IRONICO -> "SIN MILLAS"
    }

    /** Pista del sello que falta (también explica el que ya tienes). */
    fun stampHint(id: StampId): String = when (id) {
        StampId.PRIMER_DIA -> "Cierra un día en tu objetivo o por debajo."
        StampId.MEDIO_DIA -> "Cierra un día por debajo de la mitad de tu objetivo."
        StampId.DIA_EN_BLANCO -> "Pasa un día entero sin vídeos cortos, con el servicio contando."
        StampId.FIN_DE_SEMANA -> "Sábado y domingo de la misma semana, los dos bajo tu objetivo."
        StampId.RACHA_3 -> "Tres días seguidos bajo tu objetivo."
        StampId.RACHA_7 -> "Siete días seguidos bajo tu objetivo."
        StampId.RACHA_14 -> "Catorce días seguidos bajo tu objetivo."
        StampId.RACHA_30 -> "Treinta días seguidos bajo tu objetivo. Sin prisa."
        StampId.SEMANA_MENOS_20 -> "Una semana con un ${Format.percent(0.20)} menos de vídeos que la anterior."
        StampId.MES_A_LA_BAJA -> "Un mes con menos vídeos que el mes anterior."
        StampId.PRIMER_RETO -> "Gana tu primer reto semanal contra el fantasma."
        StampId.CUATRO_RETOS -> "Gana cuatro retos semanales. No hace falta que sean seguidos."
        StampId.REGRESO -> "Que Flap pase de zombi a normal o mejor."
        StampId.PRIMERA_SEMANA -> "Una semana entera con el servicio contando."
        StampId.WRAPPED_COMPARTIDO -> "Comparte un resumen desde la pestaña Resumen."
        StampId.TICKET_GUARDADO -> "Guarda en el móvil el ticket de un resumen."
        StampId.PRIMER_ASCENSO -> "Sube de pasajero a frecuente."
        StampId.MADRUGADOR -> "Sale si ves vídeos entre las 3 y las 5 de la mañana. Mejor no lo busques."
        StampId.MIL_VIDEOS -> "Llega a ${Words.videos(1_000)} en total. Llegará solo."
        StampId.DIEZ_MIL_VIDEOS -> "Llega a ${Words.videos(10_000)} en total. Tampoco hay prisa."
        StampId.TRES_PLATAFORMAS -> "TikTok, Reels y Shorts en un mismo día. No da millas."
        StampId.CINCO_CATEGORIAS -> "Cinco categorías distintas en una semana. Variado, eso sí."
        StampId.MARATON -> "Más de ${Words.videos(100)} en una sola hora. Ojalá no lo veas nunca."
    }

    /** Nota bajo la pista cuando no hay objetivo: la referencia es la media. */
    const val REFERENCE_NOTE = "Sin objetivo, cuenta tu media de las 4 semanas anteriores."

    const val STAMP_PENDING = "PENDIENTE"
    const val KICKER_HINT = "PISTA"
    const val KICKER_EARNED = "SELLO CONSEGUIDO"

    /** «12 OCT 2026» (fecha de obtención, en mono). */
    fun stampDate(date: LocalDate): String = Format.upper(Format.dayShortWithYear(date))

    /** «CONSEGUIDO EL 12 OCT 2026». */
    fun stampEarnedOn(date: LocalDate): String = "CONSEGUIDO EL ${stampDate(date)}"

    /** «50 % HECHO». */
    fun stampProgress(progress: Float): String = "${Format.percent(progress.coerceIn(0f, 1f).toDouble())} HECHO"

    /** «+60 MILLAS» o «SIN MILLAS» (irónicos y los que no dan nada). */
    fun stampXp(id: StampId): String =
        if (id.xp <= 0) stampKindLabel(StampKind.IRONICO) else "+${miles(id.xp)}"

    /** «9 DE 23». */
    fun stampsCount(earned: Int, total: Int): String = "${Format.integer(earned)} DE ${Format.integer(total)}"

    /** «Racha de 7» (para frases y TalkBack). */
    fun stampSpoken(id: StampId): String = Format.capitalize(stampName(id).lowercase(Format.locale))

    /** Para TalkBack: «Sello Racha de 7, conseguido el 12 de octubre de 2026. Da 60 millas.». */
    fun stampDescription(stamp: StampState): String {
        val id = stamp.id
        val earnedOn = stamp.earnedOn
        val miles = if (id.xp <= 0) "No da millas." else "Da ${Words.count(id.xp, "milla", "millas")}."
        return if (earnedOn != null) {
            "Sello ${stampSpoken(id)}, conseguido el ${Format.dayFull(earnedOn)}. $miles"
        } else {
            val progress = stamp.progress?.let { " ${Format.percent(it.coerceIn(0f, 1f).toDouble())} hecho." } ?: ""
            "Sello ${stampSpoken(id)}, pendiente.$progress Pista: ${stampHint(id)}"
        }
    }

    /** Acción de tocar un sello. */
    const val STAMP_OPEN = "ver la pista del sello"

    // ------------------------------------------------------------------ reto semanal

    /** «ESTA SEMANA, MENOS QUE LA ANTERIOR» / «ESTA SEMANA, UN 10 % MENOS». */
    fun challengeTitle(reductionPercent: Int): String =
        if (reductionPercent <= 0) "ESTA SEMANA, MENOS QUE LA ANTERIOR"
        else "ESTA SEMANA, UN ${Format.percent(reductionPercent / 100.0)} MENOS"

    fun challengeStatus(status: ChallengeStatus): String = when (status) {
        ChallengeStatus.EN_CURSO -> "EN CURSO"
        ChallengeStatus.CUMPLIDO -> "CUMPLIDO"
        ChallengeStatus.FALLADO -> "NO ESTA VEZ"
    }

    const val LINE_SO_FAR = "Llevas"
    const val LINE_TARGET = "Tope de la semana"
    const val LINE_GHOST_SO_FAR = "Tu fantasma, a estas alturas"
    const val LINE_REMAINING = "Te quedan"
    const val LINE_GHOST_TOTAL = "La semana pasada, entera"

    /** «DE UN TOPE DE 1.098» bajo la cifra grande del reto. */
    fun challengeCaption(target: Int): String = "DE UN TOPE DE ${Format.integer(target.coerceAtLeast(0))}"

    /** Frase del reto: en curso (por delante o por detrás del fantasma), cumplido o no. Sin culpa. */
    fun challengePhrase(challenge: WeeklyChallenge, seed: Long): String {
        val total = challenge.total.coerceAtLeast(0)
        val target = challenge.target.coerceAtLeast(0)
        val t = Format.integer(total)
        val cap = Format.integer(target)
        return when (challenge.status) {
            ChallengeStatus.CUMPLIDO -> pick(
                seed, "passport.challenge.won",
                "Reto cumplido: $t de un tope de $cap.",
                "El fantasma se queda en tierra: $t de $cap.",
                "Semana ganada. $t de $cap, sin despeinarte.",
            )
            ChallengeStatus.FALLADO -> pick(
                seed, "passport.challenge.lost",
                "Esta vez ganó el fantasma. El lunes, revancha.",
                "No salió: $t de un tope de $cap. Pasa en las mejores familias.",
                "El fantasma se lleva esta. Nadie lleva la cuenta, salvo esta app.",
            )
            ChallengeStatus.EN_CURSO -> {
                val ghost = challenge.ghostSoFar.coerceAtLeast(0)
                when {
                    total > target -> pick(
                        seed, "passport.challenge.over",
                        "Este reto ya no sale. El lunes hay otro y el fantasma no aprende.",
                        "El tope se quedó atrás. Pasa en las mejores familias.",
                        "Esta semana se la lleva el fantasma. Que no se acostumbre.",
                    )
                    total == ghost -> pick(
                        seed, "passport.challenge.tie",
                        "Vas empatado con tu fantasma. Ni un vídeo de diferencia.",
                        "Tú y tu fantasma, clavados. Queda semana para desempatar.",
                        "Empate técnico con la semana pasada.",
                    )
                    total < ghost -> {
                        val d = Words.videos(ghost - total)
                        pick(
                            seed, "passport.challenge.ahead",
                            "Vas $d por debajo de tu fantasma a estas alturas.",
                            "A estas alturas, tu fantasma llevaba $d más que tú.",
                            "De momento el fantasma va detrás: le sacas $d.",
                        )
                    }
                    else -> {
                        val d = Words.videos(total - ghost)
                        pick(
                            seed, "passport.challenge.behind",
                            "Tu fantasma iba $d por debajo a estas alturas. Queda semana.",
                            "El fantasma te saca $d. Hay días para darle la vuelta.",
                            "Por ahora gana el fantasma, por $d. Nada que no arregle un día tranquilo.",
                        )
                    }
                }
            }
        }
    }

    /** Sin reto (aún no hay semana anterior con la que comparar). */
    fun warmupText(seed: Long): String = pick(
        seed, "passport.challenge.warmup",
        "El reto empieza cuando haya una semana entera con la que comparar.",
        "Tu fantasma todavía no existe: necesita una semana completa de historial.",
        "Sin semana anterior no hay fantasma. El lunes que viene, sí.",
    )

    /** Leyenda de la gráfica del reto. */
    const val CHART_LEGEND = "BARRAS: ESTA SEMANA · LÍNEA: LA ANTERIOR"

    /** Un día de la gráfica: «Lunes: 130 vídeos; la semana pasada, 160.». */
    fun ghostDayDescription(date: LocalDate, count: Int?, ghost: Int?): String {
        val day = Format.capitalize(Format.weekday(date.dayOfWeek))
        val mine = if (count == null) "todavía no ha llegado" else Words.videos(count.coerceAtLeast(0))
        val past = if (ghost == null) "sin registro" else Format.integer(ghost.coerceAtLeast(0))
        return "$day: $mine; la semana pasada, $past."
    }

    /** Resumen hablado de la gráfica entera. */
    fun chartDescription(total: Int, ghostSoFar: Int): String =
        "Esta semana frente a la anterior, día a día. Llevas ${Words.videos(total.coerceAtLeast(0))}; " +
            "tu fantasma llevaba ${Format.integer(ghostSoFar.coerceAtLeast(0))} a estas alturas."

    /** Etiqueta de un reto pasado: «Semana del 26 oct». */
    fun pastLabel(weekStart: LocalDate): String = "Semana del ${Format.dayShort(weekStart)}"

    /** Valor de un reto pasado: «GANADO» o «NO ESTA VEZ». */
    fun pastValue(won: Boolean): String = if (won) "GANADO" else challengeStatus(ChallengeStatus.FALLADO)

    /** Para TalkBack: «Semana del 26 de octubre: 980 de un tope de 1.100, ganado.». */
    fun pastDescription(result: ChallengeResult): String {
        val outcome = if (result.won) "ganado" else "esta vez no"
        return "Semana del ${result.weekStart.dayOfMonth} de ${Format.monthName(result.weekStart.monthValue)}: " +
            "${Format.integer(result.total.coerceAtLeast(0))} de un tope de ${Format.integer(result.target.coerceAtLeast(0))}, $outcome."
    }

    /** Para TalkBack: el reto en curso entero. */
    fun challengeDescription(challenge: WeeklyChallenge, phrase: String): String =
        "Reto de la semana: ${challengeTitle(challenge.reductionPercent).lowercase(Format.locale)}. " +
            "${challengeStatus(challenge.status).lowercase(Format.locale).let(Format::capitalize)}. " +
            "Llevas ${Words.videos(challenge.total.coerceAtLeast(0))} de un tope de ${Format.integer(challenge.target.coerceAtLeast(0))}. " +
            phrase

    // ------------------------------------------------------------------ celebraciones

    const val CELEBRATE_STAMP = "SELLO NUEVO"
    const val CELEBRATE_LEVEL = "NIVEL NUEVO"
    const val CELEBRATE_CHALLENGE = "RETO CUMPLIDO"

    const val SHARE_TICKET = "COMPARTIR TICKET"
    const val SHARE_TICKET_DESCRIPTION = "Compartir el ticket de tu nivel"
    const val SEE_PASSPORT = "VER EL PASAPORTE"
    const val CONTINUE = "SEGUIR"

    /** Frase bajo un sello nuevo. Los irónicos lo dicen: no suman. */
    fun stampCaption(id: StampId, seed: Long): String = when {
        id.ironic -> pick(
            seed, "passport.celebrate.ironic",
            "Este no da millas. Pero queda bonito en el pasaporte.",
            "Sello irónico: no suma, pero cuenta.",
            "Va a la colección. Sin millas, como debe ser.",
        )
        id.xp <= 0 -> pick(
            seed, "passport.celebrate.free",
            "Va directo a la colección.",
            "Tinta fresca en el pasaporte.",
            "Uno más para la colección.",
        )
        else -> {
            val miles = Words.count(id.xp, "milla", "millas")
            pick(
                seed, "passport.celebrate.stamp",
                "Va directo al pasaporte: $miles más.",
                "Tinta fresca y $miles para la tarjeta.",
                "Uno más en la colección, y este suma: $miles.",
            )
        }
    }

    /** Frase bajo un nivel nuevo. */
    fun levelCaption(level: TravelerLevel, seed: Long): String {
        val name = levelSpoken(level)
        return pick(
            seed, "passport.celebrate.level",
            "Subes de nivel por mirar menos. Así funciona este aeropuerto.",
            "Nueva tarjeta de embarque: $name. Ni una milla salió de ver vídeos.",
            "$name. El scroll se queda en la puerta de embarque.",
        )
    }

    /** Titular de un reto cumplido: «Semana del 26 de octubre». */
    fun challengeWonTitle(result: ChallengeResult): String =
        "Semana del ${result.weekStart.dayOfMonth} de ${Format.monthName(result.weekStart.monthValue)}"

    /** Frase bajo un reto cumplido. */
    fun challengeWonCaption(result: ChallengeResult, seed: Long): String {
        val t = Format.integer(result.total.coerceAtLeast(0))
        val cap = Format.integer(result.target.coerceAtLeast(0))
        return pick(
            seed, "passport.celebrate.challenge",
            "$t de un tope de $cap. El fantasma se queda en tierra.",
            "Semana ganada al fantasma: $t de $cap.",
            "Menos que la semana anterior, que era la idea: $t de $cap.",
        )
    }

    // ------------------------------------------------------------------ ticket de nivel (imagen 1080 × 1920)

    const val TICKET_TITLE = "TARJETA DE EMBARQUE"
    const val TICKET_STAMP = "EMBARQUE CONFIRMADO"
    const val TICKET_LEVEL = "NIVEL"
    const val TICKET_MILES = "MILLAS"
    const val TICKET_STAMPS = "SELLOS"
    const val TICKET_STREAK = "RACHA"
    const val TICKET_CHALLENGES = "RETOS GANADOS"
    const val TICKET_DAYS = "DÍAS A BORDO"

    /** Frase del pie del ticket de nivel. */
    fun ticketFooter(seed: Long): String = pick(
        seed, "passport.ticket.footer",
        "Millas ganadas viendo menos vídeos.",
        "Ni una milla salió de hacer scroll.",
        "Aquí se sube de nivel mirando menos. Rarezas de esta aerolínea.",
    )

    /** Texto que acompaña a la imagen al compartirla. */
    fun ticketShareText(level: TravelerLevel): String =
        "Nivel ${levelName(level)} en Reelómetro: millas por ver menos vídeos."

    /** Nombre del PNG: «reelometro-nivel-plata.png». */
    fun ticketFileName(level: TravelerLevel): String = "reelometro-nivel-${level.name.lowercase(Format.locale)}.png"

    // ------------------------------------------------------------------ aviso (solo el nivel nuevo avisa)

    fun levelUpNotificationTitle(level: TravelerLevel): String = "Nivel ${levelSpoken(level)}"

    fun levelUpNotificationText(seed: Long): String = pick(
        seed, "passport.notify.level",
        "Subes de nivel por mirar menos. Tu tarjeta nueva está en el pasaporte.",
        "Nueva tarjeta de embarque, ganada sin hacer scroll.",
        "Hay un nivel nuevo esperándote en el pasaporte.",
    )
}
