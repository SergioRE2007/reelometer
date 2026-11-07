package com.reelometro.app.data.backup

import java.io.OutputStream
import java.time.LocalDate

/** La carpeta de las copias (en el móvil, la del Storage Access Framework; en los tests, una falsa). */
interface BackupFolder {
    /** Nombres de los ficheros que hay en la carpeta (sin entrar en subcarpetas). */
    fun list(): List<String>

    /** Abre [name] para escribirlo desde cero (lo crea si no existe; si existe, lo sobrescribe). */
    fun openForWrite(name: String): OutputStream

    /** Borra [name]. `false` si no se ha podido (no es grave: se reintenta en la próxima copia). */
    fun delete(name: String): Boolean
}

/** Hace una copia: escribe el CSV del día y rota las de la app. No lanza: un fallo vuelve como [BackupOutcome.Failed]. */
object BackupRunner {

    /**
     * Escribe con [export] (el mismo CSV que EXPORTAR) en la copia de [today] dentro de [folder] y deja solo las
     * [BackupRules.KEEP] copias de la app más recientes. Sin vídeos ([hasData] `false`), no escribe ni borra nada.
     */
    suspend fun run(
        folder: BackupFolder,
        today: LocalDate,
        hasData: suspend () -> Boolean,
        export: suspend (OutputStream) -> Int,
    ): BackupOutcome = try {
        if (!hasData()) {
            BackupOutcome.Empty
        } else {
            val name = BackupRules.fileName(today)
            val videos = folder.openForWrite(name).use { export(it) }
            // Rotar después de escribir: si la copia nueva falla, las viejas siguen ahí.
            val names = runCatching { folder.list() }.getOrDefault(emptyList()) + name
            val deleted = BackupRules.toDelete(names).filter { runCatching { folder.delete(it) }.getOrDefault(false) }
            BackupOutcome.Done(name, videos, deleted)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        BackupOutcome.Failed(BackupRules.classify(e))
    }
}
