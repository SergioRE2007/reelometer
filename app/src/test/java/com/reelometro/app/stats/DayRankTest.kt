package com.reelometro.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayRankTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val first = today.minusDays(9)

    @Test
    fun `el puesto es uno más que los días con más vídeos`() {
        val r = DayRank.of(today.minusDays(1), today, first, count = 270, daysAbove = 2)
        assertEquals(3, r.rank)
        assertEquals(10, r.totalDays)
        assertEquals(first, r.firstDay)
        assertTrue(r.isRanked)
    }

    @Test
    fun `los empates en el máximo son récord compartido`() {
        assertEquals(1, DayRank.of(today, today, first, count = 300, daysAbove = 0).rank)
    }

    @Test
    fun `sin vídeos o fuera del historial no hay puesto`() {
        assertFalse(DayRank.of(today, today, first, count = 0, daysAbove = 0).isRanked)
        assertFalse("antes del primer registro", DayRank.of(first.minusDays(1), today, first, count = 5, daysAbove = 0).isRanked)
        assertFalse("futuro", DayRank.of(today.plusDays(1), today, first, count = 5, daysAbove = 0).isRanked)
    }

    @Test
    fun `sin ningún registro no hay días contados`() {
        val r = DayRank.of(today, today, null, count = 0, daysAbove = 0)
        assertEquals(0, r.totalDays)
        assertEquals(0, r.rank)
        assertNull(r.firstDay)
    }

    @Test
    fun `el primer día cuenta como un día contado`() {
        val r = DayRank.of(today, today, today, count = 12, daysAbove = 0)
        assertEquals(1, r.totalDays)
        assertEquals(1, r.rank)
    }
}
