package com.reelometro.app.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.GameEvent
import com.reelometro.app.game.PassportMemory
import com.reelometro.app.game.StampId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** DataStore en memoria (sin disco ni Android) que cuenta las escrituras que cambian algo. */
private class PassportPrefsInMemory(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
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

/** La memoria del pasaporte en DataStore (JSON en una sola clave). Solo JVM. */
class PassportStoreTest {

    private val key = stringPreferencesKey("memory")

    @Test
    fun `guarda la memoria como JSON y la vuelve a leer`() = runBlocking {
        val prefs = PassportPrefsInMemory()
        val store = PassportStore(prefs)
        assertEquals(PassportMemory(), store.memory.first())
        val day = LocalDate.of(2026, 11, 4)
        store.update { it.withEvent(GameEvent.WRAPPED_SHARED, day).seeing(FlapMood.CANSADO) }
        store.update { it.copy(stamps = it.stamps + (StampId.RACHA_3 to day)) }
        val memory = store.memory.first()
        assertEquals(day, memory.events[GameEvent.WRAPPED_SHARED])
        assertEquals(FlapMood.CANSADO, memory.lastSeenMood)
        assertEquals(day, memory.stamps[StampId.RACHA_3])
        val json = prefs.state.value[key].orEmpty()
        assertTrue(json, "\"RACHA_3\":\"2026-11-04\"" in json)
    }

    @Test
    fun `si nada cambia no se escribe`() = runBlocking {
        val prefs = PassportPrefsInMemory()
        val store = PassportStore(prefs)
        store.update { it }
        assertEquals(0, prefs.writes)
        store.update { it.seeing(FlapMood.ZOMBI) }
        store.update { it.seeing(FlapMood.ZOMBI) }
        assertEquals(1, prefs.writes)
    }

    @Test
    fun `un JSON roto se lee como pasaporte vacío y se puede volver a escribir`() = runBlocking {
        val broken = emptyPreferences().toMutablePreferences().apply { set(key, "{roto") }
        val store = PassportStore(PassportPrefsInMemory(broken))
        assertEquals(PassportMemory(), store.memory.first())
        store.update { it.cleared() }
        assertEquals(PassportMemory(generation = 1), store.memory.first())
    }
}
