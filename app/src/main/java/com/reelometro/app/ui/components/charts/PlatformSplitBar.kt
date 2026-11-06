package com.reelometro.app.ui.components.charts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlin.math.max

/**
 * Barra única de reparto (4 dp) partida en segmentos proporcionales con huecos de 2 dp; cada
 * segmento crece desde su borde izquierdo, escalonados de izquierda a derecha. Los segmentos a 0 no
 * se dibujan; sin datos se ve solo la pista. El color nunca va solo: acompáñala de una leyenda
 * (`ReceiptLine` con el nombre de cada plataforma).
 *
 * @param segments (color, valor) en orden; usa `ReelTheme.colors.platform(...)` para las marcas.
 */
@Composable
fun PlatformSplitBar(
    segments: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    height: Dp = PlatformSplitBarDefaults.Height,
    contentDescription: String? = null,
) {
    val colors = ReelTheme.colors
    val curve = remember { SpringCurve(Motion.gentle()) }
    val visible = remember(segments) { segments.filter { it.second > 0f } }
    val stagger = staggerNanos(visible.size, Motion.STAGGER_LIST_MS * 2, MAX_STAGGER_MS)
    val clock = rememberChartClock(segments, totalNanos = stagger * max(0, visible.size - 1) + curve.durationNanos)

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .chartEntrance(clock)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .drawWithCache {
                val gap = PlatformSplitBarDefaults.Gap.toPx()
                val total = visible.sumOf { it.second.toDouble() }.toFloat()
                val available = size.width - gap * max(0, visible.size - 1)
                val lefts = FloatArray(visible.size)
                val widths = FloatArray(visible.size)
                var x = 0f
                visible.forEachIndexed { i, (_, v) ->
                    lefts[i] = x
                    widths[i] = if (total > 0f) available * v / total else 0f
                    x += widths[i] + gap
                }
                onDrawBehind {
                    if (visible.isEmpty()) {
                        drawRect(colors.bgSunken)
                        return@onDrawBehind
                    }
                    visible.forEachIndexed { i, (color, _) ->
                        val p = clock.progress(curve, stagger * i).coerceAtMost(1f)
                        if (p > 0f) drawRect(color, Offset(lefts[i], 0f), Size(widths[i] * p, size.height))
                    }
                }
            },
    )
}

object PlatformSplitBarDefaults {
    val Height: Dp = Space.x1
    val Gap: Dp = 2.dp
}
