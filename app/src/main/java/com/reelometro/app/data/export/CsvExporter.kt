package com.reelometro.app.data.export

import com.reelometro.app.data.Category
import com.reelometro.app.data.Platform
import com.reelometro.app.data.ReelDao
import com.reelometro.app.data.VideoRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exporta todos los vídeos a CSV en orden cronológico, por páginas (nunca todo en memoria).
 *
 * Columnas: `fecha_hora_local` (ISO, `2026-11-03T14:37:05`), `plataforma`, `duración_s` (`12,3`),
 * `creador`, `categoría`, `descripción`, `compartido` (veces que se compartió ese vídeo).
 * Los compartidos que no se pudieron asociar a un vídeo no aparecen.
 *
 * Si hay envíos de «A quién envías» (Instagram), detrás va el bloque `ENVÍOS`: una línea en blanco, el
 * título, la cabecera [RECIPIENTS_HEADER] (`fecha_hora_local`, `plataforma`, `destinatario`) y una fila
 * por envío, en orden cronológico. Sin envíos, el bloque no sale.
 */
class CsvExporter(
    private val dao: ReelDao,
    private val zone: () -> ZoneId,
    private val pageSize: Int = PAGE_SIZE,
) {
    init {
        require(pageSize > 0)
    }

    /**
     * Escribe el CSV en [out] (no lo cierra: es del llamante) y devuelve cuántos vídeos se han exportado.
     * Cada página se lee por separado: un vídeo que se registre durante la exportación puede salir o no.
     */
    suspend fun export(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val zone = zone()
        val writer = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8), BUFFER_CHARS)
        writer.write(Csv.BOM)
        writer.write(Csv.row(HEADER))
        var count = 0
        var afterStartedAt = Long.MIN_VALUE
        var afterId = Long.MIN_VALUE
        while (true) {
            val page = dao.exportPage(afterStartedAt, afterId, pageSize)
            for (row in page) writer.write(Csv.row(fields(row, zone)))
            count += page.size
            if (page.size < pageSize) break
            val last = page.last()
            afterStartedAt = last.startedAt
            afterId = last.id
        }
        val recipients = dao.shareRecipients()
        if (recipients.isNotEmpty()) {
            writer.write(Csv.LINE_END)
            writer.write(Csv.row(listOf(RECIPIENTS_TITLE)))
            writer.write(Csv.row(RECIPIENTS_HEADER))
            for (r in recipients) {
                writer.write(
                    Csv.row(
                        listOf(
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(r.at), zone).format(DATE_TIME),
                            Platform.fromName(r.platform)?.displayName ?: r.platform,
                            Csv.text(r.recipient),
                        ),
                    ),
                )
            }
        }
        writer.flush()
        count
    }

    private fun fields(row: VideoRow, zone: ZoneId): List<String> = listOf(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(row.startedAt), zone).format(DATE_TIME),
        Platform.fromName(row.platform)?.displayName ?: row.platform,
        Csv.seconds(row.durationMs),
        Csv.text(row.creator),
        Category.fromName(row.category).label,
        Csv.text(row.caption),
        row.shares.toString(),
    )

    companion object {
        const val PAGE_SIZE = 500
        private const val BUFFER_CHARS = 64 * 1024

        val HEADER = listOf(
            "fecha_hora_local", "plataforma", "duración_s", "creador", "categoría", "descripción", "compartido",
        )

        const val RECIPIENTS_TITLE = "ENVÍOS"
        val RECIPIENTS_HEADER = listOf("fecha_hora_local", "plataforma", "destinatario")

        private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
    }
}
