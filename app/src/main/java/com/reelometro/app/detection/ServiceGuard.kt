package com.reelometro.app.detection

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlin.coroutines.CoroutineContext

/**
 * Fallos del servicio de accesibilidad que se han **contenido** en vez de tirar el proceso. Un fallo sin capturar en
 * una corrutina, en un `Runnable` del hilo principal o en un evento cierra la app entera; y en los móviles que no
 * vuelven a enlazar el servicio (Xiaomi y compañía) eso lo deja «activado, sin contar» hasta que el usuario lo apaga
 * y lo enciende. Aquí solo se cuentan y se guarda el último, para la cabecera del registro de Calibración.
 *
 * Solo en memoria. Del fallo se guarda la clase y el sitio del código de la app donde saltó (`Fichero.kt:línea`),
 * nunca el mensaje: podría llevar texto leído de la pantalla.
 */
class ServiceFailures(private val clock: () -> Long = System::currentTimeMillis) {

    /** [lastWhere]: `Fichero.kt:línea` del primer marco de la app, si se conoce (en la release ofuscada puede faltar). */
    data class Snapshot(
        val count: Long = 0,
        val lastAt: Long? = null,
        val lastWhat: String? = null,
        val lastError: String? = null,
        val lastWhere: String? = null,
    )

    private var snapshot = Snapshot()

    /** [what]: qué se estaba haciendo («evento 4096», «lectura de pantalla»…), sin datos del usuario. */
    @Synchronized
    fun record(what: String, error: Throwable) {
        snapshot = Snapshot(
            count = snapshot.count + 1,
            lastAt = clock(),
            lastWhat = what,
            lastError = error::class.java.simpleName.ifEmpty { "Throwable" },
            lastWhere = whereOf(error),
        )
    }

    @Synchronized
    fun snapshot(): Snapshot = snapshot

    /** VACIAR en Calibración. */
    @Synchronized
    fun reset() {
        snapshot = Snapshot()
    }

    companion object {
        /** Los del servicio en marcha. */
        val live = ServiceFailures()

        private const val APP_PACKAGE = "com.reelometro."

        /** Primer marco de la pila que es código de la app: dice dónde saltó sin enseñar el mensaje. */
        internal fun whereOf(error: Throwable): String? = runCatching {
            error.stackTrace.firstOrNull { it.className.startsWith(APP_PACKAGE) }?.let { frame ->
                val file = frame.fileName ?: frame.className.substringAfterLast('.')
                if (frame.lineNumber > 0) "$file:${frame.lineNumber}" else file
            }
        }.getOrNull()
    }
}

/**
 * Red para que nada de lo que hace el servicio pueda cerrar la app: [handler] para sus ámbitos de corrutinas (sin
 * él, un fallo sin capturar en un `launch` llega al hilo y tumba el proceso) y [run] para lo que corre directamente
 * en el hilo principal (eventos, `Runnable`, receptores, toques en las burbujas). Lo contenido se apunta en
 * [ServiceFailures].
 *
 * Se captura `Throwable`, no solo `Exception`: un `NoSuchMethodError` de una ROM modificada o un
 * `OutOfMemoryError` al copiar una captura también cierran la app, y aquí seguir contando vale más que morir.
 */
object ServiceGuard {

    /**
     * @param scope nombre del ámbito, que se apunta si la corrutina no lleva [CoroutineName].
     * @param onFailure aviso a quien lo crea (para soltar lo que la corrutina dejara a medias); recibe el contexto de
     *   la corrutina que falló. Nunca debe lanzar.
     */
    fun handler(
        scope: String,
        failures: ServiceFailures = ServiceFailures.live,
        onFailure: (CoroutineContext, Throwable) -> Unit = { _, _ -> },
    ): CoroutineExceptionHandler = CoroutineExceptionHandler { context, error ->
        failures.record(context[CoroutineName]?.name ?: scope, error)
        runCatching { onFailure(context, error) }
    }

    /** Ejecuta [block]; si lanza, lo apunta, avisa con [onFailure] y sigue. Devuelve si acabó bien. */
    inline fun run(
        what: String,
        failures: ServiceFailures = ServiceFailures.live,
        onFailure: (Throwable) -> Unit = {},
        block: () -> Unit,
    ): Boolean = try {
        block()
        true
    } catch (error: Throwable) {
        failures.record(what, error)
        runCatching { onFailure(error) }
        false
    }
}
