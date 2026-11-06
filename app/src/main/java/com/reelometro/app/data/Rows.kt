package com.reelometro.app.data

// Filas de resultado de las consultas de agregación (Room las rellena por nombre de columna).

/** Vídeos y tiempo de un día. */
data class DayRow(val day: Long, val count: Int, val totalMs: Long)

/** Recuento por día (vídeos o compartidos). */
data class DayCountRow(val day: Long, val count: Int)

/** Vídeos y tiempo de una hora local (0–23). */
data class HourRow(val hour: Int, val count: Int, val totalMs: Long)

/** Vídeos y tiempo de un grupo (plataforma o categoría, por nombre del enum). */
data class GroupRow(val name: String, val count: Int, val totalMs: Long)

/** Vídeos y tiempo de un creador en una plataforma. */
data class CreatorRow(val creator: String, val platform: String, val count: Int, val totalMs: Long)

/** Envíos a una persona o grupo (Instagram). */
data class RecipientRow(val recipient: String, val platform: String, val count: Int)

/** Totales de un rango. */
data class TotalsRow(val count: Int, val totalMs: Long)

/** Un vídeo con sus veces compartido (recientes y exportación). */
data class VideoRow(
    val id: Long,
    val platform: String,
    val startedAt: Long,
    val durationMs: Long,
    val creator: String?,
    val caption: String?,
    val category: String,
    val shares: Int,
)
