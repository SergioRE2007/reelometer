package com.reelometro.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.ExitCopy
import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.data.Platform
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.CheckFailure
import com.reelometro.app.detection.OemScreen
import com.reelometro.app.detection.ServiceCheckRules
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics

/**
 * Acciones de la prueba de un minuto. [onOpenCalibration] y [onOpenSettings] pueden faltar (en el onboarding de la
 * primera vez los enlaces a otras pantallas no funcionan todavía): entonces solo se enseña el texto.
 */
@Immutable
class ServiceCheckActions(
    val onStart: (Platform) -> Unit,
    val onVerify: () -> Unit,
    val onRetry: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenAccessibility: () -> Unit,
    val onOpenCalibration: (() -> Unit)? = null,
    val onOpenSettings: (() -> Unit)? = null,
)

/**
 * Prueba de un minuto con el servicio ya conectado ([ServiceCheckViewModel]): abrir una app de vídeos, pasar unos
 * vídeos y volver. El resultado siempre lleva etiqueta de texto (no solo color) y TalkBack lo lee al cambiar.
 */
@Composable
internal fun ServiceCheckBlock(
    state: ServiceCheckUiState,
    oem: OemGuideUi?,
    onOpenOem: (OemScreen) -> Unit,
    actions: ServiceCheckActions,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val haptics = rememberHaptics()
    val passed = state is ServiceCheckUiState.Passed
    LaunchedEffect(passed) { if (passed) haptics.confirm() }

    Column(modifier.fillMaxWidth()) {
        TicketDivider(style = TicketDividerStyle.Dashed)
        Spacer(Modifier.height(Space.x5))
        // Etiqueta y frase principal: región viva para que TalkBack lea el resultado al volver de la app. Mientras
        // cuenta (Running) no lo es, para que no relea el contador en cada vídeo.
        val live = state !is ServiceCheckUiState.Running
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                if (live) liveRegion = LiveRegionMode.Polite
            },
        ) {
            Label(
                when (state) {
                    is ServiceCheckUiState.Idle -> OnboardingCopy.CHECK_LABEL
                    is ServiceCheckUiState.Running -> OnboardingCopy.CHECK_RUNNING_LABEL
                    is ServiceCheckUiState.Passed -> OnboardingCopy.CHECK_PASSED_LABEL
                    is ServiceCheckUiState.Failed -> OnboardingCopy.CHECK_FAILED_LABEL
                },
                color = if (state is ServiceCheckUiState.Idle) colors.textMuted else colors.signalText,
            )
            Spacer(Modifier.height(Space.x2))
            when (state) {
                is ServiceCheckUiState.Idle -> Text(
                    OnboardingCopy.CHECK_TITLE,
                    style = type.title,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                is ServiceCheckUiState.Running -> Text(
                    OnboardingCopy.checkProgress(state.videos, state.target),
                    style = type.dataL,
                    color = colors.text,
                )
                is ServiceCheckUiState.Passed -> Text(OnboardingCopy.checkPassed(state.videos), style = type.body, color = colors.text)
                is ServiceCheckUiState.Failed -> Text(OnboardingCopy.checkFailure(state.reason), style = type.body, color = colors.text)
            }
        }
        Spacer(Modifier.height(Space.x3))
        when (state) {
            is ServiceCheckUiState.Idle -> Idle(state.apps, actions)
            is ServiceCheckUiState.Running -> {
                Text(OnboardingCopy.CHECK_RUNNING_BODY, style = type.body, color = colors.textMuted)
                Spacer(Modifier.height(Space.x4))
                ReelButton(
                    OnboardingCopy.CHECK_VERIFY,
                    onClick = actions.onVerify,
                    style = ReelButtonStyle.Secondary,
                    trailingIcon = ReelIcons.Check,
                    modifier = Modifier.fillMaxWidth(),
                )
                ReelButton(OnboardingCopy.CHECK_CANCEL, onClick = actions.onCancel, style = ReelButtonStyle.Ghost, trailingIcon = null)
            }
            is ServiceCheckUiState.Passed -> if (state.restarted) {
                Text(OnboardingCopy.CHECK_PASSED_RESTARTED, style = type.body, color = colors.textMuted)
                if (oem != null) {
                    Spacer(Modifier.height(Space.x5))
                    OemGuideSteps(oem, onOpenOem)
                }
            }
            is ServiceCheckUiState.Failed -> {
                // Por qué cerró Android la app, si lo guarda: una línea más, antes de los pasos.
                state.exit?.let(ExitCopy::diagnosis)?.let {
                    Text(it, style = type.body, color = colors.signalText)
                    Spacer(Modifier.height(Space.x2))
                }
                // Si ha sido un fallo de la propia app, ni la guía de batería ni la de la marca: no es culpa del móvil.
                if (showsFailureFix(state.exit)) FailureFix(state.reason, oem, onOpenOem, actions)
                Spacer(Modifier.height(Space.x2))
                ReelButton(OnboardingCopy.CHECK_RETRY, onClick = actions.onRetry, style = ReelButtonStyle.Ghost)
            }
        }
    }
}

/** Con un fallo de la propia app no se muestran los pasos de batería y arranque automático. */
internal fun showsFailureFix(exit: ExitCategory?): Boolean = exit != ExitCategory.APP_CRASH

@Composable
private fun Idle(apps: List<Platform>, actions: ServiceCheckActions) {
    val type = ReelTheme.type
    Text(OnboardingCopy.checkBody(ServiceCheckRules.TARGET_VIDEOS), style = type.body, color = ReelTheme.colors.textMuted)
    Spacer(Modifier.height(Space.x4))
    if (apps.isEmpty()) {
        Text(OnboardingCopy.CHECK_NONE_INSTALLED, style = type.body, color = ReelTheme.colors.text)
    } else {
        apps.forEachIndexed { index, platform ->
            if (index > 0) Spacer(Modifier.height(Space.x2))
            ReelButton(
                OnboardingCopy.checkOpenApp(platform),
                onClick = { actions.onStart(platform) },
                style = ReelButtonStyle.Secondary,
                trailingIcon = ReelIcons.External,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Qué hacer según el fallo: la guía por marca, Accesibilidad, los ajustes de Reelometer o Calibración. */
@Composable
private fun FailureFix(reason: CheckFailure, oem: OemGuideUi?, onOpenOem: (OemScreen) -> Unit, actions: ServiceCheckActions) {
    val fullWidth = Modifier.fillMaxWidth()
    when (reason) {
        CheckFailure.OFF -> {
            Spacer(Modifier.height(Space.x2))
            ReelButton(
                Buttons.OPEN_ACCESSIBILITY,
                onClick = actions.onOpenAccessibility,
                style = ReelButtonStyle.Secondary,
                trailingIcon = ReelIcons.External,
                modifier = fullWidth,
            )
        }
        CheckFailure.KILLED, CheckFailure.NOT_CONNECTED, CheckFailure.NO_EVENTS -> if (oem != null) {
            Spacer(Modifier.height(Space.x3))
            OemGuideSteps(oem, onOpenOem)
        }
        CheckFailure.PLATFORMS_OFF -> actions.onOpenSettings?.let {
            Spacer(Modifier.height(Space.x2))
            ReelButton(OnboardingCopy.CHECK_OPEN_SETTINGS, onClick = it, style = ReelButtonStyle.Secondary, modifier = fullWidth)
        }
        CheckFailure.NO_VIDEOS -> actions.onOpenCalibration?.let {
            Spacer(Modifier.height(Space.x2))
            ReelButton(OnboardingCopy.CHECK_OPEN_CALIBRATION, onClick = it, style = ReelButtonStyle.Secondary, modifier = fullWidth)
        }
    }
}
