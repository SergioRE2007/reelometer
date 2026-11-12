package com.reelometro.app.ui.wrapped

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelometro.app.ReelometroApp
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.Buttons
import com.reelometro.app.copy.Labels
import com.reelometro.app.copy.StateCopy
import com.reelometro.app.stats.PeriodRef
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelIconButton
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.TicketDivider
import com.reelometro.app.ui.components.TicketDividerStyle
import com.reelometro.app.ui.components.flap.EmptyState
import com.reelometro.app.ui.components.flap.ErrorState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.icons.ReelIcon
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.nav.LocalBottomBarHeight
import com.reelometro.app.ui.nav.SharedKeys
import com.reelometro.app.ui.nav.reelSharedBounds
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space
import com.reelometro.app.ui.tour.TourTarget
import com.reelometro.app.ui.tour.tourTarget

/** Pestaña Resumen: titular editorial y una tira de ticket por periodo que abre su historia. */
@Composable
fun WrappedHubRoute(onOpenStory: (kind: String, offset: Int) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val viewModel: WrappedHubViewModel = viewModel {
        val repository = ReelometroApp.from(context).repository
        WrappedHubViewModel(PeriodObserver(repository::observePeriod))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WrappedHubScreen(
        state = state,
        onOpenStory = { ref -> onOpenStory(ref.kind.name, ref.offset) },
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::retry,
    )
}

@Composable
fun WrappedHubScreen(
    state: WrappedHubUiState,
    onOpenStory: (PeriodRef) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg)) {
        ReelTopBar(
            kicker = Labels.TAB_WRAPPED,
            divider = true,
            actions = { ReelIconButton(ReelIcons.Settings, contentDescription = A11y.SETTINGS, onClick = onOpenSettings) },
        )
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard()) },
            label = "hub",
        ) { s ->
            when (s) {
                WrappedHubUiState.Loading -> HubMessage {
                    FlapLoader(style = ReelTheme.type.displayXL, contentDescription = StateCopy.LOADING_DESCRIPTION)
                    Spacer(Modifier.height(Space.x4))
                    Label(StateCopy.LOADING_LABEL)
                }
                is WrappedHubUiState.Empty -> HubMessage { EmptyState(title = s.message) }
                is WrappedHubUiState.Error -> HubMessage {
                    ErrorState(message = s.message, action = { ReelButton(Buttons.RETRY, onClick = onRetry) })
                }
                is WrappedHubUiState.Content -> HubContent(s, onOpenStory)
            }
        }
    }
}

@Composable
private fun HubMessage(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(top = Space.x8, bottom = Space.x8 + LocalBottomBarHeight.current),
    ) { content() }
}

@Composable
private fun HubContent(state: WrappedHubUiState.Content, onOpenStory: (PeriodRef) -> Unit) {
    val colors = ReelTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Space.x8 + LocalBottomBarHeight.current),
    ) {
        Spacer(Modifier.height(Space.x8))
        Text(
            text = state.headline,
            style = ReelTheme.type.displayM,
            color = colors.text,
            modifier = Modifier
                .padding(horizontal = Space.screen)
                .semantics { heading() },
        )
        Spacer(Modifier.height(Space.x10))
        state.strips.forEachIndexed { i, strip ->
            if (i > 0 || !strip.featured) TicketDivider(style = TicketDividerStyle.Perforated)
            if (i == 0) {
                Box(Modifier.tourTarget(TourTarget.WRAPPED)) { HubStripRow(strip, onOpen = { onOpenStory(strip.ref) }) }
            } else {
                HubStripRow(strip, onOpen = { onOpenStory(strip.ref) })
            }
        }
        TicketDivider(style = TicketDividerStyle.Perforated)
    }
}

/**
 * Tira de ticket a sangre: periodo y fechas arriba, la cifra grande y «VÍDEOS · 29 H» debajo.
 * La destacada va en `signal` con texto `onSignal`; las que no tienen datos, atenuadas y sin acción.
 */
@Composable
private fun HubStripRow(strip: HubStrip, onOpen: () -> Unit) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val fg = when {
        strip.featured -> colors.onSignal
        strip.hasData -> colors.text
        else -> colors.textFaint
    }
    val muted = when {
        strip.featured -> colors.onSignal
        strip.hasData -> colors.textMuted
        else -> colors.textFaint
    }
    val action = if (strip.hasData) {
        Modifier
            .reelSharedBounds(SharedKeys.story(strip.ref.kind.name, strip.ref.offset))
            .pressable(onClick = onOpen, onClickLabel = A11y.OPEN_SUMMARY)
    } else {
        Modifier
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(action)
            .background(if (strip.featured) colors.signal else Color.Transparent)
            .semantics(mergeDescendants = true) { contentDescription = strip.description }
            .padding(horizontal = Space.screen, vertical = if (strip.hasData) Space.x6 else Space.x4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(strip.title, color = fg, large = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.x4))
            Label(strip.range, color = muted)
        }
        if (strip.hasData) {
            Spacer(Modifier.height(Space.x3))
            Row(verticalAlignment = Alignment.Bottom) {
                // Se encoge antes que recortarse («123.456» destacado no cabe entero en 360 dp).
                val countStyle = if (strip.featured) type.displayXL else type.displayL
                BasicText(
                    text = strip.countText,
                    style = countStyle.copy(color = fg),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = type.displayM.fontSize, maxFontSize = countStyle.fontSize),
                    modifier = Modifier.weight(1f),
                )
                ReelIcon(ReelIcons.ArrowRight, contentDescription = null, tint = fg, modifier = Modifier.padding(bottom = Space.x2))
            }
            Spacer(Modifier.height(Space.x2))
        } else {
            Spacer(Modifier.height(Space.x1))
        }
        Label(strip.detail, color = muted)
    }
}
