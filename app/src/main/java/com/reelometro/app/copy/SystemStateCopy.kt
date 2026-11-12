package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.detection.ConnectionKind
import com.reelometro.app.detection.DisconnectionKind
import com.reelometro.app.detection.ServiceFailures
import com.reelometro.app.detection.ServiceJournalState
import com.reelometro.app.detection.ServiceStatus
import com.reelometro.app.detection.SystemState
import com.reelometro.app.stats.Format
import java.time.Instant
import java.time.ZoneId

/**
 * Bloque «Estado del sistema» de la cabecera del registro de Calibración (`copy_calibration.xml`): lo que hace falta
 * para saber, sin cable, por qué el servicio deja de contar tras un rato sin usar el móvil. Restricciones de batería,
 * grupo de uso, hibernación, cuándo se conectó y desconectó el servicio, el último vídeo guardado, los fallos que se
 * han contenido y los avisos de poca memoria. Formato de consola, «clave: valor». Sin datos del usuario: horas,
 * cifras y síes o noes.
 */
object SystemStateCopy {

    /** Todo lo que entra en el bloque. */
    data class Input(
        val system: SystemState,
        val journal: ServiceJournalState,
        val failures: ServiceFailures.Snapshot,
        val memory: ServiceStatus.MemoryWarnings,
        /** Inicio del último vídeo guardado en la base de datos (epoch ms); null si no hay ninguno. */
        val lastVideoAt: Long?,
    )

    val TITLE: String get() = str(R.string.calibration_system_title)

    fun lines(input: Input, zone: ZoneId): List<String> {
        val s = input.system
        return listOf(
            TITLE,
            str(R.string.calibration_system_battery, yesNo(s.batteryUnrestricted), yesNo(s.backgroundRestricted), yesNo(s.powerSave)),
            str(R.string.calibration_system_bucket, s.standbyBucket?.let { "${Format.integer(it)} (${SystemState.bucketName(it)})" } ?: unknown()),
            str(R.string.calibration_system_hibernation, yesNo(s.hibernationExempt)),
            str(
                R.string.calibration_system_process,
                s.processAgeMs?.let(Format::duration) ?: unknown(),
                yesNo(s.notificationsAllowed),
            ),
            str(
                R.string.calibration_system_connections,
                input.journal.connections.joinToString(", ") { connection(it.kind, whenText(it.at, zone)) }.ifEmpty { none() },
            ),
            str(
                R.string.calibration_system_disconnections,
                input.journal.disconnections.joinToString(", ") { disconnection(it.kind, whenText(it.at, zone)) }.ifEmpty { none() },
            ),
            input.lastVideoAt?.let { str(R.string.calibration_system_last_video, whenText(it, zone)) }
                ?: str(R.string.calibration_system_last_video_none),
            failures(input.failures, zone),
            memory(input.memory, zone),
        )
    }

    private fun failures(f: ServiceFailures.Snapshot, zone: ZoneId): String {
        val at = f.lastAt
        if (f.count <= 0 || at == null) return str(R.string.calibration_system_failures_none)
        return str(
            R.string.calibration_system_failures,
            Format.integer(f.count),
            whenText(at, zone),
            listOfNotNull(f.lastError, f.lastWhere).joinToString(" · ").ifEmpty { unknown() },
            f.lastWhat ?: unknown(),
        )
    }

    private fun memory(m: ServiceStatus.MemoryWarnings, zone: ZoneId): String {
        val at = m.lastAt
        val level = m.lastLevel
        if (m.count <= 0 || at == null || level == null) return str(R.string.calibration_system_memory_none)
        return str(R.string.calibration_system_memory, Format.integer(m.count), whenText(at, zone), Format.integer(level))
    }

    private fun connection(kind: ConnectionKind, at: String): String = str(
        when (kind) {
            ConnectionKind.BOOT -> R.string.calibration_system_conn_boot
            ConnectionKind.COLD -> R.string.calibration_system_conn_cold
            ConnectionKind.WARM -> R.string.calibration_system_conn_warm
        },
        at,
    )

    private fun disconnection(kind: DisconnectionKind, at: String): String = str(
        when (kind) {
            DisconnectionKind.TURNED_OFF -> R.string.calibration_system_disc_off
            DisconnectionKind.UNBOUND -> R.string.calibration_system_disc_unbound
        },
        at,
    )

    /** «10 nov 14:32», como en las líneas de CIERRES. */
    private fun whenText(epochMs: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(epochMs).atZone(zone)
        return str(R.string.calibration_exit_when, Format.dayShort(at.toLocalDate()), Format.time(at.hour, at.minute))
    }

    private fun yesNo(value: Boolean?): String = when (value) {
        true -> str(R.string.calibration_yes)
        false -> str(R.string.calibration_no)
        null -> unknown()
    }

    private fun unknown(): String = str(R.string.calibration_unknown)

    private fun none(): String = str(R.string.calibration_system_none)
}
