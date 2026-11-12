package com.reelometro.app.game

import kotlinx.coroutines.flow.Flow

/**
 * Puerta de Flap para la pantalla. La implementación real está en `data/game/` (sobre los vídeos
 * por día de Room, el objetivo de Ajustes y lo último visto); en tests, previews y galería,
 * [FakeFlapSource].
 */
interface FlapSource {
    /** Estado de Flap; se recalcula al cambiar los datos de uso, el objetivo o el día (medianoche). */
    fun observe(): Flow<FlapState>

    /** Estado actual, una vez. */
    suspend fun snapshot(): FlapState

    /** El usuario ya ha visto a Flap en este estado (la animación de cambio no se repite). */
    suspend fun markMoodSeen(mood: FlapMood)
}
