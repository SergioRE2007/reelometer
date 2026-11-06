package com.reelometro.app.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelometro.app.ui.theme.LocalReducedMotion

/** Ámbito de transiciones compartidas del NavHost (null fuera de él: galería, previews). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Ámbito de animación del destino actual del NavHost. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Alto que ocupa la barra de pestañas (incluido el inset de navegación) para dejar hueco al final del contenido. */
val LocalBottomBarHeight = staticCompositionLocalOf<Dp> { 0.dp }

/** Claves de elementos compartidos entre pantallas. */
object SharedKeys {
    fun dayCounter(epochDay: Long) = "day-counter-$epochDay"
    fun dayLabel(epochDay: Long) = "day-label-$epochDay"

    /**
     * Día abierto desde una barra o una celda de Estadísticas: la caja del gancho de la gráfica
     * (`charts/SharedHook.kt`) y el marcador del Detalle del día (contenedor compartido).
     */
    fun dayOpen(epochDay: Long) = "day-open-$epochDay"

    fun periodCount(kind: String, offset: Int) = "period-count-$kind-$offset"
    fun story(kind: String, offset: Int) = "story-$kind-$offset"
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val SharedSpring = BoundsTransform { _, _ -> spring(dampingRatio = 0.86f, stiffness = 380f) }

/**
 * Elemento compartido (mismo contenido en las dos pantallas: p. ej. la cifra de Hoy y la del
 * detalle del día). No hace nada fuera del NavHost o con «reducir animaciones».
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.reelSharedElement(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@reelSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = SharedSpring,
        )
    }
}

/**
 * Contenedor compartido (contenido distinto que se transforma: p. ej. la tira de un periodo en
 * Resumen que se abre en la historia a pantalla completa).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.reelSharedBounds(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@reelSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = SharedSpring,
        )
    }
}
