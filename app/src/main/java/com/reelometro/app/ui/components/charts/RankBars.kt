package com.reelometro.app.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Shapes
import com.reelometro.app.ui.theme.Space
import kotlin.math.max

/** Una fila del ranking. [marker]: punto de color opcional (p. ej. la plataforma). */
@Immutable
data class RankItem(
    val label: String,
    val value: Float,
    val valueLabel: String,
    val marker: Color? = null,
)

/**
 * Ranking en barras horizontales finas (6 dp) sobre su pista (`bgSunken`), con etiqueta y valor en
 * mono. Las barras crecen escalonadas (30 ms por fila, muelle `gentle`) y vuelven a crecer si cambian
 * los datos. Cada fila se lee como una unidad con TalkBack («Humor, 120 vídeos»).
 *
 * @param barColor color de las barras (`text` por defecto).
 * @param contentDescription resumen opcional del conjunto (las filas ya se leen una a una).
 */
@Composable
fun RankBars(
    items: List<RankItem>,
    modifier: Modifier = Modifier,
    barColor: Color = ReelTheme.colors.text,
    contentDescription: String? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val curve = remember { SpringCurve(Motion.gentle()) }
    val stagger = staggerNanos(items.size, Motion.STAGGER_LIST_MS, MAX_STAGGER_MS)
    val clock = rememberChartClock(items, totalNanos = stagger * max(0, items.size - 1) + curve.durationNanos)
    val maxValue = remember(items) { items.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f }

    Column(
        modifier
            .fillMaxWidth()
            .chartEntrance(clock)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        items.forEachIndexed { i, item ->
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { this.contentDescription = "${item.label}, ${item.valueLabel}" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.marker != null) {
                        Box(Modifier.size(MarkerSize).background(item.marker, Shapes.dot))
                        Spacer(Modifier.width(Space.x2))
                    }
                    Text(
                        item.label,
                        style = type.data,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        item.valueLabel,
                        style = type.data,
                        color = colors.textMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = Space.x3),
                    )
                }
                Spacer(Modifier.height(Space.x2))
                val fraction = (item.value / maxValue).coerceIn(0f, 1f)
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(BarHeight)
                        .drawBehind {
                            drawRect(colors.bgSunken)
                            val p = clock.progress(curve, stagger * i).coerceAtMost(1f)
                            if (p > 0f && fraction > 0f) {
                                drawRect(barColor, Offset.Zero, Size(size.width * fraction * p, size.height))
                            }
                        },
                )
            }
        }
    }
}

private val BarHeight = Space.x1 + 2.dp
private val MarkerSize = Space.x1 + 2.dp
