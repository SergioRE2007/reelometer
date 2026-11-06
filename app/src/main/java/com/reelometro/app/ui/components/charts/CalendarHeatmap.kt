package com.reelometro.app.ui.components.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Un día del mapa de calor. */
@Immutable
data class HeatDay(val date: LocalDate, val value: Float)

/**
 * Mapa de calor del calendario (docs/DESIGN.md §4), en 5 pasos de `ReelTheme.colors.heat(step)`
 * repartidos por cuantiles de los días con actividad. Modos: [HeatmapMode.WEEK] (una fila L…D),
 * [HeatmapMode.MONTH] (calendario con el número de cada día) y [HeatmapMode.YEAR] (estilo
 * contribuciones: semanas en columnas, L…D en filas, meses arriba). Las celdas son cuadradas y
 * encajan en el ancho; aparecen escalonadas por columnas. Los días futuros se dibujan en contorno
 * (semana/mes) o no se dibujan (año); hoy lleva un filete.
 *
 * El rango sale del primer día de [days] (su semana, su mes o su año natural); los días sin dato
 * cuentan como 0.
 *
 * Tacto: tocar un día muestra su tooltip, da un tic y llama a [onDayClick]; mantener y arrastrar
 * recorre los días con tooltip y tic.
 *
 * @param sharedDay gancho de elementos compartidos (ver `SharedHook.kt`): el modificador que se pone
 *   a una caja transparente sobre el último día tocado (p. ej. `reelSharedBounds(...)`), para que la
 *   celda «se abra» en el detalle del día.
 */
@Composable
fun CalendarHeatmap(
    days: List<HeatDay>,
    mode: HeatmapMode,
    tooltip: (HeatDay) -> String,
    modifier: Modifier = Modifier,
    today: LocalDate = remember { LocalDate.now() },
    onDayClick: ((LocalDate) -> Unit)? = null,
    contentDescription: String? = null,
    sharedDay: (@Composable (date: LocalDate) -> Modifier)? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val tip = rememberChartTooltip()
    val scope = rememberCoroutineScope()
    val pending = remember { PendingJob() }

    val anchor = days.firstOrNull()?.date ?: today
    val grid = remember(mode, anchor) { heatGrid(mode, anchor) }
    val valueOf = remember(days) { days.associate { it.date to it.value } }
    val steps = remember(grid, valueOf, today) {
        val thresholds = heatThresholds(grid.cells.filter { !it.date.isAfter(today) }.map { valueOf[it.date] ?: 0f })
        IntArray(grid.cells.size) { i -> heatStep(valueOf[grid.cells[i].date] ?: 0f, thresholds) }
    }

    val curve = remember { SpringCurve(Motion.gentle()) }
    val stagger = staggerNanos(grid.cols, Motion.STAGGER_BAR_MS, MAX_STAGGER_MS)
    val clock = rememberChartClock(listOf(mode, anchor, days), totalNanos = stagger * max(0, grid.cols - 1) + curve.durationNanos)

    // Geometría calculada al medir (depende del ancho) y leída al dibujar.
    var geometry by remember { mutableStateOf<HeatGeometry?>(null) }
    val selection = remember { mutableIntStateOf(-1) }
    // Último día abierto (epochDay): sobrevive a ir al día y volver (allí espera la transición de vuelta).
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    val currentTooltip by rememberUpdatedState(tooltip)
    val currentClick by rememberUpdatedState(onDayClick)
    val tipText by remember(grid, valueOf) {
        derivedStateOf {
            grid.cells.getOrNull(selection.intValue)?.let { currentTooltip(HeatDay(it.date, valueOf[it.date] ?: 0f)) }
        }
    }
    val description = contentDescription ?: remember(grid, valueOf, today) {
        val best = grid.cells.filter { !it.date.isAfter(today) }.maxByOrNull { valueOf[it.date] ?: 0f }
        val bestValue = best?.let { valueOf[it.date] ?: 0f } ?: 0f
        if (best == null || bestValue <= 0f) "Calendario: sin datos"
        else "Calendario. Día más alto: ${spoken(tooltip(HeatDay(best.date, bestValue)))}"
    }

    val labelStyle = type.label.copy(color = colors.textMuted)
    val dayStyle = type.dataS

    fun cellIndexAt(pos: Offset): Int {
        val g = geometry ?: return -1
        val (col, row) = cellAt(pos.x - g.originX, pos.y - g.originY, g.cell, g.gap, grid.cols, grid.rows) ?: return -1
        val cell = grid.cellAt(col, row) ?: return -1
        if (cell.date.isAfter(today)) return -1
        return grid.cells.indexOf(cell)
    }

    Box(modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .chartEntrance(clock)
                .semantics { this.contentDescription = description }
                .layout { measurable, constraints ->
                    val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 360.dp.toPx()
                    val g = heatGeometry(
                        mode = mode,
                        grid = grid,
                        width = width,
                        labelHeight = measurer.measure("L", labelStyle).size.height.toFloat(),
                        gutter = if (mode == HeatmapMode.YEAR) {
                            DayOfWeek.entries.maxOf { measurer.measure(Format.weekdayInitial(it), labelStyle).size.width }.toFloat() +
                                Space.x2.toPx()
                        } else {
                            0f
                        },
                        labelGap = if (mode == HeatmapMode.YEAR) Space.x1.toPx() else Space.x2.toPx(),
                        gapSmall = 2.dp.toPx(),
                        gapLarge = 3.dp.toPx(),
                        smallCell = Space.x3.toPx(),
                        maxCell = Space.x12.toPx() + Space.x2.toPx(),
                    )
                    geometry = g
                    val h = ceil(g.height).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
                    val w = width.toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .chartTouch(
                    key = grid,
                    onTap = { pos, _ ->
                        val i = cellIndexAt(pos)
                        if (i >= 0) {
                            selection.intValue = i
                            haptics.tick()
                            if (currentClick != null) opened = grid.cells[i].date.toEpochDay()
                            currentClick?.invoke(grid.cells[i].date)
                            pending.replace(scope.launch { delay(TAP_TOOLTIP_MS); selection.intValue = -1 })
                        }
                    },
                    onScrubStart = { pos, _ ->
                        pending.replace(null)
                        selection.intValue = cellIndexAt(pos)
                        haptics.longPress()
                    },
                    onScrub = { pos, _ ->
                        val i = cellIndexAt(pos)
                        if (i >= 0 && i != selection.intValue) {
                            selection.intValue = i
                            haptics.tick()
                        }
                    },
                    onScrubEnd = { selection.intValue = -1 },
                )
                .drawWithCache {
                    val g = geometry ?: return@drawWithCache onDrawBehind { }
                    val pitch = g.cell + g.gap
                    val radius = min(2.dp.toPx(), g.cell * 0.15f).let { CornerRadius(it, it) }
                    val hair = Space.hairline.toPx()
                    val outline = Stroke(width = hair)
                    val todayStroke = Stroke(width = if (mode == HeatmapMode.YEAR) hair else hair * 1.5f)
                    val selectedStroke = Stroke(width = hair * 2f)

                    // Etiquetas: meses (año) o iniciales de los días (semana/mes) arriba; iniciales a la izquierda (año).
                    val labels = ArrayList<Pair<Offset, TextLayoutResult>>()
                    if (mode == HeatmapMode.YEAR) {
                        val monthLayouts = grid.monthStarts.map { (col, m) -> col to measurer.measure(Format.upper(Format.monthShort(m)), labelStyle) }
                        val widest = monthLayouts.maxOfOrNull { it.second.size.width }?.toFloat() ?: 0f
                        val minSpacing = monthLayouts.zipWithNext { a, b -> (b.first - a.first) * pitch }.minOrNull() ?: Float.MAX_VALUE
                        val step = uniformStep(minSpacing, widest, Space.x1.toPx(), intArrayOf(1, 2, 3, 6))
                        monthLayouts.forEachIndexed { i, (col, layout) ->
                            val x = g.originX + col * pitch
                            if (i % step == 0 && x + layout.size.width <= size.width) labels += Offset(x, 0f) to layout
                        }
                        val labelH = monthLayouts.firstOrNull()?.second?.size?.height?.toFloat() ?: 0f
                        val rowStep = rowLabelStep(pitch, labelH * 0.8f)
                        DayOfWeek.entries.forEachIndexed { row, d ->
                            if (row % rowStep != 0) return@forEachIndexed
                            val layout = measurer.measure(Format.weekdayInitial(d), labelStyle)
                            val cy = g.originY + row * pitch + g.cell / 2f
                            labels += Offset(0f, cy - layout.size.height / 2f) to layout
                        }
                    } else {
                        DayOfWeek.entries.forEachIndexed { col, d ->
                            val layout = measurer.measure(Format.weekdayInitial(d), labelStyle)
                            val cx = g.originX + col * pitch + g.cell / 2f
                            labels += Offset(cx - layout.size.width / 2f, 0f) to layout
                        }
                    }

                    // Números de día (semana y mes, si caben).
                    val showNumbers = mode != HeatmapMode.YEAR && g.cell >= Space.x8.toPx()
                    val numbers = if (showNumbers) Array(31) { measurer.measure((it + 1).toString(), dayStyle) } else emptyArray()
                    val numberPad = Space.x1.toPx() + hair

                    onDrawBehind {
                        for ((offset, layout) in labels) drawText(layout, topLeft = offset)
                        val sel = selection.intValue
                        grid.cells.forEachIndexed { i, cell ->
                            val left = g.originX + cell.col * pitch
                            val top = g.originY + cell.row * pitch
                            val future = cell.date.isAfter(today)
                            if (future && mode == HeatmapMode.YEAR) return@forEachIndexed
                            val p = clock.progress(curve, stagger * cell.col)
                            if (p <= 0f) return@forEachIndexed
                            val s = g.cell * (0.6f + 0.4f * p)
                            val inset = (g.cell - s) / 2f
                            val alpha = p.coerceIn(0f, 1f) * if (sel >= 0 && sel != i) DIM_ALPHA else 1f
                            val topLeft = Offset(left + inset, top + inset)
                            val cellSize = Size(s, s)
                            val step = steps[i]
                            if (future) {
                                drawRoundRect(colors.line, topLeft, cellSize, radius, style = outline, alpha = alpha)
                            } else {
                                drawRoundRect(colors.heat(step), topLeft, cellSize, radius, alpha = alpha)
                            }
                            if (cell.date == today) {
                                drawRoundRect(colors.text, topLeft, cellSize, radius, style = todayStroke, alpha = alpha)
                            }
                            if (showNumbers) {
                                val color = when {
                                    future -> colors.textFaint
                                    step >= 3 -> colors.onSignal
                                    step == 0 -> colors.textMuted
                                    else -> colors.text
                                }
                                drawText(
                                    numbers[cell.date.dayOfMonth - 1],
                                    color = color,
                                    topLeft = Offset(left + numberPad, top + numberPad),
                                    alpha = alpha,
                                )
                            }
                        }
                        val text = tipText
                        val selected = grid.cells.getOrNull(sel)
                        if (selected != null && text != null) {
                            val left = g.originX + selected.col * pitch
                            val top = g.originY + selected.row * pitch
                            drawRoundRect(colors.text, Offset(left, top), Size(g.cell, g.cell), radius, style = selectedStroke)
                            tip.draw(this, text, Offset(left + g.cell / 2f, top))
                        }
                    }
                },
        )
        val openedDay = opened
        val openedCell = openedDay?.let { day -> grid.cells.firstOrNull { it.date.toEpochDay() == day } }
        if (sharedDay != null && openedDay != null && openedCell != null) {
            key(openedDay) {
                Box(
                    Modifier
                        .placeAt { _ ->
                            geometry?.let { g ->
                                val pitch = g.cell + g.gap
                                val left = g.originX + openedCell.col * pitch
                                val top = g.originY + openedCell.row * pitch
                                Rect(left, top, left + g.cell, top + g.cell)
                            }
                        }
                        .then(sharedDay(openedCell.date)),
                )
            }
        }
    }
}

/** Geometría en px de un mapa de calor ya medido. */
internal data class HeatGeometry(
    val originX: Float,
    val originY: Float,
    val cell: Float,
    val gap: Float,
    val width: Float,
    val height: Float,
)

/**
 * Coloca la retícula en [width]: en modo año deja a la izquierda el canal de las iniciales
 * ([gutter]) y arriba la fila de meses; en semana/mes, arriba las iniciales. Las celdas pequeñas
 * (< [smallCell]) llevan hueco [gapSmall]; las grandes, [gapLarge], hasta [maxCell]. Celda y hueco
 * se ajustan a píxeles enteros para que la retícula sea regular; en modo año, la retícula se
 * alinea a la derecha y el sobrante queda en el canal de las iniciales.
 */
internal fun heatGeometry(
    mode: HeatmapMode,
    grid: HeatGrid,
    width: Float,
    labelHeight: Float,
    gutter: Float,
    labelGap: Float,
    gapSmall: Float,
    gapLarge: Float,
    smallCell: Float,
    maxCell: Float,
): HeatGeometry {
    val available = width - gutter
    val limit = if (mode == HeatmapMode.YEAR) Float.POSITIVE_INFINITY else maxCell
    var gap = gapLarge.roundToInt().toFloat()
    var cell = floor(fitCell(available, grid.cols, gap, limit))
    if (cell < smallCell) {
        gap = gapSmall.roundToInt().coerceAtLeast(1).toFloat()
        cell = floor(fitCell(available, grid.cols, gap, limit))
    }
    val gridWidth = grid.cols * cell + (grid.cols - 1) * gap
    val originX = if (mode == HeatmapMode.YEAR) floor(width - gridWidth) else 0f
    val originY = ceil(labelHeight + labelGap)
    val height = originY + grid.rows * cell + (grid.rows - 1) * gap
    return HeatGeometry(originX = originX, originY = originY, cell = cell, gap = gap, width = width, height = height)
}
