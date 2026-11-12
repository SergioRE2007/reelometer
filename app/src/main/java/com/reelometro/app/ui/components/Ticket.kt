package com.reelometro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// ====================================================================== ReceiptLine

/** Separación de los puntos guía; se alinean a esta rejilla para que cuadren entre líneas. */
private val LeaderPitch = 4.dp
private val LeaderDot = 1.5.dp

/**
 * Línea de ticket: `etiqueta ············ valor` (docs/DESIGN.md §3). Etiqueta en `body`, valor en
 * mono `data`; los puntos guía van sobre la línea base. TalkBack la lee entera («TikTok, 120»).
 *
 * @param mark punto de color delante de la etiqueta (plataforma, categoría).
 * @param highlight variante destacada (línea de «total»): etiqueta `labelL` en mayúsculas y valor `dataL`.
 * @param valueColor color del valor; por convención, `signalText` cuando el dato es «peor».
 * @param onClick si la línea es una acción (abre algo), se hunde al pulsar y mide al menos 48 dp.
 */
@Composable
fun ReceiptLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    mark: Color? = null,
    highlight: Boolean = false,
    valueColor: Color = ReelTheme.colors.text,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    ReceiptLine(
        label = label,
        modifier = modifier,
        mark = mark,
        highlight = highlight,
        onClick = onClick,
        onClickLabel = onClickLabel,
    ) {
        // El valor se encoge antes que recortarse a media cifra o palabra («DÉFINIR UN OBJECTIF»).
        val valueStyle = if (highlight) ReelTheme.type.dataL else ReelTheme.type.data
        BasicText(
            text = value,
            style = valueStyle.copy(color = valueColor),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = AutoSizeTokens.Min, maxFontSize = valueStyle.fontSize, stepSize = AutoSizeTokens.Step),
        )
    }
}

/**
 * Variante con un elemento a la derecha en lugar de texto: un [ReelSwitch], un [Stepper], un
 * icono… Si el elemento tiene línea base (texto) se alinea con ella; si no, se centra.
 */
@Composable
fun ReceiptLine(
    label: String,
    modifier: Modifier = Modifier,
    mark: Color? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val labelStyle = if (highlight) type.labelL else type.body
    val geometry = remember { LeaderGeometry() }
    val dotColor = colors.lineStrong
    val hasMark = mark != null

    val interaction = if (onClick != null) {
        Modifier.pressable(onClick = onClick, onClickLabel = onClickLabel)
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }

    Layout(
        content = {
            if (mark != null) ColorMark(mark)
            Text(
                text = if (highlight) Format.upper(label) else label,
                style = labelStyle,
                color = colors.text,
                onTextLayout = { geometry.label = it },
            )
            trailing()
        },
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .padding(vertical = Space.x2)
            .drawBehind {
                val pitch = LeaderPitch.toPx()
                val r = LeaderDot.toPx() / 2f
                var x = ceil(geometry.dotsStart / pitch) * pitch
                while (x <= geometry.dotsEnd) {
                    drawCircle(dotColor, radius = r, center = Offset(x, geometry.dotsY))
                    x += pitch
                }
            },
    ) { measurables, constraints ->
        val gap = Space.x2.roundToPx()
        val minLeader = Space.x3.roundToPx()
        val width = constraints.maxWidth
        // Si es una acción, 48 dp en total (con el padding vertical de 8 + 8 dp).
        val minHeight = max(
            constraints.minHeight,
            if (onClick != null) (Space.touch - Space.x4).roundToPx() else 0,
        )
        var i = 0
        val markP = if (hasMark) measurables[i++].measure(Constraints()) else null
        val labelM = measurables[i++]
        val trailingM = measurables[i]
        val markW = markP?.let { it.width + gap } ?: 0
        val trailingP = trailingM.measure(Constraints(maxWidth = ((width - markW) * 0.6f).roundToInt()))
        val labelP = labelM.measure(
            Constraints(maxWidth = max(0, width - markW - trailingP.width - gap * 2 - minLeader)),
        )

        val labelFirst = labelP[FirstBaseline].orElse(labelP.height)
        val labelLast = labelP[LastBaseline].orElse(labelP.height)
        val trailingBase = trailingP[FirstBaseline]

        var labelY: Int
        var trailingY: Int
        if (trailingBase != AlignmentLine.Unspecified) {
            labelY = max(0, trailingBase - labelLast)
            trailingY = labelY + labelLast - trailingBase
        } else {
            val h = max(labelP.height, trailingP.height)
            labelY = (h - labelP.height) / 2
            trailingY = (h - trailingP.height) / 2
        }
        val contentH = max(labelY + labelP.height, trailingY + trailingP.height)
        val height = max(contentH, minHeight)
        val shift = (height - contentH) / 2
        labelY += shift
        trailingY += shift

        val lastLine = geometry.label
        val lastRight = if (lastLine != null && lastLine.lineCount > 0) {
            lastLine.getLineRight(lastLine.lineCount - 1)
        } else {
            labelP.width.toFloat()
        }
        geometry.dotsStart = markW + lastRight + gap
        geometry.dotsEnd = (width - trailingP.width - gap).toFloat()
        // Los puntos se apoyan en la línea base, como un punto tipográfico.
        geometry.dotsY = labelY + labelLast - LeaderDot.toPx() / 2f

        layout(width, height) {
            markP?.let {
                val capMiddle = labelY + labelFirst - labelStyle.fontSize.toPx() * DisplayCapHeight / 2f
                it.place(0, (capMiddle - it.height / 2f).roundToInt())
            }
            labelP.place(markW, labelY)
            trailingP.place(width - trailingP.width, trailingY)
        }
    }
}

private fun Int.orElse(fallback: Int) = if (this == AlignmentLine.Unspecified) fallback else this

/** Datos de la medición que necesita el dibujo de los puntos guía. */
private class LeaderGeometry {
    var label: TextLayoutResult? = null
    var dotsStart = 0f
    var dotsEnd = 0f
    var dotsY = 0f
}

// ====================================================================== TicketDivider

/** Estilos de [TicketDivider]. */
enum class TicketDividerStyle {
    /** Línea discontinua de 1 dp (trazo 4, hueco 4). */
    Dashed,

    /** Discontinua con muescas semicirculares de 8 dp en los extremos: el corte del papel. */
    Perforated,
}

private val Dash = 4.dp
private val NotchRadius = 8.dp

/**
 * Separador de ticket. Ocupa todo el ancho que se le dé: el perforado debe ir **de borde a borde**
 * del papel (o de la pantalla) para que las muescas caigan en el canto.
 *
 * @param notchColor relleno de las muescas = lo que se ve «a través» del papel. Sobre el fondo de
 *   pantalla, `bgSunken` (por defecto) las marca como hendiduras; sobre una hoja (`bgRaised`),
 *   pasa el color de detrás (`bg`).
 * @param notchRim filete opcional del canto de la muesca (p. ej. el mismo color que el borde de la hoja).
 */
@Composable
fun TicketDivider(
    modifier: Modifier = Modifier,
    style: TicketDividerStyle = TicketDividerStyle.Dashed,
    color: Color = ReelTheme.colors.line,
    notchColor: Color = ReelTheme.colors.bgSunken,
    notchRim: Color? = null,
) {
    val height = if (style == TicketDividerStyle.Perforated) NotchRadius * 2 else Space.hairline
    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawWithCache {
                val dash = Dash.toPx()
                val effect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
                val hair = Space.hairline.toPx()
                val r = NotchRadius.toPx()
                val inset = if (style == TicketDividerStyle.Perforated) r + dash else 0f
                val available = size.width - inset * 2
                // Número entero de trazos, centrado: los dos extremos acaban igual.
                val count = max(1, ((available + dash) / (dash * 2)).toInt())
                val used = count * dash * 2 - dash
                val start = inset + (available - used) / 2f
                val y = size.height / 2f
                val rim = notchRim?.let { Stroke(width = hair) }
                onDrawBehind {
                    drawLine(color, Offset(start, y), Offset(start + used, y), strokeWidth = hair, pathEffect = effect)
                    if (style == TicketDividerStyle.Perforated) {
                        val box = Size(r * 2, r * 2)
                        val left = Offset(-r, y - r)
                        val right = Offset(size.width - r, y - r)
                        drawArc(notchColor, -90f, 180f, useCenter = true, topLeft = left, size = box)
                        drawArc(notchColor, 90f, 180f, useCenter = true, topLeft = right, size = box)
                        if (notchRim != null && rim != null) {
                            drawArc(notchRim, -90f, 180f, useCenter = false, topLeft = left, size = box, style = rim)
                            drawArc(notchRim, 90f, 180f, useCenter = false, topLeft = right, size = box, style = rim)
                        }
                    }
                }
            },
    )
}

// ====================================================================== JaggedEdge

/** Lado dentado de un bloque tipo ticket. */
enum class JaggedSide { Top, Bottom }

/** Dientes del papel: 8 dp de paso y 4 dp de fondo (vértices a 90°). */
val JaggedTooth: Dp = 8.dp

/**
 * Forma de rectángulo con un lado dentado (borde de ticket arrancado). El número de dientes se
 * ajusta para que encajen exactos en el ancho: los dos extremos acaban en valle.
 * Úsala como `Modifier.background(color, JaggedShape())` o con `border`/`clip`.
 */
@Immutable
class JaggedShape(
    private val side: JaggedSide = JaggedSide.Bottom,
    private val tooth: Dp = JaggedTooth,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val toothPx = with(density) { tooth.toPx() }
        val depth = toothPx / 2f
        val n = max(1, (size.width / toothPx).roundToInt())
        val tw = size.width / n
        val path = Path()
        when (side) {
            JaggedSide.Bottom -> {
                path.moveTo(0f, 0f)
                path.lineTo(size.width, 0f)
                path.lineTo(size.width, size.height - depth)
                for (k in n - 1 downTo 0) {
                    path.lineTo(k * tw + tw / 2f, size.height)
                    path.lineTo(k * tw, size.height - depth)
                }
            }
            JaggedSide.Top -> {
                path.moveTo(0f, depth)
                for (k in 0 until n) {
                    path.lineTo(k * tw + tw / 2f, 0f)
                    path.lineTo((k + 1) * tw, depth)
                }
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is JaggedShape && other.side == side && other.tooth == tooth

    override fun hashCode(): Int = 31 * side.hashCode() + tooth.hashCode()
}

/** Fondo de papel con un lado dentado: atajo de `background(color, JaggedShape(side))`. */
fun Modifier.jaggedEdge(color: Color, side: JaggedSide = JaggedSide.Bottom, tooth: Dp = JaggedTooth): Modifier =
    background(color, JaggedShape(side, tooth))

/**
 * Tira de dientes suelta para cerrar un bloque de color [color] que va justo encima
 * ([JaggedSide.Bottom]) o justo debajo ([JaggedSide.Top]). Mide 4 dp de alto.
 */
@Composable
fun JaggedEdge(
    modifier: Modifier = Modifier,
    color: Color = ReelTheme.colors.bgRaised,
    side: JaggedSide = JaggedSide.Bottom,
    tooth: Dp = JaggedTooth,
) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(tooth / 2)
            .drawWithCache {
                val toothPx = tooth.toPx()
                val n = max(1, (size.width / toothPx).roundToInt())
                val tw = size.width / n
                val h = size.height
                val path = Path().apply {
                    if (side == JaggedSide.Bottom) {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        for (k in n - 1 downTo 0) {
                            lineTo(k * tw + tw / 2f, h)
                            lineTo(k * tw, 0f)
                        }
                    } else {
                        moveTo(0f, h)
                        for (k in 0 until n) {
                            lineTo(k * tw + tw / 2f, 0f)
                            lineTo((k + 1) * tw, h)
                        }
                    }
                    close()
                }
                onDrawBehind { drawPath(path, color) }
            },
    )
}
