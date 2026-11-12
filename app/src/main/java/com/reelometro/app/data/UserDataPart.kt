package com.reelometro.app.data

/**
 * Datos del usuario que no viven en `reel_views`/`share_events` pero que tienen que borrarse con
 * BORRAR TODO y con SUSTITUIR TODO al importar un CSV (hoy, lo último visto de Flap:
 * `data/game/FlapRepository`). No van en el CSV: se recalculan del historial.
 *
 * [ReelRepository] los recibe con un proveedor perezoso (una lambda) desde `ReelometroApp`, así no
 * hay ciclo entre el repositorio y Flap y Ajustes no cambia: sigue llamando a `ReelRepository.clearAll`.
 */
interface UserDataPart {
    /** Borra sus datos. Los ajustes no se tocan. */
    suspend fun clearAll()
}
