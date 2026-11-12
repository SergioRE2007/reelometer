package com.reelometro.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.reelometro.app.data.settings.DiscoveryFeature
import com.reelometro.app.ui.components.Label
import com.reelometro.app.ui.components.ReelButton
import com.reelometro.app.ui.components.ReelButtonStyle
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/*
 * Descubrimiento progresivo en Hoy: la novedad (una pestaña recién aparecida) y la pista con lo que
 * aún falta. No aparecen en caliente (salen al cargar Hoy), así que no llevan `liveRegion`.
 */

/**
 * «NUEVO · ESTADÍSTICAS»: la misma anatomía que `Notice` (filete `signal`, etiqueta y texto), con dos
 * botones apilados (caben con la letra al 200 %). Secondary: el Primary queda para el aviso de conectar.
 */
@Composable
internal fun NewsNotice(
    news: TodayNews,
    onAction: (DiscoveryFeature) -> Unit,
    onDismiss: (DiscoveryFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReelTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(Space.rule)
                .fillMaxHeight()
                .background(colors.signal),
        )
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Label(news.title, modifier = Modifier.semantics { heading() }, color = colors.signalText)
            Spacer(Modifier.height(Space.x2))
            Text(news.text, style = ReelTheme.type.body, color = colors.text)
            Spacer(Modifier.height(Space.x4))
            ReelButton(text = news.action, onClick = { onAction(news.feature) }, style = ReelButtonStyle.Secondary)
            Spacer(Modifier.height(Space.x2))
            ReelButton(
                text = news.dismiss,
                onClick = { onDismiss(news.feature) },
                style = ReelButtonStyle.Ghost,
                trailingIcon = null,
                contentDescription = news.dismissDescription,
            )
        }
    }
}

/** «Estadísticas, Resumen y Flap irán apareciendo…» + ENSEÑARLO TODO YA, al final de Hoy. */
@Composable
internal fun RevealHint(hint: TodayRevealHint, onRevealAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ReelTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
        Text(hint.text, style = ReelTheme.type.body, color = colors.textMuted)
        Spacer(Modifier.height(Space.x2))
        ReelButton(
            text = hint.action,
            onClick = onRevealAll,
            style = ReelButtonStyle.Ghost,
            contentDescription = hint.actionDescription,
        )
    }
}
