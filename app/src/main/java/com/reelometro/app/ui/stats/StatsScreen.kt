package com.reelometro.app.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.StatsCopy
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.PeriodNavigator
import com.reelometro.app.ui.components.ReceiptLine
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelIconButton
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.SegmentedTabs
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.charts.CalendarHeatmap
import com.reelometro.app.ui.components.charts.DailyBars
import com.reelometro.app.ui.components.charts.GhostWeekChart
import com.reelometro.app.ui.components.charts.HeatDay
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.nav.LocalBottomBarHeight
import com.reelometro.app.ui.nav.SharedKeys
import com.reelometro.app.ui.nav.reelSharedBounds
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.today.CategoriesBlock
import com.reelometro.app.ui.today.CreatorsBlock
import com.reelometro.app.ui.today.DeltaText
import com.reelometro.app.ui.today.PlatformsBlock
import com.reelometro.app.ui.tour.TourTarget
import com.reelometro.app.ui.tour.tourTarget

/**
 * **Estadísticas** (docs/DESIGN.md §9): titular y Ajustes, pestañas de periodo, navegador, la cifra
 * del periodo (sale por arriba o por abajo según hacia dónde navegas), su resumen y tendencia, y
 * las secciones 01–06. Sin lógica: todo llega formateado en [state].
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    onSelectTab: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onOpenDay: (epochDay: Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    val header = state.header
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            // El inset va antes del scroll: el contenido se recorta bajo la barra de estado, no pasa por detrás.
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        ReelTopBar(
            title = StatsCopy.TITLE,
            windowInsets = WindowInsets(0),
            actions = { ReelIconButton(ReelIcons.Settings, contentDescription = A11y.SETTINGS, onClick = onOpenSettings) },
        )
        Column(Modifier.padding(horizontal = Space.screen)) {
            SegmentedTabs(options = header.tabs, selectedIndex = header.tabIndex, onSelect = onSelectTab)
        }
        Spacer(Modifier.height(Space.x2))
        PeriodNavigator(
            label = header.navigatorLabel,
            onPrevious = onPrevious,
            onNext = onNext,
            canGoPrevious = header.canGoPrevious,
            canGoNext = header.canGoNext,
            previousDescription = A11y.PREVIOUS_PERIOD,
            nextDescription = A11y.NEXT_PERIOD,
            modifier = Modifier.padding(horizontal = Space.x2),
        )
        Spacer(Modifier.height(Space.x6))

        // La cifra va siempre en el mismo sitio para que AnimatedContent anime también entre vacío y contenido.
        val summary = when (state) {
            is StatsUiState.Empty -> state.summary
            is StatsUiState.Content -> state.summary
            is StatsUiState.Loading, is StatsUiState.Error -> null
        }
        if (summary != null) SummaryBlock(summary, header.direction)

        when (state) {
            is StatsUiState.Loading -> FlapLoader(
                modifier = Modifier.padding(horizontal = Space.screen),
                style = ReelTheme.type.displayXL,
                contentDescription = A11y.LOADING,
            )
            is StatsUiState.Error -> ErrorState(
                message = state.message,
                modifier = Modifier.padding(horizontal = Space.screen),
                action = { ReelButton(Buttons.RETRY, onClick = onRetry) },
            )
            is StatsUiState.Empty -> {
                Spacer(Modifier.height(Space.x8))
                Text(
                    state.message,
                    style = ReelTheme.type.quoteSmall,
                    color = colors.text,
                    modifier = Modifier.padding(horizontal = Space.screen),
                )
            }
            is StatsUiState.Content -> {
                Spacer(Modifier.height(Space.x10))
                TicketDivider(style = TicketDividerStyle.Perforated)
                Spacer(Modifier.height(Space.x10))
                SectionsBlock(state.sections, onOpenDay, onOpenSettings)
            }
        }
        Spacer(Modifier.height(Space.section))
        Spacer(Modifier.height(LocalBottomBarHeight.current))
    }
}

/** Cifra del periodo + resumen + tendencia. Hacia periodos más recientes la cifra sale por arriba. */
@Composable
private fun SummaryBlock(summary: PeriodSummary, direction: Int) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val reduced = ReelTheme.reducedMotion
    val dir by rememberUpdatedState(direction)
    AnimatedContent(
        targetState = summary,
        contentKey = { it.key },
        transitionSpec = {
            if (reduced) {
                fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard())
            } else {
                val d = if (dir < 0) -1 else 1
                (slideInVertically(Motion.standard()) { h -> d * h / 2 } + fadeIn(Motion.standard())) togetherWith
                    (slideOutVertically(Motion.standard()) { h -> -d * h / 2 } + fadeOut(Motion.standard()))
            }.using(SizeTransform(clip = true))
        },
        label = "period-summary",
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen),
    ) { s ->
        Column(Modifier.fillMaxWidth()) {
            BasicText(
                text = s.count,
                style = type.displayXL.copy(color = colors.text),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = type.displayM.fontSize, maxFontSize = type.displayXL.fontSize),
                modifier = Modifier.semantics { contentDescription = s.description },
            )
            Spacer(Modifier.height(Space.x3))
            Text(
                s.line,
                style = type.labelL,
                color = colors.text,
                modifier = s.lineDescription?.let { spoken -> Modifier.semantics { contentDescription = spoken } } ?: Modifier,
            )
            if (s.trend != null) {
                Spacer(Modifier.height(Space.x3))
                DeltaText(s.trend)
            }
        }
    }
}

/** Gráfica desde la que se abrió el último día (para el elemento compartido de vuelta). */
private const val ORIGIN_NONE = 0
private const val ORIGIN_BARS = 1
private const val ORIGIN_CALENDAR = 2

@Composable
private fun SectionsBlock(sections: StatsSections, onOpenDay: (Long) -> Unit, onOpenSettings: () -> Unit) {
    // Solo la gráfica desde la que se abrió el último día pone su caja compartida: dos cajas con la
    // misma clave a la vez (la barra y la celda del mismo día) estropearían la transición.
    val origin = rememberSaveable { mutableIntStateOf(ORIGIN_NONE) }
    Column(
        Modifier.padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        var index = 0
        DailyBlock(
            index = ++index,
            model = sections.daily,
            shared = origin.intValue == ORIGIN_BARS,
            onOpenDay = { origin.intValue = ORIGIN_BARS; onOpenDay(it) },
        )
        if (sections.week != null) WeekBlock(++index, sections.week)
        CalendarSection(
            index = ++index,
            model = sections.calendar,
            shared = origin.intValue == ORIGIN_CALENDAR,
            onOpenDay = { origin.intValue = ORIGIN_CALENDAR; onOpenDay(it) },
        )
        PatternsBlock(++index, sections.patterns, onOpenDay, onOpenSettings)
        if (sections.platforms != null) PlatformsBlock(++index, StatsCopy.SECTION_PLATFORMS, sections.platforms)
        if (sections.categories.isNotEmpty()) CategoriesBlock(++index, StatsCopy.SECTION_CATEGORIES, sections.categories)
        if (sections.creators.isNotEmpty()) CreatorsBlock(++index, StatsCopy.SECTION_CREATORS, sections.creators)
        if (sections.recipients.isNotEmpty()) CreatorsBlock(++index, StatsCopy.SECTION_RECIPIENTS, sections.recipients)
    }
}

/**
 * 01 CADA DÍA. Tocar una barra abre su día: con [shared], la barra pulsada es el origen del
 * elemento compartido que crece hasta el marcador del Detalle del día. TalkBack ofrece «Abrir el…»
 * como acciones de la gráfica (semana y mes).
 */
@Composable
private fun DailyBlock(index: Int, model: DailyModel, shared: Boolean, onOpenDay: (Long) -> Unit) {
    val open by rememberUpdatedState(onOpenDay)
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(index, StatsCopy.SECTION_DAILY, trailing = model.trailing)
        Spacer(Modifier.height(Space.x10))
        val tooltip = remember(model) { { i: Int -> model.tooltips.getOrElse(i) { "" } } }
        val click = remember(model) { { i: Int -> model.days.getOrNull(i)?.let { open(it) }; Unit } }
        DailyBars(
            modifier = Modifier.tourTarget(TourTarget.STATS_BARS),
            values = model.values,
            labels = model.labels,
            tooltip = tooltip,
            highlightIndex = model.highlightIndex,
            average = model.average,
            onBarClick = click,
            contentDescription = model.description,
            barActions = model.actionLabels,
            sharedBar = if (shared) {
                { i -> model.days.getOrNull(i)?.let { Modifier.reelSharedBounds(SharedKeys.dayOpen(it)) } ?: Modifier }
            } else {
                null
            },
        )
    }
}

/**
 * «Contra la semana anterior» (solo en SEMANA): la semana día a día con la línea fantasma de la
 * anterior y, debajo, los totales como líneas de ticket.
 */
@Composable
private fun WeekBlock(index: Int, model: WeekCompareModel) {
    val colors = ReelTheme.colors
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(index, StatsCopy.SECTION_WEEK_VS)
        Spacer(Modifier.height(Space.x10))
        GhostWeekChart(
            bars = model.bars,
            todayIndex = model.todayIndex,
            contentDescription = model.description,
            ghostLabel = model.legend,
        )
        Spacer(Modifier.height(Space.x4))
        model.lines.forEach { line ->
            key(line.key) {
                ReceiptLine(
                    label = line.label,
                    value = line.value,
                    valueColor = if (line.alert) colors.signalText else colors.text,
                )
            }
        }
    }
}

@Composable
private fun CalendarSection(index: Int, model: CalendarModel, shared: Boolean, onOpenDay: (Long) -> Unit) {
    val open by rememberUpdatedState(onOpenDay)
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(index, StatsCopy.SECTION_CALENDAR)
        Spacer(Modifier.height(Space.x6))
        val tooltip = remember(model) { { d: HeatDay -> model.tooltips[d.date] ?: "" } }
        val click = remember(model) { { date: java.time.LocalDate -> if (date in model.openable) open(date.toEpochDay()) } }
        model.blocks.forEachIndexed { i, block ->
            key(block.key) {
                if (i > 0) Spacer(Modifier.height(Space.x8))
                if (block.label != null) {
                    // El año de cada bloque («siempre») es un subencabezado para TalkBack.
                    Label(block.label, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Space.x3))
                }
                CalendarHeatmap(
                    days = block.days,
                    mode = model.mode,
                    tooltip = tooltip,
                    today = model.today,
                    onDayClick = click,
                    contentDescription = model.description,
                    sharedDay = if (shared) {
                        { date -> if (date in model.openable) Modifier.reelSharedBounds(SharedKeys.dayOpen(date.toEpochDay())) else Modifier }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun PatternsBlock(index: Int, model: PatternsModel, onOpenDay: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(index, StatsCopy.SECTION_PATTERNS)
        Spacer(Modifier.height(Space.x2))
        model.lines.forEach { line ->
            key(line.key) {
                val day = line.epochDay
                ReceiptLine(
                    label = line.label,
                    value = line.value,
                    valueColor = if (line.alert) colors.signalText else colors.text,
                    onClick = if (day != null) ({ onOpenDay(day) }) else null,
                    onClickLabel = if (day != null) StatsCopy.OPEN_WORST_DAY else null,
                )
            }
        }
        if (model.noGoal != null) {
            ReceiptLine(
                label = StatsCopy.LINE_STREAK_NO_GOAL,
                value = Buttons.SET_GOAL,
                valueColor = colors.signalText,
                onClick = onOpenSettings,
                onClickLabel = StatsCopy.SET_GOAL_ACTION,
            )
            Text(model.noGoal, style = type.bodySmall, color = colors.textMuted)
        }
        if (model.quote != null) {
            Spacer(Modifier.height(Space.x8))
            Text(model.quote, style = type.quoteSmall, color = colors.text)
        }
    }
}
