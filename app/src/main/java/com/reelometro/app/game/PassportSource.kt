package com.reelometro.app.game

import kotlinx.coroutines.flow.Flow

/**
 * Puerta del modo Pasaporte para la pantalla, el widget y los avisos. La implementación real es
 * `data/game/PassportRepository`; en tests, previews y galería, [FakePassportSource].
 */
interface PassportSource {
    /** Estado del pasaporte; se recalcula al cambiar los datos de uso, el objetivo o el día. */
    fun observe(): Flow<PassportState>

    /** Estado actual, una vez (widget, avisos). */
    suspend fun snapshot(): PassportState

    /** Marca como vistas estas celebraciones ([Celebration.key]); no vuelven a salir. */
    suspend fun markCelebrated(keys: Collection<String>)

    /** El usuario ya ha visto a Flap en este estado (la animación de cambio no se repite). */
    suspend fun markMoodSeen(mood: FlapMood)

    /** Registra un evento que puede dar un sello (Wrapped compartido, ticket guardado…). */
    suspend fun recordEvent(event: GameEvent)
}
