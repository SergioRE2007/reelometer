package com.reelometro.app.data

/**
 * SQL de la capa de datos, en constantes para que los DAO y `QueryPlanTest` usen exactamente lo
 * mismo. Rangos de días con `:from`/`:to` en epochDay local, ambos incluidos.
 *
 * Todas las agregaciones por rango de días se resuelven con un índice cubriente (no tocan la tabla).
 */
internal object Queries {

    // ------------------------------------------------------------------ escritura

    /**
     * Fusiona la información leída de pantalla: un `null` no borra lo que ya hubiera y
     * «Sin clasificar» no pisa una categoría ya conocida.
     */
    const val UPDATE_INFO =
        "UPDATE reel_views SET creator = COALESCE(:creator, creator), caption = COALESCE(:caption, caption), " +
            "category = CASE WHEN :category = 'OTROS' THEN category ELSE :category END WHERE id = :id"

    // ------------------------------------------------------------------ agregaciones por rango de días

    const val VIEWS_BY_DAY =
        "SELECT day, COUNT(*) AS count, COALESCE(SUM(durationMs), 0) AS totalMs FROM reel_views " +
            "WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day"

    /** Solo recuentos: lo resuelve el índice estrecho de `day` (rachas sobre todo el historial). */
    const val COUNTS_BY_DAY =
        "SELECT day, COUNT(*) AS count FROM reel_views WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day"

    const val SHARES_BY_DAY =
        "SELECT day, COUNT(*) AS count FROM share_events WHERE day BETWEEN :from AND :to GROUP BY day ORDER BY day"

    const val VIEWS_BY_HOUR =
        "SELECT minuteOfDay / 60 AS hour, COUNT(*) AS count, COALESCE(SUM(durationMs), 0) AS totalMs " +
            "FROM reel_views WHERE day BETWEEN :from AND :to GROUP BY hour ORDER BY hour"

    const val VIEWS_BY_PLATFORM =
        "SELECT platform AS name, COUNT(*) AS count, COALESCE(SUM(durationMs), 0) AS totalMs FROM reel_views " +
            "WHERE day BETWEEN :from AND :to GROUP BY platform"

    const val VIEWS_BY_CATEGORY =
        "SELECT category AS name, COUNT(*) AS count, COALESCE(SUM(durationMs), 0) AS totalMs FROM reel_views " +
            "WHERE day BETWEEN :from AND :to GROUP BY category"

    const val TOP_CREATORS =
        "SELECT creator, platform, COUNT(*) AS count, COALESCE(SUM(durationMs), 0) AS totalMs FROM reel_views " +
            "WHERE day BETWEEN :from AND :to AND creator IS NOT NULL AND creator <> '' " +
            "GROUP BY creator, platform ORDER BY count DESC, totalMs DESC, creator ASC LIMIT :limit"

    const val SHARE_COUNT = "SELECT COUNT(*) FROM share_events WHERE day BETWEEN :from AND :to"

    /** A quién envías más (Instagram): lo resuelve el índice cubriente `day, recipient, platform`. */
    const val TOP_RECIPIENTS =
        "SELECT recipient, platform, COUNT(*) AS count FROM share_recipients WHERE day BETWEEN :from AND :to " +
            "GROUP BY recipient, platform ORDER BY count DESC, recipient ASC LIMIT :limit"

    /** Deshace el último envío a [recipient] desde [since] (el «Deshacer» de la hoja de compartir). */
    const val UNDO_RECIPIENT =
        "DELETE FROM share_recipients WHERE id = (SELECT id FROM share_recipients " +
            "WHERE recipient = :recipient AND at >= :since ORDER BY at DESC, id DESC LIMIT 1)"

    /** Totales de un rango cuyo último día se corta en `:untilMinute` (tramo del periodo anterior). */
    const val TOTALS_UNTIL =
        "SELECT COUNT(*) AS count, COALESCE(SUM(durationMs), 0) AS totalMs FROM reel_views " +
            "WHERE day BETWEEN :from AND :to AND (day < :to OR minuteOfDay <= :untilMinute)"

    const val SHARES_UNTIL =
        "SELECT COUNT(*) FROM share_events WHERE day BETWEEN :from AND :to AND (day < :to OR minuteOfDay <= :untilMinute)"

    /**
     * Días de referencia de una comparación: de las fechas candidatas, las que tienen algún vídeo
     * (en todo el día), las más recientes primero, con lo visto hasta `:untilMinute` (incluido).
     */
    const val SAME_WEEKDAY_BASELINE =
        "SELECT day, SUM(CASE WHEN minuteOfDay <= :untilMinute THEN 1 ELSE 0 END) AS count, " +
            "SUM(CASE WHEN minuteOfDay <= :untilMinute THEN durationMs ELSE 0 END) AS totalMs " +
            "FROM reel_views WHERE day IN (:days) GROUP BY day ORDER BY day DESC LIMIT :limit"

    // ------------------------------------------------------------------ globales

    const val FIRST_DAY = "SELECT MIN(day) FROM reel_views"

    /**
     * Puesto de un día (Detalle del día): cuántos días hasta `:to` (hoy) tienen **más** vídeos que
     * el día `:day`. Los dos recuentos los resuelve el índice estrecho de `day`.
     */
    const val DAYS_ABOVE =
        "SELECT COUNT(*) FROM (SELECT day FROM reel_views WHERE day <= :to GROUP BY day " +
            "HAVING COUNT(*) > (SELECT COUNT(*) FROM reel_views WHERE day = :day))"

    const val ALL_TOTALS = "SELECT COUNT(*) AS count, COALESCE(SUM(durationMs), 0) AS totalMs FROM reel_views"

    const val ALL_SHARES = "SELECT COUNT(*) FROM share_events"

    // ------------------------------------------------------------------ filas

    private const val VIDEO_COLUMNS =
        "r.id, r.platform, r.startedAt, r.durationMs, r.creator, r.caption, r.category, " +
            "(SELECT COUNT(*) FROM share_events s WHERE s.reelId = r.id) AS shares"

    const val RECENT =
        "SELECT $VIDEO_COLUMNS FROM reel_views r ORDER BY r.startedAt DESC, r.id DESC LIMIT :limit"

    /** Página de exportación por cursor (`startedAt`, `id`): nunca salta ni repite filas. */
    const val EXPORT_PAGE =
        "SELECT $VIDEO_COLUMNS FROM reel_views r " +
            "WHERE r.startedAt >= :afterStartedAt AND (r.startedAt > :afterStartedAt OR r.id > :afterId) " +
            "ORDER BY r.startedAt, r.id LIMIT :limit"
}
