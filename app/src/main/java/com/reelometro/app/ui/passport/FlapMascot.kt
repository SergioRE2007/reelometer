package com.reelometro.app.ui.passport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.FlapFrame
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapPixel
import com.reelometro.app.game.FlapSprite
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelColors
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/** Tamaños de Flap: 16 píxeles de arte × 10 dp = 160 dp en la pestaña. */
object FlapDefaults {
    val Size: Dp = Space.x20 * 2
    val SmallSize: Dp = Space.x16
}

/**
 * Ritmo de Flap. Todo con springs (nunca `tween` lineal): la respiración es un spring muy blando
 * sin rebote que va y vuelve; el salto, `snappy` arriba y `bouncy` abajo; el cambio de estado, un
 * volteo de ficha split-flap (`rotationX`) que se asienta con `bouncy`.
 */
private object FlapMotion {
    /** Medio ciclo de respiración ≈ 2 s. */
    val breath: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 8f)

    /** Primera mitad del volteo (hasta quedar de canto), sin rebote. */
    val flipOut: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** Escala de la respiración (3,5 % en vertical, la mitad en horizontal). */
    const val BREATH_SCALE = 0.035f

    const val BLINK_MIN_MS = 2_600L
    const val BLINK_SPREAD_MS = 2_400L
    const val BLINK_MS = 140L

    /** Pausa antes de voltear al estado nuevo (que dé tiempo a ver el anterior). */
    const val FLIP_DELAY_MS = 600L

    /** Perspectiva del volteo (en múltiplos de la densidad, como las fichas del marcador). */
    const val CAMERA = 12f
}

/**
 * Flap, la mascota del Pasaporte, en pixel art ([FlapSprite]) con los colores del tema.
 *
 * Vivo: respira en reposo, parpadea cada pocos segundos, salta al tocarlo (con un tic suave) y,
 * si [previousMood] no es `null`, enseña primero el estado anterior y voltea como una ficha
 * split-flap hasta el nuevo; después llama a [onMoodSeen]. Con «reducir animaciones», quieto:
 * sin respiración, sin parpadeo, sin salto ni volteo (el cambio se marca visto igualmente).
 *
 * @param description lo que lee TalkBack (estado, registro y frase).
 */
@Composable
fun FlapMascot(
    mood: FlapMood,
    previousMood: FlapMood?,
    description: String,
    onMoodSeen: (FlapMood) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = FlapDefaults.Size,
) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val seen by rememberUpdatedState(onMoodSeen)

    var shown by remember { mutableStateOf(if (previousMood != null && !reduced) previousMood else mood) }
    var frame by remember { mutableStateOf(FlapFrame.OPEN) }
    val flip = remember { Animatable(0f) }
    val breath = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }

    // Cambio de estado: del anterior al nuevo con un volteo de ficha.
    LaunchedEffect(mood, previousMood, reduced) {
        if (previousMood != null && previousMood != mood && !reduced) {
            shown = previousMood
            flip.snapTo(0f)
            delay(FlapMotion.FLIP_DELAY_MS)
            flip.animateTo(1f, FlapMotion.flipOut)
            shown = mood
            flip.snapTo(-1f)
            flip.animateTo(0f, Motion.bouncy())
            haptics.settle()
        } else {
            flip.snapTo(0f)
            shown = mood
        }
        // Siempre: así se guarda el primer estado visto y el próximo cambio se puede animar.
        seen(mood)
    }

    // Respira: sube y baja con un spring muy blando, en bucle.
    LaunchedEffect(reduced) {
        if (reduced) {
            breath.snapTo(0f)
        } else {
            while (true) {
                breath.animateTo(1f, FlapMotion.breath)
                breath.animateTo(0f, FlapMotion.breath)
            }
        }
    }

    // Parpadea cada pocos segundos (con algo de azar para que no parezca un metrónomo).
    LaunchedEffect(reduced) {
        frame = FlapFrame.OPEN
        if (!reduced) {
            while (true) {
                delay(FlapMotion.BLINK_MIN_MS + Random.nextLong(FlapMotion.BLINK_SPREAD_MS))
                frame = FlapFrame.BLINK
                delay(FlapMotion.BLINK_MS)
                frame = FlapFrame.OPEN
            }
        }
    }

    val jumpPx = with(LocalDensity.current) { Space.x6.toPx() }
    Canvas(
        modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = PassportCopy.FLAP_TAP,
                role = Role.Button,
            ) {
                haptics.tick()
                if (!reduced) {
                    scope.launch {
                        jump.animateTo(1f, Motion.snappy())
                        jump.animateTo(0f, Motion.bouncy())
                    }
                }
            }
            .semantics { contentDescription = description }
            // Volteo de la ficha sobre su centro…
            .graphicsLayer {
                rotationX = 90f * flip.value
                cameraDistance = FlapMotion.CAMERA * density
            }
            // …y respiración y salto con los pies en el suelo.
            .graphicsLayer {
                val b = breath.value
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleY = 1f + FlapMotion.BREATH_SCALE * b
                scaleX = 1f - FlapMotion.BREATH_SCALE * 0.5f * b
                translationY = -jumpPx * jump.value
            },
    ) {
        drawFlap(FlapSprite.frame(shown, frame), colors)
    }
}

/** Flap quieto (celebraciones, vistas estáticas). */
@Composable
fun FlapStill(mood: FlapMood, modifier: Modifier = Modifier, size: Dp = FlapDefaults.SmallSize) {
    val colors = ReelTheme.colors
    Canvas(modifier.size(size)) { drawFlap(FlapSprite.frame(mood, FlapFrame.OPEN), colors) }
}

/** Pinta la rejilla de [FlapSprite] centrada y con los píxeles ajustados a píxeles enteros. */
internal fun DrawScope.drawFlap(rows: List<String>, colors: ReelColors) {
    val n = FlapSprite.SIZE
    val cell = min(size.width, size.height) / n
    val left = (size.width - cell * n) / 2f
    val top = (size.height - cell * n) / 2f
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            val color = pixelColor(FlapSprite.pixel(c), colors)
            if (color != null) {
                val l = floor(left + x * cell)
                val t = floor(top + y * cell)
                val r = floor(left + (x + 1) * cell)
                val b = floor(top + (y + 1) * cell)
                drawRect(color, topLeft = Offset(l, t), size = Size(r - l, b - t))
            }
        }
    }
}

/** El token de cada papel de píxel (el widget usa el mismo mapeo con sus colores). */
internal fun pixelColor(pixel: FlapPixel, colors: ReelColors): Color? = when (pixel) {
    FlapPixel.EMPTY -> null
    FlapPixel.INK -> colors.text
    FlapPixel.TILE -> colors.bgSunken
    FlapPixel.HINGE -> colors.bg
    FlapPixel.MUTED -> colors.textMuted
    FlapPixel.ACCENT -> colors.signal
}
