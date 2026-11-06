package com.reelometro.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reelometro.app.copy.Step
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.components.pressable
import com.reelometro.app.ui.theme.Motion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Shapes
import com.reelometro.app.ui.theme.Space
import java.util.Locale

/** Grosor del filete de acento de los avisos integrados (el mismo que `Notice`). */
private val RuleWidth = 2.dp

/**
 * Esqueleto de un paso a pantalla completa: contenido con scroll que, si cabe, ocupa todo el alto
 * (los `Spacer(Modifier.weight(…))` del contenido reparten el aire) y un pie fijo con los botones,
 * por encima de la barra de navegación.
 */
@Composable
internal fun StepScaffold(
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val line = ReelTheme.colors.line
    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .heightIn(min = viewport)
                    .padding(horizontal = Space.screen, vertical = Space.x6),
                content = content,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                // Filete sobre el pie mientras queda texto por debajo (el corte no siempre se nota).
                .drawBehind {
                    if (scroll.canScrollForward) drawRect(line, size = Size(size.width, Space.hairline.toPx()))
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = Space.screen, end = Space.screen, top = Space.x3, bottom = Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            footer?.invoke(this)
        }
    }
}

/** Pasos numerados «01  Pulsa…» (cifra en mono y acento, texto corrido). */
@Composable
internal fun StepList(steps: List<Step>, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
        steps.forEach { step ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(
                    text = String.format(Locale.ROOT, "%02d", step.number),
                    style = ReelTheme.type.data,
                    color = colors.signalText,
                )
                Spacer(Modifier.width(Space.x4))
                Text(step.text, style = ReelTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Aviso integrado (como `Notice`) pero con la acción en botón secundario, para no competir con el
 * botón principal del pie: filete `signal` a la izquierda, etiqueta opcional, texto y acción.
 */
@Composable
internal fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = ReelTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(colors.signal, size = Size(RuleWidth.toPx(), size.height)) }
            .padding(start = RuleWidth + Space.x4),
    ) {
        if (title != null) {
            Text(title, style = ReelTheme.type.title, color = colors.text)
            Spacer(Modifier.height(Space.x2))
        }
        Text(text, style = ReelTheme.type.body, color = if (title != null) colors.textMuted else colors.text)
        if (content != null) {
            Spacer(Modifier.height(Space.x4))
            content()
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.x4))
            ReelButton(text = actionLabel, onClick = onAction, style = ReelButtonStyle.Secondary, icon = actionIcon)
        }
    }
}

/**
 * Opción de una elección única (objetivo diario): caja de 2 dp con filete; la elegida, rellena de
 * `signal`. Se hunde al pulsar y vibra. Para TalkBack es un botón de opción.
 */
@Composable
internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    val reduced = ReelTheme.reducedMotion
    val fill by animateColorAsState(
        if (selected) colors.signal else colors.bg,
        Motion.orSnap(reduced, Motion.standard()),
        label = "chipFill",
    )
    val ink by animateColorAsState(
        if (selected) colors.onSignal else colors.text,
        Motion.orSnap(reduced, Motion.standard()),
        label = "chipInk",
    )
    Row(
        modifier
            .pressable(onClick = onClick, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .heightIn(min = Space.touch)
            .background(fill, Shapes.button)
            .border(Space.hairline, if (selected) colors.signal else colors.lineStrong, Shapes.button)
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Format.upper(text),
            style = ReelTheme.type.button,
            color = ink,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Paso del selector numérico del objetivo: fino abajo, grueso arriba (5 · 10 · 50), siempre
 * múltiplo de 5. Así se llega de 150 a 2.000 sin estar un minuto pulsando.
 */
internal fun goalStep(value: Int): Int = when {
    value < 100 -> 5
    value < 500 -> 10
    else -> 50
}
