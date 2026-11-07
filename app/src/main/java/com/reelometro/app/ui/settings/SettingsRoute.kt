package com.reelometro.app.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.ui.onboarding.SystemScreens
import com.reelometro.app.ui.theme.rememberHaptics

/**
 * Ajustes. [onOpenServiceSetup] abre la pantalla de conectar el servicio (divulgación destacada +
 * guía); [onOpenCalibration] solo aparece tras los 7 toques en la versión.
 */
@SuppressLint("InlinedApi") // POST_NOTIFICATIONS: la guarda (Android 13+) es `canRequestNotifications` del ViewModel.
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenCalibration: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenServiceSetup: () -> Unit,
    /** «VER EL RECORRIDO»: tras marcarlo como no visto, vuelve a Hoy, donde empieza. */
    onReplayTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel.create(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        vm.onNotificationPermission(granted, canAskAgain)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export { context.contentResolver.openOutputStream(uri, "wt") }
    }
    // IMPORTAR CSV: el selector de documentos del sistema (sin permisos). Hojas de cálculo y algunos gestores de
    // archivos dan el CSV como texto o como binario: se aceptan esos tipos y el contenido decide.
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readImport { context.contentResolver.openInputStream(uri) }
    }
    // Copia semanal: el selector de carpetas del sistema (Storage Access Framework, sin permisos de almacenamiento).
    val backupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onBackupFolderPicked(uri.toString())
    }
    // ELEGIR FOTO (1.4.3): el selector de fotos de Android, sin permisos (en versiones viejas, el respaldo de
    // androidx abre el selector de documentos). Para quién era se guarda por si Android recrea la actividad.
    var photoFor by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val name = photoFor
        photoFor = null
        if (uri != null && name != null) vm.setQuickSendPhoto(name, uri.toString())
    }

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                SettingsEffect.Confirm -> haptics.confirm()
                SettingsEffect.RequestNotificationPermission ->
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                is SettingsEffect.PickExportFile -> exportFile.launch(effect.fileName)
                SettingsEffect.PickImportFile -> importFile.launch(IMPORT_TYPES)
                SettingsEffect.PickBackupFolder -> backupFolder.launch(null)
            }
        }
    }

    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onRetry = vm::retry,
            onGoalChange = vm::setGoal,
            onAddGoal = vm::addGoal,
            onRemoveGoal = vm::removeGoal,
            onNotifyDaily = vm::setNotifyDaily,
            onNotifyMonthly = vm::setNotifyMonthly,
            onNotifyTime = vm::setNotifyTime,
            onRequestNotifications = vm::requestNotifications,
            onOpenNotificationSettings = { SystemScreens.openNotificationSettings(context) },
            onPlatform = vm::setPlatformEnabled,
            onTheme = vm::setTheme,
            onExport = vm::requestExport,
            onDeleteAll = vm::clearAll,
            onImport = vm::requestImport,
            onImportConfirm = vm::confirmImport,
            onImportCancel = vm::cancelImport,
            onBackup = vm::setBackup,
            onBackupNow = vm::backupNow,
            onBackupChangeFolder = vm::requestBackupFolder,
            onOpenServiceSetup = onOpenServiceSetup,
            onOpenAccessibility = { SystemScreens.openAccessibility(context) },
            onVersionTap = vm::onVersionTap,
            onOpenLicenses = vm::loadLicenses,
            onReplayOnboarding = onReplayOnboarding,
            onOpenCalibration = onOpenCalibration,
            onSeedSampleData = vm::seedSampleData,
            onPassport = vm::setPassportEnabled,
            onNotifyGoalLive = vm::setNotifyGoalLive,
            onRecordRecipients = vm::setRecordRecipients,
            onQuickSend = vm::setQuickSendEnabled,
            onQuickSendTarget = vm::toggleQuickSendTarget,
            onQuickSendRelocate = vm::relocateQuickSend,
            onQuickSendPickPhoto = { name ->
                photoFor = name
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onQuickSendClearPhoto = vm::clearQuickSendPhoto,
            onReplayTour = { vm.replayTour(onReady = onReplayTour) },
        ),
    )
}

/** Tipos que ofrece el selector al importar (el CSV puede llegar como cualquiera de ellos). */
private val IMPORT_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")
