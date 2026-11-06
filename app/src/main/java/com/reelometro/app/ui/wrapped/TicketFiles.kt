package com.reelometro.app.ui.wrapped

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.reelometro.app.copy.ShareCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** El ticket ya pintado: la imagen para la vista previa, el PNG y su fichero en la caché. */
@Immutable
class RenderedTicket(val image: ImageBitmap, val png: ByteArray, val file: File)

/** Pinta, guarda y comparte el ticket (inyectable en el ViewModel; en la app, [AndroidTicketMaker]). */
interface TicketMaker {
    /** Pinta [data] y deja el PNG en la caché como [fileName]. Fuera del hilo principal. */
    suspend fun render(data: TicketData, fileName: String): RenderedTicket

    /** `content://` del fichero para compartirlo (lo vuelve a escribir si la caché se ha limpiado). */
    suspend fun shareUri(ticket: RenderedTicket): Uri

    /** Escribe el PNG en el documento que eligió la persona (SAF). */
    suspend fun save(ticket: RenderedTicket, target: Uri): Boolean
}

/**
 * Implementación real. Los ficheros van a `cacheDir/share/` (declarado en `res/xml/file_paths.xml`)
 * y se comparten con `${packageName}.fileprovider`: nada sale del móvil salvo lo que la persona
 * decida enviar.
 */
class AndroidTicketMaker(context: Context) : TicketMaker {
    private val app = context.applicationContext

    override suspend fun render(data: TicketData, fileName: String): RenderedTicket = withContext(Dispatchers.Default) {
        val bitmap = TicketRenderer.render(app, data)
        val png = TicketRenderer.encodePng(bitmap)
        val file = withContext(Dispatchers.IO) { TicketFiles.write(app, png, fileName) }
        RenderedTicket(bitmap.asImageBitmap(), png, file)
    }

    override suspend fun shareUri(ticket: RenderedTicket): Uri = withContext(Dispatchers.IO) {
        val file = if (ticket.file.exists()) ticket.file else TicketFiles.write(app, ticket.png, ticket.file.name)
        FileProvider.getUriForFile(app, TicketFiles.authority(app), file)
    }

    override suspend fun save(ticket: RenderedTicket, target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.openOutputStream(target, "w")?.use { it.write(ticket.png) } != null
        }.getOrDefault(false)
    }
}

internal object TicketFiles {
    /** Carpeta dentro de `cacheDir` (ver `file_paths.xml`). */
    const val DIR = "share"

    const val MIME = "image/png"

    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /** Escribe [png] como [fileName] y borra los tickets anteriores (solo se guarda el último). */
    fun write(context: Context, png: ByteArray, fileName: String): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.filter { it.isFile && it.name != fileName }?.forEach { it.delete() }
        return File(dir, fileName).apply { writeBytes(png) }
    }

    /** `ACTION_SEND` con la imagen y su texto, dentro del selector del sistema. */
    fun shareIntent(uri: Uri, text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            // Con ClipData el permiso de lectura llega también a la app elegida en el selector.
            clipData = ClipData.newRawUri(ShareCopy.CHOOSER_TITLE, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, ShareCopy.CHOOSER_TITLE)
    }
}
