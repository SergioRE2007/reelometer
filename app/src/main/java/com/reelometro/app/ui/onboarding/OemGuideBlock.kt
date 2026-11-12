package com.reelometro.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.detection.OemFamily
import com.reelometro.app.detection.OemGuide
import com.reelometro.app.detection.OemScreen
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/**
 * Guía por marca: la familia detectada, si el último botón no ha podido abrir nada y si Android puede pausar la app
 * por no usarla ([OemGuide.steps] añade entonces ese paso).
 */
@Immutable
data class OemGuideUi(val family: OemFamily, val openFailed: Boolean = false, val unusedApps: Boolean = false)

/**
 * Pasos de la marca del móvil ([OemGuide.steps]) con un botón bajo cada paso que tiene pantalla. Cada paso se lee
 * como una sola frase en TalkBack (StepList funde la fila); el botón queda fuera para ser un objetivo propio.
 */
@Composable
internal fun OemGuideSteps(guide: OemGuideUi, onOpen: (OemScreen) -> Unit, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val steps = OemGuide.steps(guide.family, guide.unusedApps)
    val texts = OnboardingCopy.oemSteps(guide.family, guide.unusedApps)
    Column(modifier.fillMaxWidth()) {
        Label(OnboardingCopy.oemTitle(guide.family), color = colors.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.x4))
        steps.zip(texts).forEachIndexed { index, (step, text) ->
            if (index > 0) Spacer(Modifier.height(Space.x5))
            StepList(listOf(text))
            val screen = step.screen
            if (screen != null) {
                Spacer(Modifier.height(Space.x3))
                ReelButton(
                    text = OnboardingCopy.oemButton(guide.family, screen),
                    onClick = { onOpen(screen) },
                    style = ReelButtonStyle.Secondary,
                    trailingIcon = ReelIcons.External,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (guide.openFailed) {
            Spacer(Modifier.height(Space.x4))
            Text(
                OnboardingCopy.OEM_OPEN_FAILED,
                style = ReelTheme.type.body,
                color = colors.signalText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
