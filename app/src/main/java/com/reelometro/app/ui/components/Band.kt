package com.reelometro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/**
 * Banda a sangre (docs/DESIGN.md §3): bloque `signal` de borde a borde con texto `onSignal`.
 * Máximo una por pantalla. Ponla **sin** margen lateral: el texto ya lleva el de 20 dp.
 *
 * @param kicker etiqueta pequeña encima («DESDE EL 3 MAR»).
 * @param onClick si la banda lleva a algún sitio; añade la flecha → y se hunde al pulsar.
 */
@Composable
fun Band(
    text: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Band(modifier = modifier, onClick = onClick, onClickLabel = onClickLabel) {
        if (kicker != null) {
            Label(kicker, color = ReelTheme.colors.onSignal)
            Spacer(Modifier.height(Space.x2))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = ReelTheme.type.headline, color = ReelTheme.colors.onSignal, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Spacer(Modifier.width(Space.x4))
                ReelIcon(ReelIcons.ArrowRight, contentDescription = null, tint = ReelTheme.colors.onSignal)
            }
        }
    }
}

/** Banda con contenido libre; dentro, el color de contenido es `onSignal`. */
@Composable
fun Band(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ReelTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, onClickLabel = onClickLabel) else Modifier)
            .background(colors.signal)
            .padding(horizontal = Space.screen, vertical = Space.x6),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onSignal,
            LocalTextStyle provides ReelTheme.type.body.copy(color = colors.onSignal),
        ) {
            content()
        }
    }
}
