package com.reelometro.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.ReelTheme

/**
 * Etiqueta pequeña en mono y MAYÚSCULAS con tracking («VÍDEOS», «HORA PUNTA»).
 * El texto se pasa en minúsculas o como venga: se pone en mayúsculas con [Format.upper].
 *
 * @param large usa `labelL` (13 sp, pestañas y cabeceras) en vez de `label` (11 sp).
 */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ReelTheme.colors.textMuted,
    large: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = Format.upper(text),
        modifier = modifier,
        style = if (large) ReelTheme.type.labelL else ReelTheme.type.label,
        color = color,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}
