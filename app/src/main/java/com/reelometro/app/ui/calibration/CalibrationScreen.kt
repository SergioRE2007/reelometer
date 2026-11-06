package com.reelometro.app.ui.calibration

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelometro.app.copy.A11y
import com.reelometro.app.copy.CalibrationCopy
import com.reelometro.app.copy.Labels
import com.reelometro.app.data.Platform
import com.reelometro.app.detection.CalibrationEventType
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.ReelSwitch
import com.reelometro.app.ui.components.ReelTopBar
import com.reelometro.app.ui.components.SectionHeader
import com.reelometro.app.ui.components.flap.EmptyState
import com.reelometro.app.ui.components.flap.FlapLoader
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.onboarding.StepList
import com.reelometro.app.ui.settings.StatusBarScrim
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/** Grosor del filete de los vídeos detectados y del subrayado del filtro activo. */
private val AccentRule = 2.dp

/** Sangría de las filas: deja sitio al filete de los vídeos sin desalinear las demás. */
private val RowIndent = AccentRule + Space.x3

@Composable
fun CalibrationScreen(
    state: CalibrationUiState,
    onBack: () -> Unit,
    onCapture: (Boolean) -> Unit,
    onPlatform: (Platform?) -> Unit,
    onType: (TypeFilter) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(ReelTheme.colors.bg),
    ) {
        when (state) {
            CalibrationUiState.Loading -> Column(Modifier.fillMaxSize()) {
                ReelTopBar(title = CalibrationCopy.HEADLINE, onBack = onBack, backDescription = A11y.BACK, titleStyle = ReelTheme.type.headline)
                FlapLoader(
                    style = ReelTheme.type.displayL,
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.x6),
                )
            }
            is CalibrationUiState.Ready -> Console(state.calibration, onBack, onCapture, onPlatform, onType, onCopy, onShare, onClear)
        }
        StatusBarScrim()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Console(
    s: CalibrationState,
    onBack: () -> Unit,
    onCapture: (Boolean) -> Unit,
    onPlatform: (Platform?) -> Unit,
    onType: (TypeFilter) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Space.x12

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "top") {
            ReelTopBar(title = CalibrationCopy.HEADLINE, onBack = onBack, backDescription = A11y.BACK, titleStyle = type.headline)
        }
        item(key = "status") {
            Column(Modifier.padding(horizontal = Space.screen)) {
                Text(CalibrationCopy.INTRO, style = type.bodySmall, color = colors.textMuted)
                Spacer(Modifier.height(Space.x5))
                ConsoleLine(
                    key = CalibrationCopy.SERVICE,
                    value = when (s.service) {
                        ServiceLine.CONNECTED -> Labels.CONNECTED
                        ServiceLine.WAITING -> CalibrationCopy.SERVICE_WAITING
                        ServiceLine.OFF -> Labels.DISCONNECTED
                    },
                    valueColor = if (s.service == ServiceLine.CONNECTED) colors.text else colors.signalText,
                )
                ConsoleLine(CalibrationCopy.LAST_EVENT, s.lastEvent)
                ConsoleLine(CalibrationCopy.LAST_VIDEO, s.lastVideo)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Space.touch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(CalibrationCopy.CAPTURE, color = colors.text, modifier = Modifier.weight(1f))
                    ReelSwitch(
                        checked = s.capturing,
                        onCheckedChange = onCapture,
                        contentDescription = CalibrationCopy.CAPTURE_DESCRIPTION,
                    )
                }
            }
        }
        item(key = "filters") {
            Column(Modifier.padding(horizontal = Space.screen).padding(top = Space.x5)) {
                Label(CalibrationCopy.FILTER_PLATFORM)
                FilterRow(
                    options = listOf<Platform?>(null) + Platform.entries,
                    selected = s.platform,
                    label = { it?.shortName ?: CalibrationCopy.ALL_PLATFORMS },
                    onSelect = onPlatform,
                )
                Spacer(Modifier.height(Space.x3))
                Label(CalibrationCopy.FILTER_TYPE)
                FilterRow(
                    options = TypeFilter.entries,
                    selected = s.type,
                    label = { it.label },
                    onSelect = onType,
                )
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = Space.screen).padding(top = Space.x5)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                    verticalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    ReelButton(
                        CalibrationCopy.COPY,
                        onClick = onCopy,
                        style = ReelButtonStyle.Secondary,
                        icon = ReelIcons.Copy,
                        contentDescription = CalibrationCopy.COPY_DESCRIPTION,
                    )
                    ReelButton(
                        CalibrationCopy.SHARE_LOG,
                        onClick = onShare,
                        style = ReelButtonStyle.Secondary,
                        icon = ReelIcons.Share,
                        contentDescription = CalibrationCopy.SHARE_DESCRIPTION,
                    )
                    ReelButton(
                        CalibrationCopy.CLEAR,
                        onClick = onClear,
                        style = ReelButtonStyle.Secondary,
                        icon = ReelIcons.Trash,
                        contentDescription = CalibrationCopy.CLEAR_DESCRIPTION,
                    )
                }
                if (s.message != null) {
                    Text(
                        s.message,
                        style = type.dataS,
                        color = colors.textMuted,
                        modifier = Modifier
                            .padding(top = Space.x3)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        item(key = "live") {
            SectionHeader(
                index = null,
                title = CalibrationCopy.LIVE,
                trailing = CalibrationCopy.events(s.eventCount),
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .padding(top = Space.x10, bottom = Space.x2),
            )
        }
        when {
            !s.hasAnything -> item(key = "empty") { EmptyConsole(s.capturing) }
            s.rows.isEmpty() -> item(key = "filtered") {
                Text(
                    CalibrationCopy.FILTER_EMPTY,
                    style = type.body,
                    color = colors.textMuted,
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.x4),
                )
            }
            else -> items(s.rows, key = { it.key }, contentType = { if (it.video) 1 else 0 }) { row -> RowView(row) }
        }
    }
}

/** `CLAVE ············ valor` en mono. */
@Composable
private fun ConsoleLine(key: String, value: String, valueColor: Color = ReelTheme.colors.text) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x1)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(key, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.x3))
        Text(value, style = ReelTheme.type.data, color = valueColor, maxLines = 1)
    }
}

/** Opciones de un filtro en mono; la activa en tinta con un subrayado `signal`. Se reparten en varias líneas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.x5),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val ink by animateColorAsState(
                if (isSelected) colors.text else colors.textMuted,
                Motion.orSnap(reduced, Motion.standard()),
                label = "filterInk",
            )
            val rule by animateColorAsState(
                if (isSelected) colors.signal else colors.bg,
                Motion.orSnap(reduced, Motion.standard()),
                label = "filterRule",
            )
            Box(
                Modifier
                    .pressable(onClick = { onSelect(option) }, role = Role.RadioButton)
                    .semantics { this.selected = isSelected }
                    .heightIn(min = Space.touch),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Format.upper(label(option)),
                    style = ReelTheme.type.labelL,
                    color = ink,
                    maxLines = 1,
                    modifier = Modifier.drawBehind {
                        val h = AccentRule.toPx()
                        drawRect(rule, topLeft = Offset(0f, size.height + h), size = Size(size.width, h))
                    },
                )
            }
        }
    }
}

@Composable
private fun RowView(row: CalibrationRow) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    val signal = colors.signal
    val line = colors.line
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .drawBehind {
                val hair = Space.hairline.toPx()
                drawRect(line, topLeft = Offset(0f, size.height - hair), size = Size(size.width, hair))
                if (row.video) drawRect(signal, size = Size(AccentRule.toPx(), size.height - hair))
            }
            .padding(start = RowIndent, top = Space.x2, bottom = Space.x2)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.timeText, style = type.dataS, color = colors.textMuted)
            Spacer(Modifier.width(Space.x3))
            Text(row.title, style = type.dataS, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1)
            val mark = when {
                row.decision == null -> null
                row.type == CalibrationEventType.SCROLL -> if (row.decision) CalibrationCopy.FEED_YES else CalibrationCopy.FEED_NO
                else -> if (row.decision) CalibrationCopy.MARK_YES else CalibrationCopy.MARK_NO
            }
            if (mark != null) {
                Spacer(Modifier.width(Space.x2))
                Text(mark, style = type.dataS, color = if (row.decision == true) colors.signalText else colors.textMuted)
            }
        }
        if (row.rule != null) {
            Text(CalibrationCopy.rule(row.rule), style = type.dataS, color = colors.textMuted)
        }
        if (row.details.isNotEmpty()) {
            Text(row.details, style = type.dataS, color = colors.textMuted)
        }
    }
}

/** Sin eventos (en el emulador no hay TikTok): marcador en blanco y cómo se usa en un móvil de verdad. */
@Composable
private fun EmptyConsole(capturing: Boolean) {
    Column(Modifier.padding(horizontal = Space.screen).padding(top = Space.x4)) {
        EmptyState(
            title = if (capturing) CalibrationCopy.EMPTY else CalibrationCopy.PAUSED,
            value = null,
            counterStyle = ReelTheme.type.displayL,
        )
        Spacer(Modifier.height(Space.x8))
        Label(CalibrationCopy.HOW_TO_TITLE, color = ReelTheme.colors.text)
        Spacer(Modifier.height(Space.x4))
        StepList(CalibrationCopy.HOW_TO)
    }
}
