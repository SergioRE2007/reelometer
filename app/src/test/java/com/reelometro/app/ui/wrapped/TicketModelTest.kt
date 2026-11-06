package com.reelometro.app.ui.wrapped

import com.reelometro.app.copy.CopyRules
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.ShareCopy
import com.reelometro.app.copy.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketModelTest {

    @Test
    fun `el ticket no repite vídeos ni tiempo en las líneas y cierra con el total`() {
        val stats = periodStats()
        val ticket = TicketModel.build(stats)
        assertFalse(ticket.lines.any { Labels.VIDEOS in it.label || Labels.TIME in it.label })
        assertEquals(ShareCopy.totalLine(stats.totals.totalMs), ticket.total)
        assertEquals(Words.durationLabel(stats.totals.totalMs), ticket.total.value)
        assertEquals("OCTUBRE 2026", ticket.period)
        assertEquals(3, ticket.equivalences.size)
        assertEquals(31, ticket.barcode.size)
        assertEquals("31 OCT 2026", ticket.stampDate)
    }

    @Test
    fun `los textos del ticket cumplen las reglas (con emojis permitidos)`() {
        val t = TicketModel.build(periodStats())
        val texts = listOf(t.brand, t.title, t.period, t.footer, t.stamp, t.brandFooter) +
            (t.lines + t.equivalences + t.total).flatMap { listOf(it.label, it.value) }
        texts.forEach { assertEquals(it, emptyList<String>(), CopyRules.violations(it, allowEmoji = true)) }
    }

    @Test
    fun `el sello del periodo en curso lleva la fecha de hoy`() {
        val ticket = TicketModel.build(periodStats(ref = com.reelometro.app.stats.PeriodRef.THIS_MONTH))
        assertEquals("3 NOV 2026", ticket.stampDate)
    }

    @Test
    fun `pie del código de barras con ceros a la izquierda`() {
        assertEquals("0004812 · 029 · 2026", TicketModel.barcodeCaption(4_812, 29 * 3_600_000L + 59_000, 2026))
        assertEquals("0000000 · 000 · 2026", TicketModel.barcodeCaption(0, 0, 2026))
    }

    @Test
    fun `el sello se parte en dos líneas parejas`() {
        assertEquals(listOf("PAGADO CON", "TU TIEMPO"), TicketModel.balancedLines(ShareCopy.STAMP))
        assertEquals(listOf("HOLA"), TicketModel.balancedLines("HOLA"))
    }

    @Test
    fun `el código de barras cabe en su ancho con una barra por día y cuatro guías`() {
        val values = List(31) { (it * 7 % 11).toFloat() }
        val bars = TicketBarcode.encode(values, 434f, 3f)
        assertEquals(31 + 4, bars.size)
        assertEquals(4, bars.count { it.guard })
        val last = bars.last()
        assertTrue(last.x + last.width <= 434f + 1f)
        assertTrue(bars.zipWithNext().all { (a, b) -> b.x >= a.x + a.width })
    }

    @Test
    fun `con muchos días el código agrupa para no bajar del módulo mínimo`() {
        val bars = TicketBarcode.encode(List(365) { it % 9f }, 434f, 3f)
        assertTrue(bars.size - 4 < 365)
        assertTrue(bars.filter { !it.guard }.all { it.width >= 2f })
        assertTrue(TicketBarcode.encode(emptyList(), 434f, 3f).isEmpty())
    }

    @Test
    fun `los símbolos de un carácter se fuerzan a emoji en color`() {
        assertEquals("⚡️", ShareCopy.emoji("⚡"))
        assertEquals("📚", ShareCopy.emoji("📚"))
        assertEquals("✈️", ShareCopy.emoji("✈️"))
    }
}
