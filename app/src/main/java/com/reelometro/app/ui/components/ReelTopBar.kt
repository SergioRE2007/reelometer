package com.reelometro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.reelometro.app.ui.icons.ReelIcons
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.Space

/**
 * Cabecera de pantalla propia (sin `TopAppBar`, docs/DESIGN.md §9).
 *
 *  - Sin [onBack]: una fila con *kicker* (`label`, p. ej. «HOY · MARTES 3 NOV») y/o título grande a
 *    la izquierda y acciones de 48 dp ([ReelIconButton]) a la derecha.
 *  - Con [onBack]: arriba la fila de navegación (← y acciones) y debajo el bloque de título,
 *    alineado con el margen de 20 dp como el resto del contenido.
 *
 * Los glifos de «volver» y de la última acción caen sobre el margen de 20 dp (los objetivos
 * táctiles sobresalen hacia el borde). Respeta la barra de estado con [windowInsets].
 *
 * @param titleStyle por defecto `displayM` (titular de pantalla); `headline` en subpantallas.
 * @param divider filete `line` de 1 dp bajo la cabecera.
 */
@Composable
fun ReelTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    kicker: String? = null,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Volver",
    titleStyle: TextStyle = ReelTheme.type.displayM,
    divider: Boolean = false,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = ReelTheme.colors
    Column(modifier.fillMaxWidth().windowInsetsPadding(windowInsets)) {
        if (onBack != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // 4 dp + los 12 dp internos del objetivo táctil ≈ el glifo cae en el margen de 20 dp.
                    .padding(horizontal = Space.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReelIconButton(ReelIcons.Back, contentDescription = backDescription, onClick = onBack)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            if (kicker != null || title != null) {
                TitleBlock(
                    kicker = kicker,
                    title = title,
                    titleStyle = titleStyle,
                    modifier = Modifier.padding(start = Space.screen, end = Space.screen, top = Space.x1, bottom = Space.x4),
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = Space.screen, end = Space.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TitleBlock(
                    kicker = kicker,
                    title = title,
                    titleStyle = titleStyle,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (title != null) Space.x3 else 0.dp)
                        .padding(end = Space.x4),
                )
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Space.hairline)
                    .background(colors.line),
            )
        }
    }
}

/** Kicker en `label` (en `labelL` y color de texto si va solo) y título encima del filete. */
@Composable
private fun TitleBlock(kicker: String?, title: String?, titleStyle: TextStyle, modifier: Modifier) {
    val colors = ReelTheme.colors
    Column(modifier) {
        if (kicker != null) {
            Label(kicker, color = if (title != null) colors.textMuted else colors.text, large = title == null)
        }
        if (kicker != null && title != null) Spacer(Modifier.height(Space.x1))
        if (title != null) {
            Text(
                text = title,
                style = titleStyle,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}
