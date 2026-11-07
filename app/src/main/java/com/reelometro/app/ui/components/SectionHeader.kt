package com.reelometro.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import java.util.Locale
import kotlin.math.max

/**
 * Altura de las mayúsculas de JetBrains Mono (730/1000 em): sirve para centrar filetes y marcas
 * en la línea de las letras, no en la caja del texto.
 */
internal const val MonoCapHeight = 0.73f

/** Altura de las mayúsculas de Bricolage Grotesque (660/1000 em). */
internal const val DisplayCapHeight = 0.66f

/**
 * Cabecera de sección editorial: `01  PLATAFORMAS ───────────── 3` (docs/DESIGN.md §3).
 * Índice en `textMuted`, título en `text`, filete `line` hasta el borde a media altura de las
 * mayúsculas y, si hay, un dato final. Para TalkBack es un encabezado.
 *
 * @param index número de sección (se escribe con dos cifras: «01»); `null` para no numerar.
 * @param trailing dato corto al final del filete («3», «TOP 10»); si no cabe entero, no sale.
 */
@Composable
fun SectionHeader(
    index: Int?,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val colors = ReelTheme.colors
    val style = ReelTheme.type.label
    val rule = remember { RulePosition() }
    val hasIndex = index != null
    val hasTrailing = trailing != null

    Layout(
        content = {
            if (index != null) Text(String.format(Locale.ROOT, "%02d", index), style = style, color = colors.textMuted)
            Text(Format.upper(title), style = style, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) {
                Text(Format.upper(trailing), style = style, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .drawBehind {
                if (rule.end - rule.start > 0f) {
                    val hair = Space.hairline.toPx()
                    drawRect(colors.line, topLeft = Offset(rule.start, rule.y - hair / 2f), size = Size(rule.end - rule.start, hair))
                }
            },
    ) { measurables, constraints ->
        val gap = Space.x3.roundToPx()
        val minRule = 16.dp.roundToPx()
        val width = constraints.maxWidth
        var i = 0
        val indexP = if (hasIndex) measurables[i++].measure(Constraints(maxWidth = width)) else null
        val titleM = measurables[i++]
        val indexW = indexP?.let { it.width + gap } ?: 0
        // El título manda; el dato final sale entero o no sale («NIVEL 2 DE…» es un dato a medias): con la fuente
        // grande, si no cabe, el filete llega hasta el borde.
        val titleP = titleM.measure(Constraints(maxWidth = max(0, width - indexW - gap - minRule)))
        val room = width - indexW - titleP.width - gap * 2 - minRule
        val trailingP = if (hasTrailing && room > gap * 2 && measurables[i].maxIntrinsicWidth(Constraints.Infinity) <= room) {
            measurables[i].measure(Constraints(maxWidth = room))
        } else {
            null
        }
        val height = maxOf(indexP?.height ?: 0, titleP.height, trailingP?.height ?: 0)
        val baseline = titleP[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: titleP.height

        val titleX = indexP?.let { it.width + gap } ?: 0
        rule.start = (titleX + titleP.width + gap).toFloat()
        rule.end = (width - (trailingP?.let { it.width + gap } ?: 0)).toFloat()
        rule.y = baseline - style.fontSize.toPx() * MonoCapHeight / 2f

        layout(width, height) {
            indexP?.place(0, 0)
            titleP.place(titleX, 0)
            trailingP?.place(width - trailingP.width, 0)
        }
    }
}

/** Posición del filete calculada en la medición y leída al dibujar. */
private class RulePosition {
    var start = 0f
    var end = 0f
    var y = 0f
}
