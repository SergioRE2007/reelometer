package com.reelometro.app.copy

import com.reelometro.app.R
import com.reelometro.app.detection.ExitAccess
import com.reelometro.app.detection.ExitCategory
import com.reelometro.app.detection.ExitInfo
import com.reelometro.app.detection.ExitReasons
import com.reelometro.app.detection.RecentExits
import com.reelometro.app.stats.Format
import java.time.Instant
import java.time.ZoneId

/**
 * Por qué se cerró la app ([ExitReasons]): la línea del diagnóstico cuando el servicio está activado y sin conectar
 * (Hoy, la pantalla de conectar y el aviso, en `copy_onboarding.xml`) y las líneas de la cabecera de Calibración
 * (`copy_calibration.xml`).
 */
object ExitCopy {

    /** Explicación concreta para el diagnóstico; null si el cierre no explica nada ([ExitCategory.IGNORED]). */
    fun diagnosis(category: ExitCategory): String? = when (category) {
        ExitCategory.RECENTS_CLEAN -> str(R.string.onboarding_exit_recents)
        ExitCategory.SYSTEM_LOW_MEMORY -> str(R.string.onboarding_exit_low_memory)
        ExitCategory.SYSTEM_OTHER -> str(R.string.onboarding_exit_system)
        ExitCategory.RESTRICTED -> str(R.string.onboarding_exit_restricted)
        ExitCategory.USER_STOPPED -> str(R.string.onboarding_exit_user)
        ExitCategory.APP_CRASH -> str(R.string.onboarding_exit_crash)
        ExitCategory.IGNORED -> null
    }

    /** Clave de la consola de Calibración: «CIERRES». */
    val LOG_KEY: String get() = str(R.string.calibration_exits_key)

    /** Título de las líneas en el registro que se comparte. */
    val LOG_TITLE: String get() = str(R.string.calibration_exits_title)

    /** «Android no guarda ningún cierre de la app.»: se han leído y no hay ninguno. */
    val LOG_NONE: String get() = str(R.string.calibration_exits_none)

    /** Antes de Android 11 no hay registro de cierres ([ExitAccess.UNAVAILABLE]). */
    val LOG_UNAVAILABLE: String get() = str(R.string.calibration_exits_unavailable)

    /** Android los guarda pero no se han podido leer ([ExitAccess.FAILED]). */
    val LOG_FAILED: String get() = str(R.string.calibration_exits_failed)

    /** Lo que se pone en la descripción en lugar del paquete de otra app: «[otra app 1]». */
    fun otherApp(n: Int): String = str(R.string.calibration_exit_other_app, Format.integer(n))

    fun categoryLabel(category: ExitCategory): String = str(
        when (category) {
            ExitCategory.RECENTS_CLEAN -> R.string.calibration_exit_cat_recents
            ExitCategory.SYSTEM_LOW_MEMORY -> R.string.calibration_exit_cat_low_memory
            ExitCategory.SYSTEM_OTHER -> R.string.calibration_exit_cat_system
            ExitCategory.RESTRICTED -> R.string.calibration_exit_cat_restricted
            ExitCategory.USER_STOPPED -> R.string.calibration_exit_cat_user
            ExitCategory.APP_CRASH -> R.string.calibration_exit_cat_crash
            ExitCategory.IGNORED -> R.string.calibration_exit_cat_ignored
        },
    )

    /**
     * Una línea por cierre: «10 nov 14:32 · limpiar recientes · enlazado · motivo 13/0 · SwipeUpClean». La descripción
     * pasa por [ExitReasons.safeDescription] (sin texto libre largo ni caracteres raros, y los paquetes de otras apps
     * como [otherApp]). Con el mismo [numbers] en varias líneas ([logLines]), el mismo paquete es la misma [otherApp].
     * Si Android apuntó la memoria del proceso al morir, va detrás: «… · memoria 84 MB (RSS 132 MB)».
     */
    fun logLine(info: ExitInfo, zone: ZoneId, numbers: MutableMap<String, Int> = HashMap()): String {
        val line = baseLine(info, zone, numbers)
        val r = info.record
        if (r.pssKb <= 0 && r.rssKb <= 0) return line
        return str(R.string.calibration_exit_memory, line, megabytes(r.pssKb), megabytes(r.rssKb))
    }

    private fun megabytes(kb: Long): String = Format.integer((kb.coerceAtLeast(0) + 512) / 1024)

    private fun baseLine(info: ExitInfo, zone: ZoneId, numbers: MutableMap<String, Int>): String {
        val at = Instant.ofEpochMilli(info.record.timestamp).atZone(zone)
        val r = info.record
        return str(
            R.string.calibration_exit_line,
            str(R.string.calibration_exit_when, Format.dayShort(at.toLocalDate()), Format.time(at.hour, at.minute)),
            categoryLabel(info.category),
            str(if (info.serviceBound) R.string.calibration_exit_bound else R.string.calibration_exit_unbound),
            Format.integer(r.reason),
            Format.integer(r.subreason),
            ExitReasons.safeDescription(r.description, numbers) { otherApp(it) } ?: str(R.string.calibration_exit_no_description),
        )
    }

    /**
     * Las líneas de la consola (o [LOG_NONE] si no hay ninguna). Los paquetes de otras apps se numeran en todas a la
     * vez: «[otra app 1]» es la misma app en cada línea.
     */
    fun logLines(exits: List<ExitInfo>, zone: ZoneId): List<String> {
        if (exits.isEmpty()) return listOf(LOG_NONE)
        val numbers = HashMap<String, Int>()
        return exits.map { logLine(it, zone, numbers) }
    }

    /** Lo mismo, distinguiendo que no haya ninguno de que esta versión de Android no los guarde o no se puedan leer. */
    fun logLines(recent: RecentExits, zone: ZoneId): List<String> = when (recent.access) {
        ExitAccess.READ -> logLines(recent.exits, zone)
        ExitAccess.UNAVAILABLE -> listOf(LOG_UNAVAILABLE)
        ExitAccess.FAILED -> listOf(LOG_FAILED)
    }
}
