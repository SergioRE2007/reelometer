package com.reelometro.app.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics

/** Opacidad del velo: el fondo de la app queda como niebla del color de fondo. */
private const val SCRIM_ALPHA = 0.9f

/**
 * Diálogo propio (nada de `AlertDialog`): una hoja de ticket `bgRaised` con filete y borde
 * dentado inferior, centrada sobre un velo del color de fondo. Entra subiendo 24 dp con spring
 * `standard` y sale igual (fundido con «reducir animaciones»). Atrás o tocar fuera = [onDismiss].
 *
 * Se controla con [visible] (no lo quites de la composición) para que pueda animar la salida.
 *
 * @param destructive acción irreversible: etiqueta en `signalText`, icono de papelera en el botón
 *   y vibración `reject()` al confirmar.
 * @param dismissLabel `null` para un aviso de un solo botón.
 * @param content contenido extra entre el texto y los botones.
 */
@Composable
fun ReelDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    text: String? = null,
    dismissLabel: String? = "Cancelar",
    destructive: Boolean = false,
    confirmIcon: ImageVector? = if (destructive) ReelIcons.Trash else null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return

    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val haptics = rememberHaptics()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Sin el oscurecido ni las animaciones de ventana del sistema: el velo y el movimiento son nuestros.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            // 0 significaría «las del tema»; el estilo base de animaciones está vacío.
            window?.setWindowAnimations(android.R.style.Animation)
        }

        // Con «reducir animaciones», solo un fundido corto (sin desplazamiento ni rebote).
        val spec = if (reduced) spring<Float>(dampingRatio = 1f, stiffness = 1500f) else Motion.standard()
        val transition = rememberTransition(state, label = "dialog")
        val scrim by transition.animateFloat({ spec }, label = "scrim") { if (it) 1f else 0f }
        val sheet by transition.animateFloat({ spec }, label = "sheet") { if (it) 1f else 0f }
        val rise = if (reduced) 0f else 1f

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(colors.bg, alpha = SCRIM_ALPHA * scrim) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .safeDrawingPadding()
                .padding(Space.screen),
            contentAlignment = Alignment.Center,
        ) {
            val shape = remember { JaggedShape(JaggedSide.Bottom) }
            Column(
                modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = sheet.coerceIn(0f, 1f)
                        translationY = (1f - sheet) * 24.dp.toPx() * rise
                    }
                    .semantics { paneTitle = title }
                    // Absorbe los toques para que no lleguen al velo.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .background(colors.bgRaised, shape)
                    .border(Space.hairline, colors.lineStrong, shape)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.padding(start = Space.x6, end = Space.x6, top = Space.x6)) {
                    if (kicker != null) {
                        Label(kicker, color = if (destructive) colors.signalText else colors.textMuted)
                        Spacer(Modifier.height(Space.x3))
                    }
                    Text(
                        title,
                        style = ReelTheme.type.headline,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (text != null) {
                        Spacer(Modifier.height(Space.x3))
                        Text(text, style = ReelTheme.type.body, color = colors.textMuted)
                    }
                    if (content != null) {
                        Spacer(Modifier.height(Space.x4))
                        content()
                    }
                }
                Spacer(Modifier.height(Space.x5))
                TicketDivider(
                    style = TicketDividerStyle.Perforated,
                    notchColor = colors.bg,
                    notchRim = colors.lineStrong,
                )
                Spacer(Modifier.height(Space.x4))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Space.x6, end = Space.x6, bottom = Space.x6 + JaggedTooth / 2),
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    if (dismissLabel != null) {
                        ReelButton(
                            text = dismissLabel,
                            onClick = onDismiss,
                            style = ReelButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ReelButton(
                        text = confirmLabel,
                        onClick = {
                            if (destructive) haptics.reject()
                            onConfirm()
                        },
                        icon = confirmIcon,
                        haptic = !destructive,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
