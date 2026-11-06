package com.reelometro.app.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Pestaña **Estadísticas**: tocar una barra o una celda abre ese día; la racha sin objetivo, Ajustes. */
@Composable
fun StatsRoute(onOpenDay: (epochDay: Long) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onRetry = viewModel::retry,
        onOpenDay = onOpenDay,
        onOpenSettings = onOpenSettings,
    )
}
