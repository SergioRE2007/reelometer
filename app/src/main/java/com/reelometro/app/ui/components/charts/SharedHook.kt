package com.reelometro.app.ui.components.charts

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/*
 * Gancho de elementos compartidos de las gráficas.
 *
 * Las barras y las celdas se dibujan en un único Canvas, así que no hay un composable por barra al
 * que colgarle un `sharedBounds`. En su lugar, la gráfica coloca **una caja transparente** encima de
 * la última barra o celda pulsada (con el tamaño exacto de su dibujo) y le aplica el modificador que
 * le da la pantalla (p. ej. `reelSharedBounds(SharedKeys.dayOpen(epochDay))`). Al abrir el día,
 * esa caja es el origen de la transición hacia el marcador del Detalle del día; al volver, el destino.
 * La caja no dibuja nada ni recibe toques: no cambia la gráfica.
 */

/**
 * Coloca el contenido en el rectángulo que devuelve [rect] (px, relativo al padre; recibe el tamaño
 * máximo que da el padre) **sin ocupar sitio** en el padre. `null` = aún no hay geometría: se queda
 * a 0 × 0 en la esquina.
 */
internal fun Modifier.placeAt(rect: Density.(IntSize) -> Rect?): Modifier = layout { measurable, constraints ->
    val r = rect(IntSize(constraints.maxWidth, constraints.maxHeight))
    val w = r?.width?.roundToInt()?.coerceAtLeast(1) ?: 0
    val h = r?.height?.roundToInt()?.coerceAtLeast(1) ?: 0
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(0, 0) {
        placeable.place(r?.left?.roundToInt() ?: 0, r?.top?.roundToInt() ?: 0)
    }
}
