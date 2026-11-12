package com.reelometro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelIndication
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/** Un destino de [ReelTabBar]. [contentDescription] por defecto es la propia etiqueta. */
@Immutable
data class ReelTab(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String? = null,
)

/** Altura de la barra sin contar la barra de navegación del sistema. */
val ReelTabBarHeight = 64.dp

/**
 * Barra de pestañas inferior propia (docs/DESIGN.md §4): filete superior, icono + etiqueta
 * `labelL` por destino; el activo en `text`, el resto en `textMuted`; un indicador `signal` de
 * 2 dp sobre el filete se desliza hasta el activo y toma el ancho de su etiqueta (spring `standard`).
 * Respeta la barra de navegación del sistema con [windowInsets].
 */
@Composable
fun ReelTabBar(
    items: List<ReelTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val haptics = rememberHaptics()
    var barWidth by remember { mutableIntStateOf(0) }
    val labelWidths = remember(items.size) { mutableStateListOf(*Array(items.size) { 0f }) }
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    // Un único tamaño para todas las etiquetas: el mayor con el que caben todas en su celda. Antes cada una se
    // reducía por su cuenta y, con letra grande, «ESTADÍSTICAS» salía más pequeña que «HOY» o «RESUMEN».
    val labelStyle = ReelTheme.type.labelL
    val labels = items.map { Format.upper(it.label) }
    val measurer = rememberTextMeasurer()
    val labelPadding = with(LocalDensity.current) { (Space.x1 * 2).roundToPx() }
    val labelSize = remember(labels, barWidth, labelStyle, measurer, labelPadding) {
        sharedLabelSize(labels, labelStyle, barWidth / items.size.coerceAtLeast(1) - labelPadding, measurer)
    }

    LaunchedEffect(selectedIndex, items.size) {
        var first = w.value == 0f
        snapshotFlow { barWidth to labelWidths.getOrElse(selectedIndex) { 0f } }
            .collect { (bar, label) ->
                if (bar == 0 || label <= 0f) return@collect
                val cell = bar / items.size.toFloat()
                val left = cell * selectedIndex + (cell - label) / 2f
                if (first || reduced) {
                    x.snapTo(left)
                    w.snapTo(label)
                    first = false
                } else {
                    launch { x.animateTo(left, Motion.standard()) }
                    launch { w.animateTo(label, Motion.standard()) }
                }
            }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bg)
            .drawBehind {
                val hair = Space.hairline.toPx()
                drawRect(colors.line, size = Size(size.width, hair))
                if (w.value > 0f) {
                    drawRect(colors.signal, topLeft = Offset(x.value, 0f), size = Size(w.value, IndicatorThickness.toPx()))
                }
            }
            .windowInsetsPadding(windowInsets),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ReelTabBarHeight)
                .onSizeChanged { barWidth = it.width }
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val color by animateColorAsState(
                    targetValue = if (selected) colors.text else colors.textMuted,
                    animationSpec = Motion.orSnap(reduced, Motion.standard()),
                    label = "tabBarColor",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            interactionSource = null,
                            indication = ReelIndication,
                            role = Role.Tab,
                            onClick = {
                                if (!selected) {
                                    haptics.press()
                                    onSelect(index)
                                }
                            },
                        )
                        .semantics { contentDescription = tab.contentDescription ?: tab.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ReelIcon(tab.icon, contentDescription = null, tint = { color })
                    Spacer(Modifier.height(Space.x1))
                    BasicText(
                        text = labels[index],
                        modifier = Modifier.padding(horizontal = Space.x1),
                        style = labelStyle.copy(fontSize = labelSize),
                        color = { color },
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = {
                            val width = it.size.width.toFloat()
                            if (labelWidths[index] != width) labelWidths[index] = width
                        },
                    )
                }
            }
        }
    }
}

/** Tamaño mínimo y paso con que se reducen las etiquetas de la barra cuando no caben (letra grande). */
private val TabLabelMin = 8.sp
private const val TAB_LABEL_STEP_SP = 0.25f

/**
 * El mayor tamaño, desde el de [style] hacia abajo, con el que todas las [labels] caben en una línea de [cellPx]
 * píxeles. Sin ancho medido todavía (o con un tamaño que no es sp), el de [style].
 */
internal fun sharedLabelSize(labels: List<String>, style: TextStyle, cellPx: Int, measurer: TextMeasurer): TextUnit {
    val max = style.fontSize
    if (cellPx <= 0 || !max.isSp) return max
    var size = max.value
    while (size > TabLabelMin.value) {
        val candidate = style.copy(fontSize = size.sp)
        if (labels.all { measurer.measure(it, candidate, maxLines = 1, softWrap = false).size.width <= cellPx }) return size.sp
        size -= TAB_LABEL_STEP_SP
    }
    return TabLabelMin
}
