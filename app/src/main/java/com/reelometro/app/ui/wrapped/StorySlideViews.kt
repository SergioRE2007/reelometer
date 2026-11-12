package com.reelometro.app.ui.wrapped

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.TicketLine
import com.reelometro.app.copy.Words
import com.reelometro.app.copy.WrappedCopy
import com.reelometro.app.ui.components.Barcode
import com.reelometro.app.ui.components.ButtonRow
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.PlatformMark
import com.reelometro.app.ui.components.ReceiptLine
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.charts.PlatformSplitBar
import com.reelometro.app.ui.components.charts.RankBars
import com.reelometro.app.ui.components.charts.RankItem
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.components.flap.SplitFlapCounter
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlin.math.max

/** Escalonado de entrada de los bloques de una diapositiva. */
private const val REVEAL_STAGGER_MS = 70L

/** Parte del hueco de cada día que ocupa su barra en la diapositiva del peor día. */
private const val BAR_FILL = 0.6f

/** Retardo de la ola de las barras (de izquierda a derecha), en fracción de la animación. */
private const val BAR_WAVE = 0.6f

/** Una diapositiva de la historia. [Slide.Empty] se pinta fuera (sin barra de progreso). */
@Composable
internal fun SlideView(
    slide: Slide,
    ticket: RenderedTicket?,
    ticketFailed: Boolean,
    notice: String?,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    when (slide) {
        is Slide.Cover -> CoverSlide(slide)
        is Slide.Total -> TotalSlide(slide)
        is Slide.Time -> TimeSlide(slide)
        is Slide.Equivalences -> EquivalencesSlide(slide)
        is Slide.Creator -> CreatorSlide(slide)
        is Slide.Category -> CategorySlide(slide)
        is Slide.WorstDay -> WorstDaySlide(slide)
        is Slide.Rhythm -> RhythmSlide(slide)
        is Slide.Closing -> ClosingSlide(slide, ticket, ticketFailed, notice, onShare, onSave)
        is Slide.Empty -> Unit
    }
}

// ====================================================================== diapositivas

@Composable
private fun CoverSlide(slide: Slide.Cover) {
    val colors = ReelTheme.colors
    SlideColumn {
        Spacer(Modifier.weight(1.4f))
        Text(
            text = slide.title,
            style = ReelTheme.type.displayL,
            color = colors.text,
            modifier = Modifier.reveal(0).semantics { heading() },
        )
        Spacer(Modifier.height(Space.x6))
        Label(slide.hint, modifier = Modifier.reveal(1))
        Spacer(Modifier.weight(1f))
        if (slide.barcode.isNotEmpty()) {
            Barcode(values = slide.barcode, color = colors.text, height = Space.x16, modifier = Modifier.reveal(2))
        }
    }
}

@Composable
private fun TotalSlide(slide: Slide.Total) {
    SlideColumn {
        Kicker(Labels.VIDEOS)
        Spacer(Modifier.weight(1f))
        SplitFlapCounter(
            value = slide.count,
            minDigits = 1,
            contentDescription = Words.videos(slide.count),
        )
        Spacer(Modifier.height(Space.x6))
        Quote(slide.quote, order = 1)
        Spacer(Modifier.weight(1f))
        Details(slide.details, order = 2)
    }
}

@Composable
private fun TimeSlide(slide: Slide.Time) {
    SlideColumn {
        Kicker(Labels.TIME)
        Spacer(Modifier.weight(1f))
        FitText(slide.value, ReelTheme.type.displayXL, ReelTheme.colors.text, Modifier.reveal(0))
        Spacer(Modifier.height(Space.x6))
        Quote(slide.quote, order = 1)
        Spacer(Modifier.weight(1f))
        Details(slide.details, order = 2)
    }
}

@Composable
private fun EquivalencesSlide(slide: Slide.Equivalences) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    SlideColumn {
        Kicker(slide.label)
        Spacer(Modifier.weight(1f))
        if (slide.lines.isEmpty()) {
            Quote(slide.empty.orEmpty(), order = 1)
        } else {
            slide.lines.forEachIndexed { i, line ->
                if (i > 0) {
                    Spacer(Modifier.height(Space.x5))
                    TicketDivider(Modifier.reveal(i + 1))
                    Spacer(Modifier.height(Space.x5))
                }
                Row(Modifier.reveal(i + 1)) {
                    Text(
                        text = (i + 1).toString().padStart(2, '0'),
                        style = type.labelL,
                        color = colors.textMuted,
                        modifier = Modifier.width(Space.x10).alignByBaseline(),
                    )
                    Column(Modifier.weight(1f).alignByBaseline()) {
                        Text(line.text, style = type.quote, color = colors.text)
                        Spacer(Modifier.height(Space.x2))
                        Text(line.basis, style = type.dataS, color = colors.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CreatorSlide(slide: Slide.Creator) {
    val colors = ReelTheme.colors
    SlideColumn {
        Kicker(Labels.TOP_CREATOR)
        Spacer(Modifier.weight(1f))
        if (slide.handle != null) {
            FitText(slide.handle, ReelTheme.type.displayL, colors.text, Modifier.reveal(0), maxLines = 2)
            if (slide.platform != null) {
                Spacer(Modifier.height(Space.x3))
                Row(Modifier.reveal(1), verticalAlignment = Alignment.CenterVertically) {
                    PlatformMark(slide.platform)
                    Spacer(Modifier.width(Space.x2))
                    Label(slide.platform.displayName, large = true)
                }
            }
            Spacer(Modifier.height(Space.x6))
        }
        Quote(slide.quote, order = 2)
        Spacer(Modifier.weight(1f))
        Details(slide.details, order = 3)
    }
}

@Composable
private fun CategorySlide(slide: Slide.Category) {
    SlideColumn {
        Kicker(WrappedCopy.CATEGORY_LABEL)
        Spacer(Modifier.weight(1f))
        FitText(slide.name, ReelTheme.type.displayL, ReelTheme.colors.text, Modifier.reveal(0), maxLines = 2)
        Spacer(Modifier.height(Space.x6))
        Quote(slide.quote, order = 1)
        Spacer(Modifier.weight(1f))
        if (slide.ranking.isNotEmpty()) {
            RankBars(
                items = slide.ranking.map { RankItem(it.label, it.fraction, it.valueLabel) },
                modifier = Modifier.reveal(2),
            )
        }
    }
}

@Composable
private fun WorstDaySlide(slide: Slide.WorstDay) {
    val colors = ReelTheme.colors
    SlideColumn {
        Kicker(Labels.WORST_DAY)
        Spacer(Modifier.weight(1f))
        FitText(slide.day, ReelTheme.type.displayXL, colors.signalDisplay, Modifier.reveal(0))
        Spacer(Modifier.height(Space.x2))
        Label(slide.weekday, large = true, modifier = Modifier.reveal(0))
        Spacer(Modifier.height(Space.x6))
        Quote(slide.quote, order = 1)
        Spacer(Modifier.weight(1f))
        DayBars(slide.bars, slide.highlight, Modifier.reveal(2))
        Spacer(Modifier.height(Space.x4))
        Details(slide.details, order = 3)
    }
}

@Composable
private fun RhythmSlide(slide: Slide.Rhythm) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    SlideColumn {
        if (slide.peakHour != null) {
            Kicker(Labels.PEAK_HOUR)
            Spacer(Modifier.weight(1f))
            // Sin el tracking negativo del token: en «22:00» los doses se tocarían.
            FitText(
                text = slide.peakHour,
                style = type.displayXL.copy(letterSpacing = TextUnit.Unspecified),
                color = colors.text,
                modifier = Modifier.reveal(0),
            )
            if (slide.peakQuote != null) {
                Spacer(Modifier.height(Space.x4))
                Quote(slide.peakQuote, order = 1, small = true)
            }
        } else {
            Kicker(Labels.PLATFORMS)
        }
        Spacer(Modifier.weight(1f))
        if (slide.platforms.isNotEmpty()) {
            Column(Modifier.reveal(2)) {
                if (slide.peakHour != null) {
                    Label(Labels.PLATFORMS, large = true, color = colors.signalText)
                    Spacer(Modifier.height(Space.x3))
                }
                PlatformSplitBar(segments = slide.platforms.map { colors.platform(it.platform) to it.fraction })
                Spacer(Modifier.height(Space.x2))
                slide.platforms.forEach {
                    ReceiptLine(label = it.platform.shortName, value = it.value, mark = colors.platform(it.platform))
                }
                if (slide.platformsText != null) {
                    Spacer(Modifier.height(Space.x3))
                    Text(slide.platformsText, style = type.bodySmall, color = colors.textMuted)
                }
            }
        }
    }
}

/** Cierre: la frase, la vista previa del ticket (el mismo bitmap que se comparte) y sus botones. */
@Composable
private fun ClosingSlide(
    slide: Slide.Closing,
    ticket: RenderedTicket?,
    ticketFailed: Boolean,
    notice: String?,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = Space.screen, end = Space.screen, top = Space.x6, bottom = Space.x4),
    ) {
        Kicker(slide.label)
        Spacer(Modifier.height(Space.x3))
        Text(slide.quote, style = type.quoteSmall, color = colors.text, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(Space.x5))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .reveal(2),
            contentAlignment = Alignment.Center,
        ) {
            when {
                ticket != null -> Image(
                    bitmap = ticket.image,
                    contentDescription = slide.imageDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(TicketRenderer.WIDTH.toFloat() / TicketRenderer.HEIGHT, matchHeightConstraintsFirst = true)
                        .border(Space.hairline, colors.lineStrong),
                )
                ticketFailed -> Text(WrappedCopy.SHARE_FAILED, style = type.body, color = colors.textMuted)
                else -> FlapLoader(style = type.displayL, contentDescription = StateCopy.LOADING_DESCRIPTION)
            }
        }
        Spacer(Modifier.height(Space.x5))
        ButtonRow { slot ->
            ReelButton(
                text = Buttons.SHARE,
                onClick = onShare,
                modifier = slot,
                icon = ReelIcons.Share,
                enabled = ticket != null,
                contentDescription = A11y.SHARE_SUMMARY,
            )
            ReelButton(
                text = Buttons.SAVE,
                onClick = onSave,
                modifier = slot,
                style = ReelButtonStyle.Secondary,
                icon = ReelIcons.Download,
                enabled = ticket != null,
                contentDescription = A11y.SAVE_IMAGE,
            )
        }
        Spacer(Modifier.height(Space.x2))
        Text(
            text = notice.orEmpty(),
            style = type.dataS,
            color = colors.textMuted,
            minLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

// ====================================================================== piezas

/**
 * Columna de diapositiva: llena la pantalla (los `Spacer(weight)` reparten el aire) y, si con la
 * fuente grande el contenido no cabe, se desplaza en vez de cortarse.
 */
@Composable
private fun SlideColumn(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .navigationBarsPadding()
                .padding(start = Space.screen, end = Space.screen, top = Space.x6, bottom = Space.x8),
            content = content,
        )
    }
}

/** Etiqueta de la diapositiva, en acento. */
@Composable
private fun Kicker(text: String) {
    Label(text, color = ReelTheme.colors.signalText, large = true, modifier = Modifier.reveal(0))
}

@Composable
private fun Quote(text: String, order: Int, small: Boolean = false) {
    val type = ReelTheme.type
    Text(
        text = text,
        style = if (small) type.quoteSmall else type.quote,
        color = ReelTheme.colors.text,
        modifier = Modifier.reveal(order),
    )
}

/** Líneas de ticket al pie (etiqueta en mayúsculas · valor en mono). */
@Composable
private fun Details(lines: List<TicketLine>, order: Int) {
    if (lines.isEmpty()) return
    Column(Modifier.reveal(order)) {
        TicketDivider()
        Spacer(Modifier.height(Space.x2))
        lines.forEach { ReceiptLine(label = it.label, value = it.value, highlight = true) }
    }
}

/** Texto grande que encoge lo justo para caber (nunca por debajo de `headline`). */
@Composable
private fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = 1) {
    // Interlineado proporcional al tamaño final (el del token es fijo en sp).
    val ratio = style.lineHeight.value / style.fontSize.value
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, lineHeight = ratio.em),
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = ReelTheme.type.headline.fontSize, maxFontSize = style.fontSize),
    )
}

/** Un día por barra; el peor, en `signal`. Crecen en ola de izquierda a derecha (spring `gentle`). */
@Composable
private fun DayBars(values: List<Float>, highlight: Int, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val reduced = LocalReducedMotion.current
    val grow = remember(values) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(values, reduced) {
        if (reduced) grow.snapTo(1f) else grow.animateTo(1f, Motion.gentle())
    }
    val top = remember(values) { values.maxOrNull()?.takeIf { it > 0f } ?: 1f }
    val base = colors.lineStrong
    val accent = colors.signal
    Spacer(
        modifier
            .fillMaxWidth()
            .height(Space.x20)
            .drawBehind {
                val n = values.size
                if (n == 0) return@drawBehind
                val slot = size.width / n
                val bar = max(1f, slot * BAR_FILL)
                val minH = Space.hairline.toPx()
                val g = grow.value
                for (i in 0 until n) {
                    val local = (g * (1f + BAR_WAVE) - BAR_WAVE * i / n).coerceIn(0f, 1f)
                    val h = max(minH, size.height * (values[i] / top) * local)
                    drawRect(
                        color = if (i == highlight) accent else base,
                        topLeft = Offset(i * slot + (slot - bar) / 2f, size.height - h),
                        size = Size(bar, h),
                    )
                }
            },
    )
}

/** Entrada escalonada de cada bloque: aparece y sube 16 dp (en la fase de dibujo). */
@Composable
private fun Modifier.reveal(order: Int): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(order * REVEAL_STAGGER_MS)
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
