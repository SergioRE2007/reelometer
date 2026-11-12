package com.reelometro.app.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Estado en vivo del servicio de accesibilidad, para Calibración y Ajustes. Solo vive en memoria:
 * tras reiniciarse la app, [connected] es false hasta que el sistema vuelve a conectar el servicio
 * (para saber si está activado en Ajustes del sistema usa `ReelAccessibilityService.isEnabled`).
 */
object ServiceStatus {
    private val _connected = MutableStateFlow(false)
    private val _lastEventAt = MutableStateFlow<Long?>(null)
    private val _lastVideoAt = MutableStateFlow<Long?>(null)

    /** El sistema ha conectado el servicio y está recibiendo eventos. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Hora (epoch ms) del último evento recibido de TikTok/Instagram/YouTube (con resolución de ~1 s). */
    val lastEventAt: StateFlow<Long?> = _lastEventAt.asStateFlow()

    /** Hora (epoch ms) del último vídeo nuevo contado. */
    val lastVideoAt: StateFlow<Long?> = _lastVideoAt.asStateFlow()

    private val _startError = MutableStateFlow<String?>(null)
    private val _startIssues = MutableStateFlow<List<String>>(emptyList())

    /**
     * El arranque del servicio ha fallado en lo imprescindible (repositorio o contador): no contará nada aunque esté
     * activado. Lo enseñan Hoy, la pantalla de conectar y la cabecera del registro de Calibración.
     */
    val startError: StateFlow<String?> = _startError.asStateFlow()

    /** Partes secundarias del arranque que han fallado (burbujas, receptor de pantalla…): cuenta igual. */
    val startIssues: StateFlow<List<String>> = _startIssues.asStateFlow()

    /** Empieza un arranque: borra los fallos del anterior. */
    internal fun onStarting() {
        _startError.value = null
        _startIssues.value = emptyList()
    }

    internal fun onStartFailed(error: Throwable) {
        _startError.value = describe(error)
    }

    internal fun onStartIssue(what: String, error: Throwable) {
        _startIssues.value = _startIssues.value + "$what: ${describe(error)}"
    }

    internal fun onConnected() {
        _connected.value = true
    }

    internal fun onDisconnected() {
        _connected.value = false
    }

    private val eventTotal = AtomicLong(0)
    private val _videoCount = MutableStateFlow(0L)

    /**
     * Eventos de las apps vigiladas desde que arrancó el proceso (sin limitar; un incremento atómico). Lo usa la
     * prueba de un minuto de la pantalla de conectar (`ServiceCheckViewModel`).
     */
    fun eventCount(): Long = eventTotal.get()

    /** Vídeos nuevos contados desde que arrancó el proceso (para la prueba de un minuto). */
    val videoCount: StateFlow<Long> = _videoCount.asStateFlow()

    /**
     * Contrato con `ReelAccessibilityService`: se llama en **cada** evento de una app vigilada (TikTok, Instagram,
     * YouTube), antes de filtrar por plataformas activadas. [lastEventAt] solo se publica como mucho una vez por
     * segundo; [eventCount] cuenta todos. Si el servicio deja de llamarlo, la prueba de un minuto deja de funcionar.
     */
    internal fun onEvent(now: Long) {
        eventTotal.incrementAndGet()
        val last = _lastEventAt.value
        if (last == null || now - last >= EVENT_RESOLUTION_MS || now < last) _lastEventAt.value = now
    }

    /** Contrato con `ReelAccessibilityService`: se llama en cada vídeo nuevo contado. */
    internal fun onVideo(now: Long) {
        _lastVideoAt.value = now
        _videoCount.update { it + 1 }
    }

    /** Avisos de poca memoria del sistema desde que arrancó el proceso (`onTrimMemory`). */
    data class MemoryWarnings(val count: Long = 0, val lastLevel: Int? = null, val lastAt: Long? = null)

    private val _memoryWarnings = MutableStateFlow(MemoryWarnings())

    /** Para la cabecera del registro de Calibración: si el sistema cerró la app por memoria, antes suele avisar. */
    val memoryWarnings: StateFlow<MemoryWarnings> = _memoryWarnings.asStateFlow()

    /**
     * Desde `ReelometroApp.onTrimMemory`. [TRIM_UI_HIDDEN] no cuenta: llega cada vez que se cierra la pantalla de la
     * app y no dice nada de la memoria del móvil.
     */
    fun onTrimMemory(level: Int, now: Long) {
        if (level == TRIM_UI_HIDDEN) return
        _memoryWarnings.update { MemoryWarnings(it.count + 1, level, now) }
    }

    /** `ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN`. */
    private const val TRIM_UI_HIDDEN = 20

    private fun describe(error: Throwable): String =
        listOfNotNull(error::class.java.simpleName, error.message?.take(MAX_MESSAGE)).joinToString(": ")

    private const val MAX_MESSAGE = 160
    private const val EVENT_RESOLUTION_MS = 1_000L
}
