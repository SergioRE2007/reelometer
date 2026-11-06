package com.reelometro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/**
 * Aviso integrado en la página (docs/DESIGN.md §4), no una tarjeta de error: un filete `signal` de
 * 2 dp a la izquierda, una etiqueta opcional en `signalText`, el texto y una acción.
 * Caso típico: el servicio de accesibilidad está desactivado.
 *
 * @param title etiqueta corta en mayúsculas («SIN SEÑAL»).
 * @param actionLabel texto del botón (`Primary`); sin él, no hay acción.
 */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
) {
    val colors = ReelTheme.colors
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(colors.signal),
        )
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Label(title, color = colors.signalText)
                Spacer(Modifier.height(Space.x2))
            }
            Text(text, style = ReelTheme.type.body, color = colors.text)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Space.x4))
                ReelButton(text = actionLabel, onClick = onAction, icon = actionIcon)
            }
        }
    }
}
