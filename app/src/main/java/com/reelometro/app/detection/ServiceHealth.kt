package com.reelometro.app.detection

import android.content.Context
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Salud del servicio de accesibilidad vista desde la app. Que esté **activado** en Ajustes › Accesibilidad no
 * significa que funcione: en algunos móviles (sobre todo Xiaomi, Redmi y POCO) el sistema cierra la app y no vuelve a
 * conectar el servicio, y el interruptor sigue encendido. Entonces no llega ningún evento y no se cuenta nada.
 *
 * - [OFF]: apagado en Ajustes.
 * - [OK]: el sistema lo ha conectado ([ServiceStatus.connected]).
 * - [STARTING]: activado pero aún sin conectar, con el proceso recién arrancado (el sistema tarda un poco).
 * - [STALLED]: activado y sin conectar pasado [GRACE_MS], o su arranque ha fallado ([ServiceStatus.startError]).
 *
 * Todo vive en el mismo proceso: si el servicio está conectado, lo está en este proceso.
 */
enum class ServiceHealth {
    OFF,
    OK,
    STARTING,
    STALLED,
    ;

    companion object {
        /** Lo que se espera desde que arranca el proceso a que el sistema conecte el servicio (suele ser < 2 s). */
        const val GRACE_MS = 20_000L

        /**
         * Un proceso que arranca antes de este tiempo de encendido del móvil se considera arrancado «al reiniciar».
         * La app no arranca en modo de arranque directo: su proceso nace tras desbloquear, y con PIN o patrón entre el
         * encendido y el desbloqueo pasan fácilmente más de 60 s; por eso la ventana es de varios minutos.
         */
        const val BOOT_WINDOW_MS = 5 * 60_000L

        /**
         * Margen tras reiniciar el móvil: el receptor de arranque de WorkManager (o el widget) puede arrancar el proceso
         * antes de que Android conecte los servicios de accesibilidad, que espera al desbloqueo y a que el sistema se
         * asiente. Sin este margen saldría un aviso falso de «no funciona» tras cada reinicio.
         */
        const val BOOT_GRACE_MS = 150_000L

        /**
         * Regla pura. [processAgeMs]: tiempo desde que arrancó el proceso; [graceMs]: margen para conectar
         * ([GRACE_MS], o [BOOT_GRACE_MS] si el proceso arrancó justo tras reiniciar: [graceFor]).
         */
        fun of(
            enabled: Boolean,
            connected: Boolean,
            startFailed: Boolean,
            processAgeMs: Long,
            graceMs: Long = GRACE_MS,
        ): ServiceHealth = when {
            !enabled -> OFF
            connected -> OK
            startFailed -> STALLED
            processAgeMs < graceMs -> STARTING
            else -> STALLED
        }

        /** Margen según el tiempo de encendido del móvil cuando arrancó el proceso (regla pura). */
        fun graceFor(uptimeAtProcessStartMs: Long): Long =
            if (uptimeAtProcessStartMs < BOOT_WINDOW_MS) BOOT_GRACE_MS else GRACE_MS

        fun processAgeMs(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

        /** Tiempo de encendido del móvil al arrancar el proceso (`elapsedRealtime` cuenta desde el arranque). */
        fun uptimeAtProcessStartMs(): Long = Process.getStartElapsedRealtime()

        /** Margen que aplican todos los consumidores (aviso, Hoy, widget, pantalla de conectar): el mismo tras reiniciar. */
        fun defaultGraceMs(): Long = graceFor(uptimeAtProcessStartMs())

        fun current(context: Context, graceMs: Long = defaultGraceMs()): ServiceHealth = of(
            enabled = ReelAccessibilityService.isEnabled(context),
            connected = ServiceStatus.connected.value,
            startFailed = ServiceStatus.startError.value != null,
            processAgeMs = processAgeMs(),
            graceMs = graceMs,
        )

        /** Como [current], pero si aún está en [STARTING] espera a que conecte o a que pase el margen. */
        suspend fun settle(context: Context, graceMs: Long = defaultGraceMs()): ServiceHealth {
            val remaining = graceMs - processAgeMs()
            if (remaining > 0 && current(context, graceMs) == STARTING) {
                withTimeoutOrNull(remaining) { ServiceStatus.connected.first { it } }
            }
            return current(context, graceMs)
        }

        /**
         * Momentos en que la salud puede cambiar sin volver a la pantalla: se conecta, falla el arranque o se acaba el
         * margen. (Activarlo o apagarlo en Ajustes se ve al volver, en `ON_RESUME`.)
         */
        fun changes(): Flow<Unit> = merge(
            ServiceStatus.connected.map { },
            ServiceStatus.startError.map { },
            flow {
                val remaining = defaultGraceMs() - processAgeMs()
                if (remaining > 0) {
                    delay(remaining + 250)
                    emit(Unit)
                }
            },
        )
    }
}
