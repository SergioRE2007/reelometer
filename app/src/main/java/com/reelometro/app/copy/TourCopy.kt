package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.tour.TourStep

/**
 * Textos del **recorrido guiado** de la primera vez (marcas sobre las pantallas reales), en
 * `res/values/copy_tour.xml`. Frases cortas: caben con la fuente al 130 % encima o debajo del hueco.
 * Sin variantes: el recorrido se lee una vez (o cuando se pide otra vez desde Ajustes) y debe decir
 * siempre lo mismo.
 */
object TourCopy {
    val SKIP: String get() = str(R.string.tour_skip)
    val NEXT: String get() = Buttons.NEXT
    val DONE: String get() = str(R.string.tour_done)

    /** «PASO 2 DE 6». */
    fun step(index: Int, total: Int): String = str(R.string.tour_step, Format.integer(index), Format.integer(total))

    /** Título del panel para TalkBack (`paneTitle`). */
    val PANE_TITLE: String get() = str(R.string.tour_pane_title)

    /** TalkBack de los botones. */
    val SKIP_DESCRIPTION: String get() = str(R.string.tour_skip_description)
    val NEXT_DESCRIPTION: String get() = str(R.string.tour_next_description)
    val DONE_DESCRIPTION: String get() = str(R.string.tour_done_description)

    /** Título corto de cada paso («El marcador»). */
    fun title(step: TourStep): String = when (step) {
        TourStep.COUNTER -> str(R.string.tour_counter_title)
        TourStep.COMPARISON -> str(R.string.tour_comparison_title)
        TourStep.STATS_BARS -> str(R.string.tour_stats_bars_title)
        TourStep.WRAPPED -> str(R.string.tour_wrapped_title)
        TourStep.PASSPORT -> str(R.string.tour_passport_title)
        TourStep.SETTINGS -> str(R.string.tour_settings_title)
    }

    /**
     * Texto del paso. [hasTarget] = `false` si el elemento no está en pantalla (p. ej. aún no hay
     * comparación «a esta hora» porque faltan días): el texto lo explica sin señalar nada.
     * [moreToCome] = quedan pestañas por aparecer (descubrimiento progresivo): el paso de Ajustes lo cuenta.
     */
    fun text(step: TourStep, hasTarget: Boolean = true, moreToCome: Boolean = false): String = when (step) {
        TourStep.COUNTER -> str(R.string.tour_counter_text)
        TourStep.COMPARISON ->
            if (hasTarget) str(R.string.tour_comparison_text) else str(R.string.tour_comparison_text_empty)
        TourStep.STATS_BARS -> str(R.string.tour_stats_bars_text)
        TourStep.WRAPPED -> str(R.string.tour_wrapped_text)
        TourStep.PASSPORT -> str(R.string.tour_passport_text)
        TourStep.SETTINGS ->
            if (moreToCome) str(R.string.tour_settings_text_more) else str(R.string.tour_settings_text)
    }

    /** Lo que anuncia TalkBack al llegar a cada paso: «Paso 2 de 6. A esta hora. …». */
    fun announcement(step: TourStep, index: Int, total: Int, hasTarget: Boolean = true, moreToCome: Boolean = false): String =
        str(R.string.tour_announcement, Format.integer(index), Format.integer(total), title(step), text(step, hasTarget, moreToCome))
}
