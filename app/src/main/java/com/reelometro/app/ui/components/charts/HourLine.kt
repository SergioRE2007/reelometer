package com.reelometro.app.ui.components.charts

import com.reelometro.app.copy.str
import com.reelometro.app.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Línea de 0 a 24 h (docs/DESIGN.md §4): se **traza** de izquierda a derecha al aparecer, con un
 * área muy tenue debajo, y vuelve a trazarse si cambian los datos. Con [currentHour] (hoy) la línea
 * termina en la hora actual con un punto `signal`: lo que aún no ha pasado no se dibuja.
 *
 * Tacto: mantener el dedo y arrastrar recorre las horas con un tic y un tooltip
 * ([tooltip] recibe la hora 0–23, p. ej. "41 vídeos\n23:00–24:00"); un toque lo muestra un momento.
 *
 * @param values 24 valores, uno por hora (si hay menos, el resto cuenta como 0).
 */
@Composable
fun HourLine(
    values: List<Float>,
    tooltip: (Int) -> String,
    modifier: Modifier = Modifier,
    currentHour: Int? = null,
    height: Dp = HourLineDefaults.Height,
    contentDescription: String? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val tip = rememberChartTooltip()
    val scope = rememberCoroutineScope()
    val pending = remember { PendingJob() }

    val hours = remember(values) { FloatArray(24) { values.getOrElse(it) { 0f }.coerceAtLeast(0f) } }
    val lastHour = currentHour?.coerceIn(0, 23) ?: 23
    val trace = remember { SpringCurve(Motion.gentle()) }
    val pop = remember { SpringCurve(Motion.standard()) }
    val popDelay = trace.durationNanos * 3 / 5
    val clock = rememberChartClock(listOf(values, currentHour), totalNanos = popDelay + pop.durationNanos)

    val selection = remember { mutableIntStateOf(-1) }
    val currentTooltip by rememberUpdatedState(tooltip)
    val tipText by remember(values) {
        derivedStateOf { selection.intValue.takeIf { it in 0..23 }?.let { currentTooltip(it) } }
    }
    val description = contentDescription ?: remember(values) {
        val peak = maxIndex(hours.toList())
        if (peak < 0) str(R.string.ui_hour_line_description_empty) else str(R.string.ui_hour_line_description, spoken(tooltip(peak)))
    }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .chartEntrance(clock)
            .semantics { this.contentDescription = description }
            .chartTouch(
                key = lastHour,
                onTap = { pos, size ->
                    selection.intValue = hourAt(pos.x, size.width.toFloat(), lastHour)
                    haptics.tick()
                    pending.replace(scope.launch { delay(TAP_TOOLTIP_MS); selection.intValue = -1 })
                },
                onScrubStart = { pos, size ->
                    pending.replace(null)
                    selection.intValue = hourAt(pos.x, size.width.toFloat(), lastHour)
                    haptics.longPress()
                },
                onScrub = { pos, size ->
                    val h = hourAt(pos.x, size.width.toFloat(), lastHour)
                    if (h != selection.intValue) {
                        selection.intValue = h
                        haptics.tick()
                    }
                },
                onScrubEnd = { selection.intValue = -1 },
            )
            .drawWithCache {
                val w = size.width
                val hair = Space.hairline.toPx()
                val stroke = (Space.hairline * 2).toPx()
                val dotR = Space.x1.toPx()
                val ring = (Space.hairline * 2).toPx()
                val tick = Space.x1.toPx()
                val labelStyle = type.dataS.copy(color = colors.textMuted)
                val axisLabels = listOf(0, 6, 12, 18, 24).map { h -> h to measurer.measure(str(R.string.ui_hour_axis, Format.integer(h)), labelStyle) }
                val labelH = axisLabels.first().second.size.height.toFloat()
                val baseY = size.height - labelH - tick - Space.x1.toPx()
                val topPad = dotR + ring + stroke
                val plotH = baseY - topPad
                val maxV = hours.maxOrNull()?.takeIf { it > 0f } ?: 1f

                fun x(h: Int) = (h + 0.5f) / 24f * w
                fun y(h: Int) = baseY - plotH * (hours[h] / maxV)

                // Puntos: centro de cada franja horaria, extendidos hasta los bordes (o hasta «ahora»).
                val count = lastHour + 1 + 1 + if (currentHour == null) 1 else 0
                val px = FloatArray(count)
                val py = FloatArray(count)
                px[0] = 0f
                py[0] = y(0)
                for (h in 0..lastHour) {
                    px[h + 1] = x(h)
                    py[h + 1] = y(h)
                }
                if (currentHour == null) {
                    px[count - 1] = w
                    py[count - 1] = y(23)
                }
                val m = monotoneTangents(px, py)
                val line = Path().apply {
                    moveTo(px[0], py[0])
                    for (i in 0 until count - 1) {
                        val dx = px[i + 1] - px[i]
                        cubicTo(
                            px[i] + dx / 3f, py[i] + m[i] * dx / 3f,
                            px[i + 1] - dx / 3f, py[i + 1] - m[i + 1] * dx / 3f,
                            px[i + 1], py[i + 1],
                        )
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(px[count - 1], baseY)
                    lineTo(px[0], baseY)
                    close()
                }
                val measure = PathMeasure().apply { setPath(line, false) }
                val length = measure.length
                val segment = Path()
                val lineStroke = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

                onDrawBehind {
                    // Eje, marcas y etiquetas.
                    drawLine(colors.line, Offset(0f, baseY + hair / 2f), Offset(w, baseY + hair / 2f), hair)
                    for ((h, layout) in axisLabels) {
                        val tx = (h / 24f * w).coerceIn(hair / 2f, w - hair / 2f)
                        drawLine(colors.lineStrong, Offset(tx, baseY), Offset(tx, baseY + tick), hair)
                        val lx = when (h) {
                            0 -> 0f
                            24 -> w - layout.size.width
                            else -> tx - layout.size.width / 2f
                        }
                        drawText(layout, topLeft = Offset(lx, baseY + tick + Space.x1.toPx()))
                    }

                    // Trazo progresivo + área hasta donde ha llegado.
                    val p = clock.progress(trace)
                    if (p > 0f && length > 0f) {
                        val end = length * p
                        segment.reset()
                        measure.getSegment(0f, end, segment, true)
                        val endX = measure.getPosition(end).x
                        clipRect(right = endX) { drawPath(area, colors.text, alpha = 0.06f) }
                        drawPath(segment, colors.text, style = lineStroke)
                    }

                    if (currentHour != null) {
                        val d = clock.progress(pop, popDelay)
                        if (d > 0f) {
                            val c = Offset(x(lastHour), y(lastHour))
                            drawCircle(colors.bg, radius = (dotR + ring) * d, center = c)
                            drawCircle(colors.signal, radius = dotR * d, center = c)
                        }
                    }

                    val sel = selection.intValue
                    val text = tipText
                    if (sel in 0..lastHour && text != null) {
                        val c = Offset(x(sel), y(sel))
                        drawLine(colors.lineStrong, Offset(c.x, topPad), Offset(c.x, baseY), hair)
                        drawCircle(colors.bg, radius = dotR + ring, center = c)
                        drawCircle(if (sel == currentHour) colors.signal else colors.text, radius = dotR, center = c)
                        tip.draw(this, text, Offset(c.x, max(0f, c.y - dotR)))
                    }
                }
            },
    )
}

object HourLineDefaults {
    /** Alto total (línea + eje). */
    val Height: Dp = Space.x20 + Space.x10
}
