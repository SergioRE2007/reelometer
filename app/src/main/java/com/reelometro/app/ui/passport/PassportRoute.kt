package com.reelometro.app.ui.passport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Pestaña PASAPORTE: Flap, tarjeta de embarque con el nivel, reto semanal contra el fantasma y
 * pasaporte de sellos, sobre `ReelometroApp.from(context).passport` ([PassportViewModel]).
 * «COMPARTIR TICKET» pinta el ticket del nivel actual y abre el selector del sistema.
 */
@Composable
fun PassportRoute(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PassportViewModel = viewModel(factory = PassportViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    PassportScreen(
        state = state,
        onRetry = viewModel::retry,
        onMoodSeen = viewModel::onMoodSeen,
        onOpenSettings = onOpenSettings,
        onShareLevel = {
            scope.launch {
                val data = viewModel.levelTicket()
                val shared = data != null && LevelTicketSharer.share(context, data)
                if (shared) {
                    haptics.confirm()
                    viewModel.onLevelShared()
                } else {
                    haptics.reject()
                }
            }
        },
    )
}
