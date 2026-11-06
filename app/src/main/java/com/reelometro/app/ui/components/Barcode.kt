package com.reelometro.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Ancho mínimo de un módulo en píxeles: por debajo, las barras se agrupan. */
private const val MIN_MODULE_PX = 2f

/**
 * Código de barras decorativo hecho con datos reales (docs/DESIGN.md §4): una barra por día;
 * cuanto más vídeos, más gruesa (1–4 módulos), y los días flojos dejan más hueco. Lleva barras
 * guía en los extremos que bajan junto al pie, como un EAN. Si no caben todas, agrupa días
 * contiguos (media) para que ninguna barra baje de 2 px.
 *
 * Es decorativo: sin [contentDescription], TalkBack lo ignora.
 *
 * @param values un valor por día (en cualquier escala; se normaliza con el máximo).
 * @param caption pie en mono bajo las barras («000212 · 047 · 2026»).
 * @param heightByValue además del grosor, la altura sigue al valor.
 */
@Composable
fun Barcode(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = ReelTheme.colors.text,
    caption: String? = null,
    height: Dp = 56.dp,
    heightByValue: Boolean = false,
    contentDescription: String? = null,
) {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val captionStyle = ReelTheme.type.dataS.copy(color = color, letterSpacing = 0.3.em)
    val data = remember(values) { values.toFloatArray() }

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .drawWithCache {
                val text = caption?.let { measurer.measure(it, captionStyle) }
                val captionGap = if (text != null) Space.x1.toPx() else 0f
                val barsBottom = size.height - (text?.size?.height ?: 0) - captionGap
                val guardBottom = if (text != null) barsBottom + captionGap + text.size.height / 2f else barsBottom
                val rects = encode(data, size.width, barsBottom, guardBottom, heightByValue)
                val captionAt = text?.let { Offset((size.width - it.size.width) / 2f, size.height - it.size.height) }
                onDrawBehind {
                    var i = 0
                    while (i < rects.size) {
                        drawRect(color, topLeft = Offset(rects[i], rects[i + 2]), size = Size(rects[i + 1], rects[i + 3]))
                        i += 4
                    }
                    if (text != null && captionAt != null) drawText(text, topLeft = captionAt)
                }
            },
    )
}

/**
 * Convierte los valores en rectángulos (x, ancho, y, alto) ya ajustados al píxel.
 * Secuencia en módulos: guía (1·1·1) · hueco 2 · [barra 1–4 · hueco 1–2]… · hueco 2 · guía.
 */
private fun encode(raw: FloatArray, width: Float, barsBottom: Float, guardBottom: Float, byValue: Boolean): FloatArray {
    if (raw.isEmpty() || width <= 0f) return FloatArray(0)
    // Módulos medios por barra ≈ 2,5 (barra) + 1,5 (hueco); 10 módulos de guías y márgenes.
    val maxBars = max(1, ((width / MIN_MODULE_PX - 10f) / 4f).toInt())
    val values = if (raw.size <= maxBars) raw else bucket(raw, ceil(raw.size / maxBars.toFloat()).toInt())
    val top = values.maxOrNull()?.takeIf { it > 0f } ?: 1f

    val bars = IntArray(values.size)
    val gaps = IntArray(values.size)
    var modules = 3 + 2 + 2 + 3 // guías y márgenes
    for (k in values.indices) {
        val v = (values[k] / top).coerceIn(0f, 1f)
        bars[k] = 1 + (v * 3f).roundToInt()
        gaps[k] = if (k == values.lastIndex) 0 else if (v < 0.5f) 2 else 1
        modules += bars[k] + gaps[k]
    }
    val unit = width / modules
    val out = FloatArray((values.size + 4) * 4)
    var n = 0
    fun bar(fromModule: Int, count: Int, y: Float, bottom: Float) {
        val left = (fromModule * unit).roundToInt().toFloat()
        val right = ((fromModule + count) * unit).roundToInt().toFloat()
        out[n++] = left
        out[n++] = max(1f, right - left)
        out[n++] = y
        out[n++] = bottom - y
    }
    var m = 0
    bar(m, 1, 0f, guardBottom); m += 2
    bar(m, 1, 0f, guardBottom); m += 1 + 2
    for (k in values.indices) {
        val y = if (byValue) barsBottom * (1f - (0.4f + 0.6f * (values[k] / top).coerceIn(0f, 1f))) else 0f
        bar(m, bars[k], y, barsBottom)
        m += bars[k] + gaps[k]
    }
    m += 2
    bar(m, 1, 0f, guardBottom); m += 2
    bar(m, 1, 0f, guardBottom)
    return out.copyOf(n)
}

/** Media de cada grupo de [size] valores contiguos. */
private fun bucket(values: FloatArray, size: Int): FloatArray {
    val out = FloatArray(ceil(values.size / size.toFloat()).toInt())
    for (k in out.indices) {
        var sum = 0f
        var count = 0
        var j = k * size
        while (j < values.size && j < (k + 1) * size) {
            sum += values[j]
            count++
            j++
        }
        out[k] = if (count == 0) 0f else sum / count
    }
    return out
}
