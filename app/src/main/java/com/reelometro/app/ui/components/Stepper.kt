package com.reelometro.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Shapes
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Primer intervalo de repetición y el más rápido al que se llega (ms). */
private const val REPEAT_START_MS = 260L
private const val REPEAT_FASTEST_MS = 45L

/**
 * Selector numérico `[ − │ 150 │ + ]` (docs/DESIGN.md §4). Una pieza con borde de 1 dp y
 * separadores; cada lado es un objetivo de 48 dp que se hunde al pulsar.
 * Toque = un paso. Mantener = repite cada vez más rápido, con un tic háptico por paso.
 *
 * @param range valores permitidos; en los extremos el botón correspondiente se apaga.
 * @param format cómo se escribe la cifra (por defecto, `Format.integer`).
 * @param valueContent hueco para pintar la cifra con otro componente (p. ej. `RollingNumber`);
 *   por defecto, texto `dataL`.
 */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true,
    format: (Int) -> String = { Format.integer(it) },
    decreaseDescription: String = "Restar ${Format.integer(step)}",
    increaseDescription: String = "Sumar ${Format.integer(step)}",
    valueContent: (@Composable (value: Int) -> Unit)? = null,
) {
    val colors = ReelTheme.colors
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)

    fun stepBy(sign: Int): Boolean {
        val next = (current.toLong() + sign.toLong() * step).coerceIn(range.first.toLong(), range.last.toLong()).toInt()
        if (next == current) return false
        change(next)
        return true
    }

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .border(Space.hairline, if (enabled) colors.lineStrong else colors.line, Shapes.button),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = ReelIcons.Minus,
            description = decreaseDescription,
            enabled = enabled && value > range.first,
            onStep = { stepBy(-1) },
        )
        Divider()
        Box(
            Modifier
                .widthIn(min = 72.dp)
                .padding(horizontal = Space.x3)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            if (valueContent != null) {
                valueContent(value)
            } else {
                Text(
                    text = format(value),
                    style = ReelTheme.type.dataL,
                    color = if (enabled) colors.text else colors.textFaint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Divider()
        StepperButton(
            icon = ReelIcons.Plus,
            description = increaseDescription,
            enabled = enabled && value < range.last,
            onStep = { stepBy(1) },
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(Space.hairline)
            .fillMaxHeight()
            .background(ReelTheme.colors.line),
    )
}

/**
 * Un lado del selector. Gesto propio: toque = un paso al soltar; si se mantiene más que el
 * tiempo de pulsación larga, vibra y repite (acelerando) hasta que se levanta el dedo o se llega
 * al límite. Si el dedo se arrastra (scroll), se cancela sin cambiar nada.
 */
@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onStep: () -> Boolean,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val longPress = LocalViewConfiguration.current.longPressTimeoutMillis
    val step by rememberUpdatedState(onStep)
    val isEnabled by rememberUpdatedState(enabled)

    Box(
        modifier = Modifier
            .size(Space.touch)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick {
                    if (isEnabled && step()) haptics.press()
                    true
                }
            }
            .sinkOnPress(source)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val press = PressInteraction.Press(down.position)
                        source.tryEmit(press)
                        var repeated = false
                        val repeater = launch {
                            delay(longPress)
                            repeated = true
                            haptics.longPress()
                            var interval = REPEAT_START_MS
                            while (isEnabled && step()) {
                                haptics.tick()
                                delay(interval)
                                interval = (interval * 0.82f).toLong().coerceAtLeast(REPEAT_FASTEST_MS)
                            }
                        }
                        val up = waitForUpOrCancellation()
                        repeater.cancel()
                        if (up != null) {
                            up.consume()
                            source.tryEmit(PressInteraction.Release(press))
                            if (!repeated && isEnabled && step()) haptics.press()
                        } else {
                            source.tryEmit(PressInteraction.Cancel(press))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ReelIcon(
            icon = icon,
            contentDescription = null,
            tint = if (enabled) ReelTheme.colors.text else ReelTheme.colors.textFaint,
            size = 20.dp,
        )
    }
}
