package com.reelometro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelIndication
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/** Grosor de los indicadores deslizantes (pestañas y barra inferior). */
internal val IndicatorThickness = 2.dp

/**
 * Selector de opciones en mono mayúsculas (`SEMANA   MES   AÑO   SIEMPRE`) con un subrayado
 * `signal` de 2 dp que **se desliza** hasta la opción activa y cambia de ancho (spring `standard`).
 * Alineado a la izquierda; ocupa todo el ancho para que el filete inferior llegue al borde.
 * Cada opción tiene 48 dp de alto; al cambiar, un tic háptico.
 *
 * @param divider filete `line` de 1 dp bajo todas las opciones.
 * @param descriptions lo que lee TalkBack en cada opción, si no basta con su texto (p. ej. «Ver los fijados de TikTok»).
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    descriptions: List<String>? = null,
) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val haptics = rememberHaptics()

    val lefts = remember(options.size) { mutableStateListOf(*Array(options.size) { -1f }) }
    val widths = remember(options.size) { mutableStateListOf(*Array(options.size) { 0f }) }
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    val scroll = rememberScrollState()

    LaunchedEffect(selectedIndex, options.size) {
        var first = w.value == 0f
        snapshotFlow { lefts.getOrElse(selectedIndex) { -1f } to widths.getOrElse(selectedIndex) { 0f } }
            .collect { (left, width) ->
                if (left < 0f || width <= 0f) return@collect
                // Con letra grande las opciones no caben: la elegida siempre queda a la vista.
                val start = scroll.value.toFloat()
                if (left < start || left + width > start + scroll.viewportSize) {
                    launch { if (first || reduced) scroll.scrollTo(left.toInt()) else scroll.animateScrollTo(left.toInt()) }
                }
                if (first || reduced) {
                    x.snapTo(left)
                    w.snapTo(width)
                    first = false
                } else {
                    launch { x.animateTo(left, Motion.standard()) }
                    launch { w.animateTo(width, Motion.standard()) }
                }
            }
    }

    // Capa exterior: filete hasta el borde y desplazamiento horizontal (letra grande, idiomas largos).
    // Fila interior: el indicador se dibuja sobre el contenido, así que lefts/positionInParent cuadran.
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (divider) {
                    val hair = Space.hairline.toPx()
                    drawRect(colors.line, topLeft = Offset(0f, size.height - hair), size = Size(size.width, hair))
                }
            }
            .horizontalScroll(scroll),
    ) {
    Row(
        Modifier
            .selectableGroup()
            .drawBehind {
                val thickness = IndicatorThickness.toPx()
                if (w.value > 0f) {
                    drawRect(
                        colors.signal,
                        topLeft = Offset(x.value, size.height - thickness),
                        size = Size(w.value, thickness),
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val color by animateColorAsState(
                targetValue = if (selected) colors.text else colors.textMuted,
                animationSpec = Motion.orSnap(reduced, Motion.standard()),
                label = "tabColor",
            )
            Box(
                Modifier
                    .selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = ReelIndication,
                        role = Role.Tab,
                        onClick = {
                            if (!selected) {
                                haptics.tick()
                                onSelect(index)
                            }
                        },
                    )
                    // Texto natural para TalkBack (no las mayúsculas mono que se ven).
                    .semantics { contentDescription = descriptions?.getOrNull(index) ?: option }
                    .widthIn(min = Space.touch)
                    .height(Space.touch)
                    .padding(end = if (index < options.lastIndex) Space.x6 else 0.dp)
                    .onPlaced {
                        val left = it.positionInParent().x
                        if (lefts[index] != left) lefts[index] = left
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(
                    text = Format.upper(option),
                    style = ReelTheme.type.labelL,
                    color = { color },
                    maxLines = 1,
                    onTextLayout = {
                        val width = it.size.width.toFloat()
                        if (widths[index] != width) widths[index] = width
                    },
                )
            }
        }
    }
    }
}
