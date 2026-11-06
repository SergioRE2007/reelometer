package com.reelometro.app.data

import java.io.OutputStream

/**
 * Datos del usuario que no viven en `reel_views`/`share_events` pero que tienen que salir en el CSV
 * de exportación y borrarse con BORRAR TODO (hoy, el pasaporte: `data/game/PassportRepository`).
 *
 * [ReelRepository] los recibe con un proveedor perezoso (una lambda) desde `ReelometroApp`, así no
 * hay ciclo entre el repositorio y el pasaporte y Ajustes no cambia: sigue llamando a
 * `ReelRepository.exportCsv` y `ReelRepository.clearAll`.
 */
interface UserDataPart {
    /**
     * Escribe su bloque **detrás** de los vídeos en [out], en UTF-8 y sin BOM, con el formato de
     * `data/export/Csv`. No cierra [out].
     */
    suspend fun exportCsv(out: OutputStream)

    /** Borra sus datos (BORRAR TODO). Los ajustes no se tocan. */
    suspend fun clearAll()
}
