package com.reelometro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Shapes
import com.reelometro.app.ui.theme.Space

/** Variantes de [ReelButton] (docs/DESIGN.md §4). */
enum class ReelButtonStyle {
    /** Relleno `signal` y texto `onSignal`. Una acción principal por pantalla. */
    Primary,

    /** Borde de 1 dp en `lineStrong`, fondo transparente. */
    Secondary,

    /** Solo texto y flecha →, sin caja; se alinea con el texto de alrededor. */
    Ghost,
}

/** Tamaño de los iconos dentro de un botón. */
private val ButtonIconSize = 20.dp

/**
 * Botón de la app: mono en mayúsculas, altura mínima de 48 dp, se hunde al pulsar
 * ([pressable]) y vibra. Nunca usa el `Button` de Material.
 *
 * @param text texto tal cual (se pone en mayúsculas solo).
 * @param icon icono delante del texto.
 * @param trailingIcon icono detrás; por defecto, la flecha → en [ReelButtonStyle.Ghost].
 * @param haptic vibración al pulsar; desactívala si `onClick` hace la suya (p. ej. `reject()`).
 * @param contentDescription sustituye al texto para TalkBack cuando el texto solo no basta
 *   («Compartir resumen» en vez de «Compartir»).
 */
@Composable
fun ReelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ReelButtonStyle = ReelButtonStyle.Primary,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = if (style == ReelButtonStyle.Ghost) ReelIcons.ArrowRight else null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = ReelTheme.colors
    val content: Color
    val box: Modifier
    when (style) {
        ReelButtonStyle.Primary -> {
            content = if (enabled) colors.onSignal else colors.textFaint
            box = Modifier.background(if (enabled) colors.signal else colors.bgSunken, Shapes.button)
        }
        ReelButtonStyle.Secondary -> {
            content = if (enabled) colors.text else colors.textFaint
            box = Modifier.border(Space.hairline, if (enabled) colors.lineStrong else colors.line, Shapes.button)
        }
        ReelButtonStyle.Ghost -> {
            content = if (enabled) colors.text else colors.textFaint
            box = Modifier
        }
    }
    val padding = if (style == ReelButtonStyle.Ghost) {
        PaddingValues(vertical = Space.x3)
    } else {
        PaddingValues(horizontal = Space.x5, vertical = Space.x3)
    }

    Row(
        modifier = modifier
            .pressable(onClick = onClick, haptic = haptic, enabled = enabled)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .defaultMinSize(minHeight = Space.touch)
            .then(box)
            .padding(padding),
        horizontalArrangement = if (style == ReelButtonStyle.Ghost) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ReelIcon(icon, contentDescription = null, tint = content, size = ButtonIconSize)
            Spacer(Modifier.width(Space.x2))
        }
        val style = ReelTheme.type.button
        BasicText(
            text = Format.upper(text),
            modifier = Modifier.weight(1f, fill = false),
            style = style.copy(color = content, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(Space.x2))
            ReelIcon(trailingIcon, contentDescription = null, tint = content, size = ButtonIconSize)
        }
    }
}

/**
 * Botón de icono de 48 × 48 dp (acciones de barra, chevrons). El icono va a 24 dp.
 * El [contentDescription] es obligatorio: dice lo que hace («Abrir ajustes»).
 */
@Composable
fun ReelIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = ReelTheme.colors.text,
    haptic: Boolean = true,
    size: Dp = Space.touch,
) {
    Box(
        modifier = modifier
            .pressable(onClick = onClick, haptic = haptic, enabled = enabled)
            .semantics { this.contentDescription = contentDescription }
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        ReelIcon(icon, contentDescription = null, tint = if (enabled) tint else ReelTheme.colors.textFaint)
    }
}
