package com.reelometro.app.detection

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Recorta de la pantalla la **foto de perfil** de los fijados del envío rápido, y nada más (Android 11+,
 * `canTakeScreenshot`). Solo la pide el servicio con el envío rápido encendido, en la hoja de compartir de la app
 * del envío (Instagram o TikTok; aquí no hay nada de una app en concreto: los cuadrados los da [AvatarRules.cropBox])
 * y para un fijado cuya celda o fila está a la vista. De la captura se recortan solo los círculos de [crop] (en
 * memoria): el resto se descarta al momento y nunca se guarda.
 *
 * Si la captura falla (pantallas con `FLAG_SECURE`, demasiadas seguidas, Android lo rechaza), devuelve `null`
 * y la burbuja sigue con las iniciales.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal object AvatarCapture {

    private val direct = Executor { it.run() }

    /**
     * Una captura y un círculo de [AvatarRules.SIZE_PX] por cada cuadrado de [boxes] (coordenadas de pantalla).
     * `null` si no hay captura; un elemento `null` si ese cuadrado no cabe en ella.
     */
    suspend fun crop(service: AccessibilityService, boxes: List<NodeBounds>, timeoutMs: Long = TIMEOUT_MS): List<Bitmap?>? {
        if (boxes.isEmpty()) return emptyList()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val out = try {
                            cropAll(result, boxes)
                        } catch (e: RuntimeException) {
                            null
                        }
                        if (cont.isActive) cont.resume(out) else out?.forEach { it?.recycle() }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                try {
                    service.takeScreenshot(Display.DEFAULT_DISPLAY, direct, callback)
                } catch (e: RuntimeException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun cropAll(result: AccessibilityService.ScreenshotResult, boxes: List<NodeBounds>): List<Bitmap?>? {
        val buffer = result.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            val copy: Bitmap? = try {
                hardware.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                hardware.recycle()
            }
            val screen = copy ?: return null
            try {
                return boxes.map { circle(screen, it) }
            } finally {
                // La captura entera se descarta aquí: solo quedan los círculos.
                screen.recycle()
            }
        } finally {
            buffer.close()
        }
    }

    private fun circle(screen: Bitmap, box: NodeBounds): Bitmap? {
        if (box.isEmpty || box.left < 0 || box.top < 0 || box.right > screen.width || box.bottom > screen.height) return null
        val size = AvatarRules.SIZE_PX
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = size.toFloat() / box.width
        val shader = BitmapShader(screen, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setTranslate(-box.left.toFloat(), -box.top.toFloat())
                    postScale(scale, scale)
                },
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        Canvas(out).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return out
    }

    /** Lo que se espera a la captura antes de pulsar el nombre (si tarda más, se pulsa sin foto). */
    const val TIMEOUT_MS = 450L
}
