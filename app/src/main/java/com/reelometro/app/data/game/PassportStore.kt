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
import com.reelometro.app.game.PassportMemory
import com.reelometro.app.game.PassportMemoryCodec
import com.reelometro.app.game.PassportMemoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Memoria del pasaporte en el móvil: un DataStore propio (`passport.preferences_pb`, en los ficheros
 * privados de la app, fuera de la copia de seguridad) con una sola clave que guarda el JSON de
 * [PassportMemory] ([PassportMemoryCodec]). No es Room: así no hay que cambiar el esquema.
 *
 * Una sola instancia por proceso (DataStore no admite dos sobre el mismo fichero): la crea
 * `ReelometroApp.passport`.
 */
class PassportStore internal constructor(private val store: DataStore<Preferences>) : PassportMemoryStore {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.applicationContext.preferencesDataStoreFile("passport") },
        ),
    )

    override val memory: Flow<PassportMemory> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> PassportMemoryCodec.decode(prefs[MEMORY]) }
        .distinctUntilChanged()

    override suspend fun update(transform: (PassportMemory) -> PassportMemory) {
        store.edit { prefs ->
            val current = PassportMemoryCodec.decode(prefs[MEMORY])
            val next = transform(current)
            if (next != current) prefs[MEMORY] = PassportMemoryCodec.encode(next)
        }
    }

    private companion object {
        val MEMORY = stringPreferencesKey("memory")
    }
}
