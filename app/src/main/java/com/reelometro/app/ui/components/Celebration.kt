package com.reelometro.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.reelometro.app.copy.Buttons
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics

/** Qué se celebra (cambia el gesto de entrada del [art] y la vibración). */
enum class CelebrationKind {
    /** Un sello que se estampa: escala + giro con spring `bouncy`. */
    STAMP,

    /** Nivel nuevo: la tarjeta entra y las fichas voltean (split-flap). */
    LEVEL,

    /** Reto semanal cumplido. */
    CHALLENGE,
}

/** Botón opcional de la celebración (p. ej. «COMPARTIR TICKET»). */
@Immutable
data class CelebrationAction(val label: String, val onClick: () -> Unit, val contentDescription: String? = null)

/** Gestos de entrada del arte (escala inicial, giro inicial y final del sello). */
private object CelebrationMotion {
    const val STAMP_FROM_SCALE = 1.6f
    const val STAMP_FROM_ROTATION = -18f
    const val STAMP_TO_ROTATION = -6f
    const val CHALLENGE_FROM_SCALE = 1.25f

    /** El arte se hace visible en el primer 40 % del recorrido (el resto es el asentamiento). */
    const val FADE_SHARE = 2.5f

    /** Ancho máximo de la columna en pantallas anchas (como el diálogo). */
    const val MAX_WIDTH_SPACES = 11
}

/**
 * Celebración común del modo Pasaporte (docs/DESIGN.md §6: springs, un `confirm()` al asentarse,
 * sin confeti, sin degradados, sin exclamaciones). Hoja a pantalla completa sobre `bg` con el
 * [art] (el sello, la tarjeta de nivel…) que entra estampándose, la etiqueta, el titular y la
 * frase, y los botones. Con «reducir animaciones», aparece ya asentada. Atrás la cierra.
 *
 * - [CelebrationKind.STAMP]: el sello cae sobre el papel (escala 1,6 → 1 y giro −18° → −6° con
 *   spring `bouncy`); vibración `confirm()` al tocar el papel.
 * - [CelebrationKind.LEVEL]: el arte sube 24 dp con `standard` (el panel split-flap hace el resto);
 *   `confirm()` al asentarse.
 * - [CelebrationKind.CHALLENGE]: la cifra crece desde 1,25 con `bouncy`; `confirm()` al asentarse.
 *
 * Si cambian [kind], [label] o [title] con la hoja abierta (varias celebraciones seguidas), el
 * arte vuelve a entrar sin cerrar la hoja.
 *
 * @param label etiqueta pequeña («SELLO NUEVO», «NIVEL NUEVO»).
 * @param title titular (nombre del sello o del nivel).
 * @param caption frase de `copy/` (serif), opcional.
 * @param onDismiss cerrar (botón SEGUIR, atrás o toque fuera).
 */
@Composable
fun Celebration(
    visible: Boolean,
    kind: CelebrationKind,
    label: String,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    action: CelebrationAction? = null,
    dismissLabel: String = Buttons.CLOSE,
    art: @Composable () -> Unit = {},
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    // Empieza oculta para que la primera aparición también entre con su fundido.
    val presence = remember { MutableTransitionState(false) }
    presence.targetState = visible
    AnimatedVisibility(
        visibleState = presence,
        enter = fadeIn(Motion.standard()),
        exit = fadeOut(Motion.standard()),
        modifier = modifier,
    ) {
        // Mientras se desvanece ya no está: ni atrás ni toques (nada se cierra ni se comparte dos veces).
        BackHandler(enabled = visible, onBack = onDismiss)
        val reduced = ReelTheme.reducedMotion
        val haptics = rememberHaptics()
        val impact = remember { Animatable(if (reduced) 1f else 0f) }
        LaunchedEffect(kind, label, title) {
            if (reduced) {
                impact.snapTo(1f)
                haptics.confirm()
            } else {
                impact.snapTo(0f)
                var landed = false
                val spec = if (kind == CelebrationKind.LEVEL) Motion.standard<Float>() else Motion.bouncy<Float>()
                impact.animateTo(1f, spec) {
                    // Primer contacto: la escala llega a su tamaño por primera vez.
                    if (!landed && value >= 1f) {
                        landed = true
                        haptics.confirm()
                    }
                }
                if (!landed) haptics.confirm()
            }
        }
        val rise = with(LocalDensity.current) { Space.x6.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.bg)
                // Anuncia la hoja al aparecer (como ReelDialog); el liveRegion de abajo, las encadenadas.
                .semantics { paneTitle = "$label. $title" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = visible,
                    onClickLabel = dismissLabel,
                    onClick = onDismiss,
                )
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.x8),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = Space.x10 * CelebrationMotion.MAX_WIDTH_SPACES)
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.graphicsLayer {
                        val p = impact.value
                        when (kind) {
                            CelebrationKind.STAMP -> {
                                val s = CelebrationMotion.STAMP_FROM_SCALE - (CelebrationMotion.STAMP_FROM_SCALE - 1f) * p
                                scaleX = s
                                scaleY = s
                                rotationZ = CelebrationMotion.STAMP_FROM_ROTATION +
                                    (CelebrationMotion.STAMP_TO_ROTATION - CelebrationMotion.STAMP_FROM_ROTATION) * p
                            }
                            CelebrationKind.LEVEL -> {
                                translationY = (1f - p) * rise
                            }
                            CelebrationKind.CHALLENGE -> {
                                val s = CelebrationMotion.CHALLENGE_FROM_SCALE - (CelebrationMotion.CHALLENGE_FROM_SCALE - 1f) * p
                                scaleX = s
                                scaleY = s
                            }
                        }
                        alpha = (p * CelebrationMotion.FADE_SHARE).coerceIn(0f, 1f)
                    },
                    contentAlignment = Alignment.Center,
                ) { art() }
                Spacer(Modifier.height(Space.x8))
                Label(label, color = colors.signalText)
                Spacer(Modifier.height(Space.x2))
                Text(
                    title,
                    style = type.headline,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (caption != null) {
                    Spacer(Modifier.height(Space.x4))
                    Text(caption, style = type.quoteSmall, color = colors.textMuted, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(Space.x10))
                if (action != null) {
                    ReelButton(
                        text = action.label,
                        onClick = { if (visible) action.onClick() },
                        contentDescription = action.contentDescription,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.x3))
                }
                ReelButton(
                    text = dismissLabel,
                    onClick = { if (visible) onDismiss() },
                    style = ReelButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
