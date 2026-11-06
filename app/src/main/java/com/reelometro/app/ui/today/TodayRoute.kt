package com.reelometro.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Pestaña **Hoy**. [onConnectService] abre la pantalla de conectar el servicio (divulgación
 * destacada + guía): desde aquí nunca se abren directamente los ajustes del sistema.
 */
@Composable
fun TodayRoute(
    onOpenDay: (epochDay: Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnectService: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(context))
    // Al volver de los ajustes de accesibilidad (o de cualquier otra app), el servicio puede haber cambiado.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshService() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        onOpenDay = onOpenDay,
        onOpenStats = onOpenStats,
        onOpenSettings = onOpenSettings,
        onConnectService = onConnectService,
        onRetry = viewModel::retry,
    )
}
