package com.reelometro.app.ui.theme

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Respuesta al tacto (docs/DESIGN.md §4 y §6): en lugar del ripple de Material, lo pulsado
 * **se hunde** — escala 0,96 (como mucho 8 dp en total en piezas muy anchas) y baja 1 dp con
 * spring `snappy` — y vuelve al soltar.
 * ReelTheme la instala como LocalIndication, así que cualquier clickable/selectable/toggleable
 * de la app responde igual. Las lecturas de la animación ocurren solo en la fase de dibujo.
 */

/** El elemento se hunde mientras se pulsa. Es la indicación por defecto de la app. */
val ReelIndication: Indication = SinkIndication(haptic = false)

/** Igual que [ReelIndication] y, además, vibra con `Haptics.press()` al soltar (pulsación completada). */
val ReelHapticIndication: Indication = SinkIndication(haptic = true)

/** Escala al pulsar y desplazamiento hacia abajo. */
private const val SINK_SCALE = 0.96f
private val SINK_OFFSET = 1.dp

/**
 * Encogimiento máximo en total: hasta 200 dp de ancho se aplica el 0,96 exacto; en piezas más
 * anchas (filas, botones a todo el ancho) se limita para que no «encojan» 15 dp de golpe.
 */
private val MAX_SHRINK = 8.dp

/** Tiempo mínimo hundido, para que un toque rápido también se vea. */
private const val MIN_PRESS_MS = 90L

private class SinkIndication(private val haptic: Boolean) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        SinkNode(interactionSource, haptic)

    override fun equals(other: Any?): Boolean = other is SinkIndication && other.haptic == haptic
    override fun hashCode(): Int = haptic.hashCode()
}

private class SinkNode(
    private val interactionSource: InteractionSource,
    private val haptic: Boolean,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    /** 0 = en reposo, 1 = hundido del todo. */
    private val progress = Animatable(0f)
    private var pressedAt = 0L
    private var job: Job? = null

    override val shouldAutoInvalidate: Boolean get() = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> press()
                    is PressInteraction.Release -> {
                        if (haptic) Haptics(currentValueOf(LocalView)).press()
                        release()
                    }
                    is PressInteraction.Cancel -> release()
                }
            }
        }
    }

    private fun press() {
        pressedAt = SystemClock.uptimeMillis()
        val reduced = currentValueOf(LocalReducedMotion)
        job?.cancel()
        job = coroutineScope.launch {
            progress.animateTo(1f, Motion.orSnap(reduced, Motion.snappy()))
        }
    }

    private fun release() {
        val reduced = currentValueOf(LocalReducedMotion)
        val held = SystemClock.uptimeMillis() - pressedAt
        val previous = job
        job = coroutineScope.launch {
            // Deja que la pulsación llegue a verse: mientras, la animación de bajada sigue.
            if (held < MIN_PRESS_MS) delay(MIN_PRESS_MS - held)
            previous?.cancel()
            progress.animateTo(0f, Motion.orSnap(reduced, Motion.snappy()))
        }
    }

    override fun ContentDrawScope.draw() {
        val p = progress.value
        if (p == 0f) {
            drawContent()
            return
        }
        val widest = maxOf(size.width, size.height)
        val shrink = minOf(1f - SINK_SCALE, if (widest > 0f) MAX_SHRINK.toPx() / widest else 0f)
        val s = 1f - shrink * p
        translate(top = SINK_OFFSET.toPx() * p) {
            scale(s, s) { this@draw.drawContent() }
        }
    }

    override fun onDetach() {
        job = null
    }
}
