package com.reelometro.app.data.backup

import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Por qué no se ha podido hacer la copia semanal (se guarda en los ajustes y se enseña en Ajustes › Tus datos). */
enum class BackupError {
    /** La carpeta ya no existe o Android ya no deja escribir en ella (permiso retirado): hay que elegir otra. */
    NO_ACCESS,

    /** Otro fallo al escribir (sin espacio, error del proveedor…): se reintenta en la próxima copia. */
    FAILED,
}

/** Resultado de una copia. */
sealed interface BackupOutcome {
    /** Copia escrita en [fileName] con [videos] vídeos; [deleted], las copias viejas borradas al rotar. */
    data class Done(val fileName: String, val videos: Int, val deleted: List<String>) : BackupOutcome

    /** No hay vídeos: no se escribe nada (y las copias que haya se quedan como están). */
    data object Empty : BackupOutcome

    data class Failed(val error: BackupError) : BackupOutcome
}

/**
 * Reglas puras de la copia semanal del CSV: el nombre fechado de cada copia, qué copias viejas sobran al rotar y
 * cómo se clasifica un fallo. Solo se tocan los ficheros que siguen el patrón exacto de la app ([fileName]); los
 * demás de la carpeta (también los CSV exportados a mano, `reelometer-AAAA-MM-DD.csv`) no se borran nunca.
 * Hasta la 1.8 las copias se llamaban `reelometro-auto-AAAA-MM-DD.csv`: se siguen reconociendo, cuentan para el
 * tope de [KEEP] y rotan igual que las nuevas.
 */
object BackupRules {

    /** Copias que se conservan en la carpeta. */
    const val KEEP = 4

    /** Días entre copias automáticas. */
    const val INTERVAL_DAYS = 7L

    private const val PREFIX = "reelometer-auto-"
    private const val SUFFIX = ".csv"
    private val NAME = Regex("""^reelome(?:ter|tro)-auto-(\d{4}-\d{2}-\d{2})\.csv$""")

    /** «reelometer-auto-2026-11-07.csv» (igual en todos los idiomas; una copia por día: la de hoy se sobrescribe). */
    fun fileName(date: LocalDate): String = "$PREFIX$date$SUFFIX"

    /** La fecha de una copia de la app, o `null` si [name] no es una copia de la app. */
    fun dateOf(name: String): LocalDate? {
        val iso = NAME.matchEntire(name)?.groupValues?.get(1) ?: return null
        return try {
            LocalDate.parse(iso)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Las copias de la app que sobran en [names]: todas menos las [keep] más recientes. Nunca otros ficheros. */
    fun toDelete(names: Collection<String>, keep: Int = KEEP): List<String> =
        names.asSequence()
            .distinct()
            .mapNotNull { name -> dateOf(name)?.let { name to it } }
            .sortedByDescending { it.second }
            .drop(keep.coerceAtLeast(0))
            .map { it.first }
            .toList()

    /**
     * Un fallo al escribir: sin permiso, carpeta borrada o documento que ya no existe es [BackupError.NO_ACCESS]
     * (reintentar no sirve); el resto, [BackupError.FAILED].
     */
    fun classify(error: Throwable): BackupError = when (error) {
        is SecurityException, is FileNotFoundException, is IllegalArgumentException -> BackupError.NO_ACCESS
        else -> BackupError.FAILED
    }
}
