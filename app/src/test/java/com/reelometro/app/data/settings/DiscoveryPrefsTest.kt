package com.reelometro.app.data.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiscoveryPrefsTest {

    @Test
    fun `sin decidir se ve todo`() {
        assertEquals(DiscoveryState.ALL_VISIBLE, DiscoveryPrefs.read(mutablePreferencesOf()))
        assertEquals(DiscoveryState.ALL_VISIBLE, DiscoveryPrefs.read(mutablePreferencesOf(DiscoveryPrefs.PROGRESSIVE to false)))
    }

    @Test
    fun `progresiva sin más claves es una instalación nueva`() {
        assertEquals(DiscoveryState.FRESH, DiscoveryPrefs.read(mutablePreferencesOf(DiscoveryPrefs.PROGRESSIVE to true)))
    }

    @Test
    fun `valores fuera de rango se recortan`() {
        val p = mutablePreferencesOf(
            DiscoveryPrefs.PROGRESSIVE to true,
            DiscoveryPrefs.TABS to 9,
            DiscoveryPrefs.ANNOUNCED to -3,
        )
        val state = DiscoveryPrefs.read(p)
        assertEquals(4, state.tabs)
        assertEquals(1, state.announced)
    }

    @Test
    fun `se decide una vez y solo en una instalación nueva`() {
        val fresh = mutablePreferencesOf()
        DiscoveryPrefs.decide(fresh, onboardingDone = false, tourDone = false, disclosureAccepted = false)
        assertEquals(DiscoveryState.FRESH, DiscoveryPrefs.read(fresh))
        // Ya decidida: al terminar el onboarding no cambia.
        DiscoveryPrefs.decide(fresh, onboardingDone = true, tourDone = true, disclosureAccepted = true)
        assertEquals(DiscoveryState.FRESH, DiscoveryPrefs.read(fresh))

        val existing = mutablePreferencesOf()
        DiscoveryPrefs.decide(existing, onboardingDone = true, tourDone = false, disclosureAccepted = true)
        assertEquals(false, existing[DiscoveryPrefs.PROGRESSIVE])
        DiscoveryPrefs.decide(existing, onboardingDone = false, tourDone = false, disclosureAccepted = false)
        assertEquals(DiscoveryState.ALL_VISIBLE, DiscoveryPrefs.read(existing))
    }

    @Test
    fun `actualizar guarda el cambio y no toca a los usuarios de antes`() {
        val p = mutablePreferencesOf(DiscoveryPrefs.PROGRESSIVE to true)
        DiscoveryPrefs.update(p) { Discovery.revealTo(it, 2, 42) }
        assertEquals(DiscoveryState(progressive = true, tabs = 2, tabsDay = 42, announced = 2), DiscoveryPrefs.read(p))

        val old = mutablePreferencesOf(DiscoveryPrefs.PROGRESSIVE to false)
        DiscoveryPrefs.update(old) { DiscoveryState.FRESH }
        assertFalse(old.contains(DiscoveryPrefs.TABS))
    }
}
