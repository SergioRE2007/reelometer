package com.reelometro.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime

/** Horario del refresco de medianoche (lógica pura). */
internal object WidgetSchedule {

    /** Margen tras la medianoche, para que «hoy» ya sea el día nuevo. */
    private const val AFTER_MIDNIGHT_S = 30L

    /**
     * Siguiente refresco: el inicio del día siguiente a [now] (con `atStartOfDay`, que vale también
     * en las zonas donde el cambio de hora se salta la medianoche) más un margen.
     */
    fun nextRefresh(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(AFTER_MIDNIGHT_S)
}

/**
 * Tamaños de `SizeMode.Responsive`. Glance compone uno por tamaño y el launcher pinta el mayor que
 * cabe en el hueco real (si no cabe ninguno, el más pequeño), y el contenido no crece más allá del
 * tamaño elegido: por eso van ajustados a los huecos reales, con un pequeño margen.
 *
 * Medido en el Pixel Launcher (API 35, 411 dp de ancho, rejilla de 4 columnas): 2×2 = 172×224 dp;
 * columna de 94 dp y fila de 120 dp, menos 8 dp de margen por lado → 2×1 ≈ 172×104 y 4×1 ≈ 360×104.
 * Las rejillas de 5 columnas dan unos 130 dp de ancho a 2 celdas: [NARROW] y [NARROW_TALL] evitan
 * que ahí se recorte el de 168 dp.
 */
internal object WidgetSizes {
    /** 2×1. */
    val SMALL = DpSize(168.dp, 96.dp)

    /** 2×2 (el tamaño por defecto). */
    val SQUARE = DpSize(168.dp, 208.dp)

    /** 4×1. */
    val WIDE = DpSize(352.dp, 96.dp)

    /** 4×2. */
    val LARGE = DpSize(352.dp, 208.dp)

    /** 2×1 en rejillas de 5 columnas. */
    val NARROW = DpSize(128.dp, 96.dp)

    /** 2×2 en rejillas de 5 columnas. */
    val NARROW_TALL = DpSize(128.dp, 208.dp)

    val ALL: Set<DpSize> = setOf(NARROW, NARROW_TALL, SMALL, SQUARE, WIDE, LARGE)
}
