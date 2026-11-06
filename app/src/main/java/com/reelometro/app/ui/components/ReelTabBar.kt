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
import androidx.compose.foundation.text.TextAutoSize
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
                    val labelStyle = ReelTheme.type.labelL
                    BasicText(
                        text = Format.upper(tab.label),
                        modifier = Modifier.padding(horizontal = Space.x1),
                        style = labelStyle,
                        color = { color },
                        maxLines = 1,
                        // Con letra grande, una etiqueta larga («ESTADÍSTICAS») se reduce en vez de cortarse.
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = labelStyle.fontSize, stepSize = 0.25.sp),
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
