package com.reelometro.app.data.settings

import com.reelometro.app.data.settings.DiscoveryFeature.PASSPORT
import com.reelometro.app.data.settings.DiscoveryFeature.STATS
import com.reelometro.app.data.settings.DiscoveryFeature.WRAPPED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DiscoveryTest {

    /** Martes 3 nov 2026. */
    private val tuesday = LocalDate.of(2026, 11, 3)
    private val monday = LocalDate.of(2026, 11, 2)
    private val sunday = LocalDate.of(2026, 11, 1)

    private val fresh = DiscoveryState.FRESH

    @Test
    fun `solo una instalación nueva empieza progresiva`() {
        assertTrue(Discovery.startsProgressive(onboardingDone = false, tourDone = false, disclosureAccepted = false))
        assertFalse(Discovery.startsProgressive(onboardingDone = true, tourDone = false, disclosureAccepted = false))
        assertFalse(Discovery.startsProgressive(onboardingDone = false, tourDone = true, disclosureAccepted = false))
        assertFalse(Discovery.startsProgressive(onboardingDone = false, tourDone = false, disclosureAccepted = true))
    }

    @Test
    fun `pestañas merecidas - sin datos, día 1 y día 2`() {
        assertEquals(1, Discovery.eligibleTabs(null, tuesday))
        assertEquals(1, Discovery.eligibleTabs(tuesday, tuesday))
        assertEquals(1, Discovery.eligibleTabs(tuesday.plusDays(1), tuesday))
        assertEquals(2, Discovery.eligibleTabs(monday, tuesday))
    }

    @Test
    fun `empezando en domingo, el resumen llega el martes y el pasaporte el jueves`() {
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(2, Discovery.eligibleTabs(sunday, sunday.plusDays(1)))
        assertEquals(3, Discovery.eligibleTabs(sunday, sunday.plusDays(2)))
        assertEquals(3, Discovery.eligibleTabs(sunday, sunday.plusDays(3)))
        assertEquals(4, Discovery.eligibleTabs(sunday, sunday.plusDays(4)))
    }

    @Test
    fun `empezando en lunes, hasta el lunes siguiente no hay semana cerrada`() {
        for (offset in 1L..6L) assertEquals("día ${offset + 1}", 2, Discovery.eligibleTabs(monday, monday.plusDays(offset)))
        assertEquals(4, Discovery.eligibleTabs(monday, monday.plusDays(7)))
    }

    @Test
    fun `sube una pestaña por día y el mismo día no vuelve a subir`() {
        val firstDay = sunday.minusDays(10)
        val day1 = Discovery.advance(fresh, firstDay, tuesday)
        assertEquals(2, day1.tabs)
        assertEquals(tuesday.toEpochDay(), day1.tabsDay)
        assertSame(day1, Discovery.advance(day1, firstDay, tuesday))
        val day2 = Discovery.advance(day1, firstDay, tuesday.plusDays(1))
        assertEquals(3, day2.tabs)
        val day3 = Discovery.advance(day2, firstDay, tuesday.plusDays(2))
        assertEquals(4, day3.tabs)
        assertSame(day3, Discovery.advance(day3, firstDay, tuesday.plusDays(3)))
        assertEquals("los anuncios no se tocan al subir", 1, day3.announced)
    }

    @Test
    fun `nunca baja - ni sin datos ni con fechas raras`() {
        val state = fresh.copy(tabs = 3, tabsDay = 1)
        assertSame(state, Discovery.advance(state, null, tuesday))
        assertSame(state, Discovery.advance(state, tuesday.plusDays(5), tuesday))
        assertSame(state, Discovery.advance(state, tuesday, tuesday))
    }

    @Test
    fun `sin el modo progresivo no cambia nada`() {
        val state = DiscoveryState.ALL_VISIBLE
        assertSame(state, Discovery.advance(state, sunday.minusDays(30), tuesday))
        assertSame(state, Discovery.announce(state, STATS))
        assertSame(state, Discovery.revealTo(state, 2, 1))
        assertSame(state, Discovery.showAll(state))
        assertNull(Discovery.pendingNews(state, true))
        assertTrue(Discovery.hidden(state, true).isEmpty())
    }

    @Test
    fun `veterano - con solo Hoy y 14 días lo descubre todo sin anuncios`() {
        val firstDay = tuesday.minusDays(13)
        val all = Discovery.advance(fresh, firstDay, tuesday)
        assertEquals(4, all.tabs)
        assertEquals(4, all.announced)
        assertNull(Discovery.pendingNews(all, true))
        // Con dos pestañas ya descubiertas, sigue el ritmo normal.
        val two = fresh.copy(tabs = 2, tabsDay = tuesday.toEpochDay() - 1)
        assertEquals(3, Discovery.advance(two, firstDay, tuesday).tabs)
    }

    @Test
    fun `pestañas visibles`() {
        assertEquals(4, Discovery.visibleTabs(DiscoveryState.ALL_VISIBLE, true))
        assertEquals(3, Discovery.visibleTabs(DiscoveryState.ALL_VISIBLE, false))
        assertEquals(1, Discovery.visibleTabs(fresh, true))
        assertEquals(3, Discovery.visibleTabs(fresh.copy(tabs = 4), false))
        assertEquals(2, Discovery.visibleTabs(fresh.copy(tabs = 2), false))
    }

    @Test
    fun `novedades en orden y nunca más allá de lo visible`() {
        var state = fresh.copy(tabs = 4)
        assertEquals(STATS, Discovery.pendingNews(state, true))
        state = Discovery.announce(state, STATS)
        assertEquals(WRAPPED, Discovery.pendingNews(state, true))
        state = Discovery.announce(state, WRAPPED)
        assertEquals(PASSPORT, Discovery.pendingNews(state, true))
        state = Discovery.announce(state, PASSPORT)
        assertNull(Discovery.pendingNews(state, true))
        assertSame(state, Discovery.announce(state, PASSPORT))

        val two = Discovery.announce(fresh.copy(tabs = 2), STATS)
        assertNull(Discovery.pendingNews(two, true))
        assertEquals("no anuncia lo que no se ha descubierto", 2, Discovery.announce(two, WRAPPED).announced)
    }

    @Test
    fun `anunciar dos veces la misma novedad no se come la siguiente`() {
        val state = fresh.copy(tabs = 3)
        val once = Discovery.announce(state, STATS)
        assertEquals(2, once.announced)
        assertSame(once, Discovery.announce(once, STATS))
        // Y una novedad que aún no tocaba tampoco avanza.
        assertSame(state, Discovery.announce(state, WRAPPED))
    }

    @Test
    fun `con el modo pasaporte apagado no hay novedad del pasaporte hasta encenderlo`() {
        val state = fresh.copy(tabs = 4, announced = 3)
        assertNull(Discovery.pendingNews(state, false))
        assertEquals(PASSPORT, Discovery.pendingNews(state, true))
    }

    @Test
    fun `ir a una pestaña oculta la descubre sin quitar la novedad de las que se salta`() {
        val revealed = Discovery.revealTo(fresh, 3, 99)
        assertEquals(3, revealed.tabs)
        assertEquals("las intermedias conservan su novedad", 1, revealed.announced)
        assertEquals(STATS, Discovery.pendingNews(revealed, true))
        assertEquals(99L, revealed.tabsDay)
        assertSame(revealed, Discovery.revealTo(revealed, 2, 100))
    }

    @Test
    fun `ir a la pestaña siguiente la da por presentada`() {
        val revealed = Discovery.revealTo(fresh, 2, 99)
        assertEquals(2, revealed.tabs)
        assertEquals(2, revealed.announced)
        assertNull(Discovery.pendingNews(revealed, true))
    }

    @Test
    fun `enseñarlo todo deja sin ocultas y sin novedades`() {
        val all = Discovery.showAll(fresh)
        assertTrue(Discovery.hidden(all, true).isEmpty())
        assertNull(Discovery.pendingNews(all, true))
        assertEquals(4, Discovery.visibleTabs(all, true))
    }

    @Test
    fun `las cinco combinaciones de pestañas ocultas`() {
        assertEquals(listOf(STATS, WRAPPED, PASSPORT), Discovery.hidden(fresh, true))
        assertEquals(listOf(STATS, WRAPPED), Discovery.hidden(fresh, false))
        assertEquals(listOf(WRAPPED, PASSPORT), Discovery.hidden(fresh.copy(tabs = 2), true))
        assertEquals(listOf(WRAPPED), Discovery.hidden(fresh.copy(tabs = 2), false))
        assertEquals(listOf(PASSPORT), Discovery.hidden(fresh.copy(tabs = 3), true))
        assertTrue(Discovery.hidden(fresh.copy(tabs = 3), false).isEmpty())
    }
}
