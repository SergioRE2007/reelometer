package com.reelometro.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.reelometro.app.data.Platform
import com.reelometro.app.ui.theme.ReelTheme

/** Forma de una marca de color. */
enum class MarkStyle {
    /** Punto circular de 8 dp. */
    Dot,

    /** Filete horizontal de 16 × 2 dp. */
    Rule,
}

/**
 * Marca de color de una plataforma (docs/DESIGN.md §1): solo puntos y filetes, nunca fondos.
 * Es decorativa: el nombre de la plataforma siempre va escrito al lado.
 */
@Composable
fun PlatformMark(
    platform: Platform,
    modifier: Modifier = Modifier,
    style: MarkStyle = MarkStyle.Dot,
) {
    ColorMark(ReelTheme.colors.platform(platform), modifier, style)
}

/** Marca de color genérica (categorías, series de una gráfica…). */
@Composable
fun ColorMark(
    color: Color,
    modifier: Modifier = Modifier,
    style: MarkStyle = MarkStyle.Dot,
    dotSize: Dp = 8.dp,
) {
    val size = when (style) {
        MarkStyle.Dot -> DpSize(dotSize, dotSize)
        MarkStyle.Rule -> DpSize(16.dp, 2.dp)
    }
    Canvas(modifier.size(size)) {
        when (style) {
            MarkStyle.Dot -> drawCircle(color)
            MarkStyle.Rule -> drawRect(color, size = Size(this.size.width, this.size.height))
        }
    }
}
