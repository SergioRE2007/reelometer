package com.reelometro.app.detection

/**
 * Prueba de un minuto tras conectar el servicio: «abre TikTok, Instagram o YouTube, pasa 3 vídeos y vuelve». Al
 * volver se compara lo contado con la base guardada al empezar ([ServiceStatus.eventCount] y
 * [ServiceStatus.videoCount]) y se da un veredicto concreto. Regla pura (sin Android).
 *
 * @param processRestarted el proceso no es el que empezó la prueba (Android lo ha cerrado mientras tanto): los
 *   contadores en memoria han vuelto a cero y la base ya no vale.
 * @param eventsSince eventos de las apps vigiladas desde que empezó la prueba.
 * @param videosSince vídeos nuevos contados desde que empezó la prueba.
 * @param anyPlatformEnabled alguna app vigilada está activada en los ajustes de Reelometer.
 */
data class CheckInput(
    val enabled: Boolean,
    val connected: Boolean,
    val processRestarted: Boolean,
    val eventsSince: Long,
    val videosSince: Long,
    val anyPlatformEnabled: Boolean,
)

/** Por qué no ha contado nada (decide el texto y a dónde lleva el botón). */
enum class CheckFailure {
    /** El servicio está apagado en Ajustes › Accesibilidad. */
    OFF,

    /** Android ha cerrado la app durante la prueba. */
    KILLED,

    /** Activado, pero el sistema no lo ha conectado. */
    NOT_CONNECTED,

    /** Conectado, pero no ha llegado ningún evento de las apps (o no se han abierto). */
    NO_EVENTS,

    /** Todas las apps desactivadas en los ajustes de Reelometer. */
    PLATFORMS_OFF,

    /** Llegan eventos, pero ningún vídeo reconocido (no estaba en el feed, o hay que calibrar). */
    NO_VIDEOS,
}

sealed interface CheckVerdict {
    data class Passed(val videos: Long, val restarted: Boolean) : CheckVerdict

    data class Failed(val reason: CheckFailure) : CheckVerdict
}

object ServiceCheckRules {
    /** Lo que se pide pasar (basta con uno para demostrar que cuenta). */
    const val TARGET_VIDEOS = 3

    /** Una prueba guardada más vieja que esto se descarta. */
    const val MAX_AGE_MS = 30 * 60_000L

    /**
     * Prioridad: apagado › algún vídeo (superada) › proceso cerrado › sin conectar › apps desactivadas › sin eventos ›
     * eventos sin vídeos.
     */
    fun verdict(i: CheckInput): CheckVerdict = when {
        !i.enabled -> CheckVerdict.Failed(CheckFailure.OFF)
        i.videosSince >= 1 -> CheckVerdict.Passed(i.videosSince, i.processRestarted)
        i.processRestarted -> CheckVerdict.Failed(CheckFailure.KILLED)
        !i.connected -> CheckVerdict.Failed(CheckFailure.NOT_CONNECTED)
        // Antes que NO_EVENTS: con todas las apps desactivadas es lo único que hay que arreglar, haya o no eventos.
        !i.anyPlatformEnabled -> CheckVerdict.Failed(CheckFailure.PLATFORMS_OFF)
        i.eventsSince <= 0 -> CheckVerdict.Failed(CheckFailure.NO_EVENTS)
        else -> CheckVerdict.Failed(CheckFailure.NO_VIDEOS)
    }
}
