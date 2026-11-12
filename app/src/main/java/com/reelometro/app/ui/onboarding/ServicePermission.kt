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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.DisclosureCopy
import com.reelometro.app.copy.ExitCopy
import com.reelometro.app.copy.OnboardingCopy
import com.reelometro.app.copy.Variants
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.OemFamily
import com.reelometro.app.detection.OemScreen
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
 * Con el servicio activado pero sin funcionar enseña los pasos de la marca del móvil ([oem], [OemGuideSteps]); ya
 * conectado, la prueba de un minuto ([check], [ServiceCheckBlock]) y, plegada, la guía para que no se desconecte. La
 * prueba nunca bloquea: el pie sigue ahí.
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
    oem: OemGuideUi? = null,
    onOpenOem: (OemScreen) -> Unit = {},
    check: ServiceCheckUiState? = null,
    checkActions: ServiceCheckActions? = null,
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
                state.serviceEnabled && !state.serviceStalled -> ReelButton(
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
                if (disclosure) {
                    Disclosure(declined = state.declined)
                } else {
                    Guide(state, seed, onOpenAppInfo, onToggleRestrictedHelp, GuideExtras(oem, onOpenOem, check, checkActions))
                }
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

/** Lo que añade la línea de conexión fiable a la guía: pasos por marca y prueba de un minuto. */
private class GuideExtras(
    val oem: OemGuideUi?,
    val onOpenOem: (OemScreen) -> Unit,
    val check: ServiceCheckUiState?,
    val checkActions: ServiceCheckActions?,
)

@Composable
private fun ColumnScope.Guide(
    state: ServicePermissionState,
    seed: Long,
    onOpenAppInfo: () -> Unit,
    onToggleRestrictedHelp: () -> Unit,
    extras: GuideExtras,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    // Activado pero sin funcionar cuenta como no conectado: marcador en «– –» y los pasos para arreglarlo.
    val connected = state.serviceEnabled && !state.serviceStalled
    val stalled = state.serviceEnabled && state.serviceStalled
    val haptics = rememberHaptics()

    // Solo vibra si se ha conectado con la pantalla abierta (no al entrar ya conectado).
    LaunchedEffect(state.connectedHere && connected) {
        if (state.connectedHere && connected) {
            delay(CONFIRM_AFTER_FLIP_MS)
            haptics.confirm()
        }
    }

    Label(
        when {
            connected -> OnboardingCopy.GUIDE_CONNECTED_LABEL
            stalled -> OnboardingCopy.STALLED_LABEL
            else -> OnboardingCopy.GUIDE_WAITING
        },
        color = if (connected || stalled) colors.signalText else colors.textMuted,
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
        text = when {
            connected -> OnboardingCopy.connected(seed)
            stalled -> OnboardingCopy.STALLED_TITLE
            state.serviceLost -> OnboardingCopy.LOST_TITLE
            else -> OnboardingCopy.GUIDE_TITLE
        },
        style = type.headline,
        color = colors.text,
        modifier = Modifier.semantics { heading() },
    )
    AnimatedVisibility(
        visible = connected,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                OnboardingCopy.GUIDE_CONNECTED_BODY,
                style = type.body,
                color = colors.textMuted,
                modifier = Modifier.padding(top = Space.x4),
            )
            if (extras.check != null && extras.checkActions != null) {
                Spacer(Modifier.height(Space.x8))
                ServiceCheckBlock(extras.check, extras.oem, extras.onOpenOem, extras.checkActions)
            }
            // Si el sistema ya lo apagó una vez, la guía sale abierta y también en Android de serie.
            if (extras.oem != null && (extras.oem.family != OemFamily.STOCK || state.recoveredFromLoss)) {
                PreventDisconnect(extras.oem, extras.onOpenOem, startOpen = state.recoveredFromLoss)
            }
        }
    }
    AnimatedVisibility(
        visible = !connected,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        if (stalled) {
            StalledHelp(
                state.startError,
                state.stallReason,
                extras.oem,
                extras.onOpenOem,
                onOpenAppInfo,
                Modifier.padding(top = Space.x6),
            )
        } else Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(Space.x6))
            if (state.serviceLost) {
                // No lo ha apagado el usuario: se explica (con el motivo, si Android lo apunta) antes de los pasos.
                InlineNotice(text = OnboardingCopy.lostBody(state.stallReason))
                Spacer(Modifier.height(Space.x6))
            }
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

/**
 * Activado pero sin funcionar (sobre todo Xiaomi, Redmi y POCO): quitar las restricciones de batería, «Inicio
 * automático» y apagar y encender el servicio. Si el arranque falló, el texto del fallo va debajo, tal cual, para
 * poder mandárselo al autor.
 */
@Composable
private fun StalledHelp(
    startError: String?,
    stallReason: ExitCategory?,
    oem: OemGuideUi?,
    onOpenOem: (OemScreen) -> Unit,
    onOpenAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagnosis = stallReason?.let(ExitCopy::diagnosis)
    // Fallo de la propia app: esa frase en lugar del texto general, que habla de móviles que cierran las apps.
    val crash = stallReason == ExitCategory.APP_CRASH && diagnosis != null
    InlineNotice(
        text = if (crash) diagnosis.orEmpty() else OnboardingCopy.STALLED_BODY,
        // Con la guía por marca, cada paso lleva su botón; sin ella, al menos «Información de la aplicación».
        actionLabel = if (oem == null && !crash) OnboardingCopy.RESTRICTED_BUTTON else null,
        onAction = if (oem == null && !crash) onOpenAppInfo else null,
        actionIcon = ReelIcons.External,
        modifier = modifier,
    ) {
        // Por qué se paró, si Android guarda el motivo del último cierre (limpiar recientes, falta de memoria…).
        if (diagnosis != null && !crash) {
            Text(diagnosis, style = ReelTheme.type.body, color = ReelTheme.colors.signalText)
            Spacer(Modifier.height(Space.x4))
        }
        if (oem != null && !crash) OemGuideSteps(oem, onOpenOem)
        if (startError != null) {
            Spacer(Modifier.height(Space.x4))
            Label(OnboardingCopy.STALLED_ERROR_LABEL, color = ReelTheme.colors.signalText)
            Spacer(Modifier.height(Space.x2))
            Text(startError, style = ReelTheme.type.dataS, color = ReelTheme.colors.textMuted)
        }
    }
}

/**
 * Ya conectado: «EVITA QUE SE DESCONECTE», plegado, con los pasos de la marca (no en Android de serie). Si el sistema
 * ya lo apagó una vez ([startOpen]), sale abierto: es justo lo que hay que hacer para que no se repita.
 */
@Composable
private fun PreventDisconnect(oem: OemGuideUi, onOpenOem: (OemScreen) -> Unit, startOpen: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(startOpen) }
    Spacer(Modifier.height(Space.x6))
    ReelButton(
        text = OnboardingCopy.OEM_PREVENT_LINK,
        onClick = { open = !open },
        style = ReelButtonStyle.Ghost,
        trailingIcon = if (open) ReelIcons.ChevronUp else ReelIcons.ChevronDown,
        // TalkBack: plegado o desplegado, como acción estándar.
        modifier = Modifier.semantics {
            if (open) collapse { open = false; true } else expand { open = true; true }
        },
    )
    AnimatedVisibility(
        visible = open,
        enter = revealIn(ReelTheme.reducedMotion),
        exit = revealOut(ReelTheme.reducedMotion),
    ) {
        OemGuideSteps(oem, onOpenOem, Modifier.padding(top = Space.x4))
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
