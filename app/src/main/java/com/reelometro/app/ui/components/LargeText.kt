package com.reelometro.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.reelometro.app.ui.theme.Space

/** Desde aquí la letra del sistema cuenta como «grande» (ajuste de 130 % de Android). */
const val LARGE_FONT_SCALE = 1.3f

/** Pura y testeable: 1,15 → false; 1,3 → true; 2,0 → true (con margen de 0,005 por redondeos). */
fun isLargeFontScale(fontScale: Float): Boolean = fontScale >= LARGE_FONT_SCALE - 0.005f

/** ¿El usuario tiene la letra del sistema en «grande» (130 % o más)? */
@Composable
@ReadOnlyComposable
fun isLargeFont(): Boolean = isLargeFontScale(LocalDensity.current.fontScale)

/**
 * Dos (o más) botones: en fila a partes iguales; con letra grande, uno debajo de otro a todo el ancho
 * (mismo orden de lectura). [content] recibe el modificador de cada hueco (`weight(1f)` o `fillMaxWidth()`).
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = Space.x3,
    stacked: Boolean = isLargeFont(),
    content: @Composable (slot: Modifier) -> Unit,
) {
    if (stacked) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            content(Modifier.weight(1f))
        }
    }
}

/** Tamaños mínimo y paso comunes del autoajuste de cifras y botones. */
object AutoSizeTokens {
    val Min = 10.sp
    val Step = 0.5.sp
}

/**
 * Como `TextAutoSize.StepBased`, pero un tamaño solo vale si el texto cabe **y no parte ninguna palabra** entre
 * dos líneas: Compose da por bueno «TRIPLET / E» si entra en las líneas permitidas. Si ni el mínimo vale, el mínimo.
 */
internal class WholeWordsAutoSize(private val min: TextUnit, private val max: TextUnit, private val step: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > min.value) {
            val layout = performLayout(constraints, text, size.sp)
            if (!layout.hasVisualOverflow && !layout.splitsAWord(text.text)) return size.sp
            size -= step.value
        }
        return min
    }

    override fun equals(other: Any?): Boolean =
        other is WholeWordsAutoSize && other.min == min && other.max == max && other.step == step

    override fun hashCode(): Int = (min.hashCode() * 31 + max.hashCode()) * 31 + step.hashCode()
}

/** Alguna línea acaba en mitad de una palabra (letra o cifra a los dos lados del corte). */
private fun TextLayoutResult.splitsAWord(text: String): Boolean = (0 until lineCount - 1).any { line ->
    val end = getLineEnd(line)
    end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
}
