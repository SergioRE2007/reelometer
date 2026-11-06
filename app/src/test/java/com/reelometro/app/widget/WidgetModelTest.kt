package com.reelometro.app.widget

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.WidgetCopy
import com.reelometro.app.ui.components.flap.FlapCell
import com.reelometro.app.ui.components.flap.FlapGlyphMetrics
import com.reelometro.app.ui.components.flap.boardWidthEm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetModelTest {

    private val today = LocalDate.of(2026, 11, 3)
    private fun data(count: Int, ms: Long = 47 * 60_000L, goal: Int? = null, on: Boolean = true) =
        WidgetData(today, count, ms, goal, on)

    /** Glifos de un marcador como texto: «0·0·7» con «.» para el punto de millar. */
    private fun WidgetModel.board(): String = cells.joinToString("") { c ->
        when (c) {
            is FlapCell.Tile -> c.glyph.toString()
            FlapCell.Separator -> "."
        }
    }

    private fun WidgetModel.dimmed(): List<Boolean> = cells.filterIsInstance<FlapCell.Tile>().map { it.position >= dimFrom }

    // --- Modelo ---------------------------------------------------------------------------------

    @Test
    fun `minimo tres fichas con los ceros de relleno apagados`() {
        val m = WidgetModel.of(data(7))
        assertEquals("007", m.board())
        assertEquals(listOf(true, true, false), m.dimmed())
        assertEquals(listOf(true, true, false), WidgetModel.of(data(0)).dimmed())
        assertEquals("212", WidgetModel.of(data(212)).board())
        assertEquals(listOf(false, false, false), WidgetModel.of(data(212)).dimmed())
    }

    @Test
    fun `punto de millar entre cifras reales`() {
        assertEquals("1.234", WidgetModel.of(data(1_234)).board())
        assertEquals("12.408", WidgetModel.of(data(12_408)).board())
    }

    @Test
    fun `cada celda cabe en una fila de Glance (10 hijos como mucho)`() {
        assertTrue(WidgetModel.of(data(9_999_999)).cells.size <= 10)
    }

    @Test
    fun `sin señal - fichas en blanco apagadas y el aviso en acento`() {
        val m = WidgetModel.of(data(120, on = false))
        assertEquals("–––", m.board())
        assertEquals(listOf(true, true, true), m.dimmed())
        assertEquals(WidgetCopy.NO_SIGNAL, m.detail)
        assertTrue(m.detailAlert)
        assertNull(m.status)
        assertEquals(WidgetCopy.DESCRIPTION_OFF, m.description)
    }

    @Test
    fun `sin datos todavia - fichas en blanco y nada mas`() {
        val m = WidgetModel.of(null)
        assertEquals("–––", m.board())
        assertNull(m.detail)
        assertNull(m.goalFraction)
    }

    @Test
    fun `con objetivo - estado y barra, en acento si se ha pasado`() {
        val under = WidgetModel.of(data(112, goal = 150))
        assertEquals("QUEDAN 38", under.status)
        assertFalse(under.statusAlert)
        assertEquals(112f / 150f, under.goalFraction!!, 1e-6f)

        val over = WidgetModel.of(data(212, goal = 150))
        assertEquals("+62 PASADO", CopyRules.plain(over.status!!))
        assertTrue(over.statusAlert)
        assertEquals(1f, over.goalFraction!!, 0f)
        assertNull(WidgetModel.of(data(212)).goalFraction)
    }

    @Test
    fun `el tiempo va debajo en mayusculas`() {
        assertEquals("47 MIN", CopyRules.plain(WidgetModel.of(data(212)).detail!!))
        assertEquals("Hoy: 212 vídeos, 47 minutos.", CopyRules.plain(WidgetModel.of(data(212)).description))
    }

    // --- Maqueta --------------------------------------------------------------------------------

    /** Cifras como las de Bricolage condensada (aprox.) y texto mono de 0,6 em por carácter. */
    private val metrics = FlapGlyphMetrics(advanceEm = 0.5f, inkTopEm = -0.72f, inkBottomEm = 0.01f)
    private val text = object : TextMetrics {
        override fun width(text: String, spec: TextSpec) = text.length * spec.sizeDp * (0.6f + spec.trackingEm)
        override fun lineHeight(spec: TextSpec) = spec.sizeDp * 1.32f
    }
    private val label = TextSpec(11f, 600, 0.12f)
    private val dataL = TextSpec(22f, 500, 0f)
    private val dataM = TextSpec(15f, 500, 0f)
    private val dataS = TextSpec(13f, 500, 0f)

    private fun layout(w: Float, h: Float, m: WidgetModel) =
        WidgetLayout.compute(w, h, m, metrics, label, dataL, dataM, dataS, text, maxFontDp = 132f)

    private fun layout(size: androidx.compose.ui.unit.DpSize, m: WidgetModel) = layout(size.width.value, size.height.value, m)

    private fun boardWidth(l: WidgetLayout, m: WidgetModel) = l.fontDp * boardWidthEm(m.cells, metrics.tileWidthEm)

    @Test
    fun `cada tamaño declarado tiene su maqueta`() {
        val m = WidgetModel.of(data(212, goal = 150))
        assertEquals(Arrangement.STACKED, layout(WidgetSizes.SQUARE, m).arrangement)
        assertEquals(Arrangement.STACKED, layout(WidgetSizes.LARGE, m).arrangement)
        assertEquals(Arrangement.STACKED, layout(WidgetSizes.NARROW_TALL, m).arrangement)
        assertEquals(Arrangement.SIDE, layout(WidgetSizes.SMALL, m).arrangement)
        assertEquals(Arrangement.ROW, layout(WidgetSizes.WIDE, m).arrangement)
        assertEquals(Arrangement.ROW, layout(WidgetSizes.NARROW, m).arrangement)
    }

    @Test
    fun `en 2x1 el objetivo y la barra caben con el tiempo al lado del marcador`() {
        val l = layout(WidgetSizes.SMALL, WidgetModel.of(data(212, goal = 150)))
        assertTrue(l.showDetail && l.showStatus && l.showBar)
        assertEquals(dataM, l.detail)
        // Marcador + hueco + tiempo caben a lo ancho.
        val m = WidgetModel.of(data(212, goal = 150))
        assertTrue(boardWidth(l, m) + WidgetLayout.COLUMN_GAP + text.width(m.detail!!, dataM) <= l.innerWidth + 0.01f)
    }

    @Test
    fun `el marcador cabe siempre en su hueco`() {
        val sizes = WidgetSizes.ALL.map { it.width.value to it.height.value } + listOf(110f to 40f, 250f to 40f, 180f to 90f)
        for (count in listOf(0, 212, 1_234, 12_408)) {
            for (goal in listOf(null, 150)) {
                val m = WidgetModel.of(data(count, goal = goal))
                for ((w, h) in sizes) {
                    val l = layout(w, h, m)
                    val boardH = l.fontDp * metrics.tileHeightEm
                    assertTrue("$count en ${w}x$h: ancho", boardWidth(l, m) <= l.innerWidth + 0.01f)
                    assertTrue("$count en ${w}x$h: alto", boardH <= h - 2 * l.padding + 0.01f)
                    assertTrue("$count en ${w}x$h: positivo", l.fontDp > 0f)
                }
            }
        }
    }

    @Test
    fun `apilado - el tiempo en grande si no quita sitio al marcador`() {
        val m = WidgetModel.of(data(212, goal = 150))
        val square = layout(WidgetSizes.SQUARE, m)
        assertEquals(dataL, square.detail) // limitado por el ancho: sobra alto
        val rows = text.lineHeight(label) + WidgetLayout.GAP + WidgetLayout.GAP + text.lineHeight(dataL) + WidgetLayout.GAP + WidgetLayout.BAR
        assertTrue(square.fontDp * metrics.tileHeightEm <= WidgetSizes.SQUARE.height.value - 2 * square.padding - rows + 0.01f)
        assertTrue(square.showBar && square.showDetail && square.showStatus)
    }

    @Test
    fun `en fila - si no cabe de alto se quita primero el objetivo y luego el tiempo`() {
        val m = WidgetModel.of(data(212, goal = 150))
        val wide = layout(352f, 96f, m)
        assertTrue(wide.arrangement == Arrangement.ROW && wide.showDetail && wide.showStatus)
        val short = layout(352f, 60f, m)
        assertTrue(short.arrangement == Arrangement.ROW && short.showDetail && !short.showStatus)
        val tiny = layout(352f, 44f, m)
        assertTrue(tiny.arrangement == Arrangement.ROW && !tiny.showDetail && !tiny.showStatus)
    }

    @Test
    fun `sin señal el aviso no se pierde en ningun tamaño`() {
        val m = WidgetModel.of(data(0, on = false))
        for (size in WidgetSizes.ALL) assertTrue("$size", layout(size, m).showDetail)
    }

    @Test
    fun `el estado del objetivo no se monta sobre HOY`() {
        val m = WidgetModel.of(data(212, goal = 150))
        assertFalse(layout(WidgetSizes.NARROW_TALL, m).showStatus)
        assertTrue(layout(WidgetSizes.SQUARE, m).showStatus)
    }

    @Test
    fun `margen de 16 dp en los grandes y de 12 en los pequeños`() {
        val m = WidgetModel.of(data(212))
        assertEquals(16f, layout(WidgetSizes.SQUARE, m).padding)
        assertEquals(12f, layout(WidgetSizes.SMALL, m).padding)
    }

    @Test
    fun `las fichas en px enteros no superan el tamaño pedido`() {
        val b = BoardSpec.of(fontDp = 40f, metrics = metrics, density = 2.625f)
        val fs = 40f * 2.625f
        assertTrue(b.tileWPx <= fs * metrics.tileWidthEm)
        assertTrue(b.tileHPx <= fs * metrics.tileHeightEm)
        assertTrue(b.hinge in 1f..2f)
        // La tinta de las cifras queda centrada en la ficha (mismo aire arriba y abajo).
        val inkTop = b.baselinePx + fs * metrics.inkTopEm
        val inkBottom = b.baselinePx + fs * metrics.inkBottomEm
        assertEquals(inkTop, fs * metrics.tileHeightEm - inkBottom, 0.01f)
    }

    // --- Medianoche -----------------------------------------------------------------------------

    @Test
    fun `el refresco es justo despues de la medianoche siguiente`() {
        val madrid = ZoneId.of("Europe/Madrid")
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 3, 28, 23, 59, 59), madrid)
        assertEquals(LocalDateTime.of(2026, 3, 29, 0, 0, 30), WidgetSchedule.nextRefresh(now).toLocalDateTime())
        val justAfter = ZonedDateTime.of(LocalDateTime.of(2026, 3, 29, 0, 0, 31), madrid)
        assertEquals(LocalDateTime.of(2026, 3, 30, 0, 0, 30), WidgetSchedule.nextRefresh(justAfter).toLocalDateTime())
    }

    @Test
    fun `en una zona donde el cambio de hora se salta la medianoche tambien hay refresco`() {
        // Chile adelanta la hora a las 24:00: ese día las 00:00 no existen y el día empieza a la 01:00.
        val zone = ZoneId.of("America/Santiago")
        var gap = zone.rules.nextTransition(ZonedDateTime.of(LocalDateTime.of(2026, 1, 1, 0, 0), zone).toInstant())
        while (!gap.isGap) gap = zone.rules.nextTransition(gap.instant)
        assumeTrue(gap.dateTimeBefore.toLocalTime() == LocalTime.MIDNIGHT)
        val before = gap.dateTimeBefore.minusHours(1).atZone(zone)
        val next = WidgetSchedule.nextRefresh(before)
        assertTrue(next.isAfter(before))
        assertEquals(gap.dateTimeAfter.plusSeconds(30), next.toLocalDateTime())
    }
}
