package com.reelometro.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.max
import com.reelometro.app.copy.TourCopy
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.nav.PassportDest
import com.reelometro.app.ui.nav.StatsDest
import com.reelometro.app.ui.nav.TodayDest
import com.reelometro.app.ui.nav.WrappedDest
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * Recorrido guiado de la primera vez sobre las pantallas reales (foco recortado sobre cada
 * [TourTarget], texto corto, SIGUIENTE y SALTAR siempre visibles). Lo pinta `AppShell` encima de
 * todo, también de la barra de pestañas.
 *
 * - Velo `bg` con un hueco recto sobre el elemento (un `Path` par-impar: sin degradados) y un filete
 *   `signalText` de 2 dp alrededor. El hueco viaja de un paso a otro con un spring; con «reducir
 *   animaciones», salta.
 * - El texto va debajo del hueco si este está en la mitad de arriba, y encima si está en la de abajo.
 *   Si no cabe (fuente al 130 %), el texto se desplaza y los botones siguen a la vista. Si el hueco
 *   apenas deja sitio, el panel se pone abajo con fondo propio.
 * - Se queda con todos los toques salvo los de sus botones; atrás = saltar.
 * - TalkBack: el panel es lo primero que se recorre (`paneTitle`) y cada paso se anuncia
 *   (`liveRegion`) con «Paso 2 de 6» y su texto.
 * - Cada paso pide su pestaña con [onShowTab] (`TodayDest`, `StatsDest`, `WrappedDest`, `PassportDest`).
 *
 * @param pending el recorrido está pendiente (onboarding hecho y recorrido sin ver).
 * @param currentTab índice de la pestaña visible (0 Hoy, 1 Estadísticas, 2 Resumen, 3 Pasaporte) o −1.
 * @param passportEnabled si es `false`, se salta el paso del Pasaporte.
 * @param targets límites de los elementos marcados con [tourTarget].
 * @param onShowTab cambia a la pestaña de un paso (`TodayDest`, `StatsDest`, `WrappedDest`, `PassportDest`).
 * @param onFinished al terminar o saltar (`skipped = true`); se guarda como visto.
 */
@Composable
fun TourHost(
    pending: Boolean,
    currentTab: Int,
    passportEnabled: Boolean,
    targets: TourTargets,
    onShowTab: (Any) -> Unit,
    onFinished: (skipped: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Paso en curso (sobrevive a la rotación); `null` = desde el principio.
    var savedStep by rememberSaveable { mutableStateOf<String?>(null) }
    // Terminado o saltado en esta sesión: se oculta ya, sin esperar a que se guarde como visto.
    var closed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pending) {
        // Visto y guardado: la próxima vez («VER EL RECORRIDO») empieza otra vez por el primer paso.
        if (!pending) {
            savedStep = null
            closed = false
        }
    }
    if (!pending || closed || currentTab < 0) return

    val state = TourLogic.resume(passportEnabled, savedStep)
    val step = state.step ?: return
    val showTab by rememberUpdatedState(onShowTab)
    val finish by rememberUpdatedState(onFinished)

    // El paso necesita otra pestaña: se abre (el velo se queda mientras cambia).
    val tab = TourLogic.tabToShow(state, currentTab)
    LaunchedEffect(tab) {
        if (tab != null) showTab(tab.destination())
    }

    fun advance(next: TourState) {
        if (next.finished) {
            closed = true
            savedStep = null
            finish(next.skipped)
        } else {
            savedStep = next.step?.name
        }
    }

    BackHandler { advance(TourLogic.skip(state)) }

    TourOverlay(
        state = state,
        step = step,
        targets = targets,
        onTab = tab == null,
        onNext = { advance(TourLogic.next(state)) },
        onSkip = { advance(TourLogic.skip(state)) },
        modifier = modifier,
    )
}

@Composable
private fun TourOverlay(
    state: TourState,
    step: TourStep,
    targets: TourTargets,
    onTab: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val density = LocalDensity.current

    // Posición del recorrido en la raíz (los límites de los elementos vienen en coordenadas de la raíz).
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val pad = with(density) { Space.x2.toPx() }
    val stroke = with(density) { (Space.hairline * 2).toPx() }
    val raw = if (onTab) targets[step.target()] else null
    val hole: Rect? = raw
        ?.takeIf { it.width >= 1f && it.height >= 1f && size.width > 0 && size.height > 0 }
        ?.let { Rect(it.left - origin.x - pad, it.top - origin.y - pad, it.right - origin.x + pad, it.bottom - origin.y + pad) }
        ?.intersect(Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()))
        ?.takeIf { it.width >= 1f && it.height >= 1f }

    // El hueco se mueve con un spring de un elemento a otro; la primera vez (o sin animaciones), aparece ya en su sitio.
    val animated = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(hole, reduced) {
        val target = hole ?: return@LaunchedEffect
        if (!placed || reduced) {
            animated.snapTo(target)
            placed = true
        } else {
            animated.animateTo(target, Motion.standard())
        }
    }

    // El velo entra con un fundido (spring); con «reducir animaciones», ya está.
    val veil = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (veil.value < 1f) veil.animateTo(1f, Motion.standard())
    }

    // Si el elemento aún no está (la pestaña cargando), se espera un poco antes de poner el texto sin hueco.
    // Solo en el primer paso: después el panel sigue compuesto entre pasos (mismos nodos), así TalkBack no
    // pierde el foco y su liveRegion anuncia el paso nuevo.
    var settled by remember { mutableStateOf(false) }
    val hasHole = hole != null
    LaunchedEffect(step, hasHole, onTab) {
        if (!hasHole) delay(TARGET_WAIT_MS)
        settled = true
    }

    val path = remember { Path() }
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                size = it.size
            }
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            }
            // Se queda con todos los toques: Compose no los pasa a lo que queda debajo (pantallas y barra
            // de pestañas son hermanos en `AppShell`). No se consumen: los botones del panel los reciben antes.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                }
            }
            .drawBehind {
                val alpha = VEIL_ALPHA * veil.value
                if (hasHole && placed) {
                    val h = animated.value
                    path.reset()
                    path.fillType = PathFillType.EvenOdd
                    path.addRect(Rect(Offset.Zero, this.size))
                    path.addRect(h)
                    drawPath(path, colors.bg, alpha = alpha)
                    drawRect(
                        color = colors.signalText,
                        topLeft = h.topLeft,
                        size = h.size,
                        alpha = veil.value,
                        style = Stroke(width = stroke),
                    )
                } else {
                    drawRect(colors.bg, alpha = alpha)
                }
            },
    ) {
        if (settled || hasHole) {
            TourPanel(state = state, step = step, hole = hole, onNext = onNext, onSkip = onSkip)
        }
    }
}

/** Texto y botones, encima o debajo del hueco. */
@Composable
private fun TourPanel(state: TourState, step: TourStep, hole: Rect?, onNext: () -> Unit, onSkip: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = insets.calculateTopPadding()
    val bottomInset = insets.calculateBottomPadding()
    val hasTarget = hole != null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        val holeTop: Dp? = hole?.let { with(density) { it.top.toDp() } }
        val holeBottom: Dp? = hole?.let { with(density) { it.bottom.toDp() } }
        val below = TourLogic.textBelow(holeTop?.value, holeBottom?.value, height.value)

        // Franja libre para el panel: debajo del hueco, encima o toda la pantalla si no hay hueco.
        var top = topInset + Space.x4
        var bottom = bottomInset + Space.x4
        if (holeTop != null && holeBottom != null) {
            if (below) {
                top = max(top, holeBottom + Space.x4)
            } else {
                bottom = max(bottom, height - holeTop + Space.x4)
            }
        }
        // Si el hueco apenas deja sitio, el panel va abajo, encima del hueco, con fondo propio.
        val cramped = hasTarget && height - top - bottom < MinPanelHeight
        if (cramped) {
            top = topInset + Space.x4
            bottom = bottomInset + Space.x4
        }
        val arrangement = when {
            cramped -> Arrangement.Bottom
            !hasTarget -> Arrangement.Center
            below -> Arrangement.Top
            else -> Arrangement.Bottom
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = top, bottom = bottom)
                .padding(horizontal = Space.screen),
            verticalArrangement = arrangement,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // Fondo propio siempre: con el velo (90 %) lo de detrás aún se transparenta y, con la fuente
                    // al 130 % y el tema claro, se mezclaba con el texto del paso.
                    .background(colors.bg)
                    .then(if (cramped) Modifier.padding(vertical = Space.x4) else Modifier)
                    .semantics { paneTitle = TourCopy.PANE_TITLE },
            ) {
                // El texto se desplaza si no cabe; los botones, siempre a la vista.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .clearAndSetSemantics {
                            contentDescription = TourCopy.announcement(step, state.number, state.total, hasTarget)
                            liveRegion = LiveRegionMode.Polite
                        },
                ) {
                    Label(TourCopy.step(state.number, state.total), color = colors.signalText)
                    Spacer(Modifier.height(Space.x2))
                    Text(TourCopy.title(step), style = type.title, color = colors.text)
                    Spacer(Modifier.height(Space.x2))
                    Text(TourCopy.text(step, hasTarget), style = type.body, color = colors.text)
                }
                Spacer(Modifier.height(Space.x5))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReelButton(
                        text = TourCopy.SKIP,
                        onClick = onSkip,
                        style = ReelButtonStyle.Secondary,
                        contentDescription = TourCopy.SKIP_DESCRIPTION,
                        modifier = Modifier.weight(1f),
                    )
                    ReelButton(
                        text = if (state.isLast) TourCopy.DONE else TourCopy.NEXT,
                        onClick = onNext,
                        contentDescription = if (state.isLast) TourCopy.DONE_DESCRIPTION else TourCopy.NEXT_DESCRIPTION,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Elemento que señala cada paso. */
private fun TourStep.target(): TourTarget = when (this) {
    TourStep.COUNTER -> TourTarget.COUNTER
    TourStep.COMPARISON -> TourTarget.COMPARISON
    TourStep.STATS_BARS -> TourTarget.STATS_BARS
    TourStep.WRAPPED -> TourTarget.WRAPPED
    TourStep.PASSPORT -> TourTarget.PASSPORT
    TourStep.SETTINGS -> TourTarget.SETTINGS
}

/** Destino de navegación de cada pestaña (`ui/nav/Routes.kt`). */
private fun TourTab.destination(): Any = when (this) {
    TourTab.TODAY -> TodayDest
    TourTab.STATS -> StatsDest
    TourTab.WRAPPED -> WrappedDest
    TourTab.PASSPORT -> PassportDest
}

/** Opacidad del velo (la misma que el de `ReelDialog`). */
private const val VEIL_ALPHA = 0.9f

/** Cuánto se espera a que aparezca el elemento antes de poner el texto sin hueco. */
private const val TARGET_WAIT_MS = 700L

/** Sitio mínimo para el panel junto al hueco; si no lo hay, va abajo con fondo propio. */
private val MinPanelHeight = Space.x20 * 2
