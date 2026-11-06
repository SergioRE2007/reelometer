package com.reelometro.app.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.TodayCopy
import com.reelometro.app.ui.components.Band
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.nav.SharedKeys
import com.reelometro.app.ui.nav.reelSharedBounds
import com.reelometro.app.ui.nav.reelSharedElement
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.today.CategoriesBlock
import com.reelometro.app.ui.today.CounterHero
import com.reelometro.app.ui.today.CreatorsBlock
import com.reelometro.app.ui.today.HoursBlock
import com.reelometro.app.ui.today.PlatformsBlock

/**
 * **Detalle del día**: volver, fecha (la etiqueta y el marcador vienen de Hoy como elementos
 * compartidos), el marcador del día, su frase y comparación, un corte de ticket, las secciones y,
 * al final, la banda con el puesto del día entre todos los contados.
 *
 * Desde una barra o una celda de Estadísticas, el marcador entero es un contenedor compartido
 * ([SharedKeys.dayOpen]): crece desde la barra o la celda pulsada (y vuelve a ella al salir).
 */
@Composable
fun DayScreen(state: DayUiState, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            // El inset va antes del scroll: el contenido se recorta bajo la barra de estado, no pasa por detrás.
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()),
    ) {
        ReelTopBar(onBack = onBack, backDescription = A11y.BACK, windowInsets = WindowInsets(0))
        TitleBlock(state.top)
        Column(Modifier.padding(horizontal = Space.screen)) {
            Spacer(Modifier.height(Space.x8))
            // Un único marcador para cargando, vacío y contenido: el elemento compartido que llega
            // (la cifra desde Hoy, el marcador entero desde Estadísticas) no se rehace con los datos.
            val hero = when (state) {
                is DayUiState.Loading -> state.hero
                is DayUiState.Empty -> state.hero
                is DayUiState.Content -> state.hero
                is DayUiState.Error -> null
            }
            if (hero != null) {
                CounterHero(hero, Modifier.reelSharedBounds(SharedKeys.dayOpen(state.top.epochDay)))
            } else if (state is DayUiState.Error) {
                ErrorState(
                    message = state.message,
                    action = { ReelButton(Buttons.RETRY, onClick = onRetry) },
                )
            }
        }
        if (state is DayUiState.Content) {
            Spacer(Modifier.height(Space.x10))
            TicketDivider(style = TicketDividerStyle.Perforated)
            Spacer(Modifier.height(Space.x10))
            SectionsBlock(state.sections)
            if (state.rank != null) {
                Spacer(Modifier.height(Space.section))
                Band(text = state.rank)
            }
        }
        Spacer(Modifier.height(Space.section))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun TitleBlock(top: DayTop) {
    val colors = ReelTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.screen, top = Space.x1)) {
        Label(
            top.label,
            color = colors.text,
            large = true,
            modifier = Modifier.reelSharedElement(SharedKeys.dayLabel(top.epochDay)),
        )
        Spacer(Modifier.height(Space.x2))
        Text(
            top.title,
            style = ReelTheme.type.headline,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun SectionsBlock(sections: DaySections) {
    Column(
        Modifier.padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        var index = 0
        HoursBlock(++index, sections.hoursTitle, sections.hours)
        if (sections.platforms != null) PlatformsBlock(++index, TodayCopy.SECTION_PLATFORMS, sections.platforms)
        if (sections.categories.isNotEmpty()) CategoriesBlock(++index, TodayCopy.SECTION_CATEGORIES, sections.categories)
        if (sections.creators.isNotEmpty()) CreatorsBlock(++index, TodayCopy.SECTION_CREATORS, sections.creators)
    }
}
