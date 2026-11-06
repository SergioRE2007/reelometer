package com.reelometro.app.copy

import com.reelometro.app.R
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
 * Las palabras están en `res/values/copy_wrapped.xml`; aquí se decide qué frase toca y con qué huecos.
 *
 * Semilla recomendada: el epochDay del **inicio del periodo** (`Variants.daySeed(start)`), así
 * la story y la imagen compartible dicen siempre lo mismo para el mismo periodo.
 *
 * Orden de diapositivas: portada → total → tiempo → equivalencias → creador n.º 1 → categoría →
 * peor día → hora punta / plataformas → cierre (ticket final, ver [ShareCopy]).
 */
object WrappedCopy {

    // ------------------------------------------------------------------ hub

    val HUB_LAST_MONTH: String get() = str(R.string.wrapped_hub_last_month)
    val HUB_THIS_MONTH: String get() = str(R.string.wrapped_hub_this_month)
    val HUB_THIS_WEEK: String get() = str(R.string.wrapped_hub_this_week)
    val HUB_THIS_YEAR: String get() = str(R.string.wrapped_hub_this_year)
    val HUB_ALL: String get() = str(R.string.wrapped_hub_all)

    /** Titular editorial del hub: «Tu octubre, contado.». */
    fun hubHeadline(month: YearMonth, seed: Long): String = coverTitle(CopyPeriod.MONTH, month.atDay(1), seed)

    /** Tira de un periodo sin vídeos (atenuada, no se abre). */
    val HUB_NO_DATA: String get() = str(R.string.wrapped_hub_no_data)

    /** Marca de las tiras de periodos que siguen abiertos («VÍDEOS · 3,2 H · EN CURSO»). */
    val HUB_IN_PROGRESS: String get() = str(R.string.wrapped_hub_in_progress)

    /** Detalle de una tira con datos: «VÍDEOS · 3,2 H», «VÍDEOS · 3,2 H · EN CURSO». */
    fun hubDetail(totalMs: Long, inProgress: Boolean): String =
        str(if (inProgress) R.string.wrapped_hub_detail_in_progress else R.string.wrapped_hub_detail, Words.durationLabel(totalMs))

    /**
     * La tira para TalkBack: «Este mes, 1 – 30 nov: 1.204 vídeos, 9 horas y 12 minutos.»;
     * sin datos, «Este mes, 1 – 30 nov: sin datos.». [title] y [range], ya como se dicen.
     */
    fun hubStripDescription(title: String, range: String, count: Int, totalMs: Long, hasData: Boolean): String =
        if (hasData) {
            str(R.string.wrapped_hub_strip_description, title, range, Words.videos(count), Words.duration(totalMs))
        } else {
            str(R.string.wrapped_hub_strip_description_no_data, title, range)
        }

    /** Hub sin ningún periodo con datos. */
    fun hubEmpty(seed: Long): String = pick(seed, "wrapped.hub.empty", R.array.wrapped_hub_empty)

    // ------------------------------------------------------------------ portada

    /** «REELÓMETRO · OCTUBRE 2026», «REELÓMETRO · 27 OCT – 2 NOV», «REELÓMETRO · 2026», «REELÓMETRO · DESDE EL 3 MAR 2026». */
    fun coverLabel(period: CopyPeriod, start: LocalDate, end: LocalDate): String {
        val what = when (period) {
            CopyPeriod.WEEK -> Format.upper(Format.range(start, end))
            CopyPeriod.MONTH -> Format.upper(Format.monthYearLabel(start.monthValue, start.year))
            CopyPeriod.YEAR -> start.year.toString()
            CopyPeriod.ALL -> StatsCopy.allLabel(start)
        }
        return str(R.string.wrapped_cover_label, ShareCopy.BRAND, what)
    }

    /** Pista discreta de la portada: cómo se maneja la historia. */
    val COVER_HINT: String get() = str(R.string.wrapped_cover_hint)

    /** Titular de portada: «Tu octubre, contado.», «Lo que octubre se llevó.», «Tu semana, contada.». */
    fun coverTitle(period: CopyPeriod, start: LocalDate, seed: Long): String = when (period) {
        CopyPeriod.MONTH -> {
            val m = Format.monthStandalone(start.monthValue)
            pick(seed, "wrapped.cover.month", R.array.wrapped_cover_month, m, Format.capitalize(m))
        }

        CopyPeriod.WEEK -> pick(seed, "wrapped.cover.week", R.array.wrapped_cover_week)
        CopyPeriod.YEAR -> pick(seed, "wrapped.cover.year", R.array.wrapped_cover_year, start.year.toString())
        CopyPeriod.ALL -> pick(seed, "wrapped.cover.all", R.array.wrapped_cover_all)
    }

    // ------------------------------------------------------------------ total y tiempo

    val TOTAL_LABEL: String get() = Labels.VIDEOS
    val TIME_LABEL: String get() = Labels.TIME

    /** Frase bajo el marcador del total. [days] = días del periodo. */
    fun total(count: Long, days: Int, seed: Long): String {
        val v = Words.videos(count)
        if (count <= 0) return pick(seed, "wrapped.total.zero", R.array.wrapped_total_zero)
        if (count < 5) return pick(seed, "wrapped.total.tiny", R.array.wrapped_total_tiny, v)
        val d = days.coerceAtLeast(1)
        val avg = Format.decimal(count.toDouble() / d)
        return pick(
            seed, "wrapped.total",
            if (d >= 2) R.array.wrapped_total else R.array.wrapped_total_one_day,
            v, Words.times(count), avg, Words.days(d),
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
                val wholeDays = if (d == 1L) str(R.string.wrapped_time_one_whole_day) else plural(R.plurals.wrapped_time_whole_days, d, Format.integer(d))
                val workdays = Equivalences.WORKDAY.forDuration(totalMs).amountText
                pick(seed, "wrapped.time.days", R.array.wrapped_time_days, tCap, wholeDays, workdays, minutes)
            }

            hours >= 1 -> {
                val eq = Equivalences.pick(totalMs, 1, seed).firstOrNull()?.infinitive
                pick(
                    seed, "wrapped.time.hours",
                    if (eq != null) R.array.wrapped_time_hours else R.array.wrapped_time_hours_noeq,
                    tCap, eq.orEmpty(), minutes,
                )
            }

            else -> pick(seed, "wrapped.time.minutes", R.array.wrapped_time_minutes, tCap)
        }
    }

    // ------------------------------------------------------------------ equivalencias

    /** Etiqueta de la diapositiva de equivalencias; cada línea sigue con [equivalenceLine]. */
    val EQUIVALENCES_LABEL: String get() = str(R.string.wrapped_equivalences_label)

    /** «Leer 14 libros.», «Sacarte medio grado universitario.» (va bajo [EQUIVALENCES_LABEL]). Nota: [EquivalenceValue.basis]. */
    fun equivalenceLine(value: EquivalenceValue): String = Words.sentence(value.infinitive)

    /** Si el tiempo es tan poco que no hay equivalencias que mostrar. */
    val EQUIVALENCES_EMPTY: String get() = str(R.string.wrapped_equivalences_empty)

    // ------------------------------------------------------------------ creador

    val CREATOR_LABEL: String get() = Labels.TOP_CREATOR

    /** Creador n.º 1. [name] es el usuario tal como se guardó (con o sin «@»). */
    fun creator(name: String, count: Int, seed: Long): String =
        pick(seed, "wrapped.creator", R.array.wrapped_creator, Words.handle(name), Words.videos(count), Format.integer(count))

    /** No se pudo leer ningún creador en el periodo. */
    fun noCreator(seed: Long): String = pick(seed, "wrapped.creator.none", R.array.wrapped_creator_none)

    // ------------------------------------------------------------------ categoría

    val CATEGORY_LABEL: String get() = str(R.string.wrapped_category_label)

    /** Categoría principal con su cuota ([fraction] 0–1). */
    fun category(category: Category, fraction: Double, seed: Long): String {
        val pct = Format.percent(fraction)
        if (category == Category.OTROS) return pick(seed, "wrapped.category.otros", R.array.wrapped_category_otros, pct)
        val label = category.label
        // Las frases comunes y, al final, el remate propio de la categoría.
        val texts = AppTexts.current
        val options = texts.array(R.array.wrapped_category) + listOfNotNull(zinger(category)?.let { str(it) })
        val chosen = Variants.pick(options, seed, "wrapped.category")
        return texts.format(chosen, label, pct, label.replaceFirstChar { it.lowercase(Format.locale) })
    }

    private fun zinger(category: Category): Int? = when (category) {
        Category.HUMOR -> R.string.wrapped_category_zinger_humor
        Category.DEPORTE -> R.string.wrapped_category_zinger_deporte
        Category.FITNESS -> R.string.wrapped_category_zinger_fitness
        Category.COMIDA -> R.string.wrapped_category_zinger_comida
        Category.MUSICA -> R.string.wrapped_category_zinger_musica
        Category.BAILE -> R.string.wrapped_category_zinger_baile
        Category.GAMING -> R.string.wrapped_category_zinger_gaming
        Category.APRENDIZAJE -> R.string.wrapped_category_zinger_aprendizaje
        Category.TECNOLOGIA -> R.string.wrapped_category_zinger_tecnologia
        Category.BELLEZA -> R.string.wrapped_category_zinger_belleza
        Category.ANIMALES -> R.string.wrapped_category_zinger_animales
        Category.VIAJES -> R.string.wrapped_category_zinger_viajes
        Category.MOTOR -> R.string.wrapped_category_zinger_motor
        Category.DINERO -> R.string.wrapped_category_zinger_dinero
        Category.NOTICIAS -> R.string.wrapped_category_zinger_noticias
        Category.RELACIONES -> R.string.wrapped_category_zinger_relaciones
        // «Otros» tiene sus propias frases (wrapped_category_otros).
        Category.OTROS -> null
    }

    // ------------------------------------------------------------------ peor día, hora punta, plataformas

    val WORST_DAY_LABEL: String get() = Labels.WORST_DAY
    val PEAK_HOUR_LABEL: String get() = Labels.PEAK_HOUR
    val PLATFORMS_LABEL: String get() = Labels.PLATFORMS

    /** El día con más vídeos del periodo. */
    fun worstDay(date: LocalDate, count: Int, seed: Long): String {
        val d = Format.dayLong(date)
        return pick(seed, "wrapped.worstDay", R.array.wrapped_worst_day, d, Format.capitalize(d), Words.videos(count))
    }

    /** Hora punta (misma voz que en Estadísticas). */
    fun peakHour(hour: Int, seed: Long): String = StatsCopy.peakHourQuote(hour, seed)

    /**
     * Reparto por plataformas. [shares]: plataforma y fracción (0–1), en cualquier orden.
     * Usa los nombres cortos (TikTok, Reels, Shorts).
     */
    fun platforms(shares: List<Pair<Platform, Double>>, seed: Long): String {
        val list = shares.filter { it.second > 0 }.sortedByDescending { it.second }
        if (list.isEmpty()) return str(R.string.wrapped_platforms_none)
        val (top, topShare) = list.first()
        val topName = top.shortName
        if (list.size == 1 || topShare >= 0.995) return pick(seed, "wrapped.platforms.single", R.array.wrapped_platforms_single, topName)
        val rest = list.drop(1)
        val others = Words.join(rest.map { it.first.shortName })
        val pct = Format.percent(topShare)
        return if (topShare >= 0.5) {
            pick(
                seed, "wrapped.platforms.top",
                if (rest.size == 1) R.array.wrapped_platforms_top_one_rest else R.array.wrapped_platforms_top,
                topName, pct, others,
            )
        } else {
            val detail = Words.join(list.map { str(R.string.wrapped_platform_share, it.first.shortName, Format.percent(it.second)) })
            pick(seed, "wrapped.platforms.balanced", R.array.wrapped_platforms_balanced, detail, Format.percent(0.5))
        }
    }

    /** Valor de una fila de la gráfica de plataformas: «34 % · 1.204». */
    fun platformRowValue(fraction: Double, count: Int): String =
        str(R.string.wrapped_platform_row_value, Format.percent(fraction), Format.integer(count))

    /** Días con vídeos en la diapositiva del total: «26/31». */
    fun activeDaysValue(active: Int, days: Int): String =
        str(R.string.wrapped_active_days_value, Format.integer(active), Format.integer(days))

    // ------------------------------------------------------------------ cierre

    /**
     * Cierre antes del ticket final. [finished] = `false` si el periodo sigue en curso
     * (ESTE MES, ESTA SEMANA, ESTE AÑO, SIEMPRE).
     */
    fun closing(period: CopyPeriod, start: LocalDate, finished: Boolean, seed: Long): String {
        if (!finished || period == CopyPeriod.ALL) {
            val array = when (period) {
                CopyPeriod.WEEK -> R.array.wrapped_closing_open_week
                CopyPeriod.MONTH -> R.array.wrapped_closing_open_month
                CopyPeriod.YEAR -> R.array.wrapped_closing_open_year
                CopyPeriod.ALL -> R.array.wrapped_closing_open_all
            }
            return pick(seed, "wrapped.closing.open", array)
        }
        return when (period) {
            CopyPeriod.MONTH -> {
                val m = Format.monthStandalone(start.monthValue)
                val next = Format.capitalize(Format.monthStandalone(start.plusMonths(1).monthValue))
                pick(seed, "wrapped.closing.month", R.array.wrapped_closing_month, m, next, Format.capitalize(m))
            }

            CopyPeriod.WEEK -> pick(seed, "wrapped.closing.week", R.array.wrapped_closing_week)

            else -> {
                val y = start.year
                pick(seed, "wrapped.closing.year", R.array.wrapped_closing_year, y.toString(), (y + 1).toString())
            }
        }
    }

    /** Tras GUARDAR (el sitio lo elige la persona en el selector de documentos, no siempre la galería). */
    val SAVED: String get() = str(R.string.wrapped_saved)
    val SAVE_FAILED: String get() = str(R.string.wrapped_save_failed)

    /** Si no se pudo preparar la imagen para compartirla. */
    val SHARE_FAILED: String get() = str(R.string.wrapped_share_failed)
}
