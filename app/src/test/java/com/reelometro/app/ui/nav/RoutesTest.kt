package com.reelometro.app.ui.nav

import com.reelometro.app.data.settings.DiscoveryFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesTest {

    @Test
    fun `las pestañas son un prefijo que crece`() {
        assertEquals(listOf<Any>(TodayDest), tabDests(1))
        assertEquals(listOf<Any>(TodayDest), tabDests(0))
        assertEquals(listOf(TodayDest, StatsDest), tabDests(2))
        assertEquals(TabDests, tabDests(9))
        assertEquals(TabDests, tabDests(passportEnabled = true))
        assertEquals(listOf(TodayDest, StatsDest, WrappedDest), tabDests(passportEnabled = false))
    }

    @Test
    fun `cada función lleva a su pestaña`() {
        assertEquals(StatsDest, DiscoveryFeature.STATS.dest())
        assertEquals(WrappedDest, DiscoveryFeature.WRAPPED.dest())
        assertEquals(PassportDest, DiscoveryFeature.PASSPORT.dest())
        DiscoveryFeature.entries.forEach { assertEquals(it.dest(), TabDests[it.tabs - 1]) }
    }

    @Test
    fun `el enlace flap abre la pestaña de Flap y los de antes siguen valiendo`() {
        assertEquals(PassportDest, DeepLinks.parse("flap"))
        assertEquals(PassportDest, DeepLinks.parse(" FLAP "))
        assertEquals(PassportDest, DeepLinks.parse("passport"))
        assertEquals(PassportDest, DeepLinks.parse("pasaporte"))
        assertEquals(TodayDest, DeepLinks.parse("today"))
        assertNull(DeepLinks.parse("flaps"))
        assertNull(DeepLinks.parse(""))
    }
}
