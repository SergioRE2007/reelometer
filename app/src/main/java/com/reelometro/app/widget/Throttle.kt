package com.reelometro.app.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay

/**
 * Atiende las peticiones de [requests] (un canal `CONFLATED`) ejecutando [action] como mucho una
 * vez cada [intervalMs]. La primera petición se atiende al momento; las que llegan durante la espera
 * se funden en una sola, que se atiende al acabarla. La pendiente se descarta justo antes de
 * ejecutar [action] (que lee los datos), así que siempre hay una ejecución final con lo último.
 * Un fallo de [action] no detiene el bucle. Vuelve cuando se cierra el canal.
 */
internal suspend fun throttle(requests: ReceiveChannel<Unit>, intervalMs: Long, action: suspend () -> Unit) {
    for (request in requests) {
        requests.tryReceive()
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Se reintenta con la siguiente petición.
        }
        delay(intervalMs)
    }
}
