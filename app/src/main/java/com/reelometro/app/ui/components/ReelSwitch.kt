package com.reelometro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics

private val TrackWidth = 56.dp
private val TrackHeight = 28.dp
private val KnobWidth = 26.dp
private val Inset = 3.dp
private val Corner = 2.dp

/**
 * Interruptor rectangular (docs/DESIGN.md §4): pista `bgSunken` y un mando —una ficha pequeña
 * con su bisagra— que se desliza con spring `snappy` y tapa «SÍ» o «NO» (mono). Vibra al cambiar
 * (`Haptics.toggle`). Objetivo táctil de 48 dp.
 *
 * @param onCheckedChange `null` si lo cambia la fila que lo contiene (entonces solo se dibuja).
 * @param contentDescription qué activa («Aviso diario»), si no lo dice ya la fila.
 */
@Composable
fun ReelSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val haptics = rememberHaptics()

    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = Motion.orSnap(reduced, Motion.snappy()),
        label = "switch",
    )
    val knobColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.line
            checked -> colors.signal
            else -> colors.textMuted
        },
        animationSpec = Motion.orSnap(reduced, Motion.snappy()),
        label = "switchKnob",
    )
    val onLabel = if (enabled) colors.text else colors.textFaint
    val offLabel = if (enabled) colors.textMuted else colors.textFaint
    val hinge = colors.bgSunken
    val track = colors.bgSunken

    val measurer = rememberTextMeasurer(cacheSize = 4)
    val labelStyle = ReelTheme.type.label

    // Solo reserva el objetivo táctil de 48 dp si es interactivo; si lo cambia la fila, mide 28 dp.
    val interaction = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            )
    } else {
        Modifier
    }
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier

    Box(
        modifier
            .then(interaction)
            .then(described)
            .size(TrackWidth, TrackHeight)
            .drawWithCache {
                val inset = Inset.toPx()
                val knobW = KnobWidth.toPx()
                val knobH = size.height - inset * 2
                val corner = CornerRadius(Corner.toPx())
                // Recorrido del mando = hueco libre que deja a su lado (donde va la etiqueta).
                val free = size.width - inset * 2 - knobW
                val travel = free
                // Con letra muy grande, la etiqueta se reduce para no tocar el mando ni el borde.
                val maxLabel = free - inset * 2
                fun fit(text: String, color: Color): TextLayoutResult {
                    val natural = measurer.measure(text, labelStyle.copy(color = color))
                    if (natural.size.width <= maxLabel) return natural
                    val scale = maxLabel / natural.size.width
                    return measurer.measure(text, labelStyle.copy(color = color, fontSize = labelStyle.fontSize * scale))
                }
                val yes = fit("SÍ", onLabel)
                val no = fit("NO", offLabel)
                // Las etiquetas se centran en la mitad que queda libre en cada estado.
                val yesAt = Offset(inset + (free - yes.size.width) / 2f, (size.height - yes.size.height) / 2f)
                val noAt = Offset(inset + knobW + (free - no.size.width) / 2f, (size.height - no.size.height) / 2f)
                val hingeH = 1.dp.toPx()
                onDrawBehind {
                    drawRoundRect(track, cornerRadius = corner)
                    drawText(yes, topLeft = yesAt)
                    drawText(no, topLeft = noAt)
                    val x = inset + travel * position
                    drawRoundRect(knobColor, topLeft = Offset(x, inset), size = Size(knobW, knobH), cornerRadius = corner)
                    // Bisagra de la ficha: un filete del color de la pista a media altura.
                    drawRect(hinge, topLeft = Offset(x, inset + (knobH - hingeH) / 2f), size = Size(knobW, hingeH))
                }
            },
    )
}

/**
 * Fila de ajustes `Etiqueta ·········· [SÍ ▮]`: toda la fila es el interruptor (rol `Switch` para
 * TalkBack, 48 dp de alto como mínimo, se hunde al pulsar y vibra con `Haptics.toggle`).
 */
@Composable
fun SwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mark: Color? = null,
) {
    val haptics = rememberHaptics()
    ReceiptLine(
        label = label,
        mark = mark,
        modifier = modifier
            .heightIn(min = Space.touch)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            ),
    ) {
        ReelSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
