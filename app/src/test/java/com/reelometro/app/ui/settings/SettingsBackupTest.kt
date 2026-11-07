package com.reelometro.app.ui.settings

import com.reelometro.app.copy.SettingsCopy
import com.reelometro.app.data.backup.BackupError
import com.reelometro.app.data.backup.BackupOutcome
import com.reelometro.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Lo que hace Android con la copia semanal, falso: permisos de carpetas y copias escritas. */
private class BackupSystem(private val base: FakeSettingsSystem = FakeSettingsSystem()) : SettingsSystem by base {
    /** Carpeta → nombre que da Android al quedarse con su permiso (sin entrada: no lo da). */
    val grantable = mutableMapOf("content://tree/download" to "Download", "content://tree/docs" to "Documents")
    val taken = mutableListOf<String>()
    val released = mutableListOf<String>()
    val written = mutableListOf<String>()
    var next: BackupOutcome = BackupOutcome.Done("reelometer-auto-2026-11-03.csv", 12, emptyList())

    override fun takeBackupFolder(uri: String): String? = grantable[uri]?.also { taken += uri }

    override fun releaseBackupFolder(uri: String) {
        released += uri
    }

    override suspend fun writeBackup(uri: String): BackupOutcome {
        written += uri
        return next
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackupTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_794_000_000_000L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        system: BackupSystem,
        settings: SettingsRepository = fakeSettings(),
        effects: MutableList<SettingsEffect> = mutableListOf(),
    ): SettingsViewModel {
        val vm = SettingsViewModel(
            settings = settings,
            usage = FakeUsage(),
            system = system,
            versionName = "1.0.0",
            clock = { now },
            today = { LocalDate.of(2026, 11, 3) },
            io = dispatcher,
        )
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.effects.collect { effects += it } }
        advanceUntilIdle()
        return vm
    }

    private fun SettingsViewModel.content(): SettingsState = (state.value as SettingsUiState.Content).settings

    @Test
    fun `encender abre el selector de carpetas y sigue apagada hasta elegir una`() = runTest(dispatcher) {
        val effects = mutableListOf<SettingsEffect>()
        val vm = viewModel(BackupSystem(), effects = effects)
        vm.setBackup(true)
        runCurrent()
        assertEquals(listOf<SettingsEffect>(SettingsEffect.PickBackupFolder), effects)
        assertFalse(vm.content().backupOn)
    }

    @Test
    fun `elegir carpeta guarda su permiso, hace la primera copia y lo dice`() = runTest(dispatcher) {
        val effects = mutableListOf<SettingsEffect>()
        val system = BackupSystem()
        val settings = fakeSettings()
        val vm = viewModel(system, settings, effects)
        vm.onBackupFolderPicked("content://tree/download")
        advanceUntilIdle()
        val s = vm.content()
        assertTrue(s.backupOn)
        assertEquals("Download", s.backupFolderName)
        assertEquals(listOf("content://tree/download"), system.written)
        assertEquals(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime(), s.backupLast)
        assertNull(s.backupError)
        assertEquals(SettingsCopy.backupResult(system.next, "Download"), s.dataMessage)
        assertTrue(s.dataMessage!!.contains("Download"))
        assertEquals(listOf<SettingsEffect>(SettingsEffect.Confirm), effects)
        assertFalse(s.busy)
        assertEquals(now, settings.current().backupLastMs)
    }

    @Test
    fun `si Android no da permiso a la carpeta, no se enciende y lo dice`() = runTest(dispatcher) {
        val system = BackupSystem()
        val vm = viewModel(system)
        vm.onBackupFolderPicked("content://tree/ajena")
        advanceUntilIdle()
        assertFalse(vm.content().backupOn)
        assertEquals(SettingsCopy.BACKUP_PICK_FAILED, vm.content().dataMessage)
        assertTrue(system.written.isEmpty())
    }

    @Test
    fun `sin permiso al copiar queda el aviso fijo y elegir otra carpeta lo quita`() = runTest(dispatcher) {
        val system = BackupSystem()
        val vm = viewModel(system)
        vm.onBackupFolderPicked("content://tree/download")
        advanceUntilIdle()
        system.next = BackupOutcome.Failed(BackupError.NO_ACCESS)
        vm.backupNow()
        advanceUntilIdle()
        assertEquals(BackupError.NO_ACCESS, vm.content().backupError)
        assertNull(vm.content().dataMessage) // el aviso fijo ya lo dice
        assertNotNull(vm.content().backupLast) // la última buena sigue ahí

        system.next = BackupOutcome.Done("reelometer-auto-2026-11-03.csv", 3, emptyList())
        vm.onBackupFolderPicked("content://tree/docs")
        advanceUntilIdle()
        val s = vm.content()
        assertNull(s.backupError)
        assertEquals("Documents", s.backupFolderName)
        assertEquals(listOf("content://tree/download"), system.released)
    }

    @Test
    fun `sin videos no hay copia ni error`() = runTest(dispatcher) {
        val system = BackupSystem().apply { next = BackupOutcome.Empty }
        val vm = viewModel(system)
        vm.onBackupFolderPicked("content://tree/download")
        advanceUntilIdle()
        assertTrue(vm.content().backupOn)
        assertNull(vm.content().backupLast)
        assertNull(vm.content().backupError)
        assertEquals(SettingsCopy.backupResult(BackupOutcome.Empty, "Download"), vm.content().dataMessage)
    }

    @Test
    fun `apagar olvida la carpeta y suelta el permiso`() = runTest(dispatcher) {
        val system = BackupSystem()
        val settings = fakeSettings()
        val vm = viewModel(system, settings)
        vm.onBackupFolderPicked("content://tree/download")
        advanceUntilIdle()
        vm.setBackup(false)
        advanceUntilIdle()
        assertFalse(vm.content().backupOn)
        assertNull(settings.current().backupFolder)
        assertNull(settings.current().backupLastMs)
        assertEquals(listOf("content://tree/download"), system.released)
    }

    @Test
    fun `el resultado de una copia de otra carpeta no se apunta`() = runTest(dispatcher) {
        val settings = fakeSettings()
        settings.setBackupFolder("content://tree/docs", "Documents")
        settings.recordBackup("content://tree/download", BackupOutcome.Failed(BackupError.NO_ACCESS), now)
        assertNull(settings.current().backupError)
        settings.recordBackup("content://tree/docs", BackupOutcome.Failed(BackupError.FAILED), now)
        assertEquals(BackupError.FAILED, settings.current().backupError)
        settings.recordBackup("content://tree/docs", BackupOutcome.Done("x", 1, emptyList()), now)
        assertNull(settings.current().backupError)
        assertEquals(now, settings.current().backupLastMs)
    }
}
