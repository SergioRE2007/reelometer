package com.reelometro.app.ui.today

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.data.settings.DiscoveryFeature
import com.reelometro.app.ui.components.Band
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.Notice
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelIconButton
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.nav.LocalBottomBarHeight
import com.reelometro.app.ui.nav.SharedKeys
import com.reelometro.app.ui.nav.reelSharedElement
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.tour.TourTarget
import com.reelometro.app.ui.tour.tourTarget

/**
 * Pantalla **Hoy** (docs/DESIGN.md §9): cabecera con la fecha y Ajustes, el marcador gigante
 * con su columna de objetivo, el tiempo, la frase y la comparación; debajo, un corte de ticket y
 * las secciones del día; al final, la banda a sangre con el total desde el primer registro.
 * Sin lógica: todo llega formateado en [state].
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onOpenDay: (epochDay: Long) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnectService: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onNewsAction: (DiscoveryFeature) -> Unit = {},
    onNewsDismiss: (DiscoveryFeature) -> Unit = {},
    onRevealAll: () -> Unit = {},
) {
    val colors = ReelTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            // El inset va antes del scroll: el contenido se recorta bajo la barra de estado, no pasa por detrás.
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        TodayHeaderBar(state.header, onOpenSettings)
        when (state) {
            is TodayUiState.Loading -> LoadingBlock()
            is TodayUiState.Error -> {
                Spacer(Modifier.height(Space.x8))
                ErrorState(
                    message = state.message,
                    modifier = Modifier.padding(horizontal = Space.screen),
                    action = { ReelButton(Buttons.RETRY, onClick = onRetry) },
                )
            }
            is TodayUiState.Empty -> {
                TopBlock(state.top, onOpenDay, onOpenSettings, onConnectService, onNewsAction, onNewsDismiss)
                BandBlock(state.top.band, onOpenStats)
                RevealBlock(state.top.revealHint, onRevealAll)
            }
            is TodayUiState.Content -> {
                TopBlock(state.top, onOpenDay, onOpenSettings, onConnectService, onNewsAction, onNewsDismiss)
                Spacer(Modifier.height(Space.x10))
                TicketDivider(style = TicketDividerStyle.Perforated)
                Spacer(Modifier.height(Space.x10))
                SectionsBlock(state.sections)
                BandBlock(state.top.band, onOpenStats)
                RevealBlock(state.top.revealHint, onRevealAll)
            }
        }
        Spacer(Modifier.height(Space.section))
        Spacer(Modifier.height(LocalBottomBarHeight.current))
    }
}

/** «HOY · MARTES 3 NOV» + Ajustes y filete. La fecha viaja (elemento compartido) al detalle del día. */
@Composable
private fun TodayHeaderBar(header: TodayHeader, onOpenSettings: () -> Unit) {
    val colors = ReelTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Space.x12 + Space.x2)
                .padding(start = Space.screen, end = Space.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Label(
                    header.text,
                    color = colors.text,
                    large = true,
                    modifier = Modifier
                        .reelSharedElement(SharedKeys.dayLabel(header.epochDay))
                        .semantics { heading() },
                )
            }
            ReelIconButton(
                ReelIcons.Settings,
                contentDescription = A11y.SETTINGS,
                onClick = onOpenSettings,
                modifier = Modifier.tourTarget(TourTarget.SETTINGS),
            )
        }
        Box(Modifier.fillMaxWidth().height(Space.hairline).background(colors.line))
    }
}

@Composable
private fun LoadingBlock() {
    Column(Modifier.padding(horizontal = Space.screen)) {
        Spacer(Modifier.height(Space.x8))
        Label(StateCopy.LOADING_LABEL)
        Spacer(Modifier.height(Space.x2))
        FlapLoader(contentDescription = StateCopy.LOADING_DESCRIPTION)
    }
}

@Composable
private fun TopBlock(
    top: TodayTop,
    onOpenDay: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onConnectService: () -> Unit,
    onNewsAction: (DiscoveryFeature) -> Unit,
    onNewsDismiss: (DiscoveryFeature) -> Unit,
) {
    Column(Modifier.padding(horizontal = Space.screen)) {
        Spacer(Modifier.height(Space.x8))
        CounterHero(
            model = top.hero,
            onOpen = if (top.canOpenDay) ({ onOpenDay(top.hero.epochDay) }) else null,
            tour = true,
        )
        if (top.notice != null) {
            Spacer(Modifier.height(Space.x8))
            Notice(
                text = top.notice.text,
                actionLabel = top.notice.action,
                onAction = when (top.notice.target) {
                    NoticeTarget.CONNECT_SERVICE -> onConnectService
                    NoticeTarget.SETTINGS -> onOpenSettings
                },
            )
        }
        // Excluyente con el aviso (lo decide el mapper): el de «sin señal» siempre manda.
        if (top.news != null) {
            Spacer(Modifier.height(Space.x8))
            NewsNotice(top.news, onAction = onNewsAction, onDismiss = onNewsDismiss)
        }
    }
}

@Composable
private fun RevealBlock(hint: TodayRevealHint?, onRevealAll: () -> Unit) {
    if (hint == null) return
    Spacer(Modifier.height(Space.section))
    RevealHint(hint, onRevealAll)
}

@Composable
private fun SectionsBlock(sections: TodaySections) {
    Column(
        Modifier.padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        var index = 0
        if (sections.platforms != null) PlatformsBlock(++index, TodayCopy.SECTION_PLATFORMS, sections.platforms)
        HoursBlock(++index, TodayCopy.SECTION_HOURS, sections.hours)
        if (sections.categories.isNotEmpty()) CategoriesBlock(++index, TodayCopy.SECTION_CATEGORIES, sections.categories)
        if (sections.creators.isNotEmpty()) CreatorsBlock(++index, TodayCopy.SECTION_CREATORS, sections.creators)
    }
}

@Composable
private fun BandBlock(band: BandModel?, onOpenStats: () -> Unit) {
    if (band == null) return
    Spacer(Modifier.height(Space.section))
    Band(
        text = band.text,
        kicker = band.kicker,
        onClick = onOpenStats,
        onClickLabel = TodayCopy.SINCE_BAND_ACTION,
    )
}
