package com.reelometro.app.ui.components.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decisión del eje del arrastre sobre una gráfica: horizontal recorre, vertical desplaza la página. */
class ChartTouchTest {
    private val slop = 8f

    @Test
    fun belowSlopIsUndecided() {
        assertNull(dragAxis(0f, 0f, slop))
        assertNull(dragAxis(5f, 5f, slop))
        assertNull(dragAxis(-7.9f, 0f, slop))
    }

    @Test
    fun horizontalDragScrubs() {
        assertEquals(DragAxis.Horizontal, dragAxis(9f, 0f, slop))
        assertEquals(DragAxis.Horizontal, dragAxis(-12f, 4f, slop))
        assertEquals(DragAxis.Horizontal, dragAxis(10f, -9f, slop))
    }

    @Test
    fun verticalDragKeepsScrolling() {
        assertEquals(DragAxis.Vertical, dragAxis(0f, 9f, slop))
        assertEquals(DragAxis.Vertical, dragAxis(3f, -12f, slop))
    }

    @Test
    fun diagonalTieLetsThePageScroll() {
        assertEquals(DragAxis.Vertical, dragAxis(10f, 10f, slop))
        assertEquals(DragAxis.Vertical, dragAxis(-10f, -10f, slop))
    }
}
