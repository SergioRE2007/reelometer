package com.reelometro.app.ui.wrapped

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.WrappedCopy
import com.reelometro.app.game.GameEvent
import com.reelometro.app.stats.PeriodRef
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.ReelIconButton
import com.reelometro.app.ui.components.flap.EmptyState
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.nav.SharedKeys
import com.reelometro.app.ui.nav.reelSharedBounds
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cuánto se ve el aviso de «Ticket guardado». */
private const val NOTICE_MS = 3_000L

/** Alto de los segmentos de la barra de progreso (dos filetes). */
private val SegmentHeight = Space.hairline * 2

/**
 * Wrapped de un periodo a pantalla completa, estilo Stories (siempre en paleta oscura): barra de
 * progreso segmentada, avance automático, toque a la derecha/izquierda, mantener para pausar y
 * cerrar con × o con el gesto de atrás (lo gestiona el NavHost, con atrás predictivo).
 *
 * @param kind `WEEK|MONTH|YEAR|ALL`; con [offset] forma el `PeriodRef` («MONTH:-1»).
 */
@Composable
fun StoryRoute(kind: String, offset: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val viewModel: StoryViewModel = viewModel {
        val app = ReelometroApp.from(context)
        StoryViewModel(
            ref = PeriodRef.parse("$kind:$offset"),
            loader = PeriodLoader(app.repository::period),
            tickets = AndroidTicketMaker(app),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // Sellos del Pasaporte «Wrapped compartido» y «ticket guardado» (no hacen nada si ya están). Van en el
    // appScope (no se pierden si se cierra la historia enseguida) y un fallo no cierra la app.
    val app = remember(context) { ReelometroApp.from(context) }
    fun record(event: GameEvent) {
        app.appScope.launch { runCatching { app.passport.recordEvent(event) } }
    }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            notice = null
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TicketFiles.MIME)) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = viewModel.save(uri)
                notice = if (ok) WrappedCopy.SAVED else WrappedCopy.SAVE_FAILED
                if (ok) haptics.confirm() else haptics.reject()
                if (ok) record(GameEvent.TICKET_SAVED)
            }
        }
    }

    ReelTheme(darkTheme = true) {
        DarkSystemBars()
        StoryScreen(
            state = state,
            sharedKey = SharedKeys.story(kind, offset),
            notice = notice,
            onClose = onClose,
            onRetry = viewModel::retry,
            onShare = {
                val ready = state as? StoryUiState.Ready
                if (ready?.ticket != null) {
                    scope.launch {
                        val uri = viewModel.shareUri()
                        val started = uri != null && runCatching {
                            context.startActivity(TicketFiles.shareIntent(uri, ready.shareText))
                        }.isSuccess
                        if (started) haptics.confirm() else notice = WrappedCopy.SHARE_FAILED
                        if (started) record(GameEvent.WRAPPED_SHARED)
                    }
                }
            },
            onSave = {
                val ready = state as? StoryUiState.Ready
                if (ready?.ticket != null) save.launch(ready.fileName)
            },
        )
    }
}

@Composable
fun StoryScreen(
    state: StoryUiState,
    sharedKey: Any,
    notice: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .reelSharedBounds(sharedKey)
            .background(ReelTheme.colors.bg),
    ) {
        when (state) {
            StoryUiState.Loading -> StoryMessage(label = null, onClose = onClose) {
                FlapLoader(style = ReelTheme.type.displayXL, contentDescription = StateCopy.LOADING_DESCRIPTION)
                Spacer(Modifier.height(Space.x4))
                Label(StateCopy.LOADING_LABEL)
            }
            is StoryUiState.Error -> StoryMessage(label = null, onClose = onClose) {
                ErrorState(message = state.message, action = { ReelButton(Buttons.RETRY, onClick = onRetry) })
            }
            is StoryUiState.Ready -> if (state.isEmpty) {
                val empty = state.slides.single() as Slide.Empty
                StoryMessage(label = state.label, onClose = onClose) {
                    EmptyState(
                        title = empty.title,
                        action = { ReelButton(Buttons.CLOSE, onClick = onClose, style = ReelButtonStyle.Secondary) },
                    )
                }
            } else {
                StoryPlayerView(state, notice, onClose, onShare, onSave)
            }
        }
    }
}

/** Cargando, error o periodo vacío: sin barra de progreso, con cerrar. */
@Composable
private fun StoryMessage(label: String?, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StoryHeader(label = label, onClose = onClose, progress = null)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.screen, vertical = Space.x8),
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}

@Composable
private fun StoryPlayerView(
    state: StoryUiState.Ready,
    notice: String?,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    val slides = state.slides
    val count = slides.size
    val player = rememberSaveable(count, saver = playerSaver(count)) { StoryPlayer(count) }
    val reduced = LocalReducedMotion.current
    val autoplay = !reduced && !rememberTouchExploration()
    val haptics = rememberHaptics()

    // Pausa: dedo en la pantalla, app en segundo plano (compartir, guardar) o sin avance automático.
    var holding by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = autoplay && !holding && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(running, player.index) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val deltaMs = (now - last) / 1_000_000
            last += deltaMs * 1_000_000
            if (player.tick(deltaMs)) break
        }
    }
    val chrome = animateFloatAsState(if (hidden) 0f else 1f, Motion.standard(), label = "chrome")

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(player) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    var cancelled = false
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation().also { if (it == null) cancelled = true }
                    }
                    when {
                        up != null -> {
                            up.consume()
                            if (StoryTimeline.isBackTap(up.position.x, size.width.toFloat())) player.previous() else player.next()
                        }
                        !cancelled -> {
                            // Mantener pulsado: pausa con la interfaz escondida hasta soltar.
                            haptics.longPress()
                            hidden = true
                            waitForUpOrCancellation()
                            hidden = false
                        }
                    }
                    holding = false
                }
            }
            .semantics {
                contentDescription = A11y.slide(player.index + 1, count)
                customActions = listOf(
                    CustomAccessibilityAction(A11y.NEXT_SLIDE) { player.next(); true },
                    CustomAccessibilityAction(A11y.PREVIOUS_SLIDE) { player.previous(); true },
                )
            },
    ) {
        StoryHeader(
            label = state.label,
            onClose = onClose,
            progress = { segment -> player.progress(segment, static = !autoplay) },
            segments = count,
            modifier = Modifier.graphicsLayer { alpha = chrome.value },
        )
        AnimatedContent(
            targetState = player.index,
            transitionSpec = {
                val forward = targetState > initialState
                if (reduced) {
                    fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy())
                } else {
                    (fadeIn(Motion.standard()) + slideInHorizontally(Motion.standard()) { w -> (if (forward) w else -w) / 10 }) togetherWith
                        (fadeOut(Motion.standard()) + slideOutHorizontally(Motion.standard()) { w -> (if (forward) -w else w) / 10 })
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "slide",
        ) { index ->
            SlideView(
                slide = slides[index],
                ticket = state.ticket,
                ticketFailed = state.ticketFailed,
                notice = notice,
                onShare = onShare,
                onSave = onSave,
            )
        }
    }
}

/**
 * Barra de progreso segmentada (bajo la barra de estado) y la fila con el periodo y cerrar ×.
 * [progress] se lee en la fase de dibujo: el avance no recompone nada.
 */
@Composable
private fun StoryHeader(
    label: String?,
    onClose: () -> Unit,
    progress: ((Int) -> Float)?,
    modifier: Modifier = Modifier,
    segments: Int = 0,
) {
    val colors = ReelTheme.colors
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        Spacer(Modifier.height(Space.x2))
        if (progress != null && segments > 0) {
            val track = colors.lineStrong
            val fill = colors.text
            Spacer(
                Modifier
                    .padding(horizontal = Space.screen)
                    .fillMaxWidth()
                    .height(SegmentHeight)
                    .drawBehind {
                        val gap = Space.x1.toPx()
                        val w = (size.width - gap * (segments - 1)) / segments
                        for (i in 0 until segments) {
                            val x = i * (w + gap)
                            drawRect(track, Offset(x, 0f), Size(w, size.height))
                            val p = progress(i)
                            if (p > 0f) drawRect(fill, Offset(x, 0f), Size(w * p, size.height))
                        }
                    },
            )
        } else {
            Spacer(Modifier.height(SegmentHeight))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.screen, end = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (label != null) Label(label, maxLines = 1)
            }
            ReelIconButton(ReelIcons.Close, contentDescription = A11y.CLOSE, onClick = onClose)
        }
    }
}

private fun playerSaver(count: Int): Saver<StoryPlayer, Int> = Saver(
    save = { it.index },
    restore = { StoryPlayer(count, it) },
)

/** Con TalkBack (exploración táctil) no hay avance automático: se pasa con las acciones. */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/** La historia es oscura también en tema claro: iconos de las barras del sistema en claro mientras dure. */
@Composable
private fun DarkSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null || view.isInEditMode) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
