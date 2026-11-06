package com.reelometro.app.ui.components.flap

import com.reelometro.app.copy.str
import com.reelometro.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.reelometro.app.stats.Format
import com.reelometro.app.ui.theme.LocalReducedMotion
import com.reelometro.app.ui.theme.ReelTheme
import com.reelometro.app.ui.theme.rememberHaptics

/**
 * El marcador split-flap (docs/DESIGN.md §4). Muestra [content] en fichas que **voltean** al
 * cambiar y al entrar en pantalla (la primera vez que asoma al menos la mitad: debajo del pliegue
 * o en otra página espera en blanco): la mitad superior cae sobre la bisagra revelando el glifo
 * siguiente, pasando por los intermedios (tope [maxFlips]), escalonado de izquierda a derecha y
 * con un pequeño rebote al asentarse; un único háptico al terminar. Con «reducir animaciones», el
 * cambio es instantáneo.
 *
 * Autoajuste: usa el tamaño de [style] (ya escalado por la fuente del sistema) y encoge lo justo
 * para caber en el ancho disponible; nunca parte ni corta. Ocupa el ancho de su contenido; si el
 * padre le impone más, se coloca según [alignment].
 *
 * Para cambiar de cifra a letras con el mismo volteo (p. ej. «212» → «OK»), usa siempre esta
 * función en el mismo sitio y cambia [content].
 *
 * @param minColumns columnas mínimas (cifras: se rellenan según [pad]; texto: fichas vacías).
 * @param color color de los glifos (`text`; `signalDisplay` para resaltar).
 * @param enter desde dónde voltea la primera vez.
 * @param haptic `false` para no vibrar (p. ej. varios marcadores a la vez).
 * @param contentDescription lo que lee TalkBack («212 vídeos hoy»); se anuncia al cambiar (educado).
 */
@Composable
fun SplitFlapDisplay(
    content: FlapContent,
    modifier: Modifier = Modifier,
    minColumns: Int = 3,
    pad: FlapPad = FlapPad.Zeros,
    style: TextStyle = ReelTheme.type.counter,
    color: Color = ReelTheme.colors.text,
    enter: FlapEnter = FlapEnter.Blank,
    alignment: Alignment.Horizontal = Alignment.Start,
    maxFlips: Int = FlapGlyphs.MAX_FLIPS,
    haptic: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = ReelTheme.colors
    val cells = remember(content, minColumns, pad) { flapCells(content, minColumns, pad) }
    val state = remember { FlapBoardState() }
    SideEffect { state.sync(cells, enter) }

    val reduced = LocalReducedMotion.current
    val haptics = rememberHaptics()
    val vibrate by rememberUpdatedState(haptic)
    // La entrada espera a que el marcador se vea de verdad (debajo del pliegue, en otra página…).
    var shown by remember { mutableStateOf(false) }
    val waiting = enter != FlapEnter.None && !reduced && !shown && !LocalInspectionMode.current
    LaunchedEffect(cells, reduced, maxFlips, waiting) {
        if (waiting) return@LaunchedEffect
        state.animateTo(cells, reduced, maxFlips) { if (vibrate) haptics.settle() }
    }

    val description = contentDescription ?: defaultFlapDescription(content)
    FlapBoard(
        cells = cells,
        state = state,
        style = style,
        glyphColor = color,
        tileColor = colors.bgSunken,
        hingeColor = colors.bg,
        shadeColor = flapShade(colors),
        alignment = alignment,
        modifier = modifier
            .onVisibilityChanged(minFractionVisible = 0.5f) { visible -> if (visible) shown = true }
            .semantics {
                this.contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    )
}

/**
 * Marcador de una cifra: `SplitFlapCounter(212)` → «212»; `SplitFlapCounter(7)` → «007»;
 * `SplitFlapCounter(12408)` → «12.408»; `SplitFlapCounter(null)` → fichas en blanco «– – –»
 * (servicio desactivado / sin dato).
 *
 * @param minDigits columnas mínimas; `1` para no rellenar nunca.
 * @param pad relleno a la izquierda: ceros («007», por defecto, como la maqueta de Hoy) o fichas vacías.
 * @see SplitFlapDisplay para el resto de parámetros.
 */
@Composable
fun SplitFlapCounter(
    value: Long?,
    modifier: Modifier = Modifier,
    minDigits: Int = 3,
    pad: FlapPad = FlapPad.Zeros,
    style: TextStyle = ReelTheme.type.counter,
    color: Color = ReelTheme.colors.text,
    enter: FlapEnter = FlapEnter.Blank,
    alignment: Alignment.Horizontal = Alignment.Start,
    haptic: Boolean = true,
    contentDescription: String? = null,
) {
    SplitFlapDisplay(
        content = if (value == null) FlapContent.Blank else FlapContent.Number(value),
        modifier = modifier,
        minColumns = minDigits,
        pad = pad,
        style = style,
        color = color,
        enter = enter,
        alignment = alignment,
        haptic = haptic,
        contentDescription = contentDescription,
    )
}

/**
 * Letras en fichas con el mismo volteo («OK», «HOY»). Una letra por ficha, en mayúsculas.
 *
 * @param minColumns columnas mínimas (el texto se alinea a la derecha; sobran fichas vacías).
 * @see SplitFlapDisplay para el resto de parámetros.
 */
@Composable
fun SplitFlapText(
    text: String,
    modifier: Modifier = Modifier,
    minColumns: Int = text.length,
    style: TextStyle = ReelTheme.type.counter,
    color: Color = ReelTheme.colors.text,
    enter: FlapEnter = FlapEnter.Blank,
    alignment: Alignment.Horizontal = Alignment.Start,
    haptic: Boolean = true,
    contentDescription: String? = null,
) {
    SplitFlapDisplay(
        content = FlapContent.Text(text),
        modifier = modifier,
        minColumns = minColumns,
        style = style,
        color = color,
        enter = enter,
        alignment = alignment,
        haptic = haptic,
        contentDescription = contentDescription,
    )
}

internal fun defaultFlapDescription(content: FlapContent): String = when (content) {
    is FlapContent.Number -> Format.integer(content.value)
    FlapContent.Blank -> str(R.string.ui_no_data)
    is FlapContent.Text -> content.text
}
