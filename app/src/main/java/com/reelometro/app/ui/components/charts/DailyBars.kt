package com.reelometro.app.ui.components.charts

import com.reelometro.app.copy.str
import com.reelometro.app.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Barras por día (docs/DESIGN.md §4). Crecen escalonadas desde la base con muelle `gentle` (y
 * vuelven a crecer cuando cambian los datos, p. ej. al cambiar de periodo). La barra máxima va en
 * `signal`; con [highlightIndex] (p. ej. hoy) esa barra va en `text` y el resto recede a
 * `lineStrong`; sin él, el resto va en `text` al 85 %. La [average] se dibuja como línea discontinua
 * y se rotula como leyenda arriba a la derecha («- - MEDIA 176»), en una franja propia para no
 * pisar nunca las barras. El grosor se adapta a 7, 31 o 365 barras.
 *
 * Tacto: **mantener el dedo** muestra un tooltip ([tooltip], p. ej. "212 vídeos\nmartes 3 nov") sobre
 * la barra; al arrastrar salta de barra en barra con un tic; al soltar se oculta. Un toque corto
 * muestra el tooltip un momento y llama a [onBarClick].
 *
 * @param labels etiqueta del eje de cada barra; las vacías no se dibujan. Si todas tienen texto,
 * se muestran las que quepan con paso uniforme.
 * @param averageLabel texto de la media (por defecto «MEDIA 172»).
 * @param contentDescription resumen hablado (por defecto, el máximo a partir de [tooltip]).
 * @param barActions acciones de TalkBack, una por barra («Abrir el martes 3 de noviembre»; `null`
 *   = sin acción): llaman a [onBarClick] como un toque. Pocas (semana, mes), no 365.
 * @param sharedBar gancho de elementos compartidos (ver `SharedHook.kt`): el modificador que se pone
 *   a una caja transparente sobre la última barra tocada (p. ej. `reelSharedBounds(...)`), para que
 *   la barra «se abra» en el detalle del día.
 */
@Composable
fun DailyBars(
    values: List<Float>,
    labels: List<String>,
    tooltip: (Int) -> String,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    average: Float? = null,
    averageLabel: String? = null,
    onBarClick: ((Int) -> Unit)? = null,
    height: Dp = DailyBarsDefaults.Height,
    contentDescription: String? = null,
    barActions: List<String?> = emptyList(),
    sharedBar: (@Composable (index: Int) -> Modifier)? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val tip = rememberChartTooltip()
    val scope = rememberCoroutineScope()
    val pending = remember { PendingJob() }

    val n = values.size
    val curve = remember { SpringCurve(Motion.gentle()) }
    val stagger = staggerNanos(n, Motion.STAGGER_BAR_MS, MAX_STAGGER_MS)
    val clock = rememberChartClock(values, totalNanos = stagger * max(0, n - 1) + curve.durationNanos)
    val averageDelay = stagger * max(0, n - 1) / 2

    val selection = remember { mutableIntStateOf(-1) }
    // Última barra abierta: sobrevive a ir al día y volver (allí espera la transición de vuelta).
    val opened = rememberSaveable { mutableIntStateOf(-1) }
    val top = remember(values) { maxIndex(values) }
    val currentTooltip by rememberUpdatedState(tooltip)
    val currentClick by rememberUpdatedState(onBarClick)
    val tipText by remember(values) {
        derivedStateOf { selection.intValue.takeIf { it in values.indices }?.let { currentTooltip(it) } }
    }
    val description = contentDescription ?: remember(values) {
        if (top < 0) str(R.string.ui_daily_bars_description_empty) else str(R.string.ui_daily_bars_description, spoken(tooltip(top)))
    }
    val avgText = averageLabel ?: average?.let { str(R.string.ui_daily_bars_average, Format.integer(it.roundToInt())) }

    /** La barra [i] ya crecida, como la dibuja `drawWithCache` (mismas cuentas): origen del gancho. */
    fun Density.barRect(i: Int, w: Float, h: Float): Rect {
        val labelHeight = measurer.measure("0", type.dataS).size.height.toFloat()
        val band = if (average != null && avgText != null) measurer.measure(avgText, type.label).size.height + Space.x2.toPx() else 0f
        val baseY = h - labelHeight - Space.x2.toPx()
        val plotH = baseY - band
        val geom = barGeometry(w, n, maxBarPx = Space.x8.toPx(), minGapPx = 1.dp.toPx())
        val maxV = max(values.maxOrNull() ?: 0f, average ?: 0f).takeIf { it > 0f } ?: 1f
        val barH = max(plotH * (values[i] / maxV), Space.x1.toPx())
        return Rect(geom.left(i), baseY - barH, geom.left(i) + geom.barWidth, baseY)
    }

    val actions = remember(barActions) {
        barActions.mapIndexedNotNull { i, label ->
            label?.let {
                CustomAccessibilityAction(it) {
                    val click = currentClick
                    if (click != null) {
                        opened.intValue = i
                        click(i)
                    }
                    click != null
                }
            }
        }
    }

    Box(modifier.fillMaxWidth().height(height)) {
        Spacer(
            Modifier
                .matchParentSize()
                .chartEntrance(clock)
                .semantics {
                    this.contentDescription = description
                    if (actions.isNotEmpty()) customActions = actions
                }
                .chartTouch(
                    key = n,
                    onTap = { pos, size ->
                        val i = indexAt(pos.x, size.width.toFloat(), n)
                        if (i >= 0) {
                            selection.intValue = i
                            haptics.tick()
                            if (currentClick != null) opened.intValue = i
                            currentClick?.invoke(i)
                            pending.replace(scope.launch { delay(TAP_TOOLTIP_MS); selection.intValue = -1 })
                        }
                    },
                    onScrubStart = { pos, size ->
                        pending.replace(null)
                        selection.intValue = indexAt(pos.x, size.width.toFloat(), n)
                        haptics.longPress()
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
                    // La media se rotula como leyenda en una franja propia arriba a la derecha
                    // («- - MEDIA 176»): junto a la línea pisaría las barras altas.
                    val avgLayout = if (average != null) avgText?.let { measurer.measure(it, type.label.copy(color = colors.textMuted)) } else null
                    val band = if (avgLayout != null) avgLayout.size.height + Space.x2.toPx() else 0f
                    val baseY = size.height - labelHeight - labelGap
                    val plotH = baseY - band
                    val geom = barGeometry(w, n, maxBarPx = Space.x8.toPx(), minGapPx = 1.dp.toPx())
                    val maxV = max(values.maxOrNull() ?: 0f, average ?: 0f).takeIf { it > 0f } ?: 1f

                    // Etiquetas del eje que caben.
                    val centers = FloatArray(n) { geom.center(it) }
                    val allLabelled = labels.size >= n && (0 until n).all { labels[it].isNotEmpty() }
                    val widths = if (allLabelled) {
                        val longest = (0 until n).maxByOrNull { labels[it].length } ?: 0
                        val lw = measurer.measure(labels[longest], labelStyle).size.width.toFloat()
                        FloatArray(n) { lw }
                    } else {
                        FloatArray(n) { i ->
                            val s = labels.getOrNull(i).orEmpty()
                            if (s.isEmpty()) 0f else measurer.measure(s, labelStyle).size.width.toFloat()
                        }
                    }
                    val axisLabels = visibleLabels(centers, widths, w, minGap = Space.x2.toPx()).map { i ->
                        i to measurer.measure(labels[i], if (i == highlightIndex) strongLabelStyle else labelStyle)
                    }

                    val dash = PathEffect.dashPathEffect(floatArrayOf(Space.x1.toPx(), Space.x1.toPx()))
                    val avgY = average?.let { baseY - plotH * (it / maxV) }
                    val legendX = if (avgLayout != null) w - avgLayout.size.width else 0f
                    val legendMid = (avgLayout?.size?.height ?: 0) / 2f
                    val legendSample = Space.x5.toPx()
                    val legendGap = Space.x2.toPx()
                    val restColor = if (highlightIndex != null) colors.lineStrong else colors.text.copy(alpha = 0.85f)
                    fun barColor(i: Int): Color = when (i) {
                        top -> colors.signal
                        highlightIndex -> colors.text
                        else -> restColor
                    }

                    onDrawBehind {
                        val sel = selection.intValue
                        for (i in 0 until n) {
                            val v = values[i]
                            if (v <= 0f) continue
                            val p = clock.progress(curve, stagger * i)
                            if (p <= 0f) continue
                            val h = plotH * (v / maxV) * p
                            drawRect(
                                barColor(i),
                                topLeft = Offset(geom.left(i), baseY - h),
                                size = Size(geom.barWidth, h),
                                alpha = if (sel >= 0 && sel != i) DIM_ALPHA else 1f,
                            )
                        }
                        drawLine(colors.line, Offset(0f, baseY + hair / 2f), Offset(w, baseY + hair / 2f), hair)

                        if (avgY != null) {
                            val pa = clock.progress(curve, averageDelay)
                            if (pa > 0f) {
                                drawLine(colors.textMuted, Offset(0f, avgY), Offset(w * pa, avgY), hair, pathEffect = dash)
                                if (avgLayout != null) {
                                    drawLine(
                                        colors.textMuted,
                                        Offset(legendX - legendGap - legendSample, legendMid),
                                        Offset(legendX - legendGap, legendMid),
                                        hair,
                                        pathEffect = dash,
                                        alpha = pa,
                                    )
                                    drawText(avgLayout, topLeft = Offset(legendX, 0f), alpha = pa)
                                }
                            }
                        }

                        for ((i, layout) in axisLabels) {
                            val x = (centers[i] - layout.size.width / 2f).coerceIn(0f, max(0f, w - layout.size.width))
                            drawText(layout, topLeft = Offset(x, baseY + labelGap))
                        }

                        val text = tipText
                        if (sel in 0 until n && text != null) {
                            val h = plotH * (values[sel] / maxV) * clock.progress(curve, stagger * sel)
                            tip.draw(this, text, Offset(geom.center(sel), baseY - h))
                        }
                    }
                },
        )
        if (sharedBar != null) {
            val i = opened.intValue
            if (i in 0 until n && values[i] >= 0f) {
                key(i) {
                    Box(
                        Modifier
                            .placeAt { size -> barRect(i, size.width.toFloat(), size.height.toFloat()) }
                            .then(sharedBar(i)),
                    )
                }
            }
        }
    }
}

object DailyBarsDefaults {
    /** Alto total (barras + eje). */
    val Height: Dp = Space.x20 * 2
}
