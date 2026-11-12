package com.reelometro.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/** Retícula de 4 dp (docs/DESIGN.md §3). */
object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp
    val x16 = 64.dp
    val x20 = 80.dp

    /** Margen lateral de pantalla. */
    val screen = x5

    /** Separación entre secciones. */
    val section = x12

    /** Objetivo táctil mínimo. */
    val touch = x12

    /** Grosor de los filetes. */
    val hairline = 1.dp

    /** Filete vertical de un aviso integrado (`Notice`, novedad de Hoy). */
    val rule = 2.dp
}

/** Formas permitidas: recto, casi recto (2 dp) y círculo para puntos. */
object Shapes {
    val block = RectangleShape
    val tile = RoundedCornerShape(2.dp)
    val button = RoundedCornerShape(2.dp)
    val dot = RoundedCornerShape(50)
}
