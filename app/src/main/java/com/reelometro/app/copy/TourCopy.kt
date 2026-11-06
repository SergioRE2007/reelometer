package com.reelometro.app.copy

import com.reelometro.app.stats.Format
import com.reelometro.app.ui.tour.TourStep

/**
 * Textos del **recorrido guiado** de la primera vez (marcas sobre las pantallas reales).
 * Frases cortas: caben con la fuente al 130 % encima o debajo del hueco. Sin variantes: el recorrido
 * se lee una vez (o cuando se pide otra vez desde Ajustes) y debe decir siempre lo mismo.
 */
object TourCopy {
    const val SKIP = "SALTAR"
    const val NEXT = Buttons.NEXT
    const val DONE = "LISTO"

    /** «PASO 2 DE 6». */
    fun step(index: Int, total: Int): String = "PASO ${Format.integer(index)} DE ${Format.integer(total)}"

    /** Título del panel para TalkBack (`paneTitle`). */
    const val PANE_TITLE = "Recorrido por Reelómetro"

    /** TalkBack de los botones. */
    const val SKIP_DESCRIPTION = "Saltar el recorrido"
    const val NEXT_DESCRIPTION = "Paso siguiente del recorrido"
    const val DONE_DESCRIPTION = "Terminar el recorrido"

    /** Título corto de cada paso («El marcador»). */
    fun title(step: TourStep): String = when (step) {
        TourStep.COUNTER -> "El marcador"
        TourStep.COMPARISON -> "A esta hora"
        TourStep.STATS_BARS -> "Cada día, una barra"
        TourStep.WRAPPED -> "El resumen"
        TourStep.PASSPORT -> "El pasaporte"
        TourStep.SETTINGS -> "Ajustes y calibración"
    }

    /**
     * Texto del paso. [hasTarget] = `false` si el elemento no está en pantalla (p. ej. aún no hay
     * comparación «a esta hora» porque faltan días): el texto lo explica sin señalar nada.
     */
    fun text(step: TourStep, hasTarget: Boolean = true): String = when (step) {
        TourStep.COUNTER ->
            "Tus vídeos de hoy. El marcador sube solo con cada uno y vuelve a cero a medianoche."
        TourStep.COMPARISON ->
            if (hasTarget) {
                "Cómo vas frente a tu día medio a esta misma hora. Sin juicios, solo cifras."
            } else {
                "Con unos días contados, aquí verás cómo vas frente a tu día medio a esta misma hora."
            }
        TourStep.STATS_BARS ->
            "Mantén el dedo sobre una barra y desliza para ver la cifra exacta de cada día."
        TourStep.WRAPPED ->
            "Tu semana, tu mes y tu año, contados como una historia. Con ticket para compartir, si te atreves."
        TourStep.PASSPORT ->
            "Flap y tus sellos. Aquí la experiencia se gana viendo menos, nunca más."
        TourStep.SETTINGS ->
            "Objetivo, avisos y tema, aquí arriba. Siete toques en la versión abren la calibración."
    }

    /** Lo que anuncia TalkBack al llegar a cada paso: «Paso 2 de 6. A esta hora. …». */
    fun announcement(step: TourStep, index: Int, total: Int, hasTarget: Boolean = true): String =
        "Paso ${Format.integer(index)} de ${Format.integer(total)}. ${title(step)}. ${text(step, hasTarget)}"
}
