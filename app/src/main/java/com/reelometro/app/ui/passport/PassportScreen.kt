package com.reelometro.app.ui.passport

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.PassportCopy
import com.reelometro.app.game.FlapMood
import com.reelometro.app.ui.components.JaggedShape
import com.reelometro.app.ui.components.JaggedSide
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReceiptLine
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.ReelDialog
import com.reelometro.app.ui.components.ReelIconButton
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.charts.GhostBar
import com.reelometro.app.ui.components.charts.GhostWeekChart
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.nav.LocalBottomBarHeight
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.tour.TourTarget
import com.reelometro.app.ui.tour.tourTarget
import kotlinx.coroutines.delay

/** Columnas de la rejilla de sellos. */
private const val STAMP_COLUMNS = 3

/** Segmentos de las barras de progreso tipo ticket. */
private const val PROGRESS_SEGMENTS = 24

/** Tope del escalonado de los sellos (los últimos entran a la vez). */
private const val MAX_STAGGERED_STAMPS = 12

/**
 * Pestaña **PASAPORTE** (docs/DESIGN.md: aeropuerto, tarjeta de embarque, sellos, ticket): Flap
 * con su estado, la tarjeta de embarque del nivel (millas y su desglose), el reto semanal contra el
 * fantasma y el pasaporte de sellos. Sin lógica: todo llega formateado en [state].
 *
 * Entradas escalonadas con springs (`Motion.STAGGER_LIST_MS`); con «reducir animaciones», todo
 * aparece ya en su sitio.
 */
@Composable
fun PassportScreen(
    state: PassportUiState,
    onRetry: () -> Unit,
    onMoodSeen: (FlapMood) -> Unit,
    onOpenSettings: () -> Unit,
    onShareLevel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    var stampId by rememberSaveable { mutableStateOf<String?>(null) }
    var stampOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            // El inset va antes del scroll: el contenido se recorta bajo la barra de estado.
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        ReelTopBar(
            title = PassportCopy.TITLE,
            kicker = (state as? PassportUiState.Content)?.kicker,
            windowInsets = WindowInsets(0),
            actions = { ReelIconButton(ReelIcons.Settings, contentDescription = A11y.SETTINGS, onClick = onOpenSettings) },
        )
        when (state) {
            is PassportUiState.Loading -> FlapLoader(
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .tourTarget(TourTarget.PASSPORT),
                style = ReelTheme.type.displayXL,
                contentDescription = A11y.LOADING,
            )
            is PassportUiState.Error -> ErrorState(
                message = state.message,
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .tourTarget(TourTarget.PASSPORT),
                action = { ReelButton(Buttons.RETRY, onClick = onRetry) },
            )
            is PassportUiState.Content -> PassportBody(
                content = state,
                onMoodSeen = onMoodSeen,
                onShareLevel = onShareLevel,
                onOpenStamp = { stamp ->
                    stampId = stamp.id.name
                    stampOpen = true
                },
            )
        }
        Spacer(Modifier.height(Space.section))
        Spacer(Modifier.height(LocalBottomBarHeight.current))
    }

    val content = state as? PassportUiState.Content
    val stamp = content?.stamps?.firstOrNull { it.id.name == stampId }
    if (content != null && stamp != null) {
        StampDialog(
            stamp = stamp,
            visible = stampOpen,
            referenceNote = content.referenceNote,
            onDismiss = { stampOpen = false },
        )
    }
}

@Composable
private fun PassportBody(
    content: PassportUiState.Content,
    onMoodSeen: (FlapMood) -> Unit,
    onShareLevel: () -> Unit,
    onOpenStamp: (StampUi) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FlapBlock(content.flap, onMoodSeen, Modifier.reveal(0))
        Spacer(Modifier.height(Space.x10))
        TicketDivider(style = TicketDividerStyle.Perforated, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(Space.x10))
        Column(
            Modifier.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.section),
        ) {
            LevelBlock(1, content.level, onShareLevel, Modifier.reveal(2))
            ChallengeBlock(2, content.challenge, content.past, Modifier.reveal(3))
            StampsBlock(3, content.stamps, content.stampsCount, onOpenStamp, Modifier.reveal(4))
        }
    }
}

// ------------------------------------------------------------------ Flap

@Composable
private fun FlapBlock(flap: FlapUi, onMoodSeen: (FlapMood) -> Unit, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
        Row(verticalAlignment = Alignment.Bottom) {
            FlapMascot(
                mood = flap.mood,
                previousMood = flap.previousMood,
                description = flap.description,
                onMoodSeen = onMoodSeen,
                modifier = Modifier.tourTarget(TourTarget.PASSPORT),
            )
            Spacer(Modifier.width(Space.x5))
            // TalkBack ya lo lee todo en Flap: aquí no se repite.
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Label(PassportCopy.SECTION_FLAP)
                Spacer(Modifier.height(Space.x1))
                Text(
                    flap.label,
                    style = type.headline,
                    color = if (flap.mood == FlapMood.ZOMBI && !flap.warmingUp) colors.signalText else colors.text,
                )
                Spacer(Modifier.height(Space.x3))
                Label(flap.record, color = colors.text)
                if (flap.reference != null) {
                    Spacer(Modifier.height(Space.x1))
                    Label(flap.reference)
                }
            }
        }
        Spacer(Modifier.height(Space.x6))
        Text(
            flap.phrase,
            style = type.quote,
            color = colors.text,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

// ------------------------------------------------------------------ tarjeta de embarque

@Composable
private fun LevelBlock(index: Int, level: LevelUi, onShare: () -> Unit, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val paper = remember { JaggedShape(JaggedSide.Bottom) }
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, PassportCopy.SECTION_LEVEL, trailing = level.index)
        Spacer(Modifier.height(Space.x4))
        Column(Modifier.fillMaxWidth().background(colors.bgRaised, paper)) {
            Column(
                Modifier
                    .padding(start = Space.x5, end = Space.x5, top = Space.x5, bottom = Space.x4)
                    .clearAndSetSemantics { contentDescription = level.description },
            ) {
                Row {
                    Label(PassportCopy.LABEL_ORIGIN, modifier = Modifier.weight(1f))
                    Text(
                        if (level.nextCode != null) PassportCopy.LABEL_DESTINATION else PassportCopy.LABEL_FINAL,
                        style = type.label,
                        color = colors.textMuted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Space.x1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = level.code,
                        style = type.displayL.copy(color = colors.text),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = type.headline.fontSize, maxFontSize = type.displayL.fontSize),
                        modifier = Modifier.weight(1f),
                    )
                    ReelIcon(ReelIcons.ArrowRight, contentDescription = null, tint = colors.signalText)
                    BasicText(
                        text = level.nextCode ?: level.code,
                        style = type.displayL.copy(
                            color = if (level.nextCode != null) colors.textMuted else colors.text,
                            textAlign = TextAlign.End,
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = type.headline.fontSize, maxFontSize = type.displayL.fontSize),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Space.x1))
                Row {
                    Label(level.name, color = colors.text, modifier = Modifier.weight(1f))
                    Text(
                        level.nextName ?: level.name,
                        style = type.label,
                        color = colors.textMuted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Space.x5))
                TicketProgress(level.fraction, accentLead = true)
                Spacer(Modifier.height(Space.x3))
                Text(level.miles, style = type.dataL, color = colors.text)
                Spacer(Modifier.height(Space.x1))
                Label(level.toNext)
                Spacer(Modifier.height(Space.x4))
                Text(level.tagline, style = type.bodySmall, color = colors.textMuted)
            }
            TicketDivider(style = TicketDividerStyle.Perforated, notchColor = colors.bg)
            Column(Modifier.padding(start = Space.x5, end = Space.x5, top = Space.x2, bottom = Space.x6)) {
                level.breakdown.forEach { line -> key(line.label) { ReceiptLine(line.label, line.value) } }
                ReceiptLine(level.total.label, level.total.value, highlight = true)
                ReceiptLine(level.streak.label, level.streak.value)
                Spacer(Modifier.height(Space.x3))
                Text(level.note, style = type.dataS, color = colors.textMuted)
                Spacer(Modifier.height(Space.x2))
                ReelButton(
                    text = PassportCopy.SHARE_TICKET,
                    onClick = onShare,
                    style = ReelButtonStyle.Ghost,
                    icon = ReelIcons.Share,
                    trailingIcon = null,
                    contentDescription = PassportCopy.SHARE_TICKET_DESCRIPTION,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ reto semanal

@Composable
private fun ChallengeBlock(index: Int, challenge: ChallengeUi, past: List<PastChallengeUi>, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth()) {
        when (challenge) {
            is ChallengeUi.Warmup -> {
                SectionHeader(index, PassportCopy.SECTION_CHALLENGE, trailing = challenge.label)
                Spacer(Modifier.height(Space.x4))
                Text(challenge.text, style = type.quoteSmall, color = colors.text)
            }
            is ChallengeUi.Active -> {
                SectionHeader(index, PassportCopy.SECTION_CHALLENGE, trailing = challenge.statusLabel)
                Spacer(Modifier.height(Space.x4))
                Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = challenge.description }) {
                    Label(challenge.title, color = colors.text, large = true)
                    Spacer(Modifier.height(Space.x3))
                    BasicText(
                        text = challenge.total,
                        style = type.displayL.copy(color = if (challenge.over) colors.signalDisplay else colors.text),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = type.headline.fontSize, maxFontSize = type.displayL.fontSize),
                    )
                    Spacer(Modifier.height(Space.x1))
                    Label(challenge.caption)
                    Spacer(Modifier.height(Space.x4))
                    TicketProgress(challenge.fraction, over = challenge.over)
                    Spacer(Modifier.height(Space.x5))
                    Text(
                        challenge.phrase,
                        style = type.quoteSmall,
                        color = colors.text,
                    )
                }
                Spacer(Modifier.height(Space.x6))
                GhostWeekChart(
                    bars = challenge.days.map { GhostBar(it.label, it.count, it.ghost, it.description) },
                    todayIndex = challenge.todayIndex,
                    contentDescription = challenge.chartDescription,
                )
                Spacer(Modifier.height(Space.x3))
                Label(challenge.legend)
                Spacer(Modifier.height(Space.x4))
                challenge.lines.forEach { line -> key(line.label) { ReceiptLine(line.label, line.value) } }
            }
        }
        if (past.isNotEmpty()) {
            Spacer(Modifier.height(Space.x8))
            Label(PassportCopy.SECTION_PAST)
            Spacer(Modifier.height(Space.x2))
            past.forEach { item ->
                key(item.key) {
                    Box(Modifier.clearAndSetSemantics { contentDescription = item.description }) {
                        ReceiptLine(
                            label = item.label,
                            value = item.value,
                            valueColor = if (item.won) colors.text else colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sellos

@Composable
private fun StampsBlock(index: Int, stamps: List<StampUi>, count: String, onOpen: (StampUi) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, PassportCopy.SECTION_STAMPS, trailing = count)
        Spacer(Modifier.height(Space.x6))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            stamps.chunked(STAMP_COLUMNS).forEachIndexed { row, items ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                    items.forEachIndexed { col, stamp ->
                        key(stamp.id) {
                            val order = (row * STAMP_COLUMNS + col).coerceAtMost(MAX_STAGGERED_STAMPS)
                            PassportStamp(
                                stamp = stamp,
                                onClick = { onOpen(stamp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .reveal(order),
                            )
                        }
                    }
                    repeat(STAMP_COLUMNS - items.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Pista del sello que falta (o el detalle del conseguido), con el sello dibujado en grande. */
@Composable
private fun StampDialog(stamp: StampUi, visible: Boolean, referenceNote: String?, onDismiss: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    ReelDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = PassportCopy.stampSpoken(stamp.id),
        confirmLabel = Buttons.CLOSE,
        onConfirm = onDismiss,
        kicker = if (stamp.earned) PassportCopy.KICKER_EARNED else PassportCopy.KICKER_HINT,
        text = stamp.hint,
        dismissLabel = null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PassportStamp(stamp = stamp, modifier = Modifier.width(FlapDefaults.SmallSize * 2))
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Label(stamp.status, color = if (stamp.earned) colors.text else colors.textMuted)
                Spacer(Modifier.height(Space.x2))
                Label(stamp.xp, color = if (stamp.ironic || !stamp.earned) colors.textMuted else colors.signalText)
                if (stamp.progressText != null) {
                    Spacer(Modifier.height(Space.x2))
                    Label(stamp.progressText)
                }
            }
        }
        if (referenceNote != null && !stamp.ironic) {
            Spacer(Modifier.height(Space.x4))
            Text(referenceNote, style = type.dataS, color = colors.textMuted)
        }
    }
}

// ------------------------------------------------------------------ piezas

/**
 * Barra de progreso tipo ticket: [PROGRESS_SEGMENTS] segmentos rectos con huecos (perforada), que
 * se llena con spring `gentle` al aparecer. Con [accentLead] el segmento que avanza va en
 * `signal`; con [over] (pasado el tope del reto) se llena entera en `signal`.
 */
@Composable
private fun TicketProgress(fraction: Float, modifier: Modifier = Modifier, accentLead: Boolean = false, over: Boolean = false) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val target = fraction.coerceIn(0f, 1f)
    val progress = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced) {
        if (reduced) progress.snapTo(target) else progress.animateTo(target, Motion.gentle())
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Space.x3)
            .clearAndSetSemantics {},
    ) {
        val gap = Space.hairline.toPx() * 2
        val w = (size.width - gap * (PROGRESS_SEGMENTS - 1)) / PROGRESS_SEGMENTS
        val filled = progress.value.coerceIn(0f, 1f) * PROGRESS_SEGMENTS
        val lead = filled.toInt().coerceAtMost(PROGRESS_SEGMENTS - 1)
        for (i in 0 until PROGRESS_SEGMENTS) {
            val left = i * (w + gap)
            drawRect(colors.bgSunken, topLeft = Offset(left, 0f), size = Size(w, size.height))
            val part = (filled - i).coerceIn(0f, 1f)
            if (part > 0f) {
                val color = when {
                    over -> colors.signal
                    accentLead && i == lead -> colors.signal
                    else -> colors.text
                }
                drawRect(color, topLeft = Offset(left, 0f), size = Size(w * part, size.height))
            }
        }
    }
}

/** Entrada escalonada de un bloque: aparece y sube 16 dp con spring (en la fase de dibujo). */
@Composable
private fun Modifier.reveal(order: Int): Modifier {
    val reduced = ReelTheme.reducedMotion
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(order * Motion.STAGGER_LIST_MS * 2)
            progress.animateTo(1f, Motion.gentle())
        }
    }
    val shift = with(LocalDensity.current) { Space.x4.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * shift
    }
}
