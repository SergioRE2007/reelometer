package com.reelometro.app.data

/**
 * Un vídeo registrado, tal cual (para la lista en vivo de Calibración).
 *
 * @property startedAt instante de inicio (epoch ms).
 * @property durationMs 0 mientras el vídeo sigue en pantalla.
 * @property shares veces que se pulsó «compartir» en este vídeo.
 */
data class RecentVideo(
    val id: Long,
    val platform: Platform,
    val startedAt: Long,
    val durationMs: Long,
    val creator: String?,
    val caption: String?,
    val category: Category,
    val shares: Int,
)
