package com.reelometro.app.ui.tour

/*
 * Lógica PURA del recorrido guiado (sin Android ni Compose: se prueba con tests JVM).
 * `TourHost` la pinta sobre las pantallas reales; aquí solo se decide qué paso toca, qué pestaña
 * necesita y cuándo se acaba (terminado o saltado).
 */

/** Pestañas que usa el recorrido; [index] es el de la barra (0 Hoy · 1 Estadísticas · 2 Resumen · 3 Flap). */
enum class TourTab(val index: Int) {
    TODAY(0),
    STATS(1),
    WRAPPED(2),
    PASSPORT(3),
}

/**
 * Pasos del recorrido, en orden. Cada uno señala un elemento real (su `TourTarget` del mismo nombre)
 * en la pestaña [tab]. [passportOnly]: solo existe con Flap encendido en Ajustes.
 */
enum class TourStep(val tab: TourTab, val passportOnly: Boolean = false) {
    /** El marcador de Hoy. */
    COUNTER(TourTab.TODAY),

    /** La comparación «a esta hora» (si aún no hay, el texto sale sin hueco). */
    COMPARISON(TourTab.TODAY),

    /** Las barras de Estadísticas: mantener el dedo sobre una. */
    STATS_BARS(TourTab.STATS),

    /** El Resumen y sus historias. */
    WRAPPED(TourTab.WRAPPED),

    /** Flap: empieza cada día a tope y cada vídeo le quita vida. */
    PASSPORT(TourTab.PASSPORT, passportOnly = true),

    /** Dónde están Ajustes (y, con siete toques en la versión, Calibración). */
    SETTINGS(TourTab.TODAY),
}

/**
 * Estado del recorrido. [index] apunta a [steps]; al terminar o saltar, [finished] pasa a `true`
 * y [skipped] dice cómo acabó.
 */
data class TourState(
    val steps: List<TourStep>,
    val index: Int = 0,
    val finished: Boolean = false,
    val skipped: Boolean = false,
    /** Quedan pestañas por aparecer (descubrimiento progresivo): el paso de Ajustes lo cuenta. */
    val moreToCome: Boolean = false,
) {
    /** Paso visible; `null` si ya ha terminado (o no hay pasos). */
    val step: TourStep? get() = if (finished) null else steps.getOrNull(index)

    /** Número del paso para «PASO 2 DE 6» (desde 1). */
    val number: Int get() = index + 1

    val total: Int get() = steps.size

    /** Es el último paso: el botón dice LISTO en vez de SIGUIENTE. */
    val isLast: Boolean get() = index >= steps.lastIndex
}

object TourLogic {

    /**
     * Pasos según Flap (apagado, cinco: el de Flap no existe) y las [visibleTabs]
     * pestañas a la vista (descubrimiento progresivo: no se presenta una pestaña que aún no ha aparecido).
     * Los de Hoy y Ajustes siempre quedan.
     */
    fun steps(passportEnabled: Boolean, visibleTabs: Int = 4): List<TourStep> =
        TourStep.entries.filter { (passportEnabled || !it.passportOnly) && it.tab.index < visibleTabs }

    /** Recorrido desde el principio. */
    fun start(passportEnabled: Boolean, visibleTabs: Int = 4): TourState = TourState(
        steps = steps(passportEnabled, visibleTabs),
        moreToCome = visibleTabs < (if (passportEnabled) TourTab.entries.size else TourTab.entries.size - 1),
    )

    /**
     * Retoma el recorrido en el paso guardado [stepName] (rotación, proceso recreado). Si ese paso
     * ya no existe (han apagado Flap justo en él), sigue en el siguiente que quede.
     */
    fun resume(passportEnabled: Boolean, stepName: String?, visibleTabs: Int = 4): TourState {
        val fresh = start(passportEnabled, visibleTabs)
        val saved = TourStep.entries.firstOrNull { it.name == stepName } ?: return fresh
        val index = fresh.steps.indexOfFirst { it.ordinal >= saved.ordinal }
        return if (index < 0) fresh.copy(index = fresh.steps.lastIndex.coerceAtLeast(0)) else fresh.copy(index = index)
    }

    /** SIGUIENTE (o LISTO en el último paso: termina sin saltar). */
    fun next(state: TourState): TourState = when {
        state.finished -> state
        state.isLast -> state.copy(finished = true, skipped = false)
        else -> state.copy(index = state.index + 1)
    }

    /** SALTAR o atrás: termina y lo marca como saltado. */
    fun skip(state: TourState): TourState =
        if (state.finished) state else state.copy(finished = true, skipped = true)

    /**
     * Pestaña que hay que abrir para el paso actual, o `null` si ya está a la vista (o si el recorrido
     * ha terminado). [currentTab] es el índice de la pestaña visible, −1 fuera de las pestañas.
     */
    fun tabToShow(state: TourState, currentTab: Int): TourTab? {
        val step = state.step ?: return null
        return step.tab.takeIf { it.index != currentTab }
    }

    /**
     * Dónde va el texto respecto al hueco: debajo si el hueco está en la mitad de arriba (o no hay
     * hueco), encima si está en la de abajo. [holeTop] y [holeBottom] en las mismas unidades que [height].
     */
    fun textBelow(holeTop: Float?, holeBottom: Float?, height: Float): Boolean {
        if (holeTop == null || holeBottom == null || height <= 0f) return true
        val above = holeTop
        val below = height - holeBottom
        return below >= above
    }
}
