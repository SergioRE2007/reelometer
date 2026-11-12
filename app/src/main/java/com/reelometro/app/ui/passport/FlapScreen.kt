package com.reelometro.app.ui.passport

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.FlapCopy
import com.reelometro.app.game.FlapMood
import com.reelometro.app.game.FlapRules
import com.reelometro.app.ui.components.AutoSizeTokens
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.ReelIconButton
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.components.isLargeFont
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.nav.LocalBottomBarHeight
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.tour.TourTarget
import com.reelometro.app.ui.tour.tourTarget
import kotlinx.coroutines.delay

/** Segmentos rectos de la barra de vida (cada uno, 5 de vida), como las fichas de un marcador. */
private const val LIFE_SEGMENTS = 20

/**
 * Pestaña **FLAP** (docs/DESIGN.md: el marcador, una sola columna, sin tarjetas): Flap grande y
 * animado con el nombre de su estado y una frase; la barra de vida con los vídeos de hoy y lo que
 * falta para que empeore; «Cómo funciona» (siempre a la vista); sin objetivo, la invitación a
 * ponerlo; y la semana con 7 Flaps pequeños y la racha. Sin lógica: todo llega formateado en [state].
 *
 * El acento (`signal`) solo marca el peligro: la barra y la cifra de vida cuando Flap está ENFERMO
 * o KO, y el nombre del estado en KO. Entradas escalonadas con springs (`Motion.STAGGER_LIST_MS`);
 * con «reducir animaciones», todo aparece ya en su sitio.
 */
@Composable
fun FlapScreen(
    state: FlapUiState,
    onRetry: () -> Unit,
    onMoodSeen: (FlapMood) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            // El inset va antes del scroll: el contenido se recorta bajo la barra de estado.
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        ReelTopBar(
            title = FlapCopy.TITLE,
            kicker = (state as? FlapUiState.Content)?.kicker,
            windowInsets = WindowInsets(0),
            actions = { ReelIconButton(ReelIcons.Settings, contentDescription = A11y.SETTINGS, onClick = onOpenSettings) },
        )
        when (state) {
            is FlapUiState.Loading -> FlapLoader(
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .tourTarget(TourTarget.PASSPORT),
                style = ReelTheme.type.displayXL,
                contentDescription = A11y.LOADING,
            )
            is FlapUiState.Error -> ErrorState(
                message = state.message,
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .tourTarget(TourTarget.PASSPORT),
                action = { ReelButton(Buttons.RETRY, onClick = onRetry) },
            )
            is FlapUiState.Content -> FlapBody(content = state, onMoodSeen = onMoodSeen, onOpenSettings = onOpenSettings)
        }
        Spacer(Modifier.height(Space.section))
        Spacer(Modifier.height(LocalBottomBarHeight.current))
    }
}

@Composable
private fun FlapBody(content: FlapUiState.Content, onMoodSeen: (FlapMood) -> Unit, onOpenSettings: () -> Unit) {
    // La frase de «ha revivido» se queda mientras no cambie el estado, aunque ya se haya marcado visto
    // (al marcarlo, el origen quita el estado anterior y la frase dejaría de llegar).
    var recoveredPhrase by remember(content.mood) { mutableStateOf(content.recoveredPhrase) }
    LaunchedEffect(content.recoveredPhrase) {
        content.recoveredPhrase?.let { recoveredPhrase = it }
    }
    Column(Modifier.fillMaxWidth()) {
        MoodBlock(content, phrase = recoveredPhrase ?: content.phrase, onMoodSeen = onMoodSeen, modifier = Modifier.reveal(0))
        Spacer(Modifier.height(Space.x8))
        LifeBlock(content, Modifier.reveal(1))
        Spacer(Modifier.height(Space.x10))
        TicketDivider(style = TicketDividerStyle.Perforated, modifier = Modifier.reveal(2))
        Spacer(Modifier.height(Space.x10))
        Column(
            Modifier.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.section),
        ) {
            HowBlock(1, content, onOpenSettings, Modifier.reveal(3))
            if (content.week.isNotEmpty()) WeekBlock(2, content, Modifier.reveal(4))
        }
    }
}

// ------------------------------------------------------------------ Flap y su estado

@Composable
private fun MoodBlock(content: FlapUiState.Content, phrase: String, onMoodSeen: (FlapMood) -> Unit, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val mascot: @Composable () -> Unit = {
        FlapMascot(
            mood = content.mood,
            previousMood = content.previousMood,
            description = content.description,
            onMoodSeen = onMoodSeen,
            recovered = content.recovered,
            modifier = Modifier.tourTarget(TourTarget.PASSPORT),
        )
    }
    // TalkBack ya lee el estado en Flap: aquí no se repite.
    val name: @Composable (Modifier) -> Unit = { slot ->
        Column(slot.clearAndSetSemantics {}) {
            Label(FlapCopy.STATE_LABEL)
            Spacer(Modifier.height(Space.x1))
            BasicText(
                text = content.label,
                style = type.displayL.copy(color = if (content.ko) colors.signalDisplay else colors.text),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = type.title.fontSize, maxFontSize = type.displayL.fontSize),
            )
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
        if (isLargeFont()) {
            // Letra grande: Flap encima y el nombre debajo, a todo el ancho (sin apretujarlo).
            mascot()
            Spacer(Modifier.height(Space.x4))
            name(Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                mascot()
                Spacer(Modifier.width(Space.x5))
                name(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.x6))
        Text(phrase, style = type.quote, color = colors.text)
    }
}

// ------------------------------------------------------------------ vida

@Composable
private fun LifeBlock(content: FlapUiState.Content, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
        // La barra ya dice la vida a TalkBack: la cifra no se repite.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.Bottom) {
            Label(FlapCopy.LIFE, color = colors.text, modifier = Modifier.weight(1f))
            Text(content.lifeValue, style = type.dataL, color = if (content.danger) colors.signalText else colors.text)
        }
        Spacer(Modifier.height(Space.x2))
        LifeBar(
            fraction = content.lifeFraction,
            life = content.life,
            danger = content.danger,
            description = content.lifeDescription,
        )
        Spacer(Modifier.height(Space.x3))
        Label(content.today, color = colors.text)
        Spacer(Modifier.height(Space.x1))
        Text(content.next, style = type.bodySmall, color = if (content.ko) colors.signalText else colors.textMuted)
    }
}

/**
 * Barra de vida: [LIFE_SEGMENTS] segmentos rectos con huecos, como un marcador. Entra llena (cada
 * día empieza a tope) y baja con spring `gentle` hasta la vida de ahora; en peligro, en `signal`.
 * TalkBack la lee como barra de progreso («Vida: 62 de 100»).
 */
@Composable
private fun LifeBar(fraction: Float, life: Int, danger: Boolean, description: String, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val target = fraction.coerceIn(0f, 1f)
    val progress = remember { Animatable(if (reduced) target else 1f) }
    LaunchedEffect(target, reduced) {
        if (reduced) progress.snapTo(target) else progress.animateTo(target, Motion.gentle())
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(Space.x4)
            .semantics {
                stateDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = life.coerceIn(0, FlapRules.MAX_LIFE).toFloat(),
                    range = 0f..FlapRules.MAX_LIFE.toFloat(),
                )
            },
    ) {
        val gap = Space.hairline.toPx() * 2
        val w = (size.width - gap * (LIFE_SEGMENTS - 1)) / LIFE_SEGMENTS
        val filled = progress.value.coerceIn(0f, 1f) * LIFE_SEGMENTS
        val fill = if (danger) colors.signal else colors.text
        for (i in 0 until LIFE_SEGMENTS) {
            val left = i * (w + gap)
            drawRect(colors.bgSunken, topLeft = Offset(left, 0f), size = Size(w, size.height))
            val part = (filled - i).coerceIn(0f, 1f)
            if (part > 0f) drawRect(fill, topLeft = Offset(left, 0f), size = Size(w * part, size.height))
        }
    }
}

// ------------------------------------------------------------------ cómo funciona

@Composable
private fun HowBlock(index: Int, content: FlapUiState.Content, onOpenSettings: () -> Unit, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, FlapCopy.SECTION_HOW)
        Spacer(Modifier.height(Space.x4))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            content.how.forEach { line -> key(line) { Text(line, style = type.body, color = colors.text) } }
        }
        val noGoal = content.noGoal
        if (noGoal != null) {
            Spacer(Modifier.height(Space.x6))
            Text(noGoal, style = type.bodySmall, color = colors.textMuted)
            Spacer(Modifier.height(Space.x3))
            ReelButton(
                text = FlapCopy.SET_GOAL,
                onClick = onOpenSettings,
                style = ReelButtonStyle.Secondary,
                contentDescription = FlapCopy.SET_GOAL_DESCRIPTION,
            )
        }
    }
}

// ------------------------------------------------------------------ la semana

@Composable
private fun WeekBlock(index: Int, content: FlapUiState.Content, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, FlapCopy.SECTION_WEEK)
        Spacer(Modifier.height(Space.x5))
        Row(Modifier.fillMaxWidth()) {
            content.week.forEach { day ->
                key(day.key) { WeekDay(day, Modifier.weight(1f)) }
            }
        }
        val streak = content.streak
        if (streak != null) {
            Spacer(Modifier.height(Space.x6))
            Label(FlapCopy.STREAK_LABEL)
            Spacer(Modifier.height(Space.x1))
            Text(streak, style = type.body, color = colors.text)
        }
    }
}

/** Un día de la tira: su Flap pequeño (o un hueco, sin registro), la inicial y los vídeos. */
@Composable
private fun WeekDay(day: FlapDayUi, modifier: Modifier) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val mood = day.mood
    Column(
        modifier.clearAndSetSemantics { contentDescription = day.description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mood != null) {
            FlapStill(mood, size = FlapDefaults.WeekSize)
        } else {
            EmptyDay(Modifier.size(FlapDefaults.WeekSize))
        }
        Spacer(Modifier.height(Space.x2))
        Label(day.initial, color = if (mood != null) colors.text else colors.textMuted)
        Spacer(Modifier.height(Space.x1))
        // Vacío sin registro: el texto vacío ocupa su línea y la fila no baila.
        BasicText(
            text = day.count.orEmpty(),
            style = type.dataS.copy(
                color = if (mood != null && mood.ordinal >= FlapMood.ENFERMO.ordinal) colors.signalText else colors.textMuted,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = AutoSizeTokens.Min, maxFontSize = type.dataS.fontSize),
        )
    }
}

/** Hueco de un día sin registro: una raya corta, como una ficha en blanco. */
@Composable
private fun EmptyDay(modifier: Modifier) {
    val colors = ReelTheme.colors
    Canvas(modifier) {
        val w = size.width * 0.4f
        val h = Space.rule.toPx()
        drawRect(colors.lineStrong, topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f), size = Size(w, h))
    }
}

// ------------------------------------------------------------------ piezas

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
