package com.reelometro.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.detection.DeviceInfo
import com.reelometro.app.detection.OemGuide
import com.reelometro.app.detection.OemScreen
import com.reelometro.app.detection.SystemState

/** Guía por marca y prueba de un minuto, ya cableadas para el paso del permiso. */
@Stable
class ConnectionHelp(
    val oem: OemGuideUi,
    val onOpenOem: (OemScreen) -> Unit,
    val check: ServiceCheckUiState,
    val checkActions: ServiceCheckActions,
)

/**
 * Crea el [ServiceCheckViewModel] de la pantalla, lo engancha al ciclo de vida (`ON_STOP` = se ha ido a la app de
 * vídeos, `ON_RESUME` = ha vuelto) y detecta la marca del móvil una vez.
 *
 * @param linksToScreens si los botones ABRIR CALIBRACIÓN / ABRIR AJUSTES pueden salir de esta pantalla (no en el
 *   onboarding: los enlaces internos se ignoran hasta terminarlo).
 */
@Composable
internal fun rememberConnectionHelp(linksToScreens: Boolean, onOpenAccessibility: () -> Unit): ConnectionHelp {
    val context = LocalContext.current
    val vm: ServiceCheckViewModel = viewModel { ServiceCheckViewModel.create(context) }
    val check by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onLeft() }
    // ¿Puede Android pausar la app por no usarla? Se relee al volver de Ajustes: el paso desaparece al eximirla.
    var unusedApps by remember { mutableStateOf(SystemState.hibernationExempt(context) == false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.onReturned()
        unusedApps = SystemState.hibernationExempt(context) == false
    }

    val family = remember { OemGuide.family(DeviceInfo.current()) }
    var openFailed by rememberSaveable { mutableStateOf(false) }
    val actions = remember(vm, linksToScreens, onOpenAccessibility) {
        ServiceCheckActions(
            onStart = { platform ->
                vm.start(platform)
                if (!SystemScreens.openApp(context, platform)) vm.cancel()
            },
            onVerify = vm::verify,
            onRetry = vm::retry,
            onCancel = vm::cancel,
            onOpenAccessibility = onOpenAccessibility,
            onOpenCalibration = if (linksToScreens) ({ SystemScreens.openRoute(context, "calibration") }) else null,
            onOpenSettings = if (linksToScreens) ({ SystemScreens.openRoute(context, "settings") }) else null,
        )
    }
    return ConnectionHelp(
        oem = OemGuideUi(family, openFailed, unusedApps),
        onOpenOem = { screen ->
            openFailed = SystemScreens.openOem(context, screen, family) == SystemScreens.OpenResult.NONE
        },
        check = check,
        checkActions = actions,
    )
}
