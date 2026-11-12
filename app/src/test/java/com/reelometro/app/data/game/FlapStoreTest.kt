package com.reelometro.app.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapSeen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** DataStore en memoria (sin disco ni Android) que cuenta las escrituras que cambian algo. */
private class FlapPrefsInMemory(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    val state = MutableStateFlow(initial)
    var writes = 0

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        if (next != state.value) writes++
        state.value = next
        return next
    }
}

/** Lo visto de Flap en DataStore (`flap_seen` = `MOOD|yyyy-MM-dd`) y la limpieza del pasaporte viejo. Solo JVM. */
class FlapStoreTest {

    private val seenKey = stringPreferencesKey("flap_seen")
    private val legacyKey = stringPreferencesKey("memory")
    private val day = LocalDate.of(2026, 11, 4)

    /** Lo que dejaba la 1.11.x en `passport.preferences_pb`: el JSON de la memoria del pasaporte. */
    private fun withLegacy(): Preferences = emptyPreferences().toMutablePreferences().apply {
        set(legacyKey, "{\"since\":\"2026-03-01\",\"stamps\":{\"RACHA_3\":\"2026-03-05\"}}")
    }

    @Test
    fun `guarda lo visto con su día y lo vuelve a leer`() = runBlocking {
        val prefs = FlapPrefsInMemory()
        val store = FlapStore(prefs)
        assertNull(store.seen.first())
        store.save(FlapSeen(FlapMood.CANSADO, day))
        assertEquals(FlapSeen(FlapMood.CANSADO, day), store.seen.first())
        assertEquals("CANSADO|2026-11-04", prefs.state.value[seenKey])
        store.save(FlapSeen(FlapMood.KO, day.plusDays(1)))
        assertEquals(FlapSeen(FlapMood.KO, day.plusDays(1)), store.seen.first())
    }

    @Test
    fun `si nada cambia no se escribe`() = runBlocking {
        val prefs = FlapPrefsInMemory()
        val store = FlapStore(prefs)
        store.save(FlapSeen(FlapMood.NORMAL, day))
        store.save(FlapSeen(FlapMood.NORMAL, day))
        assertEquals(1, prefs.writes)
        store.clear()
        store.clear()
        assertEquals(2, prefs.writes)
        store.dropLegacy()
        assertEquals(2, prefs.writes)
    }

    @Test
    fun `un valor que no se entiende se lee como nunca visto y se puede volver a escribir`() = runBlocking {
        for (garbage in listOf("ZOMBI|2026-11-04", "{roto", "", "KO|mañana")) {
            val broken = emptyPreferences().toMutablePreferences().apply { set(seenKey, garbage) }
            val store = FlapStore(FlapPrefsInMemory(broken))
            assertNull(garbage, store.seen.first())
            store.save(FlapSeen(FlapMood.EN_FORMA, day))
            assertEquals(garbage, FlapSeen(FlapMood.EN_FORMA, day), store.seen.first())
        }
    }

    @Test
    fun `la memoria del pasaporte viejo se borra al arrancar`() = runBlocking {
        val prefs = FlapPrefsInMemory(withLegacy())
        val store = FlapStore(prefs)
        assertNull("la memoria vieja no es lo visto de Flap", store.seen.first())
        store.dropLegacy()
        assertFalse(legacyKey in prefs.state.value)
        assertEquals(1, prefs.writes)
        store.dropLegacy()
        assertEquals(1, prefs.writes)
    }

    @Test
    fun `la memoria del pasaporte viejo se borra también en la primera escritura y con BORRAR TODO`() = runBlocking {
        val saved = FlapPrefsInMemory(withLegacy())
        FlapStore(saved).save(FlapSeen(FlapMood.ENFERMO, day))
        assertFalse(legacyKey in saved.state.value)
        assertEquals("ENFERMO|2026-11-04", saved.state.value[seenKey])

        val cleared = FlapPrefsInMemory(withLegacy())
        val store = FlapStore(cleared)
        store.clear()
        assertFalse(legacyKey in cleared.state.value)
        assertNull(store.seen.first())
    }

    @Test
    fun `el fichero es el del pasaporte de siempre`() {
        assertEquals("passport", FlapStore.FILE)
        assertEquals(seenKey, FlapStore.SEEN)
        assertEquals(legacyKey, FlapStore.LEGACY_MEMORY)
    }
}
