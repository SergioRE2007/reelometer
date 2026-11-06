package com.reelometro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics

private val OuterRule = 2.dp
private val InnerRule = 1.dp
private val RuleGap = 2.dp

/**
 * Sello de tinta rotado con doble borde y texto mono («100 % LOCAL», «PAGADO CON TU TIEMPO»).
 * Al aparecer, cae sobre el papel: entra grande y se asienta con spring `bouncy`.
 * Con «reducir animaciones» aparece directamente.
 *
 * @param rotation giro en grados (negativo = en sentido antihorario).
 * @param animate `false` para pintarlo ya asentado (listas, recomposiciones).
 * @param hapticOnLand un `confirm()` cuando el sello toca el papel (momentos de recompensa).
 */
@Composable
fun Stamp(
    text: String,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    color: Color = ReelTheme.colors.signalText,
    rotation: Float = -6f,
    animate: Boolean = true,
    hapticOnLand: Boolean = false,
) {
    val reduced = ReelTheme.reducedMotion
    val haptics = rememberHaptics()
    val impact = remember { Animatable(if (animate && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (impact.value < 1f) {
            var landed = false
            impact.animateTo(1f, Motion.bouncy()) {
                // Primer contacto con el papel: la escala llega a su tamaño por primera vez.
                if (!landed && value >= 1f) {
                    landed = true
                    if (hapticOnLand) haptics.confirm()
                }
            }
        }
    }

    Column(
        modifier
            .graphicsLayer {
                val p = impact.value
                val s = 1.6f - 0.6f * p
                scaleX = s
                scaleY = s
                alpha = (p * 2.5f).coerceIn(0f, 1f)
                rotationZ = rotation
            }
            .semantics(mergeDescendants = true) {}
            .drawBehind {
                val outer = OuterRule.toPx()
                val inner = InnerRule.toPx()
                val gap = RuleGap.toPx()
                drawRect(
                    color,
                    topLeft = Offset(outer / 2f, outer / 2f),
                    size = Size(size.width - outer, size.height - outer),
                    style = Stroke(outer),
                )
                val inset = outer + gap + inner / 2f
                drawRect(
                    color,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(inner),
                )
            }
            .padding(horizontal = Space.x4, vertical = Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Format.upper(text), style = ReelTheme.type.button, color = color, textAlign = TextAlign.Center)
        if (subtext != null) {
            Spacer(Modifier.height(Space.x1))
            Text(Format.upper(subtext), style = ReelTheme.type.label, color = color, textAlign = TextAlign.Center)
        }
    }
}
