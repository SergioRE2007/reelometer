package com.reelometro.app.ui.day

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Detalle de un día ([epochDay] = `LocalDate.toEpochDay()`), desde Hoy o Estadísticas. */
@Composable
fun DayRoute(epochDay: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: DayViewModel = viewModel(
        key = "day-$epochDay",
        factory = DayViewModel.factory(context, epochDay),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DayScreen(state = state, onBack = onBack, onRetry = viewModel::retry)
}
