package com.reelometro.app.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.reelometro.app.data.CsvRead
import com.reelometro.app.data.Platform
import com.reelometro.app.data.export.ImportError
import com.reelometro.app.data.export.ImportMode
import com.reelometro.app.data.export.ImportResult
import com.reelometro.app.data.export.ParsedCsv
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.stats.Totals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** DataStore en memoria: el [SettingsRepository] real sin disco ni Android. */
internal class FakePreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

internal fun fakeSettings(): SettingsRepository = SettingsRepository(FakePreferencesStore())

internal class FakeUsage(count: Int = 12, totalMs: Long = 600_000) : UsageData {
    val totalsFlow = MutableStateFlow(Totals(count, totalMs))
    var cleared = false
    var exportedTo: OutputStream? = null

    override val totals: Flow<Totals> = totalsFlow

    /** A quién envías más (para fijar el envío rápido). */
    val topFlow = MutableStateFlow<List<String>>(emptyList())
    override val topRecipients: Flow<List<String>> = topFlow

    override suspend fun exportCsv(out: OutputStream): Int {
        exportedTo = out
        out.write("fecha;app\n".toByteArray())
        return totalsFlow.value.count
    }

    override suspend fun clearAll() {
        cleared = true
        totalsFlow.value = Totals(0, 0)
    }

    /** Lo que devuelve leer un CSV (vista previa) y lo que se importó, con qué modo. */
    var readResult: CsvRead = CsvRead.Failed(ImportError.NOT_REELOMETRO)
    var importFails = false
    val imports = mutableListOf<ImportMode>()

    override suspend fun readCsv(input: InputStream): CsvRead = readResult

    override suspend fun importCsv(csv: ParsedCsv, mode: ImportMode): ImportResult {
        if (importFails) throw IOException("fallo simulado")
        imports += mode
        return ImportResult(mode, imported = csv.videos.size, alreadyPresent = 0, badLines = csv.badLines)
    }
}

internal class FakeSettingsSystem(
    override val needsNotificationPermission: Boolean = true,
    var allowed: Boolean = true,
    var serviceEnabled: Boolean = false,
    var installed: Set<Platform> = Platform.entries.toSet(),
) : SettingsSystem {
    var clearedCalls = 0

    override fun notificationsAllowed(): Boolean = allowed

    override fun isServiceEnabled(): Boolean = serviceEnabled

    override fun isInstalled(platform: Platform): Boolean = platform in installed

    override fun onDataCleared() {
        clearedCalls++
    }

    var importedCalls = 0

    override fun onDataImported() {
        importedCalls++
    }

    /** Cada cambio del modo pasaporte (el real repinta el widget y quita el aviso de nivel). */
    val passportChanges = mutableListOf<Boolean>()

    override fun onPassportModeChanged(enabled: Boolean) {
        passportChanges += enabled
    }

    /** Cada lista de fijados tras fijar o quitar (el real borra la foto de quien ya no está). */
    val pinnedChanges = mutableListOf<List<String>>()

    override fun onQuickSendTargetsChanged(pinned: List<String>) {
        pinnedChanges += pinned
    }

    /** Fotos elegidas de la galería (nombre → uri); [photoReadable] = false simula una foto que no se puede abrir. */
    val chosenPhotos = mutableMapOf<String, String>()
    var photoReadable = true

    override fun setQuickSendPhoto(name: String, uri: String): Boolean {
        if (!photoReadable) return false
        chosenPhotos[name] = uri
        return true
    }

    override fun clearQuickSendPhoto(name: String) {
        chosenPhotos.remove(name)
    }

    override suspend fun loadLicenses(): List<License> = listOf(License("JetBrains Mono", "OFL"))
}
