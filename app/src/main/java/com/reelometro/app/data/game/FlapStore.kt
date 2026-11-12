package com.reelometro.app.data.game

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.reelometro.app.game.FlapSeen
import com.reelometro.app.game.FlapSeenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Lo último que el usuario vio de Flap ([FlapSeen]: estado y día, `EN_FORMA|2026-11-04`) en el DataStore
 * propio de siempre (`passport.preferences_pb`, en los ficheros privados de la app, fuera de la copia de
 * seguridad). No es Room: así no hay que cambiar el esquema. Un valor que no se entiende se lee como
 * «nunca visto».
 *
 * La memoria del pasaporte de antes (clave `memory`: fechas de sellos, retos, eventos) ya no se usa: se
 * borra en cuanto se escribe algo y al arrancar la app ([dropLegacy]).
 *
 * Una sola instancia por proceso (DataStore no admite dos sobre el mismo fichero): la crea
 * `ReelometroApp.flap`.
 */
class FlapStore internal constructor(private val store: DataStore<Preferences>) : FlapSeenStore {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.applicationContext.preferencesDataStoreFile(FILE) },
        ),
    )

    private val stored: Flow<Preferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val seen: Flow<FlapSeen?> = stored
        .map { FlapSeen.decode(it[SEEN]) }
        .distinctUntilChanged()

    override suspend fun save(seen: FlapSeen) {
        val value = seen.encode()
        store.edit { prefs ->
            if (LEGACY_MEMORY in prefs) prefs.remove(LEGACY_MEMORY)
            if (prefs[SEEN] != value) prefs[SEEN] = value
        }
    }

    override suspend fun clear() {
        store.edit { prefs ->
            if (SEEN in prefs) prefs.remove(SEEN)
            if (LEGACY_MEMORY in prefs) prefs.remove(LEGACY_MEMORY)
        }
    }

    /** Borra la memoria del pasaporte viejo si sigue ahí; si no está, no escribe nada. */
    override suspend fun dropLegacy() {
        if (LEGACY_MEMORY !in stored.first()) return
        store.edit { prefs ->
            if (LEGACY_MEMORY in prefs) prefs.remove(LEGACY_MEMORY)
        }
    }

    internal companion object {
        /** El fichero de siempre (`passport.preferences_pb`): el del pasaporte, ahora con lo de Flap. */
        const val FILE = "passport"
        val SEEN = stringPreferencesKey("flap_seen")

        /** La memoria del pasaporte de antes de la 1.12.0 (JSON con fechas de sellos): se borra. */
        val LEGACY_MEMORY = stringPreferencesKey("memory")
    }
}
