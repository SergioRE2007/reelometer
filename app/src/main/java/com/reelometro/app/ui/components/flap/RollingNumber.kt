package com.reelometro.app.ui.components.flap

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Cifra en línea cuyos dígitos ruedan en vertical, como un odómetro (para cifras secundarias:
 * «47 MIN», «1.204»). Al subir, los dígitos entran desde abajo; al bajar, desde arriba, pasando por
 * los intermedios. Los caracteres que no son dígitos (punto de millar, unidades) quedan quietos.
 * Expone su línea base, así que se alinea con `Modifier.alignByBaseline()` junto a otros textos.
 *
 * @param format cómo se escribe la cifra (por defecto `Format.integer`: «1.234»).
 * @param contentDescription lo que lee TalkBack; por defecto, el texto formateado.
 */
@Composable
fun RollingNumber(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = ReelTheme.type.dataL,
    color: Color = ReelTheme.colors.text,
    format: (Long) -> String = { Format.integer(it) },
    contentDescription: String? = null,
) {
    val text = format(value)
    val measurer = rememberTextMeasurer()
    val ink = rememberFlapFontMeasurer(style).metrics
    val textStyle = remember(style, color) { style.copy(color = color) }
    val layout = remember(text, textStyle, measurer) { measurer.measure(text, textStyle) }
    val wheels = remember { RollWheels() }
    SideEffect { wheels.sync(text, value) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(text, reduced) { wheels.animate(reduced) }

    val description = contentDescription ?: text
    Spacer(
        modifier
            .semantics { this.contentDescription = description }
            .layout { measurable, _ ->
                val w = layout.size.width
                val h = layout.size.height
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(
                    w,
                    h,
                    mapOf(FirstBaseline to layout.firstBaseline.roundToInt(), LastBaseline to layout.lastBaseline.roundToInt()),
                ) { placeable.place(0, 0) }
            }
            .drawWithCache {
                val n = text.length
                val lefts = FloatArray(n)
                val rights = FloatArray(n)
                for (i in 0 until n) {
                    val box = layout.getBoundingBox(i)
                    lefts[i] = box.left
                    rights[i] = box.right
                }
                // Ventana del odómetro: la franja de tinta de las cifras (más un margen), no la caja
                // de la línea, que es más alta: así los dígitos entran y salen justo por su ranura.
                val fs = (if (style.fontSize.isSpecified) style.fontSize else DefaultRollSize).toPx()
                val margin = fs * SLOT_MARGIN
                val slotTop = layout.firstBaseline + ink.inkTopEm * fs - margin
                val slotBottom = layout.firstBaseline + ink.inkBottomEm * fs + margin
                val h = slotBottom - slotTop
                // Margen para que el clip no muerda los apoyos laterales de los glifos.
                val bleed = fs * 0.15f
                val glyphs = HashMap<Char, TextLayoutResult>()
                fun glyph(c: Char) = glyphs.getOrPut(c) { measurer.measure(c.toString(), textStyle) }

                onDrawBehind {
                    for (i in 0 until n) {
                        val c = text[i]
                        val x = lefts[i]
                        val wheel = if (c in FlapGlyphs.DIGITS) wheels.wheel(n - 1 - i) else null
                        if (wheel == null) {
                            drawText(glyph(c), topLeft = Offset(x, 0f))
                            continue
                        }
                        val w = wheel.position
                        val base = floor(w)
                        val frac = w - base
                        val d0 = Math.floorMod(base.toInt(), 10)
                        if (frac <= 0.0005f) {
                            drawText(glyph(FlapGlyphs.DIGITS[d0]), topLeft = Offset(x, 0f))
                            continue
                        }
                        val d1 = (d0 + 1) % 10
                        clipRect(x - bleed, slotTop, rights[i] + bleed, slotBottom) {
                            drawText(glyph(FlapGlyphs.DIGITS[d0]), topLeft = Offset(x, -frac * h))
                            drawText(glyph(FlapGlyphs.DIGITS[d1]), topLeft = Offset(x, (1f - frac) * h))
                        }
                    }
                }
            },
    )
}

private val DefaultRollSize = 22.sp

/** Aire de la ranura del odómetro por encima y por debajo de la tinta, en em. */
private const val SLOT_MARGIN = 0.06f

/** Rueda de un dígito: posición continua (el dígito visible es `floor(position) mod 10`). */
@Stable
internal class RollWheel(start: Float) {
    var position: Float by mutableFloatStateOf(start)
    var target: Float = start
}

/** Ruedas por posición desde la derecha. */
@Stable
internal class RollWheels {
    private val wheels = HashMap<Int, RollWheel>()
    private var lastValue: Long? = null
    private var lastText: String? = null
    private var up = true

    fun wheel(position: Int): RollWheel? = wheels[position]

    /** Ajusta las ruedas al texto nuevo y fija sus destinos. Idempotente: con el mismo texto no hace nada. */
    fun sync(text: String, value: Long) {
        if (text == lastText) return
        lastText = text
        val previous = lastValue
        up = previous == null || value >= previous
        lastValue = value
        val keep = HashSet<Int>()
        for (i in text.indices) {
            val c = text[i]
            if (c !in FlapGlyphs.DIGITS) continue
            val pos = text.length - 1 - i
            keep += pos
            val digit = c - '0'
            val wheel = wheels[pos]
            if (wheel == null) {
                // Columna nueva: la primera vez aparece quieta; después entra rodando un paso.
                val start = when {
                    previous == null -> digit.toFloat()
                    up -> digit - 1f
                    else -> digit + 1f
                }
                wheels[pos] = RollWheel(start).also { it.target = wheelTarget(start, digit, up) }
            } else {
                wheel.target = wheelTarget(wheel.position, digit, up)
            }
        }
        wheels.keys.retainAll(keep)
    }

    suspend fun animate(reduced: Boolean) = coroutineScope {
        for (wheel in wheels.values) {
            if (wheel.position == wheel.target) continue
            if (reduced) {
                wheel.position = wheel.target
                continue
            }
            launch {
                animate(wheel.position, wheel.target, animationSpec = Motion.gentle()) { v, _ -> wheel.position = v }
            }
        }
    }
}

/**
 * Destino de una rueda que está en [current] (continua) y debe acabar mostrando [digit], girando
 * hacia arriba ([up], la cifra sube) o hacia abajo, sin dar vueltas de más.
 */
internal fun wheelTarget(current: Float, digit: Int, up: Boolean): Float {
    return if (up) {
        val base = floor(current)
        var t = base + Math.floorMod(digit - Math.floorMod(base.toInt(), 10), 10)
        if (t < current - 1e-4f) t += 10f
        t
    } else {
        val base = ceil(current)
        var t = base - Math.floorMod(Math.floorMod(base.toInt(), 10) - digit, 10)
        if (t > current + 1e-4f) t -= 10f
        t
    }
}
