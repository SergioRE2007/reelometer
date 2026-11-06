package com.reelometro.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.DisclosureCopy
import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.flap.FlapContent
import com.reelometro.app.ui.components.flap.SplitFlapDisplay
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Dónde se pinta el paso del permiso: cambia el pie (seguir sin servicio / volver). */
enum class PermissionPlace { Onboarding, Setup }

/** Lo que tarda el marcador en voltear a «OK» antes de la vibración de confirmación. */
private const val CONFIRM_AFTER_FLIP_MS = 420L

/**
 * Paso del permiso de accesibilidad, compartido por el onboarding (paso 3) y la pantalla de conectar:
 *
 *  1. **Divulgación destacada** ([DisclosureCopy]) con aceptación explícita: ACEPTAR Y ACTIVAR guarda
 *     la aceptación y abre Accesibilidad; AHORA NO no abre nada.
 *  2. Ya aceptada: **guía** paso a paso con el marcador en «– –», que voltea a «OK» al volver con el
 *     servicio activo (+ háptico `confirm`), y la ayuda de «Ajuste restringido».
 *
 * @param onContinue pie con el servicio activo o al seguir sin él: siguiente paso (onboarding) o volver.
 */
@Composable
fun ServicePermissionStep(
    state: ServicePermissionState,
    place: PermissionPlace,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onToggleRestrictedHelp: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = remember { Variants.daySeed(LocalDate.now()) }
    val showDisclosure = !state.disclosureAccepted && !state.serviceEnabled

    StepScaffold(
        modifier = modifier,
        footer = {
            when {
                showDisclosure -> {
                    Text(DisclosureCopy.CONSENT, style = ReelTheme.type.bodySmall, color = ReelTheme.colors.textMuted)
                    ReelButton(DisclosureCopy.ACCEPT, onClick = onAccept, modifier = Modifier.fillMaxWidth())
                    ReelButton(
                        DisclosureCopy.DECLINE,
                        onClick = onDecline,
                        style = ReelButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.serviceEnabled -> ReelButton(
                    text = if (place == PermissionPlace.Onboarding) OnboardingCopy.GUIDE_SKIP else OnboardingCopy.GUIDE_DONE,
                    onClick = onContinue,
                    trailingIcon = if (place == PermissionPlace.Onboarding) ReelIcons.ArrowRight else ReelIcons.Check,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> {
                    ReelButton(
                        OnboardingCopy.GUIDE_BUTTON,
                        onClick = onOpenAccessibility,
                        trailingIcon = ReelIcons.External,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (place == PermissionPlace.Onboarding) {
                        ReelButton(
                            OnboardingCopy.GUIDE_SKIP,
                            onClick = onContinue,
                            style = ReelButtonStyle.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) {
        AnimatedContent(
            targetState = showDisclosure,
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard()) },
            label = "permission",
        ) { disclosure ->
            Column(Modifier.fillMaxWidth()) {
                if (disclosure) Disclosure(declined = state.declined) else Guide(state, seed, onOpenAppInfo, onToggleRestrictedHelp)
            }
        }
    }
}

@Composable
private fun ColumnScope.Disclosure(declined: Boolean) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Label(DisclosureCopy.LABEL, color = colors.signalText)
    Spacer(Modifier.height(Space.x3))
    Text(DisclosureCopy.TITLE, style = type.displayM, color = colors.text, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Space.x5))
    Text(DisclosureCopy.SUMMARY, style = type.body, color = colors.text)
    Spacer(Modifier.height(Space.x6))
    DisclosureCopy.SECTIONS.forEach { section ->
        TicketDivider(style = TicketDividerStyle.Dashed)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.x4)
                .semantics(mergeDescendants = true) {},
        ) {
            Label(section.label, color = colors.text)
            Spacer(Modifier.height(Space.x2))
            Text(section.text, style = type.body, color = colors.textMuted)
        }
    }
    TicketDivider(style = TicketDividerStyle.Dashed)
    if (declined) {
        Spacer(Modifier.height(Space.x5))
        Text(DisclosureCopy.DECLINED, style = type.quoteSmall, color = colors.textMuted)
    }
}

@Composable
private fun ColumnScope.Guide(
    state: ServicePermissionState,
    seed: Long,
    onOpenAppInfo: () -> Unit,
    onToggleRestrictedHelp: () -> Unit,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val connected = state.serviceEnabled
    val haptics = rememberHaptics()

    // Solo vibra si se ha conectado con la pantalla abierta (no al entrar ya conectado).
    LaunchedEffect(state.connectedHere && connected) {
        if (state.connectedHere && connected) {
            delay(CONFIRM_AFTER_FLIP_MS)
            haptics.confirm()
        }
    }

    Label(
        if (connected) OnboardingCopy.GUIDE_CONNECTED_LABEL else OnboardingCopy.GUIDE_WAITING,
        color = if (connected) colors.signalText else colors.textMuted,
    )
    Spacer(Modifier.height(Space.x3))
    SplitFlapDisplay(
        content = if (connected) FlapContent.Text(OnboardingCopy.GUIDE_CONNECTED_COUNTER) else FlapContent.Blank,
        minColumns = 2,
        style = type.displayXL,
        color = if (connected) colors.signalDisplay else colors.text,
        haptic = false,
        contentDescription = OnboardingCopy.guideStatusDescription(connected),
    )
    Spacer(Modifier.height(Space.x8))
    Text(
        text = if (connected) OnboardingCopy.connected(seed) else OnboardingCopy.GUIDE_TITLE,
        style = type.headline,
        color = colors.text,
        modifier = Modifier.semantics { heading() },
    )
    AnimatedVisibility(
        visible = connected,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Text(
            OnboardingCopy.GUIDE_CONNECTED_BODY,
            style = type.body,
            color = colors.textMuted,
            modifier = Modifier.padding(top = Space.x4),
        )
    }
    AnimatedVisibility(
        visible = !connected,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(Space.x6))
            StepList(OnboardingCopy.GUIDE_STEPS)
            if (state.returnedWithoutService) {
                Spacer(Modifier.height(Space.x6))
                InlineNotice(text = OnboardingCopy.notYet(seed))
            }
            Spacer(Modifier.height(Space.x8))
            if (!state.restricted) {
                ReelButton(
                    text = OnboardingCopy.RESTRICTED_LINK,
                    onClick = onToggleRestrictedHelp,
                    style = ReelButtonStyle.Ghost,
                    trailingIcon = if (state.restrictedHelpVisible) ReelIcons.ChevronUp else ReelIcons.ChevronDown,
                )
            }
            AnimatedVisibility(
                visible = state.restrictedHelpVisible,
                enter = revealIn(ReelTheme.reducedMotion),
                exit = revealOut(ReelTheme.reducedMotion),
            ) {
                RestrictedHelp(onOpenAppInfo, Modifier.padding(top = Space.x4))
            }
        }
    }
}

/** Qué hacer con «Ajuste restringido» (Android 13+, apps instaladas desde un archivo). */
@Composable
private fun RestrictedHelp(onOpenAppInfo: () -> Unit, modifier: Modifier = Modifier) {
    InlineNotice(
        title = OnboardingCopy.RESTRICTED_TITLE,
        text = OnboardingCopy.RESTRICTED_BODY,
        actionLabel = OnboardingCopy.RESTRICTED_BUTTON,
        onAction = onOpenAppInfo,
        actionIcon = ReelIcons.External,
        modifier = modifier,
    ) {
        StepList(OnboardingCopy.RESTRICTED_STEPS)
        Spacer(Modifier.height(Space.x4))
        Text(OnboardingCopy.RESTRICTED_NOTE, style = ReelTheme.type.dataS, color = ReelTheme.colors.textMuted)
    }
}

/** Aparición de un bloque que se despliega (fundido + crecer, con muelle); con «reducir animaciones», solo fundido. */
internal fun revealIn(reduced: Boolean): EnterTransition =
    if (reduced) fadeIn(Motion.standard()) else fadeIn(Motion.standard()) + expandVertically(Motion.standard())

internal fun revealOut(reduced: Boolean): ExitTransition =
    if (reduced) fadeOut(Motion.standard()) else fadeOut(Motion.standard()) + shrinkVertically(Motion.standard())
