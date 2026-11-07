package com.reelometro.app.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.reelometro.app.data.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException

/**
 * Fotos de perfil de los **fijados** del envío rápido: un PNG pequeño ([AvatarRules.SIZE_PX]) por persona o
 * grupo en el almacenamiento privado de la app (`filesDir/avatars/`, con un hash del nombre como nombre de
 * fichero). Nunca salen del móvil: no van al CSV y la app no tiene copia de seguridad en la nube
 * (`allowBackup="false"`). Se borran al quitar el fijado y con BORRAR TODO.
 *
 * Cada fijado puede tener (1.4.3):
 *  - la foto **elegida** por el usuario de su galería ([saveChosen], `<hash>.mine.png`): manda sobre las demás, y la
 *    captura no la renueva ni la sobrescribe nunca; QUITAR FOTO ([clearChosen]) la borra y vuelve la automática;
 *  - la foto **recortada** de la hoja de compartir ([save], `<hash>.c2.png`, con el recorte de [AvatarRules.cropBox]);
 *  - la recortada **antes de la 1.4.3** (`<hash>.png`), que podía llevar la etiqueta de actividad de Instagram: se
 *    pinta mientras no haya otra, pero [savedAt] no la cuenta, así que se vuelve a recortar la próxima vez que salga
 *    la hoja; al guardar la nueva se borra.
 *
 * Lo usan el servicio (que las recorta y las pinta en las burbujas) y Ajustes (que las enseña en cada fila y guarda
 * la elegida), en el mismo proceso: [changes] avisa cuando cambia alguna. E/S de disco: fuera del hilo principal.
 */
class AvatarStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, DIR)

    // 1.10.0: cada app guarda sus fotos (la misma persona en Instagram y en TikTok son dos fijados). Instagram sigue con
    // los ficheros de siempre; pasa siempre la plataforma del fijado.
    private fun cropped(name: String, platform: Platform): File = File(dir, AvatarRules.croppedFileName(name, platform))

    private fun chosen(name: String, platform: Platform): File = File(dir, AvatarRules.chosenFileName(name, platform))

    private fun legacy(name: String, platform: Platform): File = File(dir, AvatarRules.fileName(name, platform))

    /** Cuándo se recortó la foto de [name] de la hoja (con el recorte actual), o `null` si no hay. */
    fun savedAt(name: String, platform: Platform = Platform.INSTAGRAM): Long? =
        cropped(name, platform).takeIf { it.isFile }?.lastModified()?.takeIf { it > 0 }

    /** ¿Tiene [name] una foto elegida de la galería? */
    fun hasChosen(name: String, platform: Platform = Platform.INSTAGRAM): Boolean = chosen(name, platform).isFile

    fun needsRefresh(name: String, now: Long, platform: Platform = Platform.INSTAGRAM): Boolean =
        AvatarRules.needsRefresh(savedAt(name, platform), now, hasChosen(name, platform))

    /** La foto que se pinta para [name] ([AvatarRules.pick]): la elegida, la recortada o la de antes; o `null`. */
    fun load(name: String, platform: Platform = Platform.INSTAGRAM): Bitmap? {
        val source = AvatarRules.pick(hasChosen(name, platform), cropped(name, platform).isFile, legacy(name, platform).isFile) ?: return null
        val file = when (source) {
            AvatarRules.Source.CHOSEN -> chosen(name, platform)
            AvatarRules.Source.CROPPED -> cropped(name, platform)
            AvatarRules.Source.LEGACY -> legacy(name, platform)
        }
        return try {
            BitmapFactory.decodeFile(file.path)
        } catch (e: RuntimeException) {
            null
        }
    }

    /** Guarda (o renueva) la foto recortada de la hoja para [name] y borra la de antes de la 1.4.3. */
    fun save(name: String, bitmap: Bitmap, platform: Platform = Platform.INSTAGRAM): Boolean {
        val ok = write(cropped(name, platform), bitmap)
        if (ok) {
            legacy(name, platform).delete()
            bump()
        }
        return ok
    }

    /** Guarda la foto que el usuario eligió de su galería para [name] (ya cuadrada, de [AvatarRules.SIZE_PX]). */
    fun saveChosen(name: String, bitmap: Bitmap, platform: Platform = Platform.INSTAGRAM): Boolean {
        val ok = write(chosen(name, platform), bitmap)
        if (ok) bump()
        return ok
    }

    /** QUITAR FOTO: borra solo la elegida; vuelve la recortada de la hoja (o las iniciales). */
    fun clearChosen(name: String, platform: Platform = Platform.INSTAGRAM): Boolean {
        val removed = chosen(name, platform).delete()
        if (removed) bump()
        return removed
    }

    /** Primero en un temporal, para no dejar nunca un PNG a medias. */
    private fun write(target: File, bitmap: Bitmap): Boolean = try {
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        target.delete()
        tmp.renameTo(target)
    } catch (e: IOException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    /**
     * Borra las fotos (elegidas y recortadas) de quien ya no está fijado **en ninguna app**: [pins] son todos los fijados
     * por app (`AppSettings.allQuickSendTargets()`). Pasar solo los de una app borraría las fotos de la otra.
     */
    fun retainOnly(pins: Map<Platform, Collection<String>>) {
        val keep = pins.flatMapTo(HashSet()) { (platform, names) -> names.flatMap { AvatarRules.fileNames(it, platform) } }
        var removed = false
        dir.listFiles()?.forEach { f -> if (f.name !in keep && f.name.removeSuffix(".tmp") !in keep && f.delete()) removed = true }
        if (removed) bump()
    }

    /** BORRAR TODO (también las elegidas). */
    fun clear() {
        if (dir.exists() && dir.deleteRecursively()) bump()
    }

    companion object {
        private const val DIR = "avatars"

        private val version = MutableStateFlow(0)

        /** Sube cada vez que se guarda o se borra una foto (para repintar burbujas y filas). */
        val changes: StateFlow<Int> = version.asStateFlow()

        private fun bump() = version.update { it + 1 }
    }
}
