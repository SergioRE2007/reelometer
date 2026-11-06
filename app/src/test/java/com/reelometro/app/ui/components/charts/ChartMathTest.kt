package com.reelometro.app.ui.components.charts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ChartMathTest {

    // ------------------------------------------------------------------------ barras

    @Test
    fun `siete barras gruesas con hueco y tope de grosor`() {
        val g = barGeometry(width = 700f, count = 7, maxBarPx = 84f, minGapPx = 3f)
        assertEquals(100f, g.slot, 1e-4f)
        assertEquals(56f, g.barWidth, 1e-4f)
        assertEquals(22f, g.left(0), 1e-4f)
        assertEquals(650f, g.center(6), 1e-4f)
        val capped = barGeometry(width = 2000f, count = 7, maxBarPx = 84f, minGapPx = 3f)
        assertEquals(84f, capped.barWidth, 1e-4f)
    }

    @Test
    fun `treinta y una barras mas finas`() {
        val g = barGeometry(width = 930f, count = 31, maxBarPx = 84f, minGapPx = 3f)
        assertEquals(30f, g.slot, 1e-4f)
        assertEquals(19.2f, g.barWidth, 1e-3f)
    }

    @Test
    fun `con 365 barras el hueco minimo manda o las barras se tocan`() {
        val wide = barGeometry(width = 3650f, count = 365, maxBarPx = 84f, minGapPx = 3f)
        assertEquals(10f, wide.slot, 1e-4f)
        assertEquals(7f, wide.barWidth, 1e-4f)
        val dense = barGeometry(width = 980f, count = 365, maxBarPx = 84f, minGapPx = 3f)
        assertEquals(dense.slot, dense.barWidth, 1e-4f)
    }

    @Test
    fun `indice de barra bajo el dedo acotado`() {
        assertEquals(0, indexAt(0f, 700f, 7))
        assertEquals(0, indexAt(99.9f, 700f, 7))
        assertEquals(1, indexAt(100f, 700f, 7))
        assertEquals(6, indexAt(699f, 700f, 7))
        assertEquals(6, indexAt(900f, 700f, 7))
        assertEquals(0, indexAt(-50f, 700f, 7))
        assertEquals(-1, indexAt(10f, 700f, 0))
        assertEquals(364, indexAt(999f, 1000f, 365))
    }

    @Test
    fun `maximo positivo y primero en caso de empate`() {
        assertEquals(2, maxIndex(listOf(1f, 3f, 5f, 5f)))
        assertEquals(-1, maxIndex(listOf(0f, 0f)))
        assertEquals(-1, maxIndex(emptyList()))
    }

    // ------------------------------------------------------------------------ etiquetas

    @Test
    fun `etiquetas con paso uniforme si todas tienen texto`() {
        val centers = FloatArray(31) { it * 30f + 15f }
        val widths = FloatArray(31) { 40f }
        val picked = visibleLabels(centers, widths, totalWidth = 930f, minGap = 8f)
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30), picked)
        val roomy = visibleLabels(FloatArray(7) { it * 100f + 50f }, FloatArray(7) { 20f }, 700f, 8f)
        assertEquals((0..6).toList(), roomy)
    }

    @Test
    fun `etiquetas dispersas saltan las que se pisan`() {
        val centers = floatArrayOf(5f, 20f, 300f, 310f, 600f)
        val widths = floatArrayOf(40f, 0f, 40f, 40f, 40f)
        assertEquals(listOf(0, 2, 4), visibleLabels(centers, widths, 700f, 8f))
        assertEquals(emptyList<Int>(), visibleLabels(centers, FloatArray(5), 700f, 8f))
    }

    @Test
    fun `paso uniforme minimo para que quepan`() {
        assertEquals(1, uniformStep(pitch = 30f, labelWidth = 20f, minGap = 4f))
        assertEquals(2, uniformStep(pitch = 20f, labelWidth = 30f, minGap = 4f))
        assertEquals(3, uniformStep(pitch = 12f, labelWidth = 30f, minGap = 4f, candidates = intArrayOf(1, 2, 3, 6)))
    }

    // ------------------------------------------------------------------------ cuantiles

    @Test
    fun `cuantiles por interpolacion lineal`() {
        val sorted = listOf(1f, 2f, 3f, 4f, 5f)
        assertEquals(2f, quantile(sorted, 0.25f), 1e-5f)
        assertEquals(3f, quantile(sorted, 0.5f), 1e-5f)
        assertEquals(4f, quantile(sorted, 0.75f), 1e-5f)
        assertEquals(2.5f, quantile(listOf(1f, 2f, 3f, 4f), 0.5f), 1e-5f)
        assertEquals(7f, quantile(listOf(7f), 0.9f), 1e-5f)
    }

    @Test
    fun `los umbrales ignoran los dias sin actividad`() {
        val t = heatThresholds(listOf(0f, 0f, 10f, 20f, 30f, 40f, 50f))
        assertArrayEquals(floatArrayOf(20f, 30f, 40f), t, 1e-5f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), heatThresholds(listOf(0f, 0f)), 1e-5f)
    }

    @Test
    fun `cinco pasos de nada a mucho`() {
        val t = heatThresholds(listOf(10f, 20f, 30f, 40f, 50f))
        assertEquals(0, heatStep(0f, t))
        assertEquals(1, heatStep(10f, t))
        assertEquals(1, heatStep(20f, t))
        assertEquals(2, heatStep(25f, t))
        assertEquals(3, heatStep(40f, t))
        assertEquals(4, heatStep(50f, t))
        assertEquals(4, heatStep(500f, t))
    }

    // ------------------------------------------------------------------------ calendario

    @Test
    fun `semana de lunes a domingo`() {
        val g = heatGrid(HeatmapMode.WEEK, LocalDate.of(2026, 11, 5))
        assertEquals(LocalDate.of(2026, 11, 2), g.from)
        assertEquals(LocalDate.of(2026, 11, 8), g.to)
        assertEquals(7, g.cols)
        assertEquals(1, g.rows)
        assertEquals((0..6).toList(), g.cells.map { it.col })
        assertEquals(DayOfWeek.MONDAY, g.cells.first().date.dayOfWeek)
    }

    @Test
    fun `mes natural con semanas que empiezan en lunes`() {
        // Noviembre de 2026 empieza en domingo: el día 1 va solo en la primera fila.
        val g = heatGrid(HeatmapMode.MONTH, LocalDate.of(2026, 11, 3))
        assertEquals(30, g.cells.size)
        assertEquals(6, g.rows)
        val first = g.cells.first()
        assertEquals(6 to 0, first.col to first.row)
        val second = g.cells[1]
        assertEquals(0 to 1, second.col to second.row)
        val last = g.cells.last()
        assertEquals(LocalDate.of(2026, 11, 30), last.date)
        assertEquals(0 to 5, last.col to last.row)
        assertEquals(LocalDate.of(2026, 11, 3), g.cellAt(1, 1)?.date)
        assertNull(g.cellAt(0, 0))
    }

    @Test
    fun `febrero que cuadra en cuatro filas`() {
        // Febrero de 2027 empieza en lunes y tiene 28 días.
        assertEquals(4, heatGrid(HeatmapMode.MONTH, LocalDate.of(2027, 2, 10)).rows)
    }

    @Test
    fun `ano estilo contribuciones con columnas de semanas`() {
        val g = heatGrid(HeatmapMode.YEAR, LocalDate.of(2026, 6, 1))
        assertEquals(LocalDate.of(2026, 1, 1), g.from)
        assertEquals(LocalDate.of(2026, 12, 31), g.to)
        assertEquals(365, g.cells.size)
        assertEquals(7, g.rows)
        assertEquals(53, g.cols)
        // 1 de enero de 2026: jueves → columna 0, fila 3.
        assertEquals(0 to 3, g.cells.first().let { it.col to it.row })
        // 5 de enero, lunes → columna 1, fila 0.
        val monday = g.cells.first { it.date == LocalDate.of(2026, 1, 5) }
        assertEquals(1 to 0, monday.col to monday.row)
        assertEquals(12, g.monthStarts.size)
        assertEquals(0 to 1, g.monthStarts.first())
        // 1 de febrero de 2026 es domingo: la etiqueta pasa a la semana siguiente.
        assertEquals(5 to 2, g.monthStarts[1])
        assertTrue(g.monthStarts.zipWithNext().all { (a, b) -> b.first > a.first })
    }

    @Test
    fun `celda bajo el dedo repartiendo los huecos`() {
        assertEquals(0 to 0, cellAt(0f, 0f, cell = 10f, gap = 2f, cols = 7, rows = 6))
        assertEquals(0 to 0, cellAt(10.9f, 10.9f, 10f, 2f, 7, 6))
        assertEquals(1 to 1, cellAt(11.1f, 11.1f, 10f, 2f, 7, 6))
        assertEquals(6 to 5, cellAt(81f, 70f, 10f, 2f, 7, 6))
        assertNull(cellAt(83.5f, 70f, 10f, 2f, 7, 6)) // hueco tras la última columna
        assertNull(cellAt(-5f, 3f, 10f, 2f, 7, 6))
        assertNull(cellAt(90f, 3f, 10f, 2f, 7, 6))
        assertNull(cellAt(3f, 3f, 0f, 2f, 7, 6))
    }

    @Test
    fun `celdas cuadradas que encajan en el ancho`() {
        assertEquals(10f, fitCell(width = 82f, cols = 7, gap = 2f), 1e-5f)
        assertEquals(8f, fitCell(width = 82f, cols = 7, gap = 2f, maxCell = 8f), 1e-5f)
        assertEquals(0f, fitCell(width = 82f, cols = 0, gap = 2f), 1e-5f)
    }

    @Test
    fun `geometria entera del mapa del ano alineada a la derecha`() {
        val grid = heatGrid(HeatmapMode.YEAR, LocalDate.of(2026, 1, 1))
        val g = heatGeometry(
            mode = HeatmapMode.YEAR, grid = grid, width = 975f, labelHeight = 37.3f, gutter = 40f,
            labelGap = 10.5f, gapSmall = 5.25f, gapLarge = 7.875f, smallCell = 31.5f, maxCell = 168f,
        )
        assertEquals(5f, g.gap)
        assertEquals(g.cell, kotlin.math.floor(g.cell))
        val gridWidth = grid.cols * g.cell + (grid.cols - 1) * g.gap
        assertEquals(975f, g.originX + gridWidth, 1f)
        assertTrue(g.originX >= 40f)
        assertEquals(48f, g.originY)
        assertEquals(g.originY + 7 * g.cell + 6 * g.gap, g.height, 1e-4f)
    }

    @Test
    fun `geometria del mes con tope de celda`() {
        val grid = heatGrid(HeatmapMode.MONTH, LocalDate.of(2026, 11, 1))
        val g = heatGeometry(
            mode = HeatmapMode.MONTH, grid = grid, width = 2000f, labelHeight = 30f, gutter = 0f,
            labelGap = 20f, gapSmall = 5f, gapLarge = 8f, smallCell = 31f, maxCell = 147f,
        )
        assertEquals(147f, g.cell)
        assertEquals(8f, g.gap)
        assertEquals(0f, g.originX)
    }

    @Test
    fun `iniciales de los dias segun el alto disponible`() {
        assertEquals(1, rowLabelStep(pitch = 20f, labelHeight = 14f))
        assertEquals(2, rowLabelStep(pitch = 9f, labelHeight = 14f))
        assertEquals(3, rowLabelStep(pitch = 5f, labelHeight = 14f))
    }

    // ------------------------------------------------------------------------ línea

    @Test
    fun `tangentes monotonas sin inventar picos`() {
        val xs = floatArrayOf(0f, 1f, 2f, 3f, 4f)
        val ys = floatArrayOf(0f, 1f, 1f, 3f, 2f)
        val m = monotoneTangents(xs, ys)
        assertEquals(0f, m[1], 1e-5f) // 1 → 1: tramo plano
        assertEquals(0f, m[2], 1e-5f)
        assertEquals(0f, m[3], 1e-5f) // máximo local: tangente horizontal
        val rising = monotoneTangents(floatArrayOf(0f, 1f, 2f, 3f), floatArrayOf(0f, 1f, 4f, 9f))
        assertTrue(rising.all { it >= 0f })
        assertEquals(0, monotoneTangents(floatArrayOf(), floatArrayOf()).size)
    }

    @Test
    fun `hora bajo el dedo hasta la hora actual`() {
        assertEquals(0, hourAt(0f, 240f))
        assertEquals(12, hourAt(125f, 240f))
        assertEquals(23, hourAt(239f, 240f))
        assertEquals(21, hourAt(239f, 240f, maxHour = 21))
    }

    @Test
    fun `escalonado con tope total`() {
        assertEquals(24_000_000L, staggerNanos(7, 24, 360))
        assertEquals(12_000_000L, staggerNanos(31, 24, 360))
        assertEquals(989_000L, staggerNanos(365, 24, 360) / 1000 * 1000)
        assertEquals(0L, staggerNanos(1, 24, 360))
    }
}
