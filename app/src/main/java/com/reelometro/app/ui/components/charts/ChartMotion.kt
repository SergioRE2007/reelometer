package com.reelometro.app.ui.components.charts

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import com.reelometro.app.ui.theme.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlin.math.abs

/*
 * Animación y tacto compartidos por las gráficas.
 *
 * Animación: un único reloj por gráfica (un estado Long que avanza cada fotograma y solo se lee al
 * dibujar). Cada barra/celda evalúa el muelle de forma analítica en «reloj − su retraso», así que
 * 365 barras escalonadas cuestan lo mismo que una: ni recomposiciones ni 365 corrutinas.
 */

/** Muelle evaluable en un instante: 0 → 1. */
@Stable
internal class SpringCurve(spec: SpringSpec<Float>) {
    private val spring = FloatSpringSpec(spec.dampingRatio, spec.stiffness, 0.0005f)
    val durationNanos: Long = spring.getDurationNanos(0f, 1f, 0f)

    fun at(nanos: Long): Float = when {
        nanos <= 0L -> 0f
        nanos >= durationNanos -> 1f
        else -> spring.getValueFromNanos(nanos, 0f, 1f, 0f)
    }
}

/** Si la gráfica ya ha asomado en pantalla (no se reinicia al cambiar los datos). */
@Stable
internal class ChartSeen {
    var value: Boolean by mutableStateOf(false)
}

/** Reloj de entrada de una gráfica. [nanos] solo debe leerse en la fase de dibujo. */
@Stable
internal class ChartClock(start: Long, internal val seen: ChartSeen) {
    var nanos: Long by mutableLongStateOf(start)

    fun progress(curve: SpringCurve, delayNanos: Long = 0L): Float = curve.at(nanos - delayNanos)

    companion object {
        /** Animación terminada (para «reducir animaciones»). */
        const val DONE = Long.MAX_VALUE / 4
    }
}

/**
 * Reloj que arranca de cero cada vez que cambia [key] (p. ej. los datos al cambiar de periodo) y
 * se para a los [totalNanos]. La primera vez espera a que la gráfica asome en pantalla (ver
 * [chartEntrance]): debajo del pliegue no se anima a ciegas. Con «reducir animaciones» queda
 * terminado desde el principio.
 */
@Composable
internal fun rememberChartClock(key: Any?, totalNanos: Long): ChartClock {
    val instant = LocalReducedMotion.current || LocalInspectionMode.current
    val seen = remember { ChartSeen() }
    val clock = remember(key) { ChartClock(if (instant) ChartClock.DONE else 0L, seen) }
    val visible = seen.value
    LaunchedEffect(clock, instant, visible) {
        if (instant) {
            clock.nanos = ChartClock.DONE
            return@LaunchedEffect
        }
        if (!visible) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (clock.nanos < totalNanos) {
            val now = withFrameNanos { it }
            clock.nanos = now - start
        }
        clock.nanos = ChartClock.DONE
    }
    return clock
}

/** Marca la gráfica como vista cuando asoma al menos una cuarta parte; arranca su entrada. */
internal fun Modifier.chartEntrance(clock: ChartClock): Modifier =
    onVisibilityChanged(minFractionVisible = 0.25f) { visible -> if (visible) clock.seen.value = true }

/** Hacia dónde va un arrastre una vez pasado el umbral. */
internal enum class DragAxis { Horizontal, Vertical }

/**
 * Decide el eje de un arrastre de ([dx], [dy]) desde el punto de apoyo: `null` mientras no pase de
 * [slop]; después, horizontal si domina el movimiento en x (empate = vertical: ante la duda, la
 * página sigue desplazándose).
 */
internal fun dragAxis(dx: Float, dy: Float, slop: Float): DragAxis? = when {
    dx * dx + dy * dy < slop * slop -> null
    abs(dx) > abs(dy) -> DragAxis.Horizontal
    else -> DragAxis.Vertical
}

/** Cómo empieza un gesto sobre la gráfica. */
private sealed interface ChartGesture {
    /** Soltó sin moverse: un toque. */
    data class Tap(val position: Offset) : ChartGesture
    /** Arrastre horizontal o dedo quieto el tiempo de una pulsación larga: se recorre. */
    data class Scrub(val position: Offset) : ChartGesture
    /** Arrastre vertical, otro dueño del gesto o dedo perdido: la gráfica no hace nada. */
    data object Ignore : ChartGesture
}

/**
 * Tacto de las gráficas: un toque llama a [onTap]; **deslizar en horizontal** (o mantener el dedo
 * quieto) empieza a recorrer ([onScrubStart]) y cada movimiento llama a [onScrub] hasta soltar
 * ([onScrubEnd]). La dirección se decide con el primer movimiento que pasa del `touchSlop`: si es
 * vertical, la gráfica suelta el gesto y la página sigue desplazándose.
 */
internal fun Modifier.chartTouch(
    key: Any?,
    onTap: ((Offset, IntSize) -> Unit)?,
    onScrubStart: (Offset, IntSize) -> Unit,
    onScrub: (Offset, IntSize) -> Unit,
    onScrubEnd: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val start: ChartGesture = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result: ChartGesture? = null
            while (result == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                result = when {
                    change == null || change.isConsumed -> ChartGesture.Ignore
                    !change.pressed -> ChartGesture.Tap(change.position).also { change.consume() }
                    else -> when (dragAxis(change.position.x - down.position.x, change.position.y - down.position.y, slop)) {
                        DragAxis.Horizontal -> ChartGesture.Scrub(change.position).also { change.consume() }
                        DragAxis.Vertical -> ChartGesture.Ignore
                        null -> null
                    }
                }
            }
            result
        } ?: ChartGesture.Scrub(currentEvent.changes.firstOrNull { it.id == down.id }?.position ?: down.position)

        when (start) {
            is ChartGesture.Tap -> onTap?.invoke(start.position, size)
            ChartGesture.Ignore -> Unit
            is ChartGesture.Scrub -> {
                onScrubStart(start.position, size)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.positionChange() != Offset.Zero) onScrub(change.position, size)
                        change.consume()
                    }
                } finally {
                    onScrubEnd()
                }
            }
        }
    }
}

/** Trabajo pendiente (p. ej. ocultar el tooltip de un toque) que se cancela al reemplazarlo. */
internal class PendingJob {
    var job: Job? = null

    fun replace(next: Job?) {
        job?.cancel()
        job = next
    }
}

/** Cuánto se ve el tooltip tras un toque corto. */
internal const val TAP_TOOLTIP_MS = 1600L

/** Atenuación de las barras/celdas no seleccionadas mientras se recorre. */
internal const val DIM_ALPHA = 0.35f

/** Tope del escalonado total de entrada (las 365 barras no pueden tardar 9 s). */
internal const val MAX_STAGGER_MS = 360L

/** Resumen hablado a partir de un tooltip de varias líneas: «212 vídeos, martes 3 nov». */
internal fun spoken(tooltip: String): String = tooltip.lines().filter { it.isNotBlank() }.joinToString(", ")
