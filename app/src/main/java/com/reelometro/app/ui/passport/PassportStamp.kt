package com.reelometro.app.ui.passport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.StampKind
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Trazo exterior e interior del sello y el hueco entre ellos (como `Stamp`). */
private val OuterRule: Dp = Space.hairline * 2
private val InnerRule: Dp = Space.hairline
private val RuleGap: Dp = Space.hairline * 3

/** Paso de los dientes del borde dentado (sellos de hito, como un sello de correos). */
private val Tooth: Dp = Space.x2

/** Discontinua del contorno de los sellos que faltan y de los irónicos. */
private val Dash: Dp = Space.x1

/**
 * Un sello del pasaporte dibujado a mano alzada en `Canvas`: la forma sale de la familia
 * ([StampKind]) — OBJETIVO redondo, RACHA rectangular, RETO octogonal, HITO con borde dentado de
 * sello de correos e IRÓNICO hexagonal con trazo discontinuo («SIN MILLAS») —, doble borde,
 * rótulo pequeño, nombre en mono y fecha de obtención. Los que faltan van en negativo: contorno
 * tenue y discontinuo, sin relleno, con el progreso si lo hay.
 *
 * Colores: el conseguido más reciente en `signalText`, el resto en `text`, los irónicos en
 * `textMuted`; los pendientes, contorno `lineStrong` y texto `textMuted`.
 *
 * @param tilt girarlo un poco (como estampado a mano); `false` en la celebración (ya gira ella).
 * @param onClick abre la pista o el detalle; `null` si es solo dibujo.
 */
@Composable
fun PassportStamp(
    stamp: StampUi,
    modifier: Modifier = Modifier,
    tilt: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val ink = when {
        !stamp.earned -> colors.lineStrong
        stamp.latest -> colors.signalText
        stamp.ironic -> colors.textMuted
        else -> colors.text
    }
    val textColor = if (stamp.earned) ink else colors.textMuted
    val dashed = !stamp.earned || stamp.ironic
    val round = stamp.kind != StampKind.RACHA && stamp.kind != StampKind.HITO
    val action = if (onClick != null) {
        Modifier.pressable(onClick = onClick, onClickLabel = PassportCopy.STAMP_OPEN)
    } else {
        Modifier
    }
    Box(
        modifier
            .aspectRatio(1f)
            .then(action)
            .semantics(mergeDescendants = true) { contentDescription = stamp.description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .graphicsLayer { rotationZ = if (tilt) stamp.tilt else 0f }
                .drawBehind { drawStampShape(stamp.kind, ink, dashed) },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(if (round) Space.x5 else Space.x4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.x1, Alignment.CenterVertically),
            ) {
                StampText(stamp.kindLabel, type.label, textColor, maxLines = 1)
                // El nombre se mide el último: el rótulo y la fecha tienen siempre su línea (130 %).
                StampText(stamp.name, type.labelL, textColor, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                StampText(stamp.date ?: stamp.progressText ?: PassportCopy.STAMP_PENDING, type.label, textColor, maxLines = 1)
            }
        }
    }
}

/**
 * Texto del sello: centrado y encogiendo hasta caber (fuente al 130 % incluida), sin cortes. El
 * interlineado va en `em` para que encoja con la letra.
 */
@Composable
private fun StampText(text: String, style: TextStyle, color: Color, maxLines: Int, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            textAlign = TextAlign.Center,
            lineHeight = (style.lineHeight.value / style.fontSize.value).em,
        ),
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.55f, maxFontSize = style.fontSize, stepSize = style.fontSize * 0.05f),
    )
}

/** Doble borde con la forma de la familia del sello. */
private fun DrawScope.drawStampShape(kind: StampKind, ink: Color, dashed: Boolean) {
    val outer = OuterRule.toPx()
    val inner = InnerRule.toPx()
    val gap = RuleGap.toPx()
    val dash = Dash.toPx()
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(dash, dash)) else null
    val side = min(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = side / 2f - outer
    val innerR = r - outer / 2f - gap - inner / 2f
    when (kind) {
        StampKind.OBJETIVO -> {
            drawCircle(ink, radius = r, center = Offset(cx, cy), style = Stroke(outer, pathEffect = effect))
            drawCircle(ink, radius = innerR, center = Offset(cx, cy), style = Stroke(inner, pathEffect = effect))
        }
        StampKind.RACHA -> {
            val inset = outer / 2f
            drawRect(ink, topLeft = Offset(inset, inset), size = Size(size.width - outer, size.height - outer), style = Stroke(outer, pathEffect = effect))
            val i2 = outer + gap + inner / 2f
            drawRect(ink, topLeft = Offset(i2, i2), size = Size(size.width - i2 * 2, size.height - i2 * 2), style = Stroke(inner, pathEffect = effect))
        }
        StampKind.RETO -> {
            drawPath(polygon(cx, cy, r, 8, PI / 8), ink, style = Stroke(outer, pathEffect = effect))
            drawPath(polygon(cx, cy, innerR, 8, PI / 8), ink, style = Stroke(inner, pathEffect = effect))
        }
        StampKind.IRONICO -> {
            drawPath(polygon(cx, cy, r, 6, 0.0), ink, style = Stroke(outer, pathEffect = effect))
            drawPath(polygon(cx, cy, innerR, 6, 0.0), ink, style = Stroke(inner, pathEffect = effect))
        }
        StampKind.HITO -> {
            drawPath(serrated(outer / 2f, Tooth.toPx()), ink, style = Stroke(outer, pathEffect = effect))
            val i2 = outer + gap + Tooth.toPx() / 2f
            drawRect(ink, topLeft = Offset(i2, i2), size = Size(size.width - i2 * 2, size.height - i2 * 2), style = Stroke(inner, pathEffect = effect))
        }
    }
}

/** Polígono regular de [sides] lados inscrito en un círculo de radio [r], girado [start] radianes. */
private fun polygon(cx: Float, cy: Float, r: Float, sides: Int, start: Double): Path = Path().apply {
    for (k in 0 until sides) {
        val a = start + 2 * PI * k / sides
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Rectángulo con los cuatro lados dentados (dientes de [tooth] de paso), como un sello de correos. */
private fun DrawScope.serrated(inset: Float, tooth: Float): Path {
    val depth = tooth / 2f
    val l = inset
    val t = inset
    val rr = size.width - inset
    val b = size.height - inset
    val nx = maxOf(2, ((rr - l) / tooth).toInt())
    val ny = maxOf(2, ((b - t) / tooth).toInt())
    val tw = (rr - l) / nx
    val th = (b - t) / ny
    return Path().apply {
        moveTo(l, t)
        for (k in 0 until nx) {
            lineTo(l + k * tw + tw / 2f, t + depth)
            lineTo(l + (k + 1) * tw, t)
        }
        for (k in 0 until ny) {
            lineTo(rr - depth, t + k * th + th / 2f)
            lineTo(rr, t + (k + 1) * th)
        }
        for (k in 0 until nx) {
            lineTo(rr - k * tw - tw / 2f, b - depth)
            lineTo(rr - (k + 1) * tw, b)
        }
        for (k in 0 until ny) {
            lineTo(l + depth, b - k * th - th / 2f)
            lineTo(l, b - (k + 1) * th)
        }
        close()
    }
}
