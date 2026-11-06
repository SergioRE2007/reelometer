package com.reelometro.app.copy

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.stats.EquivalenceValue
import com.reelometro.app.stats.Equivalences
import com.reelometro.app.stats.Format
import java.time.LocalDate
import java.time.YearMonth

/**
 * Textos del **Resumen** (hub) y del **Wrapped** (story a pantalla completa).
 *
 * Semilla recomendada: el epochDay del **inicio del periodo** (`Variants.daySeed(start)`), así
 * la story y la imagen compartible dicen siempre lo mismo para el mismo periodo.
 *
 * Orden de diapositivas: portada → total → tiempo → equivalencias → creador n.º 1 → categoría →
 * peor día → hora punta / plataformas → cierre (ticket final, ver [ShareCopy]).
 */
object WrappedCopy {

    // ------------------------------------------------------------------ hub

    const val HUB_LAST_MONTH = "MES PASADO"
    const val HUB_THIS_MONTH = "ESTE MES"
    const val HUB_THIS_WEEK = "ESTA SEMANA"
    const val HUB_THIS_YEAR = "ESTE AÑO"
    const val HUB_ALL = "SIEMPRE"

    /** Titular editorial del hub: «Tu octubre, contado.». */
    fun hubHeadline(month: YearMonth, seed: Long): String = coverTitle(CopyPeriod.MONTH, month.atDay(1), seed)

    /** Tira de un periodo sin vídeos (atenuada, no se abre). */
    const val HUB_NO_DATA = "SIN DATOS"

    /** Marca de las tiras de periodos que siguen abiertos («VÍDEOS · 3,2 H · EN CURSO»). */
    const val HUB_IN_PROGRESS = "EN CURSO"

    /** Hub sin ningún periodo con datos. */
    fun hubEmpty(seed: Long): String = pick(
        seed, "wrapped.hub.empty",
        "Todavía no hay nada que resumir. Vuelve cuando lleves unos días.",
        "Sin datos no hay resumen. Y de momento no hay datos.",
        "El primer resumen llegará en cuanto haya algo que contar.",
    )

    // ------------------------------------------------------------------ portada

    /** «REELÓMETRO · OCTUBRE 2026», «REELÓMETRO · 27 OCT – 2 NOV», «REELÓMETRO · 2026», «REELÓMETRO · DESDE EL 3 MAR 2026». */
    fun coverLabel(period: CopyPeriod, start: LocalDate, end: LocalDate): String {
        val what = when (period) {
            CopyPeriod.WEEK -> Format.upper(Format.range(start, end))
            CopyPeriod.MONTH -> Format.upper("${Format.monthName(start.monthValue)} ${start.year}")
            CopyPeriod.YEAR -> start.year.toString()
            CopyPeriod.ALL -> Format.upper("desde el ${Format.dayShortWithYear(start)}")
        }
        return "${ShareCopy.BRAND} · $what"
    }

    /** Pista discreta de la portada: cómo se maneja la historia. */
    const val COVER_HINT = "TOCA PARA AVANZAR · MANTÉN PARA PAUSAR"

    /** Titular de portada: «Tu octubre, contado.», «Lo que octubre se llevó.», «Tu semana, contada.». */
    fun coverTitle(period: CopyPeriod, start: LocalDate, seed: Long): String = when (period) {
        CopyPeriod.MONTH -> {
            val m = Format.monthName(start.monthValue)
            pick(
                seed, "wrapped.cover.month",
                "Tu $m, contado.",
                "${Format.capitalize(m)}, en cifras.",
                "Lo que $m se llevó.",
                "${Format.capitalize(m)}, vídeo a vídeo.",
            )
        }

        CopyPeriod.WEEK -> pick(
            seed, "wrapped.cover.week",
            "Tu semana, contada.",
            "Siete días, vídeo a vídeo.",
            "La semana, en cifras.",
        )

        CopyPeriod.YEAR -> {
            val y = start.year.toString()
            pick(
                seed, "wrapped.cover.year",
                "Tu $y, contado.",
                "$y, vídeo a vídeo.",
                "Un año entero, en cifras.",
            )
        }

        CopyPeriod.ALL -> pick(
            seed, "wrapped.cover.all",
            "Todo, contado.",
            "Tu historial completo, en cifras.",
            "Desde el principio, vídeo a vídeo.",
        )
    }

    // ------------------------------------------------------------------ total y tiempo

    const val TOTAL_LABEL = Labels.VIDEOS
    const val TIME_LABEL = Labels.TIME

    /** Frase bajo el marcador del total. [days] = días del periodo. */
    fun total(count: Long, days: Int, seed: Long): String {
        val v = Words.videos(count)
        if (count <= 0) {
            return pick(
                seed, "wrapped.total.zero",
                "Ni un vídeo. Este resumen va a ser corto.",
                "Cero vídeos. Poco que contar, y eso ya dice mucho.",
                "Nada. Ni uno. El resumen más corto de la historia.",
            )
        }
        if (count < 5) {
            return pick(
                seed, "wrapped.total.tiny",
                "Solo $v. Esto se resume rápido.",
                "$v. Ni el algoritmo se lo cree.",
                "$v y punto.",
            )
        }
        val d = days.coerceAtLeast(1)
        val avg = Format.decimal(count.toDouble() / d)
        return pick(
            seed, "wrapped.total",
            "$v. Uno detrás de otro.",
            "Deslizaste hacia arriba ${Words.times(count)}.",
            if (d >= 2) "Una media de $avg al día, festivos incluidos." else "$v en un solo día. Ahí es nada.",
            "$v en ${Words.days(d)}. Las cuentas claras.",
        )
    }

    /** Valor del tiempo: «29 H», «47 MIN». */
    fun timeValue(totalMs: Long): String = Words.durationLabel(totalMs)

    /** Frase de la diapositiva de tiempo. */
    fun time(totalMs: Long, seed: Long): String {
        val t = Words.duration(totalMs)
        val tCap = Format.capitalize(t)
        val hours = Format.hours(totalMs.coerceAtLeast(0))
        val minutes = Words.minutes((totalMs.coerceAtLeast(0) + 30_000) / 60_000)
        return when {
            hours >= 24 -> {
                val d = (hours / 24).toLong()
                val dias = if (d == 1L) "un día entero" else "${Format.integer(d)}${Words.NBSP}días enteros"
                val jornadas = Equivalences.WORKDAY.forDuration(totalMs).amountText
                pick(
                    seed, "wrapped.time.days",
                    "$tCap. Más de $dias, sin dormir ni comer.",
                    "$tCap de pantalla o, lo que es lo mismo, $jornadas.",
                    "$tCap. O, dicho de otra forma, $minutes.",
                )
            }

            hours >= 1 -> {
                val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive
                pick(
                    seed, "wrapped.time.hours",
                    if (eq != null) "$tCap. Te daba para $eq." else "$tCap. Un buen rato.",
                    "$tCap. O, dicho de otra forma, $minutes.",
                    "$tCap, de quince en quince segundos.",
                )
            }

            else -> pick(
                seed, "wrapped.time.minutes",
                "$tCap. Poca cosa, para lo que podría ser.",
                "$tCap. Ni para una película.",
                "$tCap, de quince en quince segundos.",
            )
        }
    }

    // ------------------------------------------------------------------ equivalencias

    /** Etiqueta de la diapositiva de equivalencias; cada línea sigue con [equivalenceLine]. */
    const val EQUIVALENCES_LABEL = "CON ESE TIEMPO PODRÍAS"

    /** «Leer 14 libros.», «Sacarte medio grado universitario.» (va bajo [EQUIVALENCES_LABEL]). Nota: [EquivalenceValue.basis]. */
    fun equivalenceLine(value: EquivalenceValue): String = Words.sentence(value.infinitive)

    /** Si el tiempo es tan poco que no hay equivalencias que mostrar. */
    const val EQUIVALENCES_EMPTY = "Tan poco tiempo que no hay con qué compararlo."

    // ------------------------------------------------------------------ creador

    const val CREATOR_LABEL = Labels.TOP_CREATOR

    /** Creador n.º 1. [name] es el usuario tal como se guardó (con o sin «@»). */
    fun creator(name: String, count: Int, seed: Long): String {
        val h = Words.handle(name)
        val v = Words.videos(count)
        return pick(
            seed, "wrapped.creator",
            "$h: $v. A estas alturas, es de la familia.",
            "$v de $h. Más tiempo que con algunos primos.",
            "Nadie te puso más vídeos delante que $h: ${Format.integer(count)}.",
            "Tu n.º 1: $h, con $v. Ese perfil no sabe que existes.",
        )
    }

    /** No se pudo leer ningún creador en el periodo. */
    fun noCreator(seed: Long): String = pick(
        seed, "wrapped.creator.none",
        "Sin creador destacado: no he podido leer quién publicaba los vídeos.",
        "Esta vez no hay creador n.º 1. Las apps no siempre dejan ver quién firma.",
        "Ningún creador destacado. El anonimato, a veces, gana.",
    )

    // ------------------------------------------------------------------ categoría

    const val CATEGORY_LABEL = "LO QUE MÁS VISTE"

    /** Categoría principal con su cuota ([fraction] 0–1). */
    fun category(category: Category, fraction: Double, seed: Long): String {
        val pct = Format.percent(fraction)
        if (category == Category.OTROS) {
            return pick(
                seed, "wrapped.category.otros",
                "La mayoría, sin clasificar. Tus gustos son un misterio incluso para mí.",
                "Casi todo, sin categoría. Ni el clasificador se aclara.",
                "El $pct, sin clasificar. Hay feeds que no se dejan etiquetar.",
            )
        }
        val label = category.label
        return pick(
            seed, "wrapped.category",
            "Lo que más viste: $label. El $pct de todo.",
            "$label, con el $pct. Ya sabemos de qué va tu feed.",
            "Tu feed habla de ${label.replaceFirstChar { it.lowercase(Format.locale) }}: un $pct.",
            zinger(category),
        )
    }

    private fun zinger(category: Category): String = when (category) {
        Category.HUMOR -> "Te has reído mucho. O, como mínimo, has echado aire por la nariz."
        Category.DEPORTE -> "Mucho deporte. Visto, pero deporte."
        Category.FITNESS -> "Rutinas de gimnasio, vistas desde el sofá. Cuenta como calentamiento."
        Category.COMIDA -> "Recetas, muchas. Cocinadas, eso ya es otra cuenta."
        Category.MUSICA -> "Tu feed tiene banda sonora propia."
        Category.BAILE -> "Muchos bailes. Los pasos, ya si eso, otro día."
        Category.GAMING -> "Partidas ajenas, horas propias."
        Category.APRENDIZAJE -> "Vídeos para aprender. Algo se habrá quedado."
        Category.TECNOLOGIA -> "Mucho gadget. Este móvil, por cierto, aguanta bien."
        Category.BELLEZA -> "Rutinas de diez pasos, vistas en diez segundos."
        Category.ANIMALES -> "Animales. Lo mejor de internet, para qué negarlo."
        Category.VIAJES -> "Has visto medio mundo. Desde el sofá, pero medio mundo."
        Category.MOTOR -> "Muchos caballos. De los de motor."
        Category.DINERO -> "Consejos de dinero, a montones. El tiempo, por cierto, también lo es."
        Category.NOTICIAS -> "Noticias en vertical y en treinta segundos. Al día, a tu manera."
        Category.RELACIONES -> "Consejos de pareja, muchos. Aplicados, eso ya no lo cuento."
        Category.OTROS -> "La mayoría, sin clasificar."
    }

    // ------------------------------------------------------------------ peor día, hora punta, plataformas

    const val WORST_DAY_LABEL = Labels.WORST_DAY
    const val PEAK_HOUR_LABEL = Labels.PEAK_HOUR
    const val PLATFORMS_LABEL = Labels.PLATFORMS

    /** El día con más vídeos del periodo. */
    fun worstDay(date: LocalDate, count: Int, seed: Long): String {
        val d = Format.dayLong(date)
        val v = Words.videos(count)
        return pick(
            seed, "wrapped.worstDay",
            "Tu peor día: el $d, con $v.",
            "${Format.capitalize(d)}: $v. Ese día ganó el algoritmo.",
            "El récord del periodo: $v, el $d. ¿Qué pasó? No lo sé; solo lo cuento.",
            "$v en un solo día: el $d. Hay días así.",
        )
    }

    /** Hora punta (misma voz que en Estadísticas). */
    fun peakHour(hour: Int, seed: Long): String = StatsCopy.peakHourQuote(hour, seed)

    /**
     * Reparto por plataformas. [shares]: plataforma y fracción (0–1), en cualquier orden.
     * Usa los nombres cortos (TikTok, Reels, Shorts).
     */
    fun platforms(shares: List<Pair<Platform, Double>>, seed: Long): String {
        val list = shares.filter { it.second > 0 }.sortedByDescending { it.second }
        if (list.isEmpty()) return "Sin plataformas que repartir."
        val (top, topShare) = list.first()
        val topName = top.shortName
        if (list.size == 1 || topShare >= 0.995) {
            return pick(
                seed, "wrapped.platforms.single",
                "Todo en $topName. Fidelidad absoluta.",
                "$topName, y nada más. Monogamia de plataforma.",
                "Una sola app: $topName. Para qué complicarse.",
            )
        }
        val rest = list.drop(1)
        val others = Words.join(rest.map { it.first.shortName })
        val pct = Format.percent(topShare)
        return if (topShare >= 0.5) {
            val repartieron = if (rest.size == 1) "se quedó con" else "se repartieron"
            pick(
                seed, "wrapped.platforms.top",
                "$topName se llevó el $pct. $others $repartieron el resto.",
                "Ganó $topName, con el $pct.",
                "$topName, con el $pct, y el resto para $others.",
            )
        } else {
            val detail = Words.join(list.map { "${it.first.shortName} ${Format.percent(it.second)}" })
            pick(
                seed, "wrapped.platforms.balanced",
                "Repartido: $detail. No te casas con nadie.",
                "Ninguna app pasa del 50${Words.NBSP}%. Lo tuyo es repartir.",
                "Reparto casi democrático: $detail.",
            )
        }
    }

    // ------------------------------------------------------------------ cierre

    /**
     * Cierre antes del ticket final. [finished] = `false` si el periodo sigue en curso
     * (ESTE MES, ESTA SEMANA, ESTE AÑO, SIEMPRE).
     */
    fun closing(period: CopyPeriod, start: LocalDate, finished: Boolean, seed: Long): String {
        if (!finished || period == CopyPeriod.ALL) {
            val soFar = when (period) {
                CopyPeriod.WEEK -> "lo que va de semana"
                CopyPeriod.MONTH -> "lo que va de mes"
                CopyPeriod.YEAR -> "lo que va de año"
                CopyPeriod.ALL -> "todo, de momento"
            }
            val todavia = when (period) {
                CopyPeriod.WEEK -> "la semana todavía no ha terminado"
                CopyPeriod.MONTH -> "el mes todavía no ha terminado"
                CopyPeriod.YEAR -> "el año todavía no ha terminado"
                CopyPeriod.ALL -> "esto no ha terminado"
            }
            return pick(
                seed, "wrapped.closing.open",
                "Y esto es $soFar. El marcador sigue contando.",
                "Resumen provisional: $todavia.",
                "Fin del resumen, de momento. Lo que hagas con él es cosa tuya.",
            )
        }
        return when (period) {
            CopyPeriod.MONTH -> {
                val m = Format.monthName(start.monthValue)
                val next = Format.capitalize(Format.monthName(start.plusMonths(1).monthValue))
                pick(
                    seed, "wrapped.closing.month",
                    "Esto fue $m. $next ya ha empezado a contar.",
                    "Hasta aquí $m. El marcador sigue.",
                    "${Format.capitalize(m)}, cerrado. Sin devoluciones.",
                    "Fin del resumen. Lo que hagas con él es cosa tuya.",
                )
            }

            CopyPeriod.WEEK -> pick(
                seed, "wrapped.closing.week",
                "Esto fue tu semana. La siguiente ya ha empezado a contar.",
                "Semana cerrada. Sin devoluciones.",
                "Hasta aquí la semana. El marcador sigue.",
            )

            else -> {
                val y = start.year
                pick(
                    seed, "wrapped.closing.year",
                    "Esto fue $y. ${y + 1} ya ha empezado a contar.",
                    "Hasta aquí $y. El marcador sigue.",
                    "$y, cerrado. Sin devoluciones.",
                )
            }
        }
    }

    /** Tras GUARDAR (el sitio lo elige la persona en el selector de documentos, no siempre la galería). */
    const val SAVED = "Ticket guardado."
    const val SAVE_FAILED = "No se ha podido guardar la imagen. Prueba otra vez."

    /** Si no se pudo preparar la imagen para compartirla. */
    const val SHARE_FAILED = "No se ha podido preparar la imagen. Prueba otra vez."
}
