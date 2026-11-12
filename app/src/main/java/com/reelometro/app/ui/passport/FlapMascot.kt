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
import androidx.compose.runtime.withFrameNanos
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
import com.reelometro.app.copy.FlapCopy
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

    /** Los Flaps de la tira de la semana (7 en una fila). */
    val WeekSize: Dp = Space.x10
}

/**
 * Cómo se mueve Flap en cada estado: cuanto peor está, menos respira y menos salta.
 *
 * @param breathScale cuánto se estira al respirar (en vertical; la mitad en horizontal).
 * @param breath medio ciclo de respiración (spring muy blando, sin rebote).
 * @param jump altura del salto al tocarlo, en múltiplos de `Space.x6`; 0 = no salta, se aplasta.
 * @param hops da saltitos solo, de vez en cuando (EN FORMA).
 * @param shivers tiembla a ratos (ENFERMO).
 * @param blinks parpadea (KO no: tiene los ojos en X).
 */
private class FlapPose(
    val breathScale: Float,
    val breath: SpringSpec<Float>,
    val jump: Float,
    val hops: Boolean = false,
    val shivers: Boolean = false,
    val blinks: Boolean = true,
)

/**
 * Ritmo de Flap. Todo con springs (nunca `tween` lineal): la respiración es un spring muy blando
 * sin rebote que va y vuelve; el salto, `snappy` arriba y `bouncy` abajo; el temblor, un spring
 * muy rígido que va de lado a lado; el cambio de estado, un volteo de ficha split-flap
 * (`rotationX`) que se asienta con `bouncy`.
 */
private object FlapMotion {
    private val FIT = FlapPose(0.045f, spring(dampingRatio = 1f, stiffness = 10f), jump = 1.25f, hops = true)
    private val NORMAL = FlapPose(0.035f, spring(dampingRatio = 1f, stiffness = 8f), jump = 1f)
    private val TIRED = FlapPose(0.03f, spring(dampingRatio = 1f, stiffness = 5f), jump = 0.6f)
    private val SICK = FlapPose(0.022f, spring(dampingRatio = 1f, stiffness = 6f), jump = 0.35f, shivers = true)
    private val KO = FlapPose(0.008f, spring(dampingRatio = 1f, stiffness = 3f), jump = 0f, blinks = false)

    fun pose(mood: FlapMood): FlapPose = when (mood) {
        FlapMood.EN_FORMA -> FIT
        FlapMood.NORMAL -> NORMAL
        FlapMood.CANSADO -> TIRED
        FlapMood.ENFERMO -> SICK
        FlapMood.KO -> KO
    }

    /** Primera mitad del volteo (hasta quedar de canto), sin rebote. */
    val flipOut: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** Cada sacudida del temblor: rígida y sin rebote (≈ 0,1 s de lado a lado). */
    val shiver: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 4_000f)

    const val BLINK_MIN_MS = 2_600L
    const val BLINK_SPREAD_MS = 2_400L
    const val BLINK_MS = 140L

    /** Saltitos de EN FORMA: cada 3,5–6 s, a media altura. */
    const val HOP_MIN_MS = 3_500L
    const val HOP_SPREAD_MS = 2_500L
    const val HOP_HEIGHT = 0.5f

    /** Temblor de ENFERMO: cada 1,6–3 s, unas cuantas sacudidas de 1 dp. */
    const val SHIVER_MIN_MS = 1_600L
    const val SHIVER_SPREAD_MS = 1_400L
    const val SHIVER_TIMES = 6

    /** Al tocar a Flap KO: se aplasta un 8 % en vez de saltar. */
    const val SQUASH = 0.08f

    /** Salto alegre al revivir: dos botes, más altos que el de tocarlo. */
    const val JOY_HEIGHT = 1.5f
    const val JOY_HOPS = 2

    /** Pausa antes de voltear al estado nuevo (que dé tiempo a ver el anterior). */
    const val FLIP_DELAY_MS = 600L

    /** Perspectiva del volteo (en múltiplos de la densidad, como las fichas del marcador). */
    const val CAMERA = 12f
}

/**
 * Flap, la mascota de la pestaña, en pixel art ([FlapSprite]) con los colores del tema.
 *
 * Vivo y según cómo está: respira (cuanto peor, más despacio y menos), parpadea cada pocos
 * segundos (KO no: ojos en X), EN FORMA da saltitos solo, ENFERMO tiembla a ratos y KO casi ni
 * respira. Al tocarlo salta (con un tic suave; KO se aplasta). Si [previousMood] no es `null`,
 * enseña primero el estado anterior y voltea como una ficha split-flap hasta el nuevo; si ha
 * revivido ([recovered]), después da un salto alegre. Al acabar llama a [onMoodSeen]. Con
 * «reducir animaciones», quieto: nada de eso (el estado se marca visto igualmente).
 *
 * @param description lo que lee TalkBack («Flap: enfermo, vida 32 de 100.»).
 */
@Composable
fun FlapMascot(
    mood: FlapMood,
    previousMood: FlapMood?,
    description: String,
    onMoodSeen: (FlapMood) -> Unit,
    modifier: Modifier = Modifier,
    recovered: Boolean = previousMood != null && previousMood.ordinal > mood.ordinal,
    size: Dp = FlapDefaults.Size,
) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val seen by rememberUpdatedState(onMoodSeen)
    val joyful by rememberUpdatedState(recovered)

    var shown by remember { mutableStateOf(if (previousMood != null && !reduced) previousMood else mood) }
    var frame by remember { mutableStateOf(FlapFrame.OPEN) }
    val flip = remember { Animatable(0f) }
    val breath = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val joy = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) }

    // Cambio de estado: del anterior al nuevo con un volteo de ficha y, si ha revivido, un salto alegre.
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
            if (joyful) {
                repeat(FlapMotion.JOY_HOPS) {
                    joy.animateTo(1f, Motion.snappy())
                    joy.animateTo(0f, Motion.bouncy())
                }
                haptics.confirm()
            }
        } else {
            flip.snapTo(0f)
            joy.snapTo(0f)
            shown = mood
        }
        // Siempre: así se guarda el estado visto y el próximo cambio se puede animar.
        seen(mood)
    }

    // Respira: sube y baja con un spring muy blando, en bucle (el ritmo, según el estado que se ve).
    LaunchedEffect(reduced, shown) {
        if (reduced) {
            breath.snapTo(0f)
        } else {
            val pose = FlapMotion.pose(shown)
            while (true) {
                breath.animateTo(1f, pose.breath)
                breath.animateTo(0f, pose.breath)
            }
        }
    }

    // Parpadea cada pocos segundos (con algo de azar para que no parezca un metrónomo).
    LaunchedEffect(reduced, shown) {
        frame = FlapFrame.OPEN
        if (!reduced && FlapMotion.pose(shown).blinks) {
            while (true) {
                delay(FlapMotion.BLINK_MIN_MS + Random.nextLong(FlapMotion.BLINK_SPREAD_MS))
                // Con la app en segundo plano (ON_STOP) el reloj de fotogramas está en pausa: el bucle espera aquí.
                withFrameNanos { }
                frame = FlapFrame.BLINK
                delay(FlapMotion.BLINK_MS)
                frame = FlapFrame.OPEN
            }
        }
    }

    // EN FORMA: saltitos de vez en cuando, sin que nadie lo toque.
    LaunchedEffect(reduced, shown) {
        hop.snapTo(0f)
        if (!reduced && FlapMotion.pose(shown).hops) {
            while (true) {
                delay(FlapMotion.HOP_MIN_MS + Random.nextLong(FlapMotion.HOP_SPREAD_MS))
                hop.animateTo(1f, Motion.snappy())
                hop.animateTo(0f, Motion.bouncy())
            }
        }
    }

    // ENFERMO: tiembla a ratos (unas sacudidas de lado a lado y quieto otra vez).
    LaunchedEffect(reduced, shown) {
        shake.snapTo(0f)
        if (!reduced && FlapMotion.pose(shown).shivers) {
            while (true) {
                delay(FlapMotion.SHIVER_MIN_MS + Random.nextLong(FlapMotion.SHIVER_SPREAD_MS))
                repeat(FlapMotion.SHIVER_TIMES) { i -> shake.animateTo(if (i % 2 == 0) 1f else -1f, FlapMotion.shiver) }
                shake.animateTo(0f, FlapMotion.shiver)
            }
        }
    }

    // (Sin un `val density` aquí: dentro de `graphicsLayer` taparía el `density` de su ámbito.)
    val jumpPx = with(LocalDensity.current) { Space.x6.toPx() }
    val shakePx = with(LocalDensity.current) { Space.hairline.toPx() }
    Canvas(
        modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = FlapCopy.FLAP_TAP,
                role = Role.Button,
            ) {
                haptics.tick()
                if (!reduced) {
                    scope.launch {
                        if (FlapMotion.pose(shown).jump > 0f) {
                            jump.animateTo(1f, Motion.snappy())
                            jump.animateTo(0f, Motion.bouncy())
                        } else {
                            squash.animateTo(1f, Motion.snappy())
                            squash.animateTo(0f, Motion.bouncy())
                        }
                    }
                }
            }
            .semantics { contentDescription = description }
            // Volteo de la ficha sobre su centro…
            .graphicsLayer {
                rotationX = 90f * flip.value
                cameraDistance = FlapMotion.CAMERA * density
            }
            // …y respiración, saltos, temblor y aplastamiento con los pies en el suelo.
            .graphicsLayer {
                val pose = FlapMotion.pose(shown)
                val b = breath.value
                val s = squash.value
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleY = 1f + pose.breathScale * b - FlapMotion.SQUASH * s
                scaleX = 1f - pose.breathScale * 0.5f * b + FlapMotion.SQUASH * 0.5f * s
                translationY = -jumpPx * (pose.jump * jump.value + FlapMotion.HOP_HEIGHT * hop.value + FlapMotion.JOY_HEIGHT * joy.value)
                translationX = shakePx * shake.value
            },
    ) {
        drawFlap(FlapSprite.frame(shown, frame), colors)
    }
}

/** Flap quieto (la tira de la semana, el recorrido, vistas estáticas). */
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
