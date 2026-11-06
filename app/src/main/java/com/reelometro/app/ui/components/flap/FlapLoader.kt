package com.reelometro.app.ui.components.flap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.ReelTheme

/**
 * Estado «cargando»: fichas en blanco («– – –») que voltean sobre sí mismas en bucle, en ola de
 * izquierda a derecha. Con «reducir animaciones» se quedan quietas. Mide lo mismo que un
 * [SplitFlapCounter] con el mismo [style] y [columns], así que el cambio a la cifra no salta.
 */
@Composable
fun FlapLoader(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    style: TextStyle = ReelTheme.type.counter,
    alignment: Alignment.Horizontal = Alignment.Start,
    contentDescription: String = "Cargando",
) {
    val colors = ReelTheme.colors
    val cells = remember(columns) { flapCells(FlapContent.Blank, columns, FlapPad.Zeros) }
    val state = remember { FlapBoardState() }
    SideEffect { state.sync(cells, FlapEnter.None) }

    val reduced = LocalReducedMotion.current
    LaunchedEffect(cells, reduced) {
        if (!reduced) state.loop(cells)
    }

    FlapBoard(
        cells = cells,
        state = state,
        style = style,
        glyphColor = colors.textMuted,
        tileColor = colors.bgSunken,
        hingeColor = colors.bg,
        shadeColor = flapShade(colors),
        alignment = alignment,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
    )
}
