package com.reelometro.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.copy.A11y
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/**
 * Conectar el servicio fuera del onboarding (desde Hoy —CONECTAR / VER EL PERMISO— y Ajustes): la
 * misma divulgación destacada y guía del paso 3, con botón de volver. Si la divulgación ya se aceptó,
 * va directa a la guía; con el servicio ya activo muestra «OK» y un botón para volver.
 */
@Composable
fun ServiceSetupRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ServicePermissionViewModel = viewModel { ServicePermissionViewModel.create(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val openAccessibility = {
        vm.onOpenedSettings()
        SystemScreens.openAccessibility(context)
        Unit
    }
    val help = rememberConnectionHelp(linksToScreens = true, onOpenAccessibility = openAccessibility)

    ServiceSetupScreen(
        state = state,
        onBack = onBack,
        onAccept = {
            vm.accept()
            SystemScreens.openAccessibility(context)
        },
        onDecline = {
            vm.decline()
            onBack()
        },
        onOpenAccessibility = openAccessibility,
        onOpenAppInfo = { SystemScreens.openAppInfo(context) },
        onToggleRestrictedHelp = vm::toggleRestrictedHelp,
        help = help,
    )
}

@Composable
fun ServiceSetupScreen(
    state: ServicePermissionUiState,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onToggleRestrictedHelp: () -> Unit,
    modifier: Modifier = Modifier,
    help: ConnectionHelp? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(ReelTheme.colors.bg),
    ) {
        ReelTopBar(onBack = onBack, backDescription = A11y.BACK)
        when (state) {
            ServicePermissionUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(Space.screen),
                contentAlignment = Alignment.CenterStart,
            ) {
                FlapLoader(style = ReelTheme.type.displayXL)
            }
            is ServicePermissionUiState.Ready -> ServicePermissionStep(
                state = state.permission,
                place = PermissionPlace.Setup,
                onAccept = onAccept,
                onDecline = onDecline,
                onOpenAccessibility = onOpenAccessibility,
                onOpenAppInfo = onOpenAppInfo,
                onToggleRestrictedHelp = onToggleRestrictedHelp,
                onContinue = onBack,
                modifier = Modifier.weight(1f),
                oem = help?.oem,
                onOpenOem = help?.onOpenOem ?: {},
                check = help?.check,
                checkActions = help?.checkActions,
            )
        }
    }
}
