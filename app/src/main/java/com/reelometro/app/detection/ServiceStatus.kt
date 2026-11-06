package com.reelometro.app.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    internal fun onConnected() {
        _connected.value = true
    }

    internal fun onDisconnected() {
        _connected.value = false
    }

    /** Se llama en cada evento; solo publica como mucho una vez por segundo. */
    internal fun onEvent(now: Long) {
        val last = _lastEventAt.value
        if (last == null || now - last >= EVENT_RESOLUTION_MS || now < last) _lastEventAt.value = now
    }

    internal fun onVideo(now: Long) {
        _lastVideoAt.value = now
    }

    private const val EVENT_RESOLUTION_MS = 1_000L
}
