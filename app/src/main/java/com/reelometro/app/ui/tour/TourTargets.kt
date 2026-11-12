package com.reelometro.app.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Elementos reales de la app que el recorrido guiado señala (en orden de los pasos sugeridos).
 * Las pantallas marcan sus elementos con [tourTarget]; el recorrido lee sus límites de [TourTargets].
 */
enum class TourTarget {
    /** El marcador de Hoy. */
    COUNTER,

    /** La comparación «a esta hora» bajo el marcador. */
    COMPARISON,

    /** Las barras diarias de Estadísticas (mantener el dedo). */
    STATS_BARS,

    /** El Resumen / Wrapped (la primera tira del hub). */
    WRAPPED,

    /** Flap en su pestaña. */
    PASSPORT,

    /** El acceso a Ajustes (y, desde allí, Calibración). */
    SETTINGS,
}

/** Límites en la raíz de cada elemento marcado y visible ahora mismo. */
@Stable
class TourTargets {
    private val bounds = mutableStateMapOf<TourTarget, Rect>()

    /** Límites del elemento (coordenadas de la raíz); `null` si no está en pantalla. */
    operator fun get(target: TourTarget): Rect? = bounds[target]

    internal fun put(target: TourTarget, rect: Rect) {
        if (bounds[target] != rect) bounds[target] = rect
    }

    internal fun remove(target: TourTarget) {
        bounds.remove(target)
    }
}

/** Registro del recorrido; `null` fuera de `AppShell` (galería, previews): [tourTarget] no hace nada. */
val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/** Marca este elemento como [target] del recorrido guiado. */
@Composable
fun Modifier.tourTarget(target: TourTarget): Modifier {
    val registry = LocalTourTargets.current ?: return this
    DisposableEffect(registry, target) {
        onDispose { registry.remove(target) }
    }
    return this.onGloballyPositioned { registry.put(target, it.boundsInRoot()) }
}
