package com.reelometro.app.ui.settings

import com.reelometro.app.copy.SettingsCopy
import com.reelometro.app.copy.Words
import com.reelometro.app.stats.Format
import com.reelometro.app.data.Category
import com.reelometro.app.data.CsvRead
import com.reelometro.app.data.Platform
import com.reelometro.app.data.export.ImportError
import com.reelometro.app.data.export.ImportMode
import com.reelometro.app.data.export.ImportPreview
import com.reelometro.app.data.export.ImportResult
import com.reelometro.app.data.export.ImportedVideo
import com.reelometro.app.data.export.ParsedCsv
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.data.settings.ThemeMode
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.reelometro.app.detection.BubbleLayout
import com.reelometro.app.detection.BubbleSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        settings: SettingsRepository = fakeSettings(),
        usage: FakeUsage = FakeUsage(),
        system: FakeSettingsSystem = FakeSettingsSystem(),
        effects: MutableList<SettingsEffect> = mutableListOf(),
    ): SettingsViewModel {
        val vm = SettingsViewModel(
            settings = settings,
            usage = usage,
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

    private fun SettingsViewModel.content(): SettingsState =
        (state.value as SettingsUiState.Content).settings

    // ------------------------------------------------------------------ objetivo

    @Test
    fun `el objetivo se ve al momento y se guarda al dejar de tocarlo`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        assertNull(vm.content().goal)

        vm.addGoal()
        advanceUntilIdle()
        assertEquals(SettingsViewModel.DEFAULT_GOAL, settings.current().dailyGoal)

        vm.setGoal(160)
        vm.setGoal(170)
        runCurrent()
        assertEquals(170, vm.content().goal)
        assertEquals("aún sin guardar", SettingsViewModel.DEFAULT_GOAL, settings.current().dailyGoal)

        advanceTimeBy(SettingsViewModel.GOAL_SAVE_DELAY_MS + 1)
        runCurrent()
        assertEquals(170, settings.current().dailyGoal)
        assertEquals(170, vm.content().goal)
    }

    @Test
    fun `el objetivo se queda entre el mínimo y el máximo`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        vm.setGoal(5_000)
        advanceUntilIdle()
        assertEquals(AppSettings.MAX_GOAL, vm.content().goal)
        assertEquals(AppSettings.MAX_GOAL, settings.current().dailyGoal)
        vm.setGoal(1)
        advanceUntilIdle()
        assertEquals(AppSettings.MIN_GOAL, settings.current().dailyGoal)
    }

    @Test
    fun `quitar el objetivo lo borra al momento y cancela un guardado pendiente`() = runTest(dispatcher) {
        val settings = fakeSettings()
        settings.setDailyGoal(150)
        val vm = viewModel(settings)
        assertEquals(150, vm.content().goal)

        vm.setGoal(200)
        vm.removeGoal()
        advanceUntilIdle()
        assertNull(vm.content().goal)
        assertNull(settings.current().dailyGoal)
    }

    // ------------------------------------------------------------------ tema y apps

    @Test
    fun `el tema se guarda y se refleja en el estado`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        assertEquals(ThemeMode.SYSTEM, vm.content().themeMode)

        vm.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, settings.current().themeMode)
        assertEquals(ThemeMode.DARK, vm.content().themeMode)

        vm.setTheme(ThemeMode.LIGHT)
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, vm.content().themeMode)
    }

    @Test
    fun `las apps dicen si están instaladas y se pueden apagar`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings, system = FakeSettingsSystem(installed = setOf(Platform.YOUTUBE)))
        val apps = vm.content().apps.associateBy { it.platform }
        assertFalse(apps.getValue(Platform.TIKTOK).installed)
        assertTrue(apps.getValue(Platform.YOUTUBE).installed)

        Platform.entries.forEach { vm.setPlatformEnabled(it, false) }
        advanceUntilIdle()
        assertTrue(vm.content().allAppsOff)
        assertTrue(settings.current().enabledPlatforms.isEmpty())
    }

    // ------------------------------------------------------------------ 7 toques

    @Test
    fun `siete toques en la versión desbloquean la calibración con cuenta atrás discreta`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val effects = mutableListOf<SettingsEffect>()
        val vm = viewModel(settings, effects = effects)
        val messages = mutableListOf<String?>()
        repeat(6) {
            vm.onVersionTap()
            now += 300
            runCurrent()
            messages += vm.content().aboutMessage
        }
        // Dos toques sin decir nada; luego, la cuenta atrás.
        assertEquals(
            listOf(
                null,
                null,
                SettingsCopy.calibrationTapsLeft(4),
                SettingsCopy.calibrationTapsLeft(3),
                SettingsCopy.calibrationTapsLeft(2),
                SettingsCopy.calibrationTapsLeft(1),
            ),
            messages,
        )
        assertFalse(settings.current().calibrationUnlocked)

        vm.onVersionTap()
        advanceUntilIdle()
        assertTrue(settings.current().calibrationUnlocked)
        assertTrue(vm.content().calibrationUnlocked)
        assertEquals(SettingsCopy.CALIBRATION_UNLOCKED, vm.content().aboutMessage)
        assertEquals(listOf<SettingsEffect>(SettingsEffect.Confirm), effects)

        vm.onVersionTap()
        runCurrent()
        assertEquals(SettingsCopy.CALIBRATION_ALREADY, vm.content().aboutMessage)
    }

    @Test
    fun `si se para entre toques la cuenta vuelve a empezar`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        repeat(5) {
            vm.onVersionTap()
            now += 300
        }
        now += UnlockTapCounter.WINDOW_MS + 1
        vm.onVersionTap()
        vm.onVersionTap()
        advanceUntilIdle()
        assertFalse(settings.current().calibrationUnlocked)
        assertNull("tras reiniciar, dos toques no enseñan nada", vm.content().aboutMessage)
    }

    // ------------------------------------------------------------------ datos

    @Test
    fun `exportar sin datos no abre el selector y con datos propone el nombre del día`() = runTest(dispatcher) {
        val effects = mutableListOf<SettingsEffect>()
        val usage = FakeUsage(count = 0, totalMs = 0)
        val vm = viewModel(usage = usage, effects = effects)
        vm.requestExport()
        runCurrent()
        assertEquals(SettingsCopy.EXPORT_EMPTY, vm.content().dataMessage)
        assertTrue(effects.isEmpty())

        usage.totalsFlow.value = com.reelometro.app.stats.Totals(12, 600_000)
        runCurrent()
        vm.requestExport()
        runCurrent()
        assertEquals(listOf<SettingsEffect>(SettingsEffect.PickExportFile("reelometer-2026-11-03.csv")), effects)
    }

    @Test
    fun `exportar escribe el CSV, avisa y vibra, y si no se puede abrir el archivo, lo dice`() = runTest(dispatcher) {
        val effects = mutableListOf<SettingsEffect>()
        val usage = FakeUsage(count = 12)
        val vm = viewModel(usage = usage, effects = effects)
        val out = ByteArrayOutputStream()
        vm.export { out }
        advanceUntilIdle()
        assertEquals(SettingsCopy.exportDone(12), vm.content().dataMessage)
        assertTrue(out.size() > 0)
        assertEquals(listOf<SettingsEffect>(SettingsEffect.Confirm), effects)

        vm.export { null }
        advanceUntilIdle()
        assertEquals(SettingsCopy.EXPORT_FAILED, vm.content().dataMessage)
        assertFalse(vm.content().busy)
    }

    // ------------------------------------------------------------------ importar CSV

    private fun parsed(videos: Int, bad: Int = 0): ParsedCsv = ParsedCsv(
        videos = List(videos) { i ->
            ImportedVideo(Platform.TIKTOK, 1_000L * i, 1_000, null, null, Category.OTROS, 0, null)
        },
        orphanShares = emptyList(),
        recipients = emptyList(),
        blocks = emptyList(),
        badLines = bad,
    )

    @Test
    fun `importar abre el selector, enseña la vista previa y AÑADIR importa, avisa y vibra`() = runTest(dispatcher) {
        val effects = mutableListOf<SettingsEffect>()
        val usage = FakeUsage()
        val system = FakeSettingsSystem()
        val vm = viewModel(usage = usage, system = system, effects = effects)
        vm.requestImport()
        runCurrent()
        assertEquals(listOf<SettingsEffect>(SettingsEffect.PickImportFile), effects)

        val preview = ImportPreview(4, alreadyPresent = 1, from = LocalDate.of(2026, 3, 3), to = LocalDate.of(2026, 11, 3), badLines = 2, hasPassport = true)
        usage.readResult = CsvRead.Ready(parsed(4, bad = 2), preview)
        vm.readImport { ByteArrayInputStream(ByteArray(0)) }
        advanceUntilIdle()
        assertEquals(preview, vm.content().importPreview)
        assertFalse(vm.content().importing)
        val text = SettingsCopy.importPreview(preview)
        assertTrue(text, text.contains(Words.videos(4)) && text.contains(Format.rangeWithYear(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 11, 3))))
        assertTrue(text, text.contains("1 ya estaba guardado") && text.contains("2 líneas no se entienden"))

        vm.confirmImport(ImportMode.ADD)
        advanceUntilIdle()
        assertEquals(listOf(ImportMode.ADD), usage.imports)
        assertNull(vm.content().importPreview)
        assertEquals("Importados: ${Words.videos(4)}. 2 líneas no se entendían.", vm.content().dataMessage)
        runCurrent()
        assertEquals(1, system.importedCalls)
        assertEquals(SettingsEffect.Confirm, effects.last())

        // Ya no hay nada pendiente: otra confirmación no importa dos veces.
        vm.confirmImport(ImportMode.ADD)
        advanceUntilIdle()
        assertEquals(1, usage.imports.size)
    }

    @Test
    fun `importar un archivo que no es de Reelometer lo dice y no abre la vista previa`() = runTest(dispatcher) {
        val usage = FakeUsage()
        val vm = viewModel(usage = usage)
        usage.readResult = CsvRead.Failed(ImportError.NOT_REELOMETRO)
        vm.readImport { ByteArrayInputStream(ByteArray(0)) }
        advanceUntilIdle()
        assertNull(vm.content().importPreview)
        assertEquals(SettingsCopy.importError(ImportError.NOT_REELOMETRO), vm.content().dataMessage)

        vm.readImport { null }
        advanceUntilIdle()
        assertEquals(SettingsCopy.importError(ImportError.FAILED), vm.content().dataMessage)
        assertFalse(vm.content().busy)
    }

    @Test
    fun `cancelar la vista previa no importa y un fallo al importar lo dice`() = runTest(dispatcher) {
        val usage = FakeUsage()
        val system = FakeSettingsSystem()
        val vm = viewModel(usage = usage, system = system)
        usage.readResult = CsvRead.Ready(parsed(2), ImportPreview(2, 0, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), 0, false))
        vm.readImport { ByteArrayInputStream(ByteArray(0)) }
        advanceUntilIdle()
        vm.cancelImport()
        vm.confirmImport(ImportMode.REPLACE)
        advanceUntilIdle()
        assertTrue(usage.imports.isEmpty())
        assertNull(vm.content().importPreview)

        vm.readImport { ByteArrayInputStream(ByteArray(0)) }
        advanceUntilIdle()
        usage.importFails = true
        vm.confirmImport(ImportMode.REPLACE)
        advanceUntilIdle()
        assertEquals(SettingsCopy.importError(ImportError.FAILED), vm.content().dataMessage)
        assertEquals(0, system.importedCalls)
        assertFalse(vm.content().busy)
    }

    @Test
    fun `textos del resultado de importar`() {
        assertEquals("Importado: ${Words.videos(1)}.", SettingsCopy.importDone(ImportResult(ImportMode.ADD, 1, 0, 0)))
        assertEquals(
            "Importados: ${Words.videos(1_004)}. 230 ya estaban. 1 línea no se entendía.",
            SettingsCopy.importDone(ImportResult(ImportMode.ADD, 1_004, 230, 1)),
        )
        assertEquals(
            "No había nada nuevo: todo ya estaba guardado.",
            SettingsCopy.importDone(ImportResult(ImportMode.ADD, 0, 230, 0)),
        )
    }

    @Test
    fun `borrar todo vacía los datos, refresca el widget y lo dice`() = runTest(dispatcher) {
        val usage = FakeUsage(count = 12)
        val system = FakeSettingsSystem()
        val vm = viewModel(usage = usage, system = system)
        vm.clearAll()
        advanceUntilIdle()
        assertTrue(usage.cleared)
        assertEquals(1, system.clearedCalls)
        assertEquals(SettingsCopy.DELETE_DONE, vm.content().dataMessage)
        assertEquals(0, vm.content().totals.count)
    }

    // ------------------------------------------------------------------ avisos

    @Test
    fun `encender un aviso sin permiso lo pide y si Android ya no deja, manda a sus ajustes`() = runTest(dispatcher) {
        val effects = mutableListOf<SettingsEffect>()
        val settings = fakeSettings()
        settings.setNotifyDaily(false)
        val vm = viewModel(settings, system = FakeSettingsSystem(allowed = false), effects = effects)
        assertTrue(vm.content().canRequestNotifications)

        vm.setNotifyDaily(true)
        advanceUntilIdle()
        assertTrue(settings.current().notifyDaily)
        assertEquals(listOf<SettingsEffect>(SettingsEffect.RequestNotificationPermission), effects)

        vm.onNotificationPermission(granted = false, canAskAgain = false)
        advanceUntilIdle()
        assertFalse(vm.content().canRequestNotifications)
    }

    // ------------------------------------------------------------------ pasaporte, aviso en directo y recorrido

    @Test
    fun `el modo pasaporte viene encendido, se apaga, se guarda y refresca el widget`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val system = FakeSettingsSystem()
        val vm = viewModel(settings, system = system)
        assertTrue(vm.content().passportEnabled)

        vm.setPassportEnabled(false)
        advanceUntilIdle()
        assertFalse(settings.current().passportEnabled)
        assertFalse(vm.content().passportEnabled)
        assertEquals(listOf(false), system.passportChanges)

        vm.setPassportEnabled(true)
        advanceUntilIdle()
        assertTrue(settings.current().passportEnabled)
        assertEquals(listOf(false, true), system.passportChanges)
    }

    @Test
    fun `el aviso en directo viene apagado y sin objetivo no se puede encender`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        assertFalse(vm.content().notifyGoalLive)
        assertFalse(vm.content().goalLiveAvailable)

        vm.setNotifyGoalLive(true)
        advanceUntilIdle()
        assertFalse(settings.current().notifyGoalLive)
        assertEquals(SettingsCopy.GOAL_LIVE_NEEDS_GOAL, SettingsCopy.goalLiveDescription(vm.content().goal))
    }

    @Test
    fun `con objetivo el aviso en directo se enciende y pide permiso como el diario`() = runTest(dispatcher) {
        val effects = mutableListOf<SettingsEffect>()
        val settings = fakeSettings()
        settings.setDailyGoal(150)
        settings.setNotifyDaily(false)
        settings.setNotifyMonthly(false)
        val vm = viewModel(settings, system = FakeSettingsSystem(allowed = false), effects = effects)
        assertTrue(vm.content().goalLiveAvailable)
        assertFalse(vm.content().anyNotificationOn)

        vm.setNotifyGoalLive(true)
        advanceUntilIdle()
        assertTrue(settings.current().notifyGoalLive)
        assertTrue(vm.content().notifyGoalLive)
        assertTrue("el aviso de permiso sale también por este", vm.content().anyNotificationOn)
        assertEquals(listOf<SettingsEffect>(SettingsEffect.RequestNotificationPermission), effects)

        // Apagarlo no pide nada.
        vm.setNotifyGoalLive(false)
        advanceUntilIdle()
        assertFalse(settings.current().notifyGoalLive)
        assertEquals(1, effects.size)

        // Si luego se quita el objetivo, el aviso deja de contar como encendido.
        vm.setNotifyGoalLive(true)
        vm.removeGoal()
        advanceUntilIdle()
        assertFalse(vm.content().goalLiveAvailable)
        assertFalse(vm.content().anyNotificationOn)
    }

    @Test
    fun `ver el recorrido lo marca como no visto y despues vuelve a hoy`() = runTest(dispatcher) {
        val settings = fakeSettings()
        settings.setTourDone(true)
        val vm = viewModel(settings)
        var back = false

        vm.replayTour(onReady = { back = true })
        advanceUntilIdle()
        assertFalse(settings.current().tourDone)
        assertTrue(back)
    }
    // ------------------------------------------------------------------ envío rápido

    @Test
    fun `envio rapido apagado por defecto y solo se enciende con a quien envias`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        assertFalse(vm.content().quickSendEnabled)
        assertFalse(vm.content().quickSendAvailable)
        assertTrue(vm.content().quickSendLines.isEmpty())

        // Sin «A quién envías» no se enciende: busca en la hoja nombres que solo se leen con ese ajuste.
        vm.setQuickSendEnabled(true)
        advanceUntilIdle()
        assertFalse(settings.current().quickSendEnabled)

        vm.setRecordRecipients(true)
        advanceUntilIdle()
        assertTrue(vm.content().quickSendAvailable)
        vm.setQuickSendEnabled(true)
        advanceUntilIdle()
        assertTrue(settings.current().quickSendEnabled)
        assertTrue(vm.content().quickSendEnabled)
    }

    @Test
    fun `dos toques seguidos en fijar no se pisan`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val usage = FakeUsage().apply { topFlow.value = listOf("Ana", "Primos") }
        val vm = viewModel(settings, usage)
        vm.toggleQuickSendTarget("Ana")
        vm.toggleQuickSendTarget("Primos")
        advanceUntilIdle()
        assertEquals(listOf("Ana", "Primos"), settings.current().quickSendTargets)
        // Doble toque en la misma fila: fija y quita.
        vm.toggleQuickSendTarget("Primos")
        vm.toggleQuickSendTarget("Primos")
        advanceUntilIdle()
        assertEquals(listOf("Ana", "Primos"), settings.current().quickSendTargets)
    }

    @Test
    fun `borrar todo tambien olvida los fijados del envio rapido`() = runTest(dispatcher) {
        val settings = fakeSettings()
        settings.setQuickSendTargets(listOf("Ana"))
        val vm = viewModel(settings)
        vm.clearAll()
        advanceUntilIdle()
        assertTrue(settings.current().quickSendTargets.isEmpty())
    }

    @Test
    fun `se fijan hasta tres del historial en el orden en que se fijan`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val usage = FakeUsage().apply { topFlow.value = listOf("Los del piso", "Ana", "Primos", "Marta") }
        val vm = viewModel(settings, usage)
        assertEquals(listOf("Los del piso", "Ana", "Primos", "Marta"), vm.content().quickSendLines.map { it.name })
        assertTrue(vm.content().quickSendLines.none { it.pinned })

        vm.toggleQuickSendTarget("Primos")
        advanceUntilIdle()
        vm.toggleQuickSendTarget("Los del piso")
        advanceUntilIdle()
        vm.toggleQuickSendTarget("Marta")
        advanceUntilIdle()
        assertEquals(listOf("Primos", "Los del piso", "Marta"), settings.current().quickSendTargets)
        assertTrue(vm.content().quickSendFull)

        // El cuarto no entra hasta quitar uno.
        vm.toggleQuickSendTarget("Ana")
        advanceUntilIdle()
        assertEquals(listOf("Primos", "Los del piso", "Marta"), settings.current().quickSendTargets)

        vm.toggleQuickSendTarget("Los del piso")
        advanceUntilIdle()
        assertEquals(listOf("Primos", "Marta"), settings.current().quickSendTargets)
        assertFalse(vm.content().quickSendFull)
        assertEquals(
            listOf(QuickSendLine("Los del piso", false), QuickSendLine("Ana", false), QuickSendLine("Primos", true), QuickSendLine("Marta", true)),
            vm.content().quickSendLines,
        )
    }

    @Test
    fun `un fijado que ya no sale en el historial sigue en la lista para poder quitarlo`() = runTest(dispatcher) {
        val settings = fakeSettings()
        settings.setQuickSendTargets(listOf("Primos", "  ", "Primos", "Abuela"))
        assertEquals(listOf("Primos", "Abuela"), settings.current().quickSendTargets)
        val usage = FakeUsage().apply { topFlow.value = listOf("Ana", "Primos") }
        val vm = viewModel(settings, usage)
        assertEquals(
            listOf(QuickSendLine("Ana", false), QuickSendLine("Primos", true), QuickSendLine("Abuela", true)),
            vm.content().quickSendLines,
        )
        // Apagar el envío rápido no olvida a quién tenías fijado.
        vm.setQuickSendEnabled(true)
        vm.setQuickSendEnabled(false)
        advanceUntilIdle()
        assertEquals(listOf("Primos", "Abuela"), settings.current().quickSendTargets)
    }

    @Test
    fun `quitar un fijado avisa para borrar su foto`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val system = FakeSettingsSystem()
        val usage = FakeUsage().apply { topFlow.value = listOf("Ana", "Primos") }
        val vm = viewModel(settings, usage, system)
        vm.toggleQuickSendTarget("Ana")
        advanceUntilIdle()
        vm.toggleQuickSendTarget("Primos")
        advanceUntilIdle()
        vm.toggleQuickSendTarget("Ana")
        advanceUntilIdle()
        assertEquals(listOf(listOf("Ana"), listOf("Ana", "Primos"), listOf("Primos")), system.pinnedChanges)
        // Cada fila lleva las iniciales para cuando no hay foto.
        assertEquals(listOf("A", "P"), vm.content().quickSendLines.map { it.initials })
    }

    @Test
    fun `elegir y quitar la foto de un fijado`() = runTest(dispatcher) {
        val settings = fakeSettings()
        settings.setQuickSendTargets(listOf("Primos"))
        val system = FakeSettingsSystem()
        val usage = FakeUsage().apply { topFlow.value = listOf("Ana", "Primos") }
        val vm = viewModel(settings, usage, system)

        // Solo a un fijado.
        vm.setQuickSendPhoto("Ana", "content://media/picker/0/1")
        advanceUntilIdle()
        assertTrue(system.chosenPhotos.isEmpty())

        vm.setQuickSendPhoto("Primos", "content://media/picker/0/2")
        advanceUntilIdle()
        assertEquals(mapOf("Primos" to "content://media/picker/0/2"), system.chosenPhotos)
        assertEquals(SettingsCopy.QUICK_SEND_PHOTO_SET, vm.content().quickSendMessage)

        system.photoReadable = false
        vm.setQuickSendPhoto("Primos", "content://media/picker/0/3")
        advanceUntilIdle()
        assertEquals(SettingsCopy.QUICK_SEND_PHOTO_FAILED, vm.content().quickSendMessage)
        assertEquals("content://media/picker/0/2", system.chosenPhotos["Primos"])

        vm.clearQuickSendPhoto("Primos")
        advanceUntilIdle()
        assertTrue(system.chosenPhotos.isEmpty())
        assertEquals(SettingsCopy.QUICK_SEND_PHOTO_CLEARED, vm.content().quickSendMessage)
    }

    @Test
    fun `recolocar devuelve las burbujas a su sitio`() = runTest(dispatcher) {
        val settings = fakeSettings()
        val vm = viewModel(settings)
        assertFalse(vm.content().quickSendMoved)
        settings.setQuickSendBubble(BubbleSpot(x = 0.3f, y = 0.8f))
        advanceUntilIdle()
        assertEquals(BubbleSpot(x = 0.3f, y = 0.8f), settings.current().quickSendBubble)
        assertTrue(vm.content().quickSendMoved)

        vm.relocateQuickSend()
        advanceUntilIdle()
        assertEquals(null, settings.current().quickSendBubble)
        assertFalse(vm.content().quickSendMoved)
        assertEquals(SettingsCopy.QUICK_SEND_RELOCATED, vm.content().quickSendMessage)
    }

    @Test
    fun `la posicion guardada no se sale de la pantalla`() = runTest(dispatcher) {
        val settings = fakeSettings()
        settings.setQuickSendBubble(BubbleSpot(x = -2f, y = 7f))
        assertEquals(BubbleSpot(x = 0f, y = 1f), settings.current().quickSendBubble)
        settings.setQuickSendBubble(BubbleSpot(x = Float.NaN, y = Float.NaN))
        assertEquals(BubbleLayout.DEFAULT, settings.current().quickSendBubble)
    }

    @Test
    fun `la posicion de la 1_4_1 se lee convertida y no se pierde`() = runTest(dispatcher) {
        val store = FakePreferencesStore()
        store.edit {
            it[booleanPreferencesKey("quick_send_bubble_left")] = true
            it[floatPreferencesKey("quick_send_bubble_y")] = 0.7f
        }
        val settings = SettingsRepository(store)
        assertEquals(BubbleLayout.fromLegacy(left = true, centerY = 0.7f), settings.current().quickSendBubble)
        assertEquals(BubbleSpot(BubbleLayout.LEGACY_EDGE_X, 0.7f), settings.current().quickSendBubble)

        // Al guardar la nueva, la de antes se va (y RECOLOCAR quita las dos).
        settings.setQuickSendBubble(BubbleSpot(x = 0.5f, y = 0.85f))
        assertEquals(BubbleSpot(x = 0.5f, y = 0.85f), settings.current().quickSendBubble)
        assertNull(store.data.first()[floatPreferencesKey("quick_send_bubble_y")])
        assertNull(store.data.first()[booleanPreferencesKey("quick_send_bubble_left")])

        store.edit { it[floatPreferencesKey("quick_send_bubble_y")] = 0.2f }
        assertEquals("la nueva manda sobre la vieja", BubbleSpot(x = 0.5f, y = 0.85f), settings.current().quickSendBubble)
        settings.resetQuickSendBubble()
        assertNull(settings.current().quickSendBubble)
    }
}
