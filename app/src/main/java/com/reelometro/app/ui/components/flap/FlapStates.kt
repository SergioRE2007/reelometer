package com.reelometro.app.ui.components.flap

import com.reelometro.app.copy.str
import com.reelometro.app.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/**
 * Estado vacío con el marcador de protagonista (a cero, «000», o en blanco con `value = null`),
 * la frase en serif itálica ([title], p. ej. «De momento, ganas tú.»), una explicación opcional
 * y una acción opcional (un `ReelButton`, por ejemplo). Alineado a la izquierda, sin contenedor.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    value: Long? = 0,
    counterStyle: TextStyle = ReelTheme.type.displayXL,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth()) {
        SplitFlapCounter(
            value = value,
            style = counterStyle,
            haptic = false,
            contentDescription = if (value == null) str(R.string.ui_no_data) else Format.integer(value),
        )
        Spacer(Modifier.height(Space.x6))
        Text(title, style = type.quote, color = colors.text, modifier = Modifier.semantics { heading() })
        if (body != null) {
            Spacer(Modifier.height(Space.x2))
            Text(body, style = type.body, color = colors.textMuted)
        }
        if (action != null) {
            Spacer(Modifier.height(Space.x6))
            action()
        }
    }
}

/**
 * Estado de error: marcador en blanco («– – –»), una etiqueta opcional en acento ([label], p. ej.
 * «SIN SEÑAL»), el [message] y la acción (normalmente un `ReelButton` «REINTENTAR»).
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    counterStyle: TextStyle = ReelTheme.type.displayXL,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = ReelTheme.colors
    val type = ReelTheme.type
    Column(modifier.fillMaxWidth()) {
        SplitFlapCounter(value = null, style = counterStyle, haptic = false, contentDescription = str(R.string.ui_no_data))
        Spacer(Modifier.height(Space.x6))
        if (label != null) {
            Text(Format.upper(label), style = type.label, color = colors.signalText)
            Spacer(Modifier.height(Space.x2))
        }
        Text(message, style = type.quote, color = colors.text, modifier = Modifier.semantics { heading() })
        if (action != null) {
            Spacer(Modifier.height(Space.x6))
            action()
        }
    }
}
