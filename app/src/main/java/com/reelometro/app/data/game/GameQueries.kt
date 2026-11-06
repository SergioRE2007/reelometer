package com.reelometro.app.data.game

/**
 * SQL del modo Pasaporte sobre `reel_views`, en constantes para que [GameDao] y los tests usen
 * exactamente lo mismo. Días en epochDay local.
 *
 * Los recuentos por día de todo el historial salen de `Queries.COUNTS_BY_DAY` (índice estrecho de
 * `day`). Estas consultas son para los sellos irónicos; recorren el índice cubriente
 * (`day, platform, category, minuteOfDay, durationMs, creator`) sin leer la tabla, y el repositorio
 * deja de lanzarlas en cuanto el sello está guardado.
 */
internal object GameQueries {

    /** Primer día con vídeos de al menos `:platforms` plataformas distintas (TRES_PLATAFORMAS). */
    const val FIRST_DAY_WITH_PLATFORMS =
        "SELECT MIN(day) FROM (SELECT day FROM reel_views GROUP BY day HAVING COUNT(DISTINCT platform) >= :platforms)"

    /** Primer día con algún vídeo empezado entre `:fromMinute` y `:toMinute` (MADRUGADOR: 180–299). */
    const val FIRST_DAY_BETWEEN_MINUTES =
        "SELECT MIN(day) FROM reel_views WHERE minuteOfDay BETWEEN :fromMinute AND :toMinute"

    /** Primer día con **más** de `:perHour` vídeos en una misma hora del reloj (MARATON). */
    const val FIRST_DAY_WITH_BUSY_HOUR =
        "SELECT MIN(day) FROM (SELECT day FROM reel_views GROUP BY day, minuteOfDay / 60 HAVING COUNT(*) > :perHour)"

    /**
     * Por cada semana (lunes a domingo: el epochDay 0 fue jueves, así que `(day + 3) / 7` numera las
     * semanas desde un lunes) y categoría conocida, el primer día en que apareció (CINCO_CATEGORIAS).
     * «Sin clasificar» (`OTROS`) no cuenta como categoría.
     */
    const val CATEGORY_WEEK_FIRST_DAYS =
        "SELECT category AS name, MIN(day) AS firstDay FROM reel_views WHERE category <> 'OTROS' " +
            "GROUP BY (day + 3) / 7, category"
}
