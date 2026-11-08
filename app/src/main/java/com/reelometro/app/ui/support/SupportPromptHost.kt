package com.reelometro.app.ui.support

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.reelometro.app.AppLinks
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.SupportCopy
import com.reelometro.app.ui.components.JaggedShape
import com.reelometro.app.ui.components.JaggedSide
import com.reelometro.app.ui.components.JaggedTooth
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Lo que tarda en salir tras entrar a una pestaña: que dé tiempo a ver la pantalla antes. */
private const val SHOW_DELAY_MS = 2_500L

/** Lo que se espera a la primera lectura del Pasaporte para saber si hay celebraciones pendientes. */
private const val PASSPORT_WAIT_MS = 3_000L

/** Opacidad del velo (la misma que `ReelDialog`). */
private const val SCRIM_ALPHA = 0.9f

/**
 * **Aviso de apoyo**: un aviso grande en mitad de la pantalla que cuenta que la app la hace un estudiante y
 * lleva a su GitHub (allí está el botón de donaciones). Cuándo sale lo decide [SupportPrompt]; aquí, además:
 *
 * - solo en las pestañas y nunca durante el recorrido ([enabled]); [navReady] = la navegación ya tiene destino
 *   (al recrear la actividad, el primer fotograma no lo tiene y no cuenta como «salir de las pestañas»);
 * - unos [SHOW_DELAY_MS] ms después de entrar a una pestaña, como mucho una vez por sesión (la de la
 *   actividad: sobrevive a girar la pantalla);
 * - nunca en una sesión en la que haya una celebración del Pasaporte pendiente (salen ellas y este espera);
 * - nunca si [link] está vacía.
 *
 * Se apunta como enseñado al salir. VER SU GITHUB abre el navegador y lo da por terminado; si no se puede abrir,
 * el aviso sigue abierto con el error. AHORA NO (o atrás, o tocar fuera) solo lo cierra. «No volver a
 * mostrar» lo da por terminado.
 */
@Composable
fun SupportPromptHost(
    enabled: Boolean,
    passportEnabled: Boolean,
    modifier: Modifier = Modifier,
    navReady: Boolean = true,
    link: String = AppLinks.PROJECT_URL,
) {
    val context = LocalContext.current
    val app = remember(context) { ReelometroApp.from(context) }
    val haptics = rememberHaptics()

    // Ya decidido en esta sesión (enseñado o descartado por una celebración).
    var handled by rememberSaveable { mutableStateOf(false) }
    var celebrationSeen by rememberSaveable { mutableStateOf(false) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var passportRead by remember { mutableStateOf(false) }

    // Si en algún momento de la sesión hay una celebración pendiente, el aviso ya no sale.
    val watchPassport = enabled && passportEnabled && !handled
    LaunchedEffect(watchPassport) {
        if (!watchPassport) return@LaunchedEffect
        app.passport.observe()
            .catch { passportRead = true }
            .collect { state ->
                passportRead = true
                if (state.pending.isNotEmpty()) celebrationSeen = true
            }
    }

    LaunchedEffect(enabled, passportEnabled, link) {
        if (!enabled || handled || link.isBlank()) return@LaunchedEffect
        delay(SHOW_DELAY_MS)
        if (passportEnabled) {
            withTimeoutOrNull(PASSPORT_WAIT_MS) { snapshotFlow { passportRead }.first { it } } ?: return@LaunchedEffect
            if (celebrationSeen) {
                handled = true
                return@LaunchedEffect
            }
        }
        val settings = app.settings.current()
        val state = app.settings.supportPrompt.first()
        val today = LocalDate.now().toEpochDay()
        val show = SupportPrompt.shouldShow(
            state = state,
            today = today,
            onboardingDone = settings.onboardingDone,
            tourDone = settings.tourDone,
            hasLink = link.isNotBlank(),
        )
        if (!show) return@LaunchedEffect
        handled = true
        failed = false
        visible = true
        // Fuera del efecto: si se sale de la pestaña justo ahora, se apunta igual.
        app.appScope.launch { runCatching { app.settings.markSupportPromptShown(today) } }
    }

    // Al salir de las pestañas (un enlace, el recorrido) se cierra como con AHORA NO. Solo con la navegación ya
    // lista: al recrear la actividad (girar la pantalla) el primer fotograma no tiene pestaña y no debe cerrarlo.
    LaunchedEffect(enabled, navReady) { if (navReady && !enabled) visible = false }

    SupportPromptDialog(
        visible = visible && (enabled || !navReady),
        failed = failed,
        link = link,
        onOpen = {
            if (AppLinks.open(context, link)) {
                haptics.confirm()
                visible = false
                app.appScope.launch { runCatching { app.settings.finishSupportPrompt() } }
            } else {
                haptics.reject()
                failed = true
            }
        },
        onLater = { visible = false },
        onNever = {
            visible = false
            app.appScope.launch { runCatching { app.settings.finishSupportPrompt() } }
        },
        modifier = modifier,
    )
}

/**
 * La hoja del aviso: como `ReelDialog` (ticket `bgRaised` con filete y borde dentado, velo del color de fondo,
 * spring `standard`), pero con los botones uno encima del otro a todo el ancho y un enlace discreto debajo.
 * Atrás o tocar fuera = [onLater].
 */
@Composable
private fun SupportPromptDialog(
    visible: Boolean,
    failed: Boolean,
    link: String,
    onOpen: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return

    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val reduced = ReelTheme.reducedMotion

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(android.R.style.Animation)
        }

        val spec = if (reduced) spring<Float>(dampingRatio = 1f, stiffness = 1500f) else Motion.standard()
        val transition = rememberTransition(state, label = "support")
        val scrim by transition.animateFloat({ spec }, label = "scrim") { if (it) 1f else 0f }
        val sheet by transition.animateFloat({ spec }, label = "sheet") { if (it) 1f else 0f }
        val rise = if (reduced) 0f else 1f

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(colors.bg, alpha = SCRIM_ALPHA * scrim) }
                .pointerInput(onLater) { detectTapGestures { onLater() } }
                .safeDrawingPadding()
                .padding(Space.screen),
            contentAlignment = Alignment.Center,
        ) {
            val shape = remember { JaggedShape(JaggedSide.Bottom) }
            Column(
                modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = sheet.coerceIn(0f, 1f)
                        translationY = (1f - sheet) * 24.dp.toPx() * rise
                    }
                    .semantics { paneTitle = SupportCopy.PANE_TITLE }
                    .pointerInput(Unit) { detectTapGestures { } }
                    .background(colors.bgRaised, shape)
                    .border(Space.hairline, colors.lineStrong, shape)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.padding(start = Space.x6, end = Space.x6, top = Space.x8)) {
                    Label(SupportCopy.KICKER, color = colors.signalText)
                    Spacer(Modifier.height(Space.x4))
                    Text(
                        SupportCopy.TITLE,
                        style = type.headline,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.x4))
                    Text(SupportCopy.TEXT, style = type.body, color = colors.textMuted)
                    Spacer(Modifier.height(Space.x3))
                    Text(SupportCopy.NOTE, style = type.dataS, color = colors.textFaint)
                    if (failed) {
                        Spacer(Modifier.height(Space.x4))
                        Text(
                            SupportCopy.openFailed(link),
                            style = type.bodySmall,
                            color = colors.signalText,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
                Spacer(Modifier.height(Space.x6))
                TicketDivider(
                    style = TicketDividerStyle.Perforated,
                    notchColor = colors.bg,
                    notchRim = colors.lineStrong,
                )
                Spacer(Modifier.height(Space.x4))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Space.x6, end = Space.x6, bottom = Space.x4 + JaggedTooth / 2),
                    verticalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    ReelButton(
                        text = SupportCopy.OPEN,
                        onClick = onOpen,
                        icon = ReelIcons.External,
                        haptic = false,
                        contentDescription = SupportCopy.OPEN_DESCRIPTION,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ReelButton(
                        text = SupportCopy.LATER,
                        onClick = onLater,
                        style = ReelButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touch)
                            .pressable(onClick = onNever)
                            .semantics { contentDescription = SupportCopy.NEVER_DESCRIPTION },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            SupportCopy.NEVER,
                            style = type.bodySmall.copy(textDecoration = TextDecoration.Underline),
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
