package com.reelometro.app.ui.components.charts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Un día de la comparación semanal: lo de esta semana ([count]) frente al mismo día de la semana
 * anterior ([ghost], el «fantasma»).
 *
 * @param label etiqueta del eje («L», «M»…).
 * @param count vídeos de este día; `null` si todavía no ha llegado.
 * @param ghost vídeos del mismo día de la semana anterior; `null` si no hubo registro.
 * @param description texto para el tooltip y TalkBack («Martes: 125 vídeos; la semana pasada, 140»).
 *   Si tiene varias líneas, la primera va destacada en el tooltip y TalkBack las une con comas.
 */
@Immutable
data class GhostBar(
    val label: String,
    val count: Int?,
    val ghost: Int?,
    val description: String,
)

/**
 * Semana día a día contra la semana anterior: barras de esta semana y la **línea fantasma**
 * (discontinua, escalonada por día) de la anterior. La usa la comparativa de Estadísticas (SEMANA);
 * hasta la 1.11.x también el reto semanal del Pasaporte.
 *
 * Como `DailyBars` (docs/DESIGN.md §4): las barras **crecen escalonadas** desde la base con muelle
 * `gentle` y la línea fantasma se traza de izquierda a derecha mientras tanto; hoy va en `text` y
 * el resto recede a `lineStrong` (sin hoy, todas en `text` al 85 %). La línea fantasma va en
 * `textMuted` y corta las barras con un filete de `bg` para leerse también encima de ellas. Con
 * [ghostLabel] se rotula su leyenda arriba a la derecha («- - SEMANA ANTERIOR»), en una franja
 * propia. Con «reducir animaciones», todo aparece completo al instante.
 *
 * Tacto: **mantener el dedo** muestra el tooltip ([GhostBar.description]) sobre el día y, al
 * arrastrar, salta de día en día con un tic; un toque corto lo enseña un momento.
 *
 * @param todayIndex índice del día de hoy (se resalta), o `null`.
 * @param contentDescription resumen hablado que precede a los días (que TalkBack lee siempre, uno a uno).
 * @param ghostLabel leyenda de la línea fantasma; `null` = sin leyenda.
 */
@Composable
fun GhostWeekChart(
    bars: List<GhostBar>,
    modifier: Modifier = Modifier,
    todayIndex: Int? = null,
    height: Dp = GhostWeekChartDefaults.Height,
    contentDescription: String? = null,
    ghostLabel: String? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val tip = rememberChartTooltip()
    val scope = rememberCoroutineScope()
    val pending = remember { PendingJob() }

    val n = bars.size
    val curve = remember { SpringCurve(Motion.gentle()) }
    val stagger = staggerNanos(n, Motion.STAGGER_BAR_MS, MAX_STAGGER_MS)
    val clock = rememberChartClock(bars, totalNanos = stagger * max(0, n - 1) + curve.durationNanos)
    // La línea fantasma empieza a trazarse cuando va por la mitad el escalonado de las barras.
    val ghostDelay = stagger * max(0, n - 1) / 2

    val selection = remember { mutableIntStateOf(-1) }
    val tipText by remember(bars) { derivedStateOf { bars.getOrNull(selection.intValue)?.description } }
    val description = remember(bars, contentDescription) {
        listOfNotNull(contentDescription, bars.joinToString(". ") { spoken(it.description) }).joinToString(" ")
    }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .chartEntrance(clock)
            .semantics { this.contentDescription = description }
            .chartTouch(
                key = n,
                onTap = { pos, size ->
                    val i = indexAt(pos.x, size.width.toFloat(), n)
                    if (i >= 0) {
                        selection.intValue = i
                        haptics.tick()
                        pending.replace(scope.launch { delay(TAP_TOOLTIP_MS); selection.intValue = -1 })
                    }
                },
                onScrubStart = { pos, size ->
                    pending.replace(null)
                    selection.intValue = indexAt(pos.x, size.width.toFloat(), n)
                    haptics.tick()
                },
                onScrub = { pos, size ->
                    val i = indexAt(pos.x, size.width.toFloat(), n)
                    if (i != selection.intValue) {
                        selection.intValue = i
                        haptics.tick()
                    }
                },
                onScrubEnd = { selection.intValue = -1 },
            )
            .drawWithCache {
                val w = size.width
                val hair = Space.hairline.toPx()
                val labelStyle = type.dataS.copy(color = colors.textMuted)
                val strongLabelStyle = type.dataS.copy(color = colors.text)
                val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
                val labelGap = Space.x2.toPx()
                // Leyenda en una franja propia arriba a la derecha: junto a la línea pisaría las barras altas.
                val legend = ghostLabel?.let { measurer.measure(it, type.label.copy(color = colors.textMuted)) }
                val band = if (legend != null) legend.size.height + Space.x2.toPx() else 0f
                val baseY = size.height - labelHeight - labelGap
                val plotH = (baseY - band).coerceAtLeast(0f)
                val geom = barGeometry(w, n, maxBarPx = Space.x8.toPx(), minGapPx = 1.dp.toPx())
                val maxV = (bars.maxOfOrNull { max(it.count ?: 0, it.ghost ?: 0) } ?: 0).takeIf { it > 0 }?.toFloat() ?: 1f
                val axis = bars.mapIndexed { i, bar -> measurer.measure(bar.label, if (i == todayIndex) strongLabelStyle else labelStyle) }

                val ghostWidth = (Space.hairline * 1.5f).toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(Space.x1.toPx(), Space.x1.toPx()))
                val ghostStyle = Stroke(width = ghostWidth, pathEffect = dash)
                val cutStyle = Stroke(width = ghostWidth + 2 * hair)
                // Escalonada: un tramo por día a la altura de la semana anterior, unido con el del día
                // siguiente por un escalón en el borde de su franja; se corta donde no hay dato.
                val ghost = Path()
                var open = false
                for (i in 0 until n) {
                    val g = bars[i].ghost
                    if (g == null) {
                        open = false
                        continue
                    }
                    val y = baseY - plotH * (g / maxV)
                    val x0 = i * geom.slot
                    if (open) ghost.lineTo(x0, y) else ghost.moveTo(x0, y)
                    ghost.lineTo(x0 + geom.slot, y)
                    open = true
                }

                val restColor = if (todayIndex != null) colors.lineStrong else colors.text.copy(alpha = 0.85f)
                val legendX = if (legend != null) w - legend.size.width else 0f
                val legendMid = (legend?.size?.height ?: 0) / 2f
                val legendSample = Space.x5.toPx()
                val legendGap = Space.x2.toPx()

                onDrawBehind {
                    val sel = selection.intValue
                    for (i in 0 until n) {
                        val v = bars[i].count ?: continue
                        if (v <= 0) continue
                        val p = clock.progress(curve, stagger * i)
                        if (p <= 0f) continue
                        val h = plotH * (v / maxV) * p
                        drawRect(
                            if (i == todayIndex) colors.text else restColor,
                            topLeft = Offset(geom.left(i), baseY - h),
                            size = Size(geom.barWidth, h),
                            alpha = if (sel >= 0 && sel != i) DIM_ALPHA else 1f,
                        )
                    }
                    drawLine(colors.line, Offset(0f, baseY + hair / 2f), Offset(w, baseY + hair / 2f), hair)

                    val pg = clock.progress(curve, ghostDelay)
                    if (pg > 0f) {
                        clipRect(right = w * pg) {
                            drawPath(ghost, colors.bg, style = cutStyle)
                            drawPath(ghost, colors.textMuted, style = ghostStyle)
                        }
                        if (legend != null) {
                            drawLine(
                                colors.textMuted,
                                Offset(legendX - legendGap - legendSample, legendMid),
                                Offset(legendX - legendGap, legendMid),
                                ghostWidth,
                                pathEffect = dash,
                                alpha = pg.coerceIn(0f, 1f),
                            )
                            drawText(legend, topLeft = Offset(legendX, 0f), alpha = pg.coerceIn(0f, 1f))
                        }
                    }

                    axis.forEachIndexed { i, layout ->
                        val x = (geom.center(i) - layout.size.width / 2f).coerceIn(0f, max(0f, w - layout.size.width))
                        drawText(layout, topLeft = Offset(x, baseY + labelGap))
                    }

                    val text = tipText
                    if (sel in 0 until n && text != null) {
                        val bar = bars[sel]
                        val barTop = baseY - plotH * ((bar.count ?: 0) / maxV) * clock.progress(curve, stagger * sel)
                        val ghostTop = bar.ghost?.let { baseY - plotH * (it / maxV) } ?: baseY
                        tip.draw(this, text, Offset(geom.center(sel), min(barTop, ghostTop)))
                    }
                }
            },
    )
}

object GhostWeekChartDefaults {
    /** Alto total (leyenda + barras + eje), el mismo que `DailyBars`. */
    val Height: Dp = Space.x20 * 2
}
