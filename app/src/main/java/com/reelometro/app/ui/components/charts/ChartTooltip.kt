package com.reelometro.app.ui.components.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlin.math.max

/**
 * Tooltip de las gráficas: bloque recto en `text` con texto `bg` en mono y una flecha hacia el
 * punto. La primera línea (el valor) va en `data`; el resto (la fecha), en `dataS` algo más tenue.
 * Ejemplo de texto: "212 vídeos\nmartes 3 nov".
 *
 * Se dibuja dentro del Canvas de la gráfica, encima del punto: puede sobresalir por arriba (flota
 * sobre lo anterior), nunca por los lados.
 */
@Stable
class ChartTooltip internal constructor(
    private val measurer: TextMeasurer,
    private val valueStyle: TextStyle,
    private val detailStyle: TextStyle,
    private val background: Color,
    private val padH: Float,
    private val padV: Float,
    private val arrowW: Float,
    private val arrowH: Float,
    private val lift: Float,
) {
    private val arrow = Path()
    private var cachedText: String? = null
    private var cachedLayout: TextLayoutResult? = null

    private fun layoutOf(text: String): TextLayoutResult {
        val cached = cachedLayout
        if (cached != null && text == cachedText) return cached
        val nl = text.indexOf('\n')
        val annotated = buildAnnotatedString {
            if (nl < 0) {
                withStyle(valueStyle.toSpanStyle()) { append(text) }
            } else {
                withStyle(valueStyle.toSpanStyle()) { append(text, 0, nl) }
                append('\n')
                withStyle(detailStyle.toSpanStyle()) { append(text, nl + 1, text.length) }
            }
        }
        return measurer.measure(annotated, detailStyle.copy(lineHeight = valueStyle.lineHeight)).also {
            cachedText = text
            cachedLayout = it
        }
    }

    /** Dibuja el tooltip con la punta en [anchor] (coordenadas de la gráfica). */
    fun draw(scope: DrawScope, text: String, anchor: Offset) = with(scope) {
        val layout = layoutOf(text)
        val bodyW = layout.size.width + 2 * padH
        val bodyH = layout.size.height + 2 * padV
        val left = (anchor.x - bodyW / 2f).coerceIn(0f, max(0f, size.width - bodyW))
        val tipY = anchor.y - lift
        val bottom = tipY - arrowH
        val top = bottom - bodyH
        drawRect(background, Offset(left, top), Size(bodyW, bodyH))
        val baseX = anchor.x.coerceIn(left + arrowW / 2f, left + bodyW - arrowW / 2f)
        arrow.reset()
        arrow.moveTo(baseX - arrowW / 2f, bottom - 0.5f)
        arrow.lineTo(baseX + arrowW / 2f, bottom - 0.5f)
        arrow.lineTo(anchor.x.coerceIn(left, left + bodyW), tipY)
        arrow.close()
        drawPath(arrow, background)
        drawText(layout, topLeft = Offset(left + padH, top + padV))
    }
}

/** Tooltip con los colores y tipos del tema actual. */
@Composable
fun rememberChartTooltip(): ChartTooltip {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(colors, type, measurer, density) {
        with(density) {
            ChartTooltip(
                measurer = measurer,
                valueStyle = type.data.copy(color = colors.bg),
                detailStyle = type.dataS.copy(color = colors.bg.copy(alpha = 0.72f)),
                background = colors.text,
                padH = Space.x2.toPx(),
                padV = (Space.x1 + Space.x1 / 2).toPx(),
                arrowW = Space.x3.toPx(),
                arrowH = (Space.x1 + Space.x1 / 2).toPx(),
                lift = Space.x1.toPx(),
            )
        }
    }
}
