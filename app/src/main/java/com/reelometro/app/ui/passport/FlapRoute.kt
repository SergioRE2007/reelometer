package com.reelometro.app.ui.passport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Pestaña FLAP: la mascota que empieza cada día a tope y enferma con cada vídeo, sobre
 * `ReelometroApp.from(context).flap` ([FlapViewModel]). Sin objetivo, «PONER OBJETIVO» llama a
 * [onOpenSettings].
 */
@Composable
fun FlapRoute(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val viewModel: FlapViewModel = viewModel(factory = FlapViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Al salir (a Ajustes, a otra pestaña) se olvida lo marcado: lo visto puede borrarse entretanto.
    DisposableEffect(viewModel) { onDispose { viewModel.onScreenLeft() } }
    FlapScreen(
        state = state,
        onRetry = viewModel::retry,
        onMoodSeen = viewModel::onMoodSeen,
        onOpenSettings = onOpenSettings,
    )
}
