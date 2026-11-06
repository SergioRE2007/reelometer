package com.reelometro.app.detection

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.IOException

/**
 * La foto que el usuario elige de su galería para la burbuja de un fijado (1.4.3), del selector de fotos de Android
 * (sin permisos). Se decodifica con muestreo ([AvatarRules.sampleSize]: una foto de 50 MP no se carga entera),
 * respetando la orientación EXIF, y se queda el cuadrado centrado ([AvatarRules.centerSquare]) escalado a
 * [AvatarRules.SIZE_PX]. La foto original no se copia ni se guarda: solo ese cuadrado, en [AvatarStore].
 *
 * E/S y decodificación: fuera del hilo principal.
 */
object AvatarImport {

    /** El cuadrado de [AvatarRules.SIZE_PX] de la foto de [uri], o `null` si no se puede leer. */
    fun decode(resolver: ContentResolver, uri: Uri): Bitmap? = try {
        val (source, orientation) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeModern(resolver, uri) to 1 // ImageDecoder ya aplica la orientación EXIF
        } else {
            decodeLegacy(resolver, uri) to orientation(resolver, uri)
        }
        source?.let { square(it, orientation) }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun decodeModern(resolver: ContentResolver, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // para dibujarla en un Canvas
            decoder.setTargetSampleSize(AvatarRules.sampleSize(info.size.width, info.size.height))
        }
    }

    /** Android 8: BitmapFactory con `inSampleSize` (primero solo las medidas). */
    private fun decodeLegacy(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = AvatarRules.sampleSize(bounds.outWidth, bounds.outHeight) }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** La orientación EXIF (1–8; 1 si no hay o no se lee), con el `ExifInterface` del sistema (API 24+). */
    private fun orientation(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: 1
    } catch (e: IOException) {
        1
    } catch (e: RuntimeException) {
        1
    }

    /** El cuadrado centrado de [bitmap], girado según [orientation] y escalado a [AvatarRules.SIZE_PX]. */
    private fun square(bitmap: Bitmap, orientation: Int): Bitmap? {
        try {
            val box = AvatarRules.centerSquare(bitmap.width, bitmap.height) ?: return null
            val size = AvatarRules.SIZE_PX
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // El cuadrado está centrado: girarlo (o darle la vuelta) después de recortarlo es lo mismo que antes.
            val half = size / 2f
            val matrix = Matrix()
            matrix.postRotate(AvatarRules.exifRotation(orientation).toFloat(), half, half)
            if (AvatarRules.exifMirror(orientation)) matrix.postScale(-1f, 1f, half, half)
            canvas.concat(matrix)
            canvas.drawBitmap(
                bitmap,
                Rect(box.left, box.top, box.right, box.bottom),
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            return out
        } finally {
            bitmap.recycle()
        }
    }
}
