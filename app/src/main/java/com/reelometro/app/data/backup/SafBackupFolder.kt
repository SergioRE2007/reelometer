package com.reelometro.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.reelometro.app.ReelometroApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.OutputStream
import java.time.LocalDate

/**
 * Una carpeta elegida con `ACTION_OPEN_DOCUMENT_TREE` (Storage Access Framework): sin permisos de almacenamiento,
 * la app solo ve y escribe dentro de ella. Todo en el hilo de E/S.
 */
class SafBackupFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {

    private val treeId: String = DocumentsContract.getTreeDocumentId(tree)
    private val parent: Uri = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)

    /** Nombre e id de cada documento de la carpeta. */
    private fun children(): Map<String, String> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = resolver.query(uri, columns, null, null, null) ?: throw FileNotFoundException("Carpeta no accesible")
        return cursor.use {
            buildMap {
                while (it.moveToNext()) {
                    val id = it.getString(0) ?: continue
                    val name = it.getString(1) ?: continue
                    putIfAbsent(name, id)
                }
            }
        }
    }

    override fun list(): List<String> = children().keys.toList()

    override fun openForWrite(name: String): OutputStream {
        val existing = children()[name]?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
        val target = existing
            ?: DocumentsContract.createDocument(resolver, parent, MIME, name)
            ?: throw FileNotFoundException("No se ha podido crear $name")
        return resolver.openOutputStream(target, "wt") ?: throw FileNotFoundException("No se ha podido abrir $name")
    }

    override fun delete(name: String): Boolean {
        val id = children()[name] ?: return false
        return DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
    }

    companion object {
        const val MIME = "text/csv"
    }
}

/** La copia semanal en el móvil: permisos de la carpeta y la copia de verdad (desde Ajustes y desde el worker). */
object CsvBackup {

    private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Se queda con el permiso de [uri] (una carpeta recién elegida) para siempre y devuelve su nombre para
     * enseñarlo, o `null` si Android no lo da.
     */
    fun takeFolder(context: Context, uri: String): String? = runCatching {
        val tree = Uri.parse(uri)
        context.contentResolver.takePersistableUriPermission(tree, FLAGS)
        folderName(context.contentResolver, tree)
    }.getOrNull()

    /** Suelta el permiso de una carpeta que ya no se usa (no pasa nada si ya no estaba). */
    fun releaseFolder(context: Context, uri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), FLAGS) }
    }

    /** La app sigue teniendo permiso para escribir en [uri]. */
    private fun canWrite(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    /** Nombre de la carpeta («Download»); si el proveedor no lo da, el último trozo de su ruta. */
    private fun folderName(resolver: ContentResolver, tree: Uri): String {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = runCatching {
            resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
            ?: DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/').ifBlank { tree.toString() }
    }

    /** Escribe la copia de hoy en [uri] con el mismo CSV que EXPORTAR. En el hilo de E/S; no lanza. */
    suspend fun write(context: Context, uri: String, today: LocalDate = LocalDate.now()): BackupOutcome =
        withContext(Dispatchers.IO) {
            val app = ReelometroApp.from(context)
            val tree = runCatching { Uri.parse(uri) }.getOrNull()
            if (tree == null || !canWrite(app, tree)) return@withContext BackupOutcome.Failed(BackupError.NO_ACCESS)
            val folder = try {
                SafBackupFolder(app.contentResolver, tree)
            } catch (e: Exception) {
                return@withContext BackupOutcome.Failed(BackupRules.classify(e))
            }
            BackupRunner.run(
                folder = folder,
                today = today,
                hasData = { app.repository.observeAllTime().first().totals.count > 0 },
                export = { app.repository.exportCsv(it) },
            )
        }
}
