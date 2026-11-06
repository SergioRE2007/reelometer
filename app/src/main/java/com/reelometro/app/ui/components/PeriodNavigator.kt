package com.reelometro.app.ui.components

import com.reelometro.app.copy.A11y
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/**
 * Navegador de periodo `‹  27 OCT – 2 NOV  ›` (docs/DESIGN.md §4). Chevrons de 48 dp; si no se
 * puede ir más allá, el chevron se apaga (`textFaint`) y deja de responder.
 * Al avanzar, la etiqueta nueva entra por la derecha y la vieja sale por la izquierda (al revés al
 * retroceder), 16 dp con spring `standard`; si la etiqueta cambia por otra causa (otra pestaña),
 * solo funde. Con «reducir animaciones», fundido.
 *
 * El glifo del chevron queda a ~12 dp del borde de su objetivo táctil: para alinearlo con el margen
 * de pantalla, coloca el navegador con `padding(horizontal = Space.x2)` en lugar de `Space.screen`.
 */
@Composable
fun PeriodNavigator(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoPrevious: Boolean = true,
    canGoNext: Boolean = true,
    previousDescription: String = A11y.PREVIOUS_PERIOD,
    nextDescription: String = A11y.NEXT_PERIOD,
) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val shift = with(LocalDensity.current) { Space.x4.roundToPx() }
    // −1 = atrás, +1 = adelante, 0 = cambio ajeno a los chevrons.
    var direction by remember { mutableIntStateOf(0) }
    LaunchedEffect(label) { direction = 0 }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ReelIconButton(
            icon = ReelIcons.ChevronLeft,
            contentDescription = previousDescription,
            onClick = {
                direction = -1
                onPrevious()
            },
            enabled = canGoPrevious,
        )
        Box(
            Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    val d = direction
                    if (reduced || d == 0) {
                        fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard())
                    } else {
                        (slideInHorizontally(Motion.standard()) { d * shift } + fadeIn(Motion.standard())) togetherWith
                            (slideOutHorizontally(Motion.standard()) { -d * shift } + fadeOut(Motion.standard()))
                    }.using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.Center,
                label = "period",
            ) { text ->
                val style = ReelTheme.type.labelL
                BasicText(
                    text = Format.upper(text),
                    style = style.copy(color = colors.text, textAlign = TextAlign.Center),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                )
            }
        }
        ReelIconButton(
            icon = ReelIcons.ChevronRight,
            contentDescription = nextDescription,
            onClick = {
                direction = 1
                onNext()
            },
            enabled = canGoNext,
        )
    }
}
