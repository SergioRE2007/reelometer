package com.reelometro.app.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * Claves de DataStore del descubrimiento progresivo ([Discovery]) y su lectura y escritura, puras
 * (se prueban con `mutablePreferencesOf`). Van aparte de `SettingsRepository` para que sus cambios
 * sean solo aditivos.
 */
internal object DiscoveryPrefs {
    /** Sin clave = aún sin decidir (se ve todo). */
    val PROGRESSIVE = booleanPreferencesKey("discovery_progressive")
    val TABS = intPreferencesKey("discovery_tabs")
    val TABS_DAY = longPreferencesKey("discovery_tabs_day")
    val ANNOUNCED = intPreferencesKey("discovery_announced")

    fun read(p: Preferences): DiscoveryState {
        if (p[PROGRESSIVE] != true) return DiscoveryState.ALL_VISIBLE
        return DiscoveryState(
            progressive = true,
            tabs = (p[TABS] ?: 1).coerceIn(1, Discovery.ALL_TABS),
            tabsDay = p[TABS_DAY],
            announced = (p[ANNOUNCED] ?: 1).coerceIn(1, Discovery.ALL_TABS),
        )
    }

    /** Decide el modo una sola vez (idempotente): progresivo solo en una instalación nueva. */
    fun decide(p: MutablePreferences, onboardingDone: Boolean, tourDone: Boolean, disclosureAccepted: Boolean) {
        if (p.contains(PROGRESSIVE)) return
        val progressive = Discovery.startsProgressive(onboardingDone, tourDone, disclosureAccepted)
        p[PROGRESSIVE] = progressive
        if (progressive) {
            p[TABS] = DiscoveryState.FRESH.tabs
            p[ANNOUNCED] = DiscoveryState.FRESH.announced
        }
    }

    /** Lee, cambia y guarda (dentro del mismo `edit`); no escribe nada si no cambia o si no es progresivo. */
    fun update(p: MutablePreferences, change: (DiscoveryState) -> DiscoveryState) {
        val current = read(p)
        if (!current.progressive) return
        val next = change(current)
        if (next == current) return
        p[TABS] = next.tabs.coerceIn(1, Discovery.ALL_TABS)
        p[ANNOUNCED] = next.announced.coerceIn(1, Discovery.ALL_TABS)
        val day = next.tabsDay
        if (day == null) p.remove(TABS_DAY) else p[TABS_DAY] = day
    }
}
