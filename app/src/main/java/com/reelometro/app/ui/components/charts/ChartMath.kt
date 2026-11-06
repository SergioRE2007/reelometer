package com.reelometro.app.ui.components.charts

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Lógica pura de las gráficas (sin Compose ni Android): geometría de barras, índice bajo el dedo,
 * reparto de etiquetas, cuantiles del mapa de calor y retícula del calendario. Probada en
 * ChartMathTest.
 */

// ---------------------------------------------------------------------------------------- barras

/** Geometría horizontal de `count` barras en `width` px: hueco por barra y grosor. */
internal data class BarGeometry(val slot: Float, val barWidth: Float) {
    fun left(index: Int): Float = index * slot + (slot - barWidth) / 2f
    fun center(index: Int): Float = index * slot + slot / 2f
}

/**
 * Grosor según la densidad: con 7 barras, gruesas y separadas (sin pasar de [maxBarPx]); con 31,
 * más finas; con 365, casi sin hueco (si el hueco no llega a [minGapPx], las barras se tocan).
 */
internal fun barGeometry(width: Float, count: Int, maxBarPx: Float, minGapPx: Float): BarGeometry {
    if (count <= 0 || width <= 0f) return BarGeometry(0f, 0f)
    val slot = width / count
    val ratio = when {
        count <= 7 -> 0.56f
        count <= 31 -> 0.64f
        else -> 0.8f
    }
    var bar = min(slot * ratio, maxBarPx)
    if (slot - bar < minGapPx) bar = if (slot > minGapPx * 2.5f) slot - minGapPx else slot
    return BarGeometry(slot, bar.coerceAtLeast(0f))
}

/** Índice de la barra (o franja) bajo la coordenada [x], acotado a `0 until count`. */
internal fun indexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 0 || width <= 0f) return -1
    return floor(x / (width / count)).toInt().coerceIn(0, count - 1)
}

/** Índice del primer máximo estrictamente positivo, o -1. */
internal fun maxIndex(values: List<Float>): Int {
    var best = -1
    var bestValue = 0f
    values.forEachIndexed { i, v -> if (v > bestValue) { best = i; bestValue = v } }
    return best
}

// -------------------------------------------------------------------------------------- etiquetas

/**
 * Qué etiquetas del eje caben sin solaparse. Si todas tienen texto, se elige un paso uniforme (cada
 * 1, 2, 3… desde la primera); si algunas están vacías (p. ej. solo el día 1 de cada mes), se
 * colocan las que tienen texto, en orden, saltando las que pisarían a la anterior.
 *
 * @param centers centro de cada etiqueta en px. @param widths ancho de cada etiqueta (0 = sin texto).
 * Las etiquetas se acotan a `[0, totalWidth]` como al dibujarlas.
 */
internal fun visibleLabels(centers: FloatArray, widths: FloatArray, totalWidth: Float, minGap: Float): List<Int> {
    val n = centers.size
    if (n == 0) return emptyList()
    fun span(i: Int): Pair<Float, Float> {
        val w = widths[i]
        val l = (centers[i] - w / 2f).coerceIn(0f, max(0f, totalWidth - w))
        return l to l + w
    }
    val all = (0 until n).filter { widths[it] > 0f }
    if (all.isEmpty()) return emptyList()
    if (all.size == n) {
        for (step in 1..n) {
            val picked = (0 until n step step).toList()
            var ok = true
            for (k in 1 until picked.size) {
                if (span(picked[k]).first - span(picked[k - 1]).second < minGap) { ok = false; break }
            }
            if (ok) return picked
        }
        return listOf(0)
    }
    val result = ArrayList<Int>()
    var lastRight = Float.NEGATIVE_INFINITY
    for (i in all) {
        val (l, r) = span(i)
        if (l - lastRight >= minGap) {
            result += i
            lastRight = r
        }
    }
    return result
}

/** Paso uniforme mínimo (1, 2, 3, 6…) para que etiquetas de ancho [labelWidth] quepan en [pitch] px. */
internal fun uniformStep(pitch: Float, labelWidth: Float, minGap: Float, candidates: IntArray = intArrayOf(1, 2, 3, 4, 6, 12)): Int {
    for (s in candidates) if (s * pitch >= labelWidth + minGap) return s
    return candidates.last()
}

// --------------------------------------------------------------------------------- mapa de calor

/**
 * Umbrales (cuartiles 25/50/75 %, interpolación lineal) de los valores **positivos**. El paso 0 es
 * «nada» (0 o sin dato); 1–4, de menos a más. Con una lista vacía devuelve ceros.
 */
internal fun heatThresholds(values: Collection<Float>): FloatArray {
    val positives = values.filter { it > 0f }.sorted()
    if (positives.isEmpty()) return floatArrayOf(0f, 0f, 0f)
    return floatArrayOf(quantile(positives, 0.25f), quantile(positives, 0.5f), quantile(positives, 0.75f))
}

/** Paso 0–4 de [value] según [thresholds]. */
internal fun heatStep(value: Float, thresholds: FloatArray): Int = when {
    value <= 0f -> 0
    value <= thresholds[0] -> 1
    value <= thresholds[1] -> 2
    value <= thresholds[2] -> 3
    else -> 4
}

/** Cuantil por interpolación lineal (tipo 7) sobre una lista ya ordenada. */
internal fun quantile(sorted: List<Float>, q: Float): Float {
    if (sorted.isEmpty()) return 0f
    if (sorted.size == 1) return sorted[0]
    val pos = q.coerceIn(0f, 1f) * (sorted.size - 1)
    val lo = floor(pos).toInt()
    val hi = min(lo + 1, sorted.lastIndex)
    val t = pos - lo
    return sorted[lo] + (sorted[hi] - sorted[lo]) * t
}

/** Modo del calendario de calor. */
enum class HeatmapMode {
    /** Una fila de 7 (L…D). */
    WEEK,

    /** Calendario mensual: 7 columnas × semanas. */
    MONTH,

    /** Estilo contribuciones: columnas = semanas, filas = L…D, meses arriba. */
    YEAR,
}

/** Posición de un día en la retícula. */
internal data class HeatCell(val date: LocalDate, val col: Int, val row: Int)

/** Retícula del calendario: tamaño, días colocados y columnas donde empieza cada mes (modo año). */
internal data class HeatGrid(
    val mode: HeatmapMode,
    val from: LocalDate,
    val to: LocalDate,
    val cols: Int,
    val rows: Int,
    val cells: List<HeatCell>,
    /** (columna, mes 1–12) de las etiquetas de mes (solo en YEAR). */
    val monthStarts: List<Pair<Int, Int>>,
) {
    private val index: Map<Long, HeatCell> = cells.associateBy { it.col.toLong() shl 32 or it.row.toLong() }

    fun cellAt(col: Int, row: Int): HeatCell? = index[col.toLong() shl 32 or row.toLong()]
}

/**
 * Coloca los días. El rango sale de [anchor]: la semana (lunes a domingo) que lo contiene, su mes
 * natural o su año natural. Las semanas empiezan en lunes.
 */
internal fun heatGrid(mode: HeatmapMode, anchor: LocalDate): HeatGrid {
    val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
    return when (mode) {
        HeatmapMode.WEEK -> {
            val from = anchor.with(monday)
            val cells = (0..6).map { HeatCell(from.plusDays(it.toLong()), it, 0) }
            HeatGrid(mode, from, from.plusDays(6), cols = 7, rows = 1, cells = cells, monthStarts = emptyList())
        }

        HeatmapMode.MONTH -> {
            val from = anchor.withDayOfMonth(1)
            val to = anchor.with(TemporalAdjusters.lastDayOfMonth())
            val start = from.with(monday)
            val cells = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
                HeatCell(d, d.dayOfWeek.value - 1, (ChronoUnit.DAYS.between(start, d) / 7).toInt())
            }.toList()
            val rows = cells.maxOf { it.row } + 1
            HeatGrid(mode, from, to, cols = 7, rows = rows, cells = cells, monthStarts = emptyList())
        }

        HeatmapMode.YEAR -> {
            val from = anchor.withDayOfYear(1)
            val to = anchor.with(TemporalAdjusters.lastDayOfYear())
            val start = from.with(monday)
            val cells = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
                HeatCell(d, (ChronoUnit.DAYS.between(start, d) / 7).toInt(), d.dayOfWeek.value - 1)
            }.toList()
            val cols = cells.maxOf { it.col } + 1
            val months = (1..12).map { m ->
                val first = LocalDate.of(from.year, m, 1)
                val col = (ChronoUnit.DAYS.between(start, first) / 7).toInt()
                // Si el día 1 cae de viernes a domingo, esa columna es casi del mes anterior.
                (if (first.dayOfWeek.value >= 5) col + 1 else col).coerceAtMost(cols - 1) to m
            }
            HeatGrid(mode, from, to, cols = cols, rows = 7, cells = cells, monthStarts = months)
        }
    }
}

/**
 * Celda (columna, fila) bajo el punto ([x], [y]) relativo al origen de la retícula, o `null` si
 * cae fuera. Los huecos se reparten: el punto pertenece a la celda más cercana de su paso.
 */
internal fun cellAt(x: Float, y: Float, cell: Float, gap: Float, cols: Int, rows: Int): Pair<Int, Int>? {
    if (cell <= 0f) return null
    val pitch = cell + gap
    val col = floor((x + gap / 2f) / pitch).toInt()
    val row = floor((y + gap / 2f) / pitch).toInt()
    if (col !in 0 until cols || row !in 0 until rows) return null
    return col to row
}

/** Lado de celda para que [cols] celdas con huecos [gap] quepan en [width] (sin pasar de [maxCell]). */
internal fun fitCell(width: Float, cols: Int, gap: Float, maxCell: Float = Float.POSITIVE_INFINITY): Float {
    if (cols <= 0) return 0f
    return min(maxCell, (width - gap * (cols - 1)) / cols).coerceAtLeast(0f)
}

/** Cada cuántas filas cabe una etiqueta de alto [labelHeight] con un paso de fila [pitch]. */
internal fun rowLabelStep(pitch: Float, labelHeight: Float): Int {
    if (pitch <= 0f) return 1
    var step = 1
    while (step * pitch < labelHeight && step < 7) step++
    return step
}

// ------------------------------------------------------------------------------------------ línea

/**
 * Tangentes de una interpolación cúbica monótona (Fritsch–Carlson): la curva pasa por todos los
 * puntos sin inventarse picos ni valores negativos entre ellos.
 */
internal fun monotoneTangents(xs: FloatArray, ys: FloatArray): FloatArray {
    val n = xs.size
    val m = FloatArray(n)
    if (n < 2) return m
    val d = FloatArray(n - 1) { i ->
        val dx = xs[i + 1] - xs[i]
        if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx
    }
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
            continue
        }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) {
            val t = 3f / kotlin.math.sqrt(s)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
        }
    }
    return m
}

/** Hora (0–23) bajo [x] en una línea de 24 franjas de [width] px, acotada a [maxHour]. */
internal fun hourAt(x: Float, width: Float, maxHour: Int = 23): Int =
    indexAt(x, width, 24).coerceIn(0, maxHour.coerceIn(0, 23))

/** Escalonado por elemento (ns) para que el total no pase de [maxTotalMs]. */
internal fun staggerNanos(count: Int, perItemMs: Long, maxTotalMs: Long): Long {
    if (count <= 1) return 0L
    val per = min(perItemMs.toDouble(), maxTotalMs.toDouble() / (count - 1))
    return (per * 1_000_000).toLong()
}
