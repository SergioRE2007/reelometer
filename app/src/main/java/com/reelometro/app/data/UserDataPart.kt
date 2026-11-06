package com.reelometro.app.data

import com.reelometro.app.data.export.CsvBlock
import java.io.OutputStream

/**
 * Datos del usuario que no viven en `reel_views`/`share_events` pero que tienen que salir en el CSV
 * de exportación, volver al importarlo y borrarse con BORRAR TODO (hoy, el pasaporte:
 * `data/game/PassportRepository`).
 *
 * [ReelRepository] los recibe con un proveedor perezoso (una lambda) desde `ReelometroApp`, así no
 * hay ciclo entre el repositorio y el pasaporte y Ajustes no cambia: sigue llamando a
 * `ReelRepository.exportCsv`, `importCsv` y `clearAll`.
 */
interface UserDataPart {
    /** Título de su bloque en el CSV (`PASAPORTE`): al importar, ese bloque llega a [importBlock]. */
    val blockTitle: String

    /**
     * Escribe su bloque **detrás** de los vídeos en [out], en UTF-8 y sin BOM, con el formato de
     * `data/export/Csv`. No cierra [out].
     */
    suspend fun exportCsv(out: OutputStream)

    /**
     * Tras importar un CSV (con los vídeos ya guardados): [block] es su bloque, o `null` si el fichero no
     * lo trae. Se llama siempre, para que la parte se ponga al día con el historial importado (el pasaporte
     * toma una base nueva y no celebra de golpe lo importado). Con SUSTITUIR TODO ya se ha llamado antes
     * a [clearAll].
     */
    suspend fun importBlock(block: CsvBlock?)

    /** Borra sus datos (BORRAR TODO). Los ajustes no se tocan. */
    suspend fun clearAll()
}
