package com.reelometro.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.DayCopy
import com.reelometro.app.copy.DeltaLine
import com.reelometro.app.copy.Direction
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.copy.Words
import com.reelometro.app.data.Platform
import com.reelometro.app.stats.CategorySlice
import com.reelometro.app.stats.CreatorRank
import com.reelometro.app.stats.DayStats
import com.reelometro.app.stats.PlatformSlice
import com.reelometro.app.stats.Totals
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReceiptLine
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.charts.HourLine
import com.reelometro.app.ui.components.charts.PlatformSplitBar
import com.reelometro.app.ui.components.charts.RankBars
import com.reelometro.app.ui.components.charts.RankItem
import com.reelometro.app.ui.components.flap.SplitFlapCounter
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.nav.SharedKeys
import com.reelometro.app.ui.nav.reelSharedElement
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.tour.TourTarget
import com.reelometro.app.ui.tour.tourTarget

// Piezas comunes de Hoy, Detalle del día y Estadísticas: modelos ya formateados (los rellena
// el ViewModel con `copy/` y `stats/Format`), su mapeo puro desde las estadísticas y los bloques
// de interfaz que los pintan. Las pantallas no calculan nada: solo colocan estos bloques.

// ====================================================================== modelos

/**
 * Marcador de un día: etiqueta, fichas, columna de objetivo, tiempo, frase y comparación.
 *
 * @property count `null` = fichas en blanco («– – –»): sin señal o cargando.
 * @property alert la etiqueta va en `signalText` (sin señal).
 * @property quote frase ya entre comillas («…»).
 */
@Immutable
data class HeroModel(
    val epochDay: Long,
    val label: String,
    val count: Long?,
    val description: String,
    val alert: Boolean = false,
    val goal: GoalModel? = null,
    val screenTime: ScreenTimeModel? = null,
    val quote: String? = null,
    val comparison: DeltaLine? = null,
)

/** Columna estrecha del objetivo: «OBJETIVO 150» / «+62 PASADO». */
@Immutable
data class GoalModel(val label: String, val status: String, val description: String, val exceeded: Boolean)

/** «47 MIN» + «DE PANTALLA · 4 COMPARTIDOS». */
@Immutable
data class ScreenTimeModel(val value: String, val rest: String, val description: String)

@Immutable
data class PlatformRow(val platform: Platform, val name: String, val count: Int, val value: String)

@Immutable
data class PlatformsModel(val rows: List<PlatformRow>, val description: String)

/** Línea 0–24 h: 24 valores y sus tooltips; [currentHour] solo para hoy. */
@Immutable
data class HoursModel(
    val values: List<Float>,
    val tooltips: List<String>,
    val currentHour: Int?,
    val peak: String?,
    val description: String,
)

/** Fila de [RankBars] (categorías). */
@Immutable
data class RankRow(val key: String, val label: String, val value: Float, val valueLabel: String)

/** Fila del ranking de creadores. */
@Immutable
data class CreatorRow(val key: String, val handle: String, val platform: Platform, val value: String)

// ====================================================================== mapeo (puro)

/** Convierte las estadísticas en los modelos de arriba. Sin Android: se prueba en la JVM. */
object DayParts {
    /** Categorías que se enseñan en un día. */
    const val CATEGORY_ROWS = 6

    /** Objetivo de un día: para hoy «QUEDAN 38»; para un día cerrado «38 POR DEBAJO». */
    fun goal(day: DayStats): GoalModel? = day.goal?.let { g ->
        if (day.isToday) {
            GoalModel(
                label = TodayCopy.goalLabel(g.goal),
                status = TodayCopy.goalStatus(g.count, g.goal),
                description = TodayCopy.goalStatusDescription(g.count, g.goal),
                exceeded = g.isExceeded,
            )
        } else {
            GoalModel(
                label = TodayCopy.goalLabel(g.goal),
                status = DayCopy.goalStatus(g.count, g.goal),
                description = DayCopy.goalStatusDescription(g.count, g.goal),
                exceeded = g.isExceeded,
            )
        }
    }

    fun screenTime(totals: Totals): ScreenTimeModel = ScreenTimeModel(
        value = TodayCopy.screenTimeValue(totals.totalMs),
        rest = TodayCopy.screenTimeRest(totals.shares),
        description = TodayCopy.screenTimeDescription(totals.totalMs, totals.shares),
    )

    /** Comparación con la media de ese día de la semana; hoy, hasta la misma hora. */
    fun comparison(day: DayStats): DeltaLine {
        val c = day.comparison
        val average = c?.averageCount?.takeIf { c.sampleDays > 0 }
        val weekday = c?.weekday ?: day.date.dayOfWeek
        return if (day.isToday) {
            TodayCopy.comparison((c?.count ?: day.totals.count).toDouble(), average, weekday, untilNow = true)
        } else {
            DayCopy.comparison(day.totals.count, average, weekday)
        }
    }

    /** `null` si no hay plataformas con vídeos. */
    fun platforms(slices: List<PlatformSlice>, value: (PlatformSlice) -> String): PlatformsModel? {
        if (slices.isEmpty()) return null
        val rows = slices.map { PlatformRow(it.platform, it.platform.shortName, it.count, value(it)) }
        return PlatformsModel(rows, TodayCopy.platformsDescription(rows.map { it.name to it.count }))
    }

    fun hours(day: DayStats): HoursModel {
        val byHour = day.hours.associateBy { it.hour }
        val counts = List(24) { h -> byHour[h]?.count ?: 0 }
        val peak = day.peakHour?.takeIf { it.count > 0 }
        return HoursModel(
            values = counts.map { it.toFloat() },
            tooltips = counts.mapIndexed { h, n -> TodayCopy.hourTooltip(h, n) },
            currentHour = day.untilMinute?.let { it / 60 },
            peak = peak?.let { TodayCopy.peakHour(it.hour, it.count) },
            description = TodayCopy.hoursDescription(peak?.hour, peak?.count ?: 0),
        )
    }

    fun categories(slices: List<CategorySlice>, limit: Int, value: (CategorySlice) -> String): List<RankRow> =
        slices.take(limit).map { RankRow(it.category.name, it.category.label, it.count.toFloat(), value(it)) }

    fun creators(ranks: List<CreatorRank>, limit: Int, value: (CreatorRank) -> String): List<CreatorRow> =
        ranks.take(limit).map {
            CreatorRow("${it.platform.name}:${it.creator}", Words.handle(it.creator), it.platform, value(it))
        }
}

// ====================================================================== bloques

/**
 * El marcador de un día. Las fichas llevan el elemento compartido [SharedKeys.dayCounter]: la
 * misma cifra viaja de Hoy a su detalle. Con [onOpen], se hunden al pulsar y abren el día.
 */
@Composable
fun CounterHero(model: HeroModel, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null, tour: Boolean = false) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth()) {
        Label(
            model.label,
            color = if (model.alert) colors.signalText else colors.textMuted,
            // «VÍDEOS» ya lo dice el marcador («212 vídeos»): TalkBack no lo repite. Un aviso sí se lee.
            modifier = if (model.alert) Modifier else Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.height(Space.x2))
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) {
                SplitFlapCounter(
                    value = model.count,
                    modifier = Modifier
                        .then(if (tour) Modifier.tourTarget(TourTarget.COUNTER) else Modifier)
                        .reelSharedElement(SharedKeys.dayCounter(model.epochDay))
                        .then(if (onOpen != null) Modifier.pressable(onClick = onOpen, onClickLabel = A11y.OPEN_DAY) else Modifier),
                    contentDescription = model.description,
                )
            }
            if (model.goal != null) {
                Spacer(Modifier.width(Space.x4))
                GoalColumn(model.goal)
            }
        }
        if (model.screenTime != null) {
            Spacer(Modifier.height(Space.x4))
            ScreenTimeLine(model.screenTime)
        }
        if (model.quote != null) {
            Spacer(Modifier.height(Space.x8))
            Text(model.quote, style = type.quote, color = colors.text)
        }
        if (model.comparison != null) {
            Spacer(Modifier.height(Space.x4))
            DeltaText(model.comparison, if (tour) Modifier.tourTarget(TourTarget.COMPARISON) else Modifier)
        }
    }
}

@Composable
private fun GoalColumn(goal: GoalModel) {
    val colors = ReelTheme.colors
    Column(
        Modifier.clearAndSetSemantics { contentDescription = goal.description },
        horizontalAlignment = Alignment.End,
    ) {
        Label(goal.label)
        Spacer(Modifier.height(Space.x1))
        Text(
            goal.status,
            style = ReelTheme.type.labelL,
            color = if (goal.exceeded) colors.signalText else colors.text,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ScreenTimeLine(model: ScreenTimeModel) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    FlowRow(
        Modifier.clearAndSetSemantics { contentDescription = model.description },
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Text(model.value, style = type.dataL, color = colors.text, modifier = Modifier.alignByBaseline())
        Text(model.rest, style = type.data, color = colors.textMuted, modifier = Modifier.alignByBaseline())
    }
}

/** Línea de comparación con flecha; más vídeos = `signalText`, menos = `text` (docs/DESIGN.md §1). */
@Composable
fun DeltaText(line: DeltaLine, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val arrow = TodayCopy.arrow(line.direction)
    Text(
        text = if (arrow != null) "$arrow ${line.text}" else line.text,
        style = ReelTheme.type.data,
        color = when (line.direction) {
            Direction.UP -> colors.signalText
            Direction.DOWN -> colors.text
            Direction.SAME, Direction.NONE -> colors.textMuted
        },
        modifier = modifier.semantics { contentDescription = line.description },
    )
}

@Composable
fun PlatformsBlock(index: Int, title: String, model: PlatformsModel, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, title, trailing = model.rows.size.toString())
        Spacer(Modifier.height(Space.x4))
        val segments = remember(model.rows, colors) { model.rows.map { colors.platform(it.platform) to it.count.toFloat() } }
        PlatformSplitBar(segments, contentDescription = model.description)
        Spacer(Modifier.height(Space.x3))
        model.rows.forEach { row ->
            key(row.platform) { ReceiptLine(row.name, row.value, mark = colors.platform(row.platform)) }
        }
    }
}

@Composable
fun HoursBlock(index: Int, title: String, model: HoursModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, title)
        Spacer(Modifier.height(Space.x8))
        val tooltip = remember(model.tooltips) { { hour: Int -> model.tooltips.getOrElse(hour) { "" } } }
        HourLine(
            values = model.values,
            tooltip = tooltip,
            currentHour = model.currentHour,
            contentDescription = model.description,
        )
        if (model.peak != null) {
            Spacer(Modifier.height(Space.x3))
            Label(model.peak)
        }
    }
}

@Composable
fun CategoriesBlock(index: Int, title: String, rows: List<RankRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, title, trailing = rows.size.toString())
        Spacer(Modifier.height(Space.x4))
        val items = remember(rows) { rows.map { RankItem(it.label, it.value, it.valueLabel) } }
        RankBars(items)
    }
}

@Composable
fun CreatorsBlock(index: Int, title: String, rows: List<CreatorRow>, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    Column(modifier.fillMaxWidth()) {
        SectionHeader(index, title, trailing = rows.size.toString())
        Spacer(Modifier.height(Space.x2))
        rows.forEach { row ->
            key(row.key) { ReceiptLine(row.handle, row.value, mark = colors.platform(row.platform)) }
        }
    }
}
