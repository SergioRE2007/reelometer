package com.reelometro.app.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelometro.app.BuildConfig
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.SettingsCopy
import com.reelometro.app.data.Platform
import com.reelometro.app.data.CsvRead
import com.reelometro.app.data.ReelRepository
import com.reelometro.app.data.backup.BackupError
import com.reelometro.app.data.backup.BackupOutcome
import com.reelometro.app.data.backup.BackupRules
import com.reelometro.app.data.backup.CsvBackup
import com.reelometro.app.data.export.ImportError
import com.reelometro.app.data.export.ImportMode
import com.reelometro.app.data.export.ImportPreview
import com.reelometro.app.data.export.ImportResult
import com.reelometro.app.data.export.ParsedCsv
import com.reelometro.app.data.settings.AppSettings
import com.reelometro.app.data.settings.SettingsRepository
import com.reelometro.app.data.settings.ThemeMode
import com.reelometro.app.debug.DebugTools
import com.reelometro.app.detection.AvatarImport
import com.reelometro.app.detection.AvatarRules
import com.reelometro.app.detection.AvatarStore
import com.reelometro.app.detection.QuickSendMemory
import com.reelometro.app.stats.Totals
import com.reelometro.app.ui.onboarding.AndroidNotificationAccess
import com.reelometro.app.ui.onboarding.AndroidServiceProbe
import com.reelometro.app.widget.WidgetUpdater
import com.reelometro.app.work.LevelAlert
import com.reelometro.app.work.LiveAlerts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Datos de uso que necesita Ajustes (interfaz para probar el ViewModel sin Room). */
interface UsageData {
    /** Totales de siempre (para «Vídeos guardados» y el diálogo de borrar). */
    val totals: Flow<Totals>

    /** Escribe el CSV en [out] (no lo cierra). Devuelve los vídeos exportados. */
    suspend fun exportCsv(out: OutputStream): Int

    suspend fun clearAll()

    /** Lee un CSV para la vista previa de IMPORTAR (no escribe nada). Con valor por defecto para las fuentes falsas. */
    suspend fun readCsv(input: InputStream): CsvRead = CsvRead.Failed(ImportError.FAILED)

    /** Importa el CSV ya leído (una transacción: o todo o nada). */
    suspend fun importCsv(csv: ParsedCsv, mode: ImportMode): ImportResult = throw IOException("Sin importación")

    /**
     * A quién envías más en Instagram, de siempre (para fijar el envío rápido). Con valor por defecto para
     * que las fuentes falsas de los tests no tengan que darlo.
     */
    val topRecipients: Flow<List<String>> get() = flowOf(emptyList())
}

class RepositoryUsageData(private val repository: ReelRepository) : UsageData {
    override val totals: Flow<Totals> = repository.observeAllTime().map { it.totals }

    override suspend fun exportCsv(out: OutputStream): Int = repository.exportCsv(out)

    override suspend fun clearAll() = repository.clearAll()

    override suspend fun readCsv(input: InputStream): CsvRead = repository.readCsv(input)

    override suspend fun importCsv(csv: ParsedCsv, mode: ImportMode): ImportResult = repository.importCsv(csv, mode)

    override val topRecipients: Flow<List<String>> = repository.observeTopRecipients()
}

/** Licencia de una tipografía (texto de `assets/licenses/`). */
@Immutable
data class License(val name: String, val text: String)

/** Lo que Ajustes pregunta a Android (interfaz para probar el ViewModel sin dispositivo). */
interface SettingsSystem {
    /** Android 13+: los avisos necesitan el permiso `POST_NOTIFICATIONS`. */
    val needsNotificationPermission: Boolean

    fun notificationsAllowed(): Boolean

    fun isServiceEnabled(): Boolean

    fun isInstalled(platform: Platform): Boolean

    /** Tras borrar los datos: refrescar el widget. */
    fun onDataCleared()

    /** Tras importar un CSV: repintar el widget y tomar una base nueva del aviso de nivel (lo importado no avisa). */
    fun onDataImported() {}

    suspend fun loadLicenses(): List<License>

    /** Tras cambiar el modo pasaporte: repintar el widget (Flap) y, si se apaga, quitar su aviso. */
    fun onPassportModeChanged(enabled: Boolean) {}

    /** Tras fijar o quitar: borrar la foto de perfil de quien ya no está fijado (en el hilo de E/S). */
    fun onQuickSendTargetsChanged(pinned: List<String>) {}

    /**
     * ELEGIR FOTO (1.4.3): decodifica la foto de [uri] (del selector de fotos), se queda con el cuadrado centrado y la
     * guarda como la foto elegida de [name]. `false` si no se puede leer. En el hilo de E/S.
     */
    fun setQuickSendPhoto(name: String, uri: String): Boolean = false

    /** QUITAR FOTO: borra la foto elegida de [name] (vuelve la recortada de la hoja). En el hilo de E/S. */
    fun clearQuickSendPhoto(name: String) {}

    /**
     * Copia semanal: se queda con el permiso persistente de la carpeta [uri] recién elegida y devuelve su nombre, o
     * `null` si Android no lo da. En el hilo de E/S.
     */
    fun takeBackupFolder(uri: String): String? = null

    /** Suelta el permiso de una carpeta de copias que ya no se usa. */
    fun releaseBackupFolder(uri: String) {}

    /** Escribe la copia de hoy en [uri] (el mismo CSV que EXPORTAR) y rota las viejas. En el hilo de E/S; no lanza. */
    suspend fun writeBackup(uri: String): BackupOutcome = BackupOutcome.Failed(BackupError.FAILED)
}

class AndroidSettingsSystem(context: Context) : SettingsSystem {
    private val app = context.applicationContext
    private val notifications = AndroidNotificationAccess(app)
    private val probe = AndroidServiceProbe(app)

    override val needsNotificationPermission: Boolean get() = notifications.needsRuntimePermission

    override fun notificationsAllowed(): Boolean = notifications.areAllowed()

    override fun isServiceEnabled(): Boolean = probe.isServiceEnabled()

    override fun isInstalled(platform: Platform): Boolean = platform.packages.any { pkg ->
        try {
            app.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onDataCleared() {
        LiveAlerts.clear(app) // el día del aviso en directo y el último nivel avisado
        // Envío rápido: las fotos de los fijados y lo aprendido (ids, quién no sale en la cuadrícula).
        ReelometroApp.from(app).appScope.launch(Dispatchers.IO) { AvatarStore(app).clear() }
        QuickSendMemory.clear(app)
        WidgetUpdater.requestUpdate(app)
    }

    override fun onDataImported() {
        LevelAlert.onPassportEnabled(app)
        WidgetUpdater.requestUpdate(app)
    }

    override fun onQuickSendTargetsChanged(pinned: List<String>) {
        AvatarStore(app).retainOnly(pinned)
    }

    override fun setQuickSendPhoto(name: String, uri: String): Boolean {
        val bitmap = AvatarImport.decode(app.contentResolver, Uri.parse(uri)) ?: return false
        return try {
            AvatarStore(app).saveChosen(name, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    override fun clearQuickSendPhoto(name: String) {
        AvatarStore(app).clearChosen(name)
    }

    override fun takeBackupFolder(uri: String): String? = CsvBackup.takeFolder(app, uri)

    override fun releaseBackupFolder(uri: String) = CsvBackup.releaseFolder(app, uri)

    override suspend fun writeBackup(uri: String): BackupOutcome = CsvBackup.write(app, uri)

    override fun onPassportModeChanged(enabled: Boolean) {
        WidgetUpdater.requestUpdate(app)
        if (!enabled) {
            LevelAlert.onPassportDisabled(app)
        } else {
            // Lo conseguido con el modo oculto no llega de golpe: ni celebraciones atrasadas ni aviso de nivel.
            LevelAlert.onPassportEnabled(app)
            val reel = ReelometroApp.from(app)
            reel.appScope.launch { runCatching { reel.passport.rebase() } }
        }
    }

    override suspend fun loadLicenses(): List<License> = withContext(Dispatchers.IO) {
        LICENSE_FILES.mapNotNull { (name, file) ->
            runCatching { app.assets.open(file).bufferedReader().use { it.readText().trim() } }
                .getOrNull()
                ?.let { License(name, it) }
        }
    }

    private companion object {
        /** Nombre propio de cada familia y su fichero OFL. */
        val LICENSE_FILES = listOf(
            "Bricolage Grotesque" to "licenses/BricolageGrotesque-OFL.txt",
            "JetBrains Mono" to "licenses/JetBrainsMono-OFL.txt",
            "Instrument Serif" to "licenses/InstrumentSerif-OFL.txt",
        )
    }
}

/** Una persona o un grupo que se puede fijar para el envío rápido (sección 03). */
@Immutable
data class QuickSendLine(val name: String, val pinned: Boolean) {
    /** Lo que lleva su burbuja si no hay foto. */
    val initials: String get() = AvatarRules.initials(name)
}

/** Una app de la sección 03. */
@Immutable
data class AppLine(val platform: Platform, val enabled: Boolean, val installed: Boolean)

@Immutable
data class SettingsState(
    val goal: Int?,
    val notifyDaily: Boolean,
    val notifyMonthly: Boolean,
    val notifyHour: Int,
    val notifyMinute: Int,
    /** Android deja mostrar los avisos de la app. */
    val notificationsAllowed: Boolean,
    /** Se puede pedir el permiso con el diálogo del sistema (si no, hay que ir a Ajustes de Android). */
    val canRequestNotifications: Boolean,
    val apps: List<AppLine>,
    val themeMode: ThemeMode,
    val totals: Totals,
    /** Resultado de exportar o borrar. */
    val dataMessage: String?,
    /** Exportando, borrando o generando datos. */
    val busy: Boolean,
    /** Vista previa de IMPORTAR CSV: con valor, se ve su diálogo. */
    val importPreview: ImportPreview? = null,
    /** Leyendo o importando un CSV. */
    val importing: Boolean = false,
    val serviceEnabled: Boolean,
    val calibrationUnlocked: Boolean,
    /** Cuenta atrás de los 7 toques o confirmación del desbloqueo. */
    val aboutMessage: String?,
    val versionName: String,
    val debugAvailable: Boolean,
    val debugMessage: String?,
    /** `null` mientras no se han cargado. */
    val licenses: List<License>?,
    /** Modo pasaporte (pestaña, aviso de nivel y Flap en el widget). */
    val passportEnabled: Boolean = true,
    /** Aviso en directo al pasar el objetivo (solo tiene efecto con objetivo). */
    val notifyGoalLive: Boolean = false,
    /** «A quién envías» en Instagram (apagado por defecto). */
    val recordRecipients: Boolean = false,
    /** «Envío rápido» en Instagram (apagado por defecto). */
    val quickSendEnabled: Boolean = false,
    /** Lo que se puede fijar: tu top de envíos y, detrás, los fijados que ya no salen en él. */
    val quickSendLines: List<QuickSendLine> = emptyList(),
    /** Las burbujas no están en su sitio por defecto (RECOLOCAR hace algo). */
    val quickSendMoved: Boolean = false,
    /** Confirmación de RECOLOCAR. */
    val quickSendMessage: String? = null,
    /** Copia semanal del CSV encendida (hay carpeta). */
    val backupOn: Boolean = false,
    /** Nombre de la carpeta de las copias («Download»). */
    val backupFolderName: String? = null,
    /** Hora local de la última copia; `null` si todavía no hay ninguna. */
    val backupLast: LocalDateTime? = null,
    /** Por qué falló la última copia (se enseña con CAMBIAR CARPETA). */
    val backupError: BackupError? = null,
) {
    /** El envío rápido necesita «A quién envías» encendido. */
    val quickSendAvailable: Boolean get() = recordRecipients

    /** Ya hay [AppSettings.MAX_QUICK_SEND] fijados: no se puede fijar otro sin quitar uno. */
    val quickSendFull: Boolean get() = quickSendLines.count { it.pinned } >= AppSettings.MAX_QUICK_SEND

    /** El aviso en directo solo se puede encender con objetivo. */
    val goalLiveAvailable: Boolean get() = goal != null

    /** Algún aviso encendido: si Android no deja avisar, se enseña cómo permitirlo. */
    val anyNotificationOn: Boolean get() = notifyDaily || notifyMonthly || (notifyGoalLive && goalLiveAvailable)

    val allAppsOff: Boolean get() = apps.none { it.enabled }
}

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data object Error : SettingsUiState

    data class Content(val settings: SettingsState) : SettingsUiState
}

/** Efectos de una sola vez para la pantalla. */
sealed interface SettingsEffect {
    /** Vibración de éxito (exportado, calibración desbloqueada, datos generados). */
    data object Confirm : SettingsEffect

    /** Pedir `POST_NOTIFICATIONS` con el diálogo del sistema. */
    data object RequestNotificationPermission : SettingsEffect

    /** Abrir el selector de archivos para guardar el CSV con este nombre. */
    data class PickExportFile(val fileName: String) : SettingsEffect

    /** Abrir el selector de archivos para elegir el CSV que se importa. */
    data object PickImportFile : SettingsEffect

    /** Abrir el selector de carpetas del sistema (`ACTION_OPEN_DOCUMENT_TREE`) para la copia semanal. */
    data object PickBackupFolder : SettingsEffect
}

/**
 * Ajustes: cada cambio se guarda al momento en [SettingsRepository], salvo el objetivo, que se
 * pinta al instante y se escribe tras [GOAL_SAVE_DELAY_MS] sin tocarlo (el selector repite al
 * mantenerlo pulsado y no hace falta escribir 20 veces por segundo).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val usage: UsageData,
    private val system: SettingsSystem,
    private val versionName: String,
    private val seedSampleData: (suspend () -> Int)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** Lo que solo vive en la pantalla. [goalEdit] ≠ null: el objetivo que se ve (puede no estar guardado aún). */
    private data class Local(
        val goalEdit: GoalEdit? = null,
        val dataMessage: String? = null,
        val busy: Boolean = false,
        val aboutMessage: String? = null,
        val debugMessage: String? = null,
        val licenses: List<License>? = null,
        val notificationsBlocked: Boolean = false,
        val quickSendMessage: String? = null,
        /** Vista previa de IMPORTAR CSV (abre su diálogo). */
        val importPreview: ImportPreview? = null,
        /** Leyendo o importando un CSV (indicador de carga). */
        val importing: Boolean = false,
    )

    private data class GoalEdit(val value: Int?)

    private data class SystemInfo(
        val notificationsAllowed: Boolean = true,
        val serviceEnabled: Boolean = false,
        val installed: Set<Platform> = Platform.entries.toSet(),
    )

    private val local = MutableStateFlow(Local())
    private val systemInfo = MutableStateFlow(SystemInfo())
    private val retry = MutableStateFlow(0)
    private val taps = UnlockTapCounter()
    private var goalJob: Job? = null

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = retry
        .flatMapLatest {
            // Sin base de datos, Ajustes sigue sirviendo (tema, avisos…): los totales caen a cero.
            combine(
                settings.settings,
                usage.totals.catch { emit(Totals(0, 0)) },
                local,
                systemInfo,
                usage.topRecipients.catch { emit(emptyList()) },
            ) { s, totals, l, sys, top ->
                SettingsUiState.Content(content(s, totals, l, sys, top)) as SettingsUiState
            }.catch { emit(SettingsUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    init {
        refresh()
    }

    private fun content(s: AppSettings, totals: Totals, l: Local, sys: SystemInfo, top: List<String>) = SettingsState(
        goal = l.goalEdit?.value ?: s.dailyGoal.takeIf { l.goalEdit == null },
        notifyDaily = s.notifyDaily,
        notifyMonthly = s.notifyMonthly,
        notifyHour = s.notifyHour,
        notifyMinute = s.notifyMinute,
        notificationsAllowed = sys.notificationsAllowed,
        canRequestNotifications = system.needsNotificationPermission && !l.notificationsBlocked,
        apps = Platform.entries.map { AppLine(it, s.isEnabled(it), it in sys.installed) },
        themeMode = s.themeMode,
        totals = totals,
        dataMessage = l.dataMessage,
        busy = l.busy,
        importPreview = l.importPreview,
        importing = l.importing,
        serviceEnabled = sys.serviceEnabled,
        calibrationUnlocked = s.calibrationUnlocked,
        aboutMessage = l.aboutMessage,
        versionName = versionName,
        debugAvailable = seedSampleData != null,
        debugMessage = l.debugMessage,
        licenses = l.licenses,
        passportEnabled = s.passportEnabled,
        notifyGoalLive = s.notifyGoalLive,
        recordRecipients = s.recordRecipients,
        quickSendEnabled = s.quickSendEnabled,
        quickSendLines = (top + s.quickSendTargets).distinct().map { QuickSendLine(it, it in s.quickSendTargets) },
        quickSendMoved = s.quickSendBubble != null,
        quickSendMessage = l.quickSendMessage,
        backupOn = s.backupFolder != null,
        backupFolderName = s.backupFolderName,
        backupLast = s.backupLastMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
        backupError = s.backupError.takeIf { s.backupFolder != null },
    )

    private val current: SettingsState? get() = (state.value as? SettingsUiState.Content)?.settings

    /** Relee lo que puede haber cambiado fuera de la app (servicio, avisos, apps instaladas). En cada `ON_RESUME`. */
    fun refresh() {
        viewModelScope.launch {
            val info = withContext(io) {
                SystemInfo(
                    notificationsAllowed = system.notificationsAllowed(),
                    serviceEnabled = system.isServiceEnabled(),
                    installed = Platform.entries.filterTo(mutableSetOf()) { system.isInstalled(it) },
                )
            }
            systemInfo.value = info
        }
    }

    fun retry() {
        retry.update { it + 1 }
    }

    // ------------------------------------------------------------------ 01 objetivo

    /** Cambia el objetivo (del selector). Se ve al momento y se guarda al soltar. */
    fun setGoal(value: Int) {
        val goal = value.coerceIn(AppSettings.MIN_GOAL, AppSettings.MAX_GOAL)
        local.update { it.copy(goalEdit = GoalEdit(goal)) }
        goalJob?.cancel()
        goalJob = viewModelScope.launch {
            delay(GOAL_SAVE_DELAY_MS)
            settings.setDailyGoal(goal)
        }
    }

    /** PONER OBJETIVO (sin objetivo): arranca en [DEFAULT_GOAL] y se guarda ya. */
    fun addGoal() = saveGoalNow(DEFAULT_GOAL)

    /** QUITAR OBJETIVO. */
    fun removeGoal() = saveGoalNow(null)

    private fun saveGoalNow(goal: Int?) {
        goalJob?.cancel()
        local.update { it.copy(goalEdit = GoalEdit(goal)) }
        goalJob = viewModelScope.launch { settings.setDailyGoal(goal) }
    }

    // ------------------------------------------------------------------ 02 avisos

    fun setNotifyDaily(on: Boolean) {
        viewModelScope.launch { settings.setNotifyDaily(on) }
        if (on) askForNotificationsIfNeeded()
    }

    fun setNotifyMonthly(on: Boolean) {
        viewModelScope.launch { settings.setNotifyMonthly(on) }
        if (on) askForNotificationsIfNeeded()
    }

    fun setNotifyTime(hour: Int, minute: Int) {
        viewModelScope.launch { settings.setNotifyTime(hour, minute) }
    }

    /** Botón PERMITIR AVISOS del aviso integrado. */
    fun requestNotifications() {
        _effects.trySend(SettingsEffect.RequestNotificationPermission)
    }

    /**
     * Respuesta al diálogo de `POST_NOTIFICATIONS`. [canAskAgain] = `shouldShowRequestPermissionRationale`:
     * si es `false` tras un «no», Android ya no enseña el diálogo y hay que ir a sus Ajustes.
     */
    fun onNotificationPermission(granted: Boolean, canAskAgain: Boolean) {
        local.update { it.copy(notificationsBlocked = !granted && !canAskAgain) }
        refresh()
    }

    private fun askForNotificationsIfNeeded() {
        val s = current ?: return
        if (!s.notificationsAllowed && s.canRequestNotifications) {
            _effects.trySend(SettingsEffect.RequestNotificationPermission)
        }
    }

    /** Aviso en directo al pasar el objetivo. Sin objetivo no se enciende (el interruptor sale deshabilitado). */
    fun setNotifyGoalLive(on: Boolean) {
        if (on && current?.goalLiveAvailable == false) return
        viewModelScope.launch { settings.setNotifyGoalLive(on) }
        if (on) askForNotificationsIfNeeded()
    }

    // ------------------------------------------------------------------ 01 modo pasaporte

    /** Modo pasaporte: con `false` desaparecen la pestaña, el aviso de nivel y Flap del widget. */
    fun setPassportEnabled(on: Boolean) {
        viewModelScope.launch {
            settings.setPassportEnabled(on)
            system.onPassportModeChanged(on)
        }
    }

    // ------------------------------------------------------------------ 03 apps · 04 tema

    fun setPlatformEnabled(platform: Platform, enabled: Boolean) {
        viewModelScope.launch { settings.setPlatformEnabled(platform, enabled) }
    }

    /** «A quién envías»: el servicio lo lee del ajuste al momento. Apagarlo no borra lo ya guardado. */
    fun setRecordRecipients(on: Boolean) {
        viewModelScope.launch { settings.setRecordRecipients(on) }
    }

    /**
     * «Envío rápido»: el servicio pone o quita las burbujas al momento. Apagarlo no olvida los fijados. Sin
     * «A quién envías» no se enciende (el interruptor sale deshabilitado): busca en la hoja nombres que solo
     * se leen con ese ajuste.
     */
    fun setQuickSendEnabled(on: Boolean) {
        if (on && current?.recordRecipients == false) return
        viewModelScope.launch { settings.setQuickSendEnabled(on) }
    }

    /**
     * FIJAR / FIJADO en una fila: fija [name] al final (las burbujas salen en ese orden) o lo quita. Con
     * [AppSettings.MAX_QUICK_SEND] fijados no fija otro (la pantalla explica que hay que quitar uno).
     */
    fun toggleQuickSendTarget(name: String) {
        viewModelScope.launch {
            val pinned = settings.toggleQuickSendTarget(name)
            // Quitar un fijado borra su foto de perfil.
            withContext(io) { system.onQuickSendTargetsChanged(pinned) }
        }
    }

    /**
     * ELEGIR FOTO (1.4.3): la foto de [uri] (del selector de fotos de Android) pasa a ser la de la burbuja de [name],
     * si sigue fijado. La captura de la hoja ya no la renueva; QUITAR FOTO vuelve a la automática.
     */
    fun setQuickSendPhoto(name: String, uri: String) {
        viewModelScope.launch {
            // De los ajustes guardados y no del estado de la pantalla: si Android mató la app con el selector abierto,
            // el resultado llega con el ViewModel recién creado y aún cargando (revisión de la 1.4.3).
            val pinned = runCatching { name in settings.current().quickSendTargets }.getOrDefault(false)
            if (!pinned) return@launch
            val saved = runCatching { withContext(io) { system.setQuickSendPhoto(name, uri) } }.getOrDefault(false)
            // Si mientras se decodificaba se quitó el fijado (o BORRAR TODO), la foto recién guardada no se queda.
            val stillPinned = runCatching { name in settings.current().quickSendTargets }.getOrDefault(false)
            if (saved && !stillPinned) runCatching { withContext(io) { system.clearQuickSendPhoto(name) } }
            if (!stillPinned) return@launch
            val ok = saved
            local.update { it.copy(quickSendMessage = if (ok) SettingsCopy.QUICK_SEND_PHOTO_SET else SettingsCopy.QUICK_SEND_PHOTO_FAILED) }
        }
    }

    /** QUITAR FOTO: borra la foto elegida de [name]; vuelve la recortada de la hoja de compartir (o las iniciales). */
    fun clearQuickSendPhoto(name: String) {
        viewModelScope.launch {
            withContext(io) { system.clearQuickSendPhoto(name) }
            local.update { it.copy(quickSendMessage = SettingsCopy.QUICK_SEND_PHOTO_CLEARED) }
        }
    }

    /** RECOLOCAR: las burbujas vuelven al borde derecho, a media altura (el servicio las mueve al momento). */
    fun relocateQuickSend() {
        viewModelScope.launch {
            settings.resetQuickSendBubble()
            local.update { it.copy(quickSendMessage = SettingsCopy.QUICK_SEND_RELOCATED) }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    // ------------------------------------------------------------------ 05 tus datos

    /** EXPORTAR CSV: sin nada que exportar no abre el selector de archivos. */
    fun requestExport() {
        val s = current ?: return
        if (s.busy) return
        if (s.totals.count == 0) {
            local.update { it.copy(dataMessage = SettingsCopy.EXPORT_EMPTY) }
        } else {
            _effects.trySend(SettingsEffect.PickExportFile(SettingsCopy.exportFileName(today())))
        }
    }

    /** Escribe el CSV en el archivo elegido; [open] lo abre (en el hilo de E/S). */
    fun export(open: () -> OutputStream?) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, dataMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(io) {
                    val out = open() ?: throw IOException("No se ha podido abrir el archivo")
                    out.use { usage.exportCsv(it) }
                }
            }
            val rows = result.getOrNull()
            local.update {
                it.copy(
                    busy = false,
                    dataMessage = when {
                        rows == null -> SettingsCopy.EXPORT_FAILED
                        rows == 0 -> SettingsCopy.EXPORT_EMPTY
                        else -> SettingsCopy.exportDone(rows)
                    },
                )
            }
            if (rows != null && rows > 0) _effects.send(SettingsEffect.Confirm)
        }
    }

    /** BORRAR TODO, ya confirmado en el diálogo (que vibra con `reject`). */
    fun clearAll() {
        if (local.value.busy) return
        local.update { it.copy(busy = true, dataMessage = null) }
        viewModelScope.launch {
            val ok = runCatching { withContext(io) { usage.clearAll() } }.isSuccess
            if (ok) {
                // Los fijados del envío rápido son nombres del historial que se acaba de borrar: también se olvidan.
                runCatching { settings.setQuickSendTargets(emptyList()) }
                system.onDataCleared()
            }
            local.update {
                it.copy(busy = false, dataMessage = if (ok) SettingsCopy.DELETE_DONE else SettingsCopy.DELETE_FAILED)
            }
        }
    }

    // ------------------------------------------------------------------ copia semanal del CSV

    /**
     * Interruptor «Copia semanal»: encenderlo abre el selector de carpetas (sigue apagado hasta que se elige una);
     * apagarlo olvida la carpeta y suelta su permiso. Las copias que ya hay se quedan en la carpeta.
     */
    fun setBackup(on: Boolean) {
        if (on) {
            requestBackupFolder()
            return
        }
        viewModelScope.launch {
            val old = settings.current().backupFolder
            settings.setBackupFolder(null, null)
            if (old != null) withContext(io) { system.releaseBackupFolder(old) }
        }
    }

    /** CAMBIAR CARPETA (o encender la copia): abre el selector de carpetas del sistema. */
    fun requestBackupFolder() {
        if (local.value.busy) return
        _effects.trySend(SettingsEffect.PickBackupFolder)
    }

    /** Carpeta elegida en el selector: se queda con su permiso, la guarda y hace ya la primera copia. */
    fun onBackupFolderPicked(uri: String) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, dataMessage = null) }
        viewModelScope.launch {
            val name = withContext(io) { system.takeBackupFolder(uri) }
            if (name == null) {
                local.update { it.copy(busy = false, dataMessage = SettingsCopy.BACKUP_PICK_FAILED) }
                return@launch
            }
            val old = settings.current().backupFolder
            settings.setBackupFolder(uri, name)
            if (old != null && old != uri) withContext(io) { system.releaseBackupFolder(old) }
            copyBackup(uri, name)
        }
    }

    /** COPIAR AHORA. */
    fun backupNow() {
        if (local.value.busy) return
        local.update { it.copy(busy = true, dataMessage = null) }
        viewModelScope.launch {
            val s = settings.current()
            val folder = s.backupFolder
            if (folder == null) {
                local.update { it.copy(busy = false) }
                return@launch
            }
            copyBackup(folder, s.backupFolderName.orEmpty())
        }
    }

    /** Hace la copia (con `busy` ya puesto), apunta el resultado y lo dice. Un fallo queda en el aviso fijo. */
    private suspend fun copyBackup(folder: String, name: String) {
        val outcome = runCatching { withContext(io) { system.writeBackup(folder) } }
            .getOrElse { BackupOutcome.Failed(BackupRules.classify(it)) }
        runCatching { settings.recordBackup(folder, outcome, clock()) }
        local.update {
            it.copy(
                busy = false,
                dataMessage = if (outcome is BackupOutcome.Failed) null else SettingsCopy.backupResult(outcome, name),
            )
        }
        if (outcome is BackupOutcome.Done) _effects.send(SettingsEffect.Confirm)
    }

    // ------------------------------------------------------------------ importar CSV (1.6.0)

    /** El CSV leído, a la espera de AÑADIR o SUSTITUIR TODO (no va en el estado: puede ser grande). */
    private var pendingImport: ParsedCsv? = null

    /** IMPORTAR CSV: abre el selector de archivos del sistema (sin permisos de almacenamiento). */
    fun requestImport() {
        if (local.value.busy) return
        _effects.trySend(SettingsEffect.PickImportFile)
    }

    /** Lee el archivo elegido ([open] lo abre, en el hilo de E/S) y enseña la vista previa o el error. */
    fun readImport(open: () -> InputStream?) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, importing = true, dataMessage = null, importPreview = null) }
        viewModelScope.launch {
            val read = runCatching {
                withContext(io) {
                    val input = open() ?: throw IOException("No se ha podido abrir el archivo")
                    input.use { usage.readCsv(it) }
                }
            }.getOrElse { CsvRead.Failed(ImportError.FAILED) }
            when (read) {
                is CsvRead.Ready -> {
                    pendingImport = read.csv
                    local.update { it.copy(busy = false, importing = false, importPreview = read.preview) }
                }
                is CsvRead.Failed -> {
                    pendingImport = null
                    local.update { it.copy(busy = false, importing = false, dataMessage = SettingsCopy.importError(read.error)) }
                }
            }
        }
    }

    /** CANCELAR en la vista previa: no se importa nada. */
    fun cancelImport() {
        pendingImport = null
        local.update { it.copy(importPreview = null) }
    }

    /** AÑADIR o SUSTITUIR TODO (este, ya confirmado en su diálogo). */
    fun confirmImport(mode: ImportMode) {
        val csv = pendingImport ?: return
        if (local.value.busy) return
        pendingImport = null
        local.update { it.copy(busy = true, importing = true, importPreview = null, dataMessage = null) }
        viewModelScope.launch {
            val result = runCatching { withContext(io) { usage.importCsv(csv, mode) } }.getOrNull()
            if (result != null) system.onDataImported()
            local.update {
                it.copy(
                    busy = false,
                    importing = false,
                    dataMessage = result?.let(SettingsCopy::importDone) ?: SettingsCopy.importError(ImportError.FAILED),
                )
            }
            if (result != null) _effects.send(SettingsEffect.Confirm)
        }
    }

    // ------------------------------------------------------------------ 08 acerca de

    /** Un toque en la versión. Al 7.º, desbloquea Calibración. */
    fun onVersionTap() {
        val s = current ?: return
        if (s.calibrationUnlocked) {
            local.update { it.copy(aboutMessage = SettingsCopy.CALIBRATION_ALREADY) }
            return
        }
        val left = taps.tap(clock())
        when {
            left <= 0 -> {
                taps.reset()
                local.update { it.copy(aboutMessage = SettingsCopy.CALIBRATION_UNLOCKED) }
                viewModelScope.launch { settings.setCalibrationUnlocked(true) }
                _effects.trySend(SettingsEffect.Confirm)
            }
            left <= UnlockTapCounter.COUNTDOWN_FROM ->
                local.update { it.copy(aboutMessage = SettingsCopy.calibrationTapsLeft(left)) }
            // Primeros toques (o la cuenta ha vuelto a empezar): nada que decir todavía.
            else -> local.update { it.copy(aboutMessage = null) }
        }
    }

    /**
     * VER EL RECORRIDO: lo marca como no visto y, ya guardado, [onReady] vuelve a Hoy, donde empieza
     * (así la escritura no se pierde aunque Ajustes se cierre al navegar).
     */
    fun replayTour(onReady: () -> Unit) {
        viewModelScope.launch {
            runCatching { settings.setTourDone(false) }
            onReady()
        }
    }

    fun loadLicenses() {
        if (local.value.licenses != null) return
        viewModelScope.launch {
            val licenses = runCatching { system.loadLicenses() }.getOrDefault(emptyList())
            local.update { it.copy(licenses = licenses) }
        }
    }

    // ------------------------------------------------------------------ depuración

    fun seedSampleData() {
        val seed = seedSampleData ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true, debugMessage = null) }
        viewModelScope.launch {
            val count = runCatching { seed() }.getOrNull()
            local.update {
                it.copy(busy = false, debugMessage = count?.let(SettingsCopy::debugSeeded) ?: SettingsCopy.DEBUG_FAILED)
            }
            if (count != null) {
                system.onDataCleared()
                _effects.send(SettingsEffect.Confirm)
            }
        }
    }

    companion object {
        const val GOAL_SAVE_DELAY_MS = 450L

        /** Objetivo con el que arranca PONER OBJETIVO. */
        const val DEFAULT_GOAL = 150

        /** Semanas de los datos de ejemplo. */
        const val SAMPLE_WEEKS = 10

        fun create(context: Context): SettingsViewModel {
            val app = ReelometroApp.from(context)
            return SettingsViewModel(
                settings = app.settings,
                usage = RepositoryUsageData(app.repository),
                system = AndroidSettingsSystem(app),
                versionName = BuildConfig.VERSION_NAME,
                seedSampleData = if (DebugTools.AVAILABLE) {
                    { DebugTools.seedSampleData(app, weeks = SAMPLE_WEEKS) }
                } else {
                    null
                },
            )
        }
    }
}
